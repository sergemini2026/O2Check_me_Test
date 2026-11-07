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
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HrvForegroundService extends Service {

    private static final boolean DEBUG = true;
    private static final String TAG = "HrvForegroundService";

    public static final String CHANNEL_ID = "HrvServiceChannel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND";

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;

    private final HrvSessionManager trainingSessionManager = new HrvSessionManager();
    private final HrvSessionManager testSessionManager = new HrvSessionManager();

    private final List<DataPoint> trainingDataPoints = Collections.synchronizedList(new ArrayList<>());
    private final List<DataPoint> testDataPoints = Collections.synchronizedList(new ArrayList<>());

    private final List<Integer> slidingWindowList = Collections.synchronizedList(new ArrayList<>());
    private boolean isPostiTraceActive = false;

    private final List<Integer> rsaCycleBuffer = Collections.synchronizedList(new ArrayList<>());
    private int rsaCycleAccumulatedMs = 0;
    private static final int TARGET_CYCLE_MS = 10000;

    // --- МАКРО-СЛОЙ И НЕЗАВИСИМЫЕ СЧЕТЧИКИ ИНДЕКСОВ ---
    private static final int TARGET_MACRO_RR_COUNT = 300;
    private final List<Integer> macroRrBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> macroHrWindow = Collections.synchronizedList(new ArrayList<>());
    
    // Целевой параметр x (1, 2 или 3) и независимые счетчики сессии
    private int trainingTargetX = 1;
    private int globalUniqueCounter = 0;
    private int trainingDumpCounter = 0;
    private int testDumpCounter = 0;
    private int currentBlockNumber = 0;

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
    private long currentModulationPeriodMs = 10000;

    public static class QuantumContext {
        private final int globalUniqueIndex;
        private final int trainingDumpIndex;
        private final int blockNumber;
        private final boolean isPstiPhase;
        private final int targetX;
        private final float macroPhase;

        public QuantumContext(int globalUniqueIndex, int trainingDumpIndex, int blockNumber, boolean isPstiPhase, int targetX, float macroPhase) {
            this.globalUniqueIndex = globalUniqueIndex;
            this.trainingDumpIndex = trainingDumpIndex;
            this.blockNumber = blockNumber;
            this.isPstiPhase = isPstiPhase;
            this.targetX = targetX;
            this.macroPhase = macroPhase;
        }

        public int getGlobalUniqueIndex() { return globalUniqueIndex; }
        public int getTrainingDumpIndex() { return trainingDumpIndex; }
        public int getBlockNumber() { return blockNumber; }
        public boolean isPstiPhase() { return isPstiPhase; }
        public int getTargetX() { return targetX; }
        public float getMacroPhase() { return macroPhase; }
    }

    public interface ServiceCallback {
        void onRrDataReceived(int rrMs, int hr);
        void onO2DataReceived(int spo2, int hr);
        void onConnectionStatusChanged(String deviceType, boolean isConnected);
        default void onPhaseCorrectionCalculated(float correctionDelta) {}
        default void onMacroDumpCompleted(int avgHr, HrvCalculator.Metrics hrv, int rrCount, QuantumContext context) {}
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

    public void setPresetPattern(int targetX) {
        this.trainingTargetX = Math.max(1, targetX);
        if (DEBUG) {
            Log.d(TAG, "[PRESET] Установлен целевой параметр тренировочных дампов x = " + trainingTargetX);
        }
    }

    public void setAudioResource(int resId) {
        this.currentAudioResId = resId;
        if (isRecording) {
            if (serviceAudioEngine != null) {
                serviceAudioEngine.stop();
                serviceAudioEngine = null;
            }
            serviceAudioEngine = new AudioPacerEngine(this, currentAudioResId);
            serviceAudioEngine.prepareAsync(() -> {
                if (serviceAudioEngine != null && isRecording) {
                    serviceAudioEngine.start();
                }
            });
        }
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
        trainingSessionManager.resetSession();
        testSessionManager.resetSession();
        synchronized (trainingDataPoints) { trainingDataPoints.clear(); }
        synchronized (testDataPoints) { testDataPoints.clear(); }
        synchronized (slidingWindowList) { slidingWindowList.clear(); }
        synchronized (snapshotBuffer) { snapshotBuffer.clear(); }
        synchronized (rsaCycleBuffer) { rsaCycleBuffer.clear(); }
        synchronized (macroRrBuffer) { macroRrBuffer.clear(); }
        synchronized (macroHrWindow) { macroHrWindow.clear(); }
        
        globalUniqueCounter = 0;
        trainingDumpCounter = 0;
        testDumpCounter = 0;
        currentBlockNumber = 0;

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
        synchronized (macroRrBuffer) {
            macroRrBuffer.clear();
        }
        synchronized (macroHrWindow) {
            macroHrWindow.clear();
        }
        rsaCycleAccumulatedMs = 0;
        
        updateNotificationText("Мониторинг активен (запись остановлена)");
    }

    public void startPostiRecording() {
        testSessionManager.resetSession();
        synchronized (testDataPoints) { testDataPoints.clear(); }
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
        return testSessionManager.getRawSessionData();
    }

    public void addDataPoint(DataPoint dp) {
        if (!isRecording) return;
        if (isPostiTraceActive) {
            synchronized (testDataPoints) {
                testDataPoints.add(dp);
            }
        } else {
            synchronized (trainingDataPoints) {
                trainingDataPoints.add(dp);
            }
        }
    }

    public List<DataPoint> getTrainingDataPoints() {
        synchronized (trainingDataPoints) {
            return new ArrayList<>(trainingDataPoints);
        }
    }

    public List<DataPoint> getTestDataPoints() {
        synchronized (testDataPoints) {
            return new ArrayList<>(testDataPoints);
        }
    }

    public void addRrInterval(int rrMs) {
        if (isPostiTraceActive) {
            testSessionManager.addRrInterval(rrMs);
            return;
        }

        if (isRecording) {
            trainingSessionManager.addRrInterval(rrMs);
            
            if (rrMs > 300 && rrMs < 2000) {
                // 1. МИКРО-УРОВЕНЬ: Скользящее окно (150/50 RR)
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

                // 2. Оценка дыхательного цикла (10 сек)
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

                // 3. МАКРО-УРОВЕНЬ: Накопление 300 RR для макро-дампа
                synchronized (macroRrBuffer) {
                    macroRrBuffer.add(rrMs);
                    int instantHr = Math.round(60000.0f / rrMs);
                    macroHrWindow.add(instantHr);

                    if (macroRrBuffer.size() >= TARGET_MACRO_RR_COUNT) {
                        List<Integer> dumpSnapshotData = new ArrayList<>(macroRrBuffer);
                        int avgHr = calculateAverageHr(macroHrWindow);

                        macroRrBuffer.clear();
                        macroHrWindow.clear();

                        processAndSaveMacroDump(dumpSnapshotData, avgHr);
                    }
                }
            }
        }
    }

    private int calculateAverageHr(List<Integer> hrList) {
        if (hrList == null || hrList.isEmpty()) return 0;
        int sum = 0;
        for (int hr : hrList) {
            sum += hr;
        }
        return Math.round((float) sum / hrList.size());
    }

    private void processAndSaveMacroDump(List<Integer> dumpData, int avgHr) {
        try {
            HrvCalculator.Metrics metrics = HrvCalculator.calculate(dumpData);
            if (metrics != null && metrics.isValid) {
                globalUniqueCounter++;
                trainingDumpCounter++;
                currentBlockNumber++;

                boolean isCycleComplete = (currentBlockNumber >= trainingTargetX);

                // Вычисляем фазу Майера по завершенному макро-дампу (300 RR)
                float macroPhase = MayerWaveAnalyzer.calculateInitialPhase(dumpData, currentModulationPeriodMs);

                QuantumContext quantum = new QuantumContext(globalUniqueCounter, trainingDumpCounter, currentBlockNumber, isCycleComplete, trainingTargetX, macroPhase);
                float currentModulationSec = currentModulationPeriodMs / 1000.0f;

                // Создаем макро-дамп с использованием нового строгого конструктора
                SessionSnapshot dumpSnapshot = new SessionSnapshot(
                        System.currentTimeMillis(),
                        metrics,
                        currentModulationSec,
                        false, // isPstiPhase
                        true,  // isDumpSnapshot
                        globalUniqueCounter,
                        trainingDumpCounter,
                        0,     // testDumpIndex
                        currentBlockNumber
                );

                synchronized (snapshotBuffer) {
                    snapshotBuffer.add(dumpSnapshot);
                }

                if (DEBUG) {
                    Log.d(TAG, String.format(Locale.US, "[HRV_SERVICE] [МАКРО-ДАМП] Сформирован дамп (Global #%d, Training #%d) | Блок %d/%d | Переход к PSTI: %b | RMSSD: %.1f ms | Фаза Майера: %.2f",
                            globalUniqueCounter, trainingDumpCounter, currentBlockNumber, trainingTargetX, isCycleComplete, metrics.rmssd, macroPhase));
                }

                if (callback != null) {
                    callback.onMacroDumpCompleted(avgHr, metrics, TARGET_MACRO_RR_COUNT, quantum);
                }

                if (isCycleComplete) {
                    currentBlockNumber = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void evaluateCyclePhaseCorrection(List<Integer> cycleRrList) {
        if (cycleRrList == null || cycleRrList.size() < 5) return;
        try {
            float measuredPhase = MayerWaveAnalyzer.calculateInitialPhase(cycleRrList, currentModulationPeriodMs);
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
                int activeBlockNum = Math.max(1, currentBlockNumber);

                // Для микро-срезов сквозные индексы равны 0, а blockNumber отражает текущую фазу
                SessionSnapshot snapshot = new SessionSnapshot(
                        System.currentTimeMillis(),
                        metrics,
                        currentModulationSec,
                        isPostiTraceActive,
                        false, // isDumpSnapshot
                        0,     // globalUniqueIndex
                        0,     // trainingDumpIndex
                        0,     // testDumpIndex
                        activeBlockNum
                );
                synchronized (snapshotBuffer) {
                    snapshotBuffer.add(snapshot);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getSessionRrData() {
        return trainingSessionManager.getRawSessionData();
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

    public void addSnapshot(SessionSnapshot snapshot) {
        synchronized (snapshotBuffer) {
            snapshotBuffer.add(snapshot);
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
