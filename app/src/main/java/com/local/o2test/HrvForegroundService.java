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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HrvForegroundService extends Service {

    public static final String CHANNEL_ID = "HrvServiceChannel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND";

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;

    private final List<Integer> rrSessionList = Collections.synchronizedList(new ArrayList<>());
    private boolean isRecording = false;

    private ServiceCallback callback;

    // Буфер обработанных снимков и фоновый планировщик
    private final List<SessionSnapshot> snapshotBuffer = Collections.synchronizedList(new ArrayList<>());
    private ScheduledExecutorService scheduler;
    
    // Указатель для реализации скользящего окна без дублирования одних и тех же данных
    private int lastProcessedIndex = 0;

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

        // Запуск фонового планировщика для расчета метрик и наполнения буфера снимков каждые 30 секунд
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processSnapshotCalculation, 10, 30, TimeUnit.SECONDS);
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

    // --- Управление накопительным буфером сессии ---

    public void startSessionRecording() {
        synchronized (rrSessionList) {
            rrSessionList.clear();
        }
        synchronized (snapshotBuffer) {
            snapshotBuffer.clear();
        }
        lastProcessedIndex = 0;
        isRecording = true;
        updateNotificationText("Идет запись сессии ВСР...");
    }

    public void stopSessionRecording() {
        isRecording = false;
        updateNotificationText("Мониторинг активен (запись остановлена)");
    }

    public void addRrInterval(int rrMs) {
        if (isRecording && rrMs > 0) {
            rrSessionList.add(rrMs);
        }
    }

    public List<Integer> getSessionRrData() {
        synchronized (rrSessionList) {
            return new ArrayList<>(rrSessionList);
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Фоновый расчет метрик по реальному скользящему окну за последние 30-45 секунд
     */
    private void processSnapshotCalculation() {
        if (!isRecording) return;
        try {
            List<Integer> currentData = getSessionRrData();
            int totalSize = currentData.size();
            
            // Требуем минимум 30 точек (~30 ударов сердца) для формирования достоверного среза
            if (totalSize - lastProcessedIndex < 30 && totalSize < 30) return;

            // Берем скользящее окно за последний интервал (или хвост из последних 120 точек)
            int windowSize = 120;
            int startIndex = Math.max(0, totalSize - windowSize);
            List<Integer> windowData = currentData.subList(startIndex, totalSize);

            // Расчет через математическое ядро с учетом фильтрации и валидации
            HrvCalculator.Metrics metrics = HrvCalculator.calculate(windowData);

            // Если окно прошло пострасчетную валидацию, добавляем в буфер снимков
            if (metrics != null && metrics.isValid) {
                SessionSnapshot snapshot = new SessionSnapshot(System.currentTimeMillis(), metrics);
                snapshotBuffer.add(snapshot);
                lastProcessedIndex = totalSize;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
