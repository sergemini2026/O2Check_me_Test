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

    private final List<Integer> rrSessionList = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> slidingWindowList = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> postiRrBuffer = Collections.synchronizedList(new ArrayList<>());
    private boolean isPostiTraceActive = false;

    private final List<Integer> rsaCycleBuffer = Collections.synchronizedList(new ArrayList<>());
    private int rsaCycleAccumulatedMs = 0;
    private static final int TARGET_CYCLE_MS = 10000;

    private boolean isRecording = false;
    private ServiceCallback callback;

    private final List<SessionSnapshot> snapshotBuffer = Collections.synchronizedList(new ArrayList<>());

    private static final int WINDOW_SIZE = 150;
    private static final int STEP_SIZE = 50;
    private int counterSinceLastProcess = 0;

    // --- АУДИО И ТАКТОВЫЙ КОНТУР В СЕРВИСЕ ДЛЯ ФОНОВОЙ РАБОТЫ ---
    private AudioPacerEngine serviceAudioEngine;
    private MasterClock serviceMasterClock;
    private int currentAudioResId = R.raw.o2test_01;
    private long currentModulationPeriodMs = 10000; // Текущий период модуляции по умолчанию (10 с)

    public interface ServiceCallback {
        void onRrDataReceived(int rrMs, int hr);
        void onO2DataReceived(int spo2, int hr);
        void onConnectionStatusChanged(String deviceType, boolean isConnected);
        default void onPhaseCorrectionCalculated(float correctionDelta) {}
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
        stopAudioAndClock();
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

    public void setAudioResource(int resId) {
        this.currentAudioResId = resId;
    }

    public void setModulationPeriod(long durationMs) {
        this.currentModulationPeriodMs = Math.max(6000, Math.min(14000, durationMs));
        if (serviceMasterClock != null) {
            serviceMasterClock.setBaseDurationMs(this.currentModulationPeriodMs);
        }
    }

    public long getModulationPeriodMs() {
        return currentModulationPeriodMs;
    }

    public void startAudioAndClock(Context context, float initialPhase, MasterClock.MasterClockListener tickListener) {
        stopAudioAndClock();
        
        serviceAudioEngine = new AudioPacerEngine(context, currentAudioResId);
        serviceAudioEngine.prepareAsync(() -> {
            if (serviceAudioEngine != null && isRecording) {
                serviceAudioEngine.start();
            }
        });

        serviceMasterClock = new MasterClock(phase -> {
            if (serviceAudioEngine != null) {
                serviceAudioEngine.updatePhase(phase);
            }
            if (tickListener != null) {
                tickListener.onTick(phase);
            }
        });
        
        // Устанавливаем текущую длительность перед стартом
        serviceMasterClock.setBaseDurationMs(currentModulationPeriodMs);
        serviceMasterClock.start(initialPhase);
    }

    public void adjustServicePhase(float targetPhase) {
        if (serviceMasterClock != null) {
            serviceMasterClock.adjustPhase(targetPhase);
        }
    }

    public void stopAudioAndClock() {
        if (serviceMasterClock != null) {
            serviceMasterClock.stop();
            serviceMasterClock = null;
        }
        if (serviceAudioEngine != null) {
            serviceAudioEngine.stop();
            serviceAudioEngine = null;
        }
    }

    public void startSessionRecording() {
        synchronized (rrSessionList) { rrSessionList.clear(); }
        synchronized (slidingWindowList) { slidingWindowList.clear(); }
        synchronized (snapshotBuffer) { snapshotBuffer.clear(); }
        synchronized (postiRrBuffer) { postiRrBuffer.clear(); }
        synchronized (rsaCycleBuffer) { rsaCycleBuffer.clear(); }
        rsaCycleAccumulatedMs = 0;
        isPostiTraceActive = false;
        counterSinceLastProcess = 0;
        isRecording = true;
        updateNotificationText("Идет запись сессии ВСР...");
    }

    public void stopSessionRecording() {
        isRecording = false;
        isPostiTraceActive = false;
        stopAudioAndClock();
        
        synchronized (slidingWindowList) {
            if (slidingWindowList.size() >= 16 && counterSinceLastProcess > 0) {
                processAndSaveWindow(new ArrayList<>(slidingWindowList));
            }
        }
        synchronized (rsaCycleBuffer) {
            rsaCycleBuffer.clear();
        }
        rsaCycleAccumulatedMs = 0;
        
        updateNotificationText("Мониторинг активен (запись остановлена)");
    }

    public void startPostiRecording() {
        synchronized (postiRrBuffer) {
            postiRrBuffer.clear();
        }
        isPostiTraceActive = true;
        stopAudioAndClock();
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
            if (isPostiTraceActive) {
                synchronized (postiRrBuffer) {
                    postiRrBuffer.add(rrMs);
                }
                return;
            }

            if (isRecording) {
                synchronized (rrSessionList) {
                    rrSessionList.add(rrMs);
                }
                
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

                synchronized (rsaCycleBuffer) {
                    rsaCycleBuffer.add(rrMs);
                    rsaCycleAccumulatedMs += rrMs;

                    if (rsaCycleAccumulatedMs >= TARGET_CYCLE_MS) {
                        List<Integer> cycleCopy = new ArrayList<>(rsaCycleBuffer);
                        rsaCycleBuffer.clear();
                        rsaCycleAccumulatedMs = 0;

                        evaluateCyclePhaseCorrection(cycleCopy);
                    }
                }
            }
        }
    }

    private void evaluateCyclePhaseCorrection(List<Integer> cycleRrList) {
        if (cycleRrList == null || cycleRrList.size() < 5) return;
        try {
            float measuredPhase = MayerWaveAnalyzer.calculateInitialPhase(cycleRrList);
            if (callback != null) {
                callback.onPhaseCorrectionCalculated(measuredPhase);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processAndSaveWindow(List<Integer> windowData) {
        try {
            HrvCalculator.Metrics metrics = HrvCalculator.calculate(windowData);
            if (metrics != null && metrics.isValid) {
                float currentModulationSec = currentModulationPeriodMs / 1000.0f;
                SessionSnapshot snapshot = new SessionSnapshot(System.currentTimeMillis(), metrics, currentModulationSec);
                synchronized (snapshotBuffer) {
                    snapshotBuffer.add(snapshot);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    public List<SessionSnapshot> flushSnapshotBuffer() {
        synchronized (snapshotBuffer) {
            List<SessionSnapshot> snapshotCopy = new ArrayList<>(snapshotBuffer);
            snapshotBuffer.clear();
            return snapshotCopy;
        }
    }

    @Override
    public void onDestroy() {
        stopAudioAndClock();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
