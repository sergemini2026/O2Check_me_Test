package com.local.o2test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HrvForegroundService extends Service {

    public static final String CHANNEL_ID = "HrvServiceChannel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND";

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;

    // Полный накопительный массив всей сессии (для графиков, CSV и общего анализа)
    private final List<Integer> rrSessionList = Collections.synchronizedList(new ArrayList<>());
    
    // Буфер для накопления текущих RR-интервалов перед расчетом окна
    private final List<Integer> slidingWindowList = Collections.synchronizedList(new ArrayList<>());
    
    // Изолированный потокобезопасный буфер и флаг для замера следовой когерентности (PSTI)
    private final List<Integer> postiRrBuffer = Collections.synchronizedList(new ArrayList<>());
    private boolean isPostiTraceActive = false;

    private boolean isRecording = false;
    private ServiceCallback callback;

    // Буфер готовых снимков для базы данных и хронологического лога PDF отчета
    private final List<SessionSnapshot> snapshotBuffer = Collections.synchronizedList(new ArrayList<>());

    // Настройки скользящего окна
    private static final int WINDOW_SIZE = 150; // Размер окна анализа
    private static final int STEP_SIZE = 50;    // Шаг смещения (новые удары для следующего расчёта)
    private int counterSinceLastProcess = 0;

    public interface ServiceCallback {
        void onRrDataReceived(int rrMs, int hr);
        void onO2DataReceived(int spo2, int hr);
        void onConnectionStatusChanged(String deviceType, boolean isConnected);
    }

    public class LocalBinder extends Binder {
        public HrvForegroundService getService() {
            return HrvForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "O2Test::HrvWakeLock");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_START_FOREGROUND.equals(action)) {
                startForegroundServiceInternal();
            } else if (ACTION_STOP_FOREGROUND.equals(action)) {
                stopForegroundServiceInternal();
            }
        }
        return START_STICKY;
    }

    private void startForegroundServiceInternal() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        Notification notification = buildNotification("Мониторинг ВСР активен...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundServiceInternal() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
        stopSelf();
    }

    public void updateNotificationText(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("O2Test — Запись сессии")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "HRV Monitoring Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    public ServiceCallback getCallback() {
        return callback;
    }

    // --- Управление накопительными буферами ---

    public void startSessionRecording() {
        synchronized (rrSessionList) {
            rrSessionList.clear();
        }
        synchronized (slidingWindowList) {
            slidingWindowList.clear();
        }
        synchronized (snapshotBuffer) {
            snapshotBuffer.clear();
        }
        synchronized (postiRrBuffer) {
            postiRrBuffer.clear();
        }
        isPostiTraceActive = false;
        counterSinceLastProcess = 0;
        isRecording = true;
        updateNotificationText("Идет запись сессии ВСР...");
    }

    public void stopSessionRecording() {
        isRecording = false;
        isPostiTraceActive = false;
        
        synchronized (slidingWindowList) {
            if (slidingWindowList.size() >= 16 && counterSinceLastProcess > 0) {
                processAndSaveWindow(new ArrayList<>(slidingWindowList));
            }
        }
        
        updateNotificationText("Мониторинг активен (запись остановлена)");
    }

    // --- Управление режимом замера следа (PSTI) ---

    public void startPostiRecording() {
        synchronized (postiRrBuffer) {
            postiRrBuffer.clear();
        }
        isPostiTraceActive = true;
        updateNotificationText("Замер следовой когерентности (PSTI)...");
    }

    public void stopPostiRecording() {
        isPostiTraceActive = false;
        updateNotificationText("Замер PSTI завершен");
    }

    public boolean isPostiTraceActive() {
        return isPostiTraceActive;
    }

    public List<Integer> getPostiRrData() {
        synchronized (postiRrBuffer) {
            return new ArrayList<>(postiRrBuffer);
        }
    }

    public void addRrInterval(int rrMs) {
        if (rrMs > 300 && rrMs < 2000) {
            // Если активен этап замера следовой когерентности (PSTI), пишем в изолированный буфер
            if (isPostiTraceActive) {
                synchronized (postiRrBuffer) {
                    postiRrBuffer.add(rrMs);
                }
                return;
            }

            if (isRecording) {
                // 1. Накапливаем абсолютно все RR-интервалы в длинный массив всей сессии
                synchronized (rrSessionList) {
                    rrSessionList.add(rrMs);
                }
                
                // 2. Управляем скользящим окном для хронологических снимков
                synchronized (slidingWindowList) {
                    slidingWindowList.add(rrMs);
                    
                    while (slidingWindowList.size() > WINDOW_SIZE) {
                        slidingWindowList.remove(0);
                    }

                    counterSinceLastProcess++;

                    if (slidingWindowList.size() >= WINDOW_SIZE && counterSinceLastProcess >= STEP_SIZE) {
                        counterSinceLastProcess = 0;
                        processAndSaveWindow(new ArrayList<>(slidingWindowList));
                    }
                }
            }
        }
    }

    /**
     * Расчет метрик по текущему срезу скользящего окна и запись в буфер снимков
     */
    private void processAndSaveWindow(List<Integer> windowData) {
        try {
            HrvCalculator.Metrics metrics = HrvCalculator.calculate(windowData);
            if (metrics != null && metrics.isValid) {
                SessionSnapshot snapshot = new SessionSnapshot(System.currentTimeMillis(), metrics);
                synchronized (snapshotBuffer) {
                    snapshotBuffer.add(snapshot);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Получение полного массива всей сессии для анализа и экспорта
     */
    public List<Integer> getSessionRrData() {
        synchronized (rrSessionList) {
            return new ArrayList<>(rrSessionList);
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Атомарный сброс и получение буфера готовых снимков для записи в БД Room
     */
    public List<SessionSnapshot> flushSnapshotBuffer() {
        synchronized (snapshotBuffer) {
            List<SessionSnapshot> snapshotCopy = new ArrayList<>(snapshotBuffer);
            snapshotBuffer.clear();
            return snapshotCopy;
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
