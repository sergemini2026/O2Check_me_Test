package com.local.o2test;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements O2BleManager.BleListener {

    private static final String PREF_NAME = "O2AppPrefs";
    private static final int PI_SMOOTHING_WINDOW = 5;
    private static final int CALIBRATION_TARGET = 100;
    private static final int TARGET_RR_PER_DUMP = 300;     // 300 RR-интервалов на 1 дамп
    private static final int POSTI_WINDOW_SEC = 300;       // 300 секунд для теста PSTI
    private static final int POSTI_INTERVAL_DUMPS = 3;     // Замер каждые 3 дампа

    private O2BleManager bleManager;
    private PolarH10Manager polarManager;
    private UiBuilder.Views ui;
    private OxygenSensorManager oxygenSensorManager;
    private SessionModulationManager modulationManager;
    private BreatheProgressManager progressManager; // Менеджер прогресс-бара

    private HrvForegroundService hrvService;
    private boolean isBound = false;

    private boolean isRecording = false;
    private boolean isTrainingModeActive = false;
    private boolean isCalibrationCompleted = false;
    private int sessionDumpCount = 0;
    private long sessionStartTime = 0;
    private long postiStartTime = 0;

    private long fixedModulationPeriodMs = 10000L;
    private int completedCyclesInCurrentDump = 0;

    private String currentWorkoutMode = "Базовый мониторинг";
    private final List<DataPoint> sessionData = new ArrayList<>();
    private final List<Integer> phaseBuffer = new ArrayList<>();
    private final Queue<Float> piWindow = new LinkedList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final Handler pstiHandler = new Handler(Looper.getMainLooper());
    private Runnable pstiCountdownRunnable;
    private int pstiRemainingSec = POSTI_WINDOW_SEC;

    private double lastPstiScore = 0.0;
    private double lastPstiRmssdRatio = 0.0;

    private int currentSpo2 = 0;
    private float currentPi = 0.0f;
    private int currentBattery = 0;
    private int currentPolarHr = 0;
    private int currentPolarBattery = 0;
    private int prevRrMs = 0;
    private int consecutiveArtifactsCount = 0;

    private long activeProfileId = -1L;
    private boolean isProfileAuthorized = false;
    private String activeProfileName = "Не выбран";

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            HrvForegroundService.LocalBinder binder = (HrvForegroundService.LocalBinder) service;
            hrvService = binder.getService();
            isBound = true;
            
            updateRightPanelStatus();
            if (ui.btnHeart != null) {
                ui.btnHeart.setAlpha(isProfileAuthorized ? 1.0f : 0.5f);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            hrvService = null;
        }
    };

    private final SettingsDialogManager.SettingsCallback settingsCallback = new SettingsDialogManager.SettingsCallback() {
        @Override
        public void onLog(String message) {
            MainActivity.this.onLog(message);
        }

        @Override
        public void onAudioResourceChanged(int resId, String trackName) {
            if (isBound && hrvService != null) {
                hrvService.setAudioResource(resId);
            }
        }

        @Override
        public void onExportBackupRequested(File backupFile) {
            SessionExportManager.exportDatabaseBackup(MainActivity.this, backupFile, dbExecutor, msg -> onLog(msg));
        }

        @Override
        public void onImportBackupRequested(File backupFile) {
            SessionExportManager.importDatabaseBackup(MainActivity.this, backupFile, dbExecutor, msg -> onLog(msg));
        }

        @Override
        public boolean isProfileAuthorized() {
            return isProfileAuthorized;
        }

        @Override
        public String getActiveProfileName() {
            return activeProfileName;
        }

        @Override
        public void openProfilesManager() {
            MainActivity.this.openProfilesManager();
        }
    };

    private final PasswordAuthManager.AuthCallback authCallback = new PasswordAuthManager.AuthCallback() {
        @Override
        public void onPasswordAuthorized() {
            onLog("Система запущена. Выберите профиль для начала работы.");
        }

        @Override
        public void onExitRequested() {
            stopAudioAndClockInService();
            if (ui != null && ui.pacerView != null) ui.pacerView.stopMonitoring();
            finish();
        }

        @Override
        public void onLog(String message) {
            MainActivity.this.onLog(message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new O2BleManager(this, this);
        polarManager = new PolarH10Manager(this, polarCallback);
        modulationManager = new SessionModulationManager(this);

        ui = UiBuilder.buildUi(this);
        oxygenSensorManager = new OxygenSensorManager(ui);
        
        // Инициализация менеджера прогресс-бара
        progressManager = new BreatheProgressManager(ui.pacerView);

        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, 0, 0, 0f, 0, 0);

        setInterfaceAuthorizedState(false);
        updateMetricsDisplay("--", "--", "--", "--");
        updateRightPanelStatus();

        if (ui.pacerView != null) {
            ui.pacerView.setOnPointerChangeListener(value -> {
                long newDurationMs = (long) (value * 1000.0);
                modulationManager.setModulationPeriodMs(newDurationMs);
                modulationManager.applyModulation(hrvService, isBound);
                onLog(String.format(Locale.US, "[МОДУЛЯЦИЯ] Период изменен пользователем: %.1f сек", value));
            });
        }

        ui.btnHeart.setOnClickListener(v -> {
            if (!isProfileAuthorized) {
                Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
                openProfilesManager();
                return;
            }
            if (!isRecording) {
                isTrainingModeActive = false;
                startMonitoringPanel();
            } else {
                stopMonitoring();
            }
        });

        ui.btnReconnect.setOnClickListener(v -> {
            if (!isProfileAuthorized) {
                Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
                openProfilesManager();
                return;
            }
            
            if (!isLocationEnabled(this)) {
                Toast.makeText(this, "Для работы Bluetooth необходимо включить геолокацию!", Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            onLog("Подключение BLE устройств и запрос прав...");
            checkAndRequestBlePermissions();
        });

        ui.btnSave.setOnClickListener(v -> {
            if (!isProfileAuthorized) {
                Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
                openProfilesManager();
                return;
            }
            saveSessionAutomatically();
        });

        ui.btnSettings.setOnClickListener(v -> {
            SettingsDialogManager.showSettingsDialog(this, getSharedPreferences(PREF_NAME, MODE_PRIVATE), settingsCallback);
        });

        ui.btnExit.setOnClickListener(v -> {
            stopAudioAndClockInService();
            if (ui != null && ui.pacerView != null) {
                ui.pacerView.stopMonitoring();
            }
            isRecording = false;
            finish();
        });

        if (ui.btnWorkout != null) {
            ui.btnWorkout.setOnClickListener(v -> TrainingDialogHelper.openTrainingDialog(
                    this, dbExecutor, activeProfileId, activeProfileName, isProfileAuthorized,
                    new TrainingDialogHelper.TrainingStartCallback() {
                        @Override
                        public void onStartTraining(String trainingType, String modeName) {
                            currentWorkoutMode = trainingType + ", " + modeName;
                            isTrainingModeActive = true;
                            onLog("[СТАРТ] " + currentWorkoutMode);
                            startMonitoringPanel();
                        }
                        @Override
                        public void onRequireProfileAuth() {
                            openProfilesManager();
                        }
                    }
            ));
        }

        if (ui.btnSessions != null) {
            ui.btnSessions.setOnClickListener(v -> SessionDialogHelper.openSessionsDialog(
                    this, dbExecutor, activeProfileId, activeProfileName, isProfileAuthorized
            ));
        }

        if (ui.btnProfiles != null) {
            ui.btnProfiles.setOnClickListener(v -> openProfilesManager());
        }

        setContentView(ui.mainLayout);
        PasswordAuthManager.checkMasterPasswordOnStart(this, getSharedPreferences(PREF_NAME, MODE_PRIVATE), authCallback);
    }

    private boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}
        return gpsEnabled || networkEnabled;
    }

    private void checkAndRequestBlePermissions() {
        if (!isProfileAuthorized) {
            Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
            openProfilesManager();
            return;
        }
        if (PermissionHelper.checkAndRequestBlePermissions(this)) {
            startHrvForegroundService();
            bleManager.initAndStartScan();
        } else {
            onLog("Запрос разрешений BLE датчиков...");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.BLE_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                onLog("Разрешения BLE получены, запуск сканирования...");
                startHrvForegroundService();
                bleManager.initAndStartScan();
            } else {
                onLog("Ошибка: необходимые разрешения BLE не предоставлены!");
            }
        }
    }

    private void saveSessionAutomatically() {
        SessionExportManager.saveSessionAutomatically(
                this,
                getSharedPreferences(PREF_NAME, MODE_PRIVATE),
                sessionData,
                activeProfileId,
                activeProfileName,
                sessionStartTime,
                currentPolarHr,
                currentSpo2,
                lastPstiScore,
                lastPstiRmssdRatio,
                dbExecutor,
                hrvService,
                isBound,
                msg -> onLog(msg)
        );
    }

    private void updateRightPanelStatus() {
        updateRightPanelStatusWithCountdown(pstiRemainingSec);
    }

    private void updateRightPanelStatusWithCountdown(int seconds) {
        if (ui.tvStatusValue == null) return;
        boolean isPostiActive = isBound && hrvService != null && hrvService.isPostiTraceActive();
        
        String pstiSuffix = (lastPstiScore > 0.0) ? String.format(Locale.US, " PSTI [%.1f%%]", lastPstiScore) : "";

        if (!isRecording) {
            ui.tvStatusValue.setText("Статус: Ожидание" + pstiSuffix);
        } else {
            if (isPostiActive) {
                ui.tvStatusValue.setText(String.format(Locale.US, "Статус: Замер следа (PSTI) - %d сек%s", seconds, pstiSuffix));
            } else if (isTrainingModeActive) {
                ui.tvStatusValue.setText("Статус: " + currentWorkoutMode + pstiSuffix);
            } else {
                ui.tvStatusValue.setText("Статус: Пранаяма" + pstiSuffix);
            }
        }
    }

    private void updateMetricsDisplay(String rmssd, String pnn50, String lfHf, String tp) {
        if (ui.tvHrvMetrics != null) {
            String displayStr = String.format(Locale.US,
                    "Профиль: %s  |  RMSSD: %s  |  pNN50: %s  |  LF/HF: %s  |  TP: %s",
                    activeProfileName, rmssd, pnn50, lfHf, tp);
            ui.tvHrvMetrics.setText(displayStr);
        }
    }

    private void setInterfaceAuthorizedState(boolean authorized) {
        float alphaVal = authorized ? 1.0f : 0.5f;

        if (ui.btnWorkout != null) {
            ui.btnWorkout.setEnabled(authorized);
            ui.btnWorkout.setAlpha(alphaVal);
        }
        if (ui.btnSessions != null) {
            ui.btnSessions.setEnabled(authorized);
            ui.btnSessions.setAlpha(alphaVal);
        }
        if (ui.btnHeart != null) {
            ui.btnHeart.setEnabled(authorized);
            ui.btnHeart.setAlpha(authorized && isRecording ? 1.0f : alphaVal);
        }
        if (ui.btnReconnect != null) {
            ui.btnReconnect.setEnabled(authorized);
            ui.btnReconnect.setAlpha(alphaVal);
        }
        if (ui.btnSave != null) {
            ui.btnSave.setEnabled(authorized);
            ui.btnSave.setAlpha(alphaVal);
        }
    }

    private void openProfilesManager() {
        ProfileDialogHelper.openProfilesDialog(this, dbExecutor, new ProfileDialogHelper.ProfileCallback() {
            @Override
            public void onProfileAuthorized(long profileId, String profileName) {
                activeProfileId = profileId;
                activeProfileName = profileName;
                isProfileAuthorized = true;
                
                setInterfaceAuthorizedState(true);
                updateMetricsDisplay("--", "--", "--", "--");
                onLog("[GATEKEEPER] Активный профиль: " + profileName + " (ID: " + profileId + ")");
                Toast.makeText(MainActivity.this, "Профиль активирован. Доступна связь с датчиками.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProfileDeleted(long deletedProfileId) {
                if (activeProfileId == deletedProfileId) {
                    if (isRecording) {
                        stopMonitoring();
                    }
                    activeProfileId = -1L;
                    activeProfileName = "Не выбран";
                    isProfileAuthorized = false;
                    setInterfaceAuthorizedState(false);
                    updateMetricsDisplay("--", "--", "--", "--");
                    onLog("[GATEKEEPER] Активный профиль удален. Сессия завершена.");
                    Toast.makeText(MainActivity.this, "Активный профиль был удален. Сессия остановлена.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onLog(String message) {
                MainActivity.this.onLog(message);
            }
        });
    }

    private void startHrvForegroundService() {
        Intent serviceIntent = new Intent(this, HrvForegroundService.class);
        serviceIntent.setAction(HrvForegroundService.ACTION_START_FOREGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, HrvForegroundService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void animateHeartPulse() {
        if (ui.btnHeart == null || !isRecording) return;
        ui.btnHeart.animate().scaleX(1.18f).scaleY(1.18f).setDuration(110)
            .withEndAction(() -> {
                if (ui.btnHeart != null) {
                    ui.btnHeart.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).start();
                }
            }).start();
    }

    private void startMonitoringPanel() {
        if (!isProfileAuthorized || activeProfileId == -1L) {
            onLog("[GATEKEEPER] Сеанс заблокирован: профиль не выбран!");
            Toast.makeText(this, "Требуется авторизация в профиле!", Toast.LENGTH_SHORT).show();
            openProfilesManager();
            return;
        }

        stopAudioAndClockInService();

        isRecording = true;
        isCalibrationCompleted = false;
        sessionDumpCount = 0;
        completedCyclesInCurrentDump = 0;
        lastPstiScore = 0.0;
        lastPstiRmssdRatio = 0.0;

        fixedModulationPeriodMs = (isBound && hrvService != null) ? hrvService.getModulationPeriodMs() : modulationManager.getModulationPeriodMs();

        // Запуск через менеджер прогресс-бара
        if (progressManager != null) {
            progressManager.start();
        }

        if (isBound && hrvService != null) {
            hrvService.stopPostiRecording();
            hrvService.startSessionRecording();
            
            modulationManager.applyModulation(hrvService, isBound);
            
            hrvService.setCallback(new HrvForegroundService.ServiceCallback() {
                @Override
                public void onRrDataReceived(int rrMs, int hr) {}
                @Override
                public void onO2DataReceived(int spo2, int hr) {}
                @Override
                public void onConnectionStatusChanged(String deviceType, boolean isConnected) {}

                @Override
                public void onPhaseCorrectionCalculated(float correctionDelta) {
                    runOnUiThread(() -> {
                        if (isBound && hrvService != null && isRecording) {
                            hrvService.adjustServicePhase(correctionDelta);
                        }
                    });
                }
            });
        }
        
        if (polarManager != null) {
            polarManager.startCollection();
        }

        sessionStartTime = System.currentTimeMillis();
        synchronized (sessionData) { sessionData.clear(); }
        synchronized (phaseBuffer) { phaseBuffer.clear(); }
        piWindow.clear();
        prevRrMs = 0;
        consecutiveArtifactsCount = 0;
        if (ui.chartView != null) ui.chartView.clearData();
        
        updateMetricsDisplay("--", "--", "--", "--");
        updateRightPanelStatus();
        if (ui.btnHeart != null) ui.btnHeart.setAlpha(1.0f);
        onLog("Мониторинг активен для профиль: " + activeProfileName + ", период T = " + fixedModulationPeriodMs + " мс");
    }

    private void startClockAndPacerAfterCalibration(float initialPhase) {
        if (!isRecording || !isBound || hrvService == null) return;

        hrvService.startAudioAndClock(MainActivity.this, initialPhase, phase -> {
            if (isRecording && !hrvService.isPostiTraceActive() && isCalibrationCompleted) {
                // Передаем тик в менеджер прогресс-бара
                if (progressManager != null) {
                    progressManager.onClockTick(phase);
                }
            }
        });
    }

    private void startPostiTraceWindow() {
        postiStartTime = System.currentTimeMillis();
        pstiRemainingSec = POSTI_WINDOW_SEC;
        
        stopAudioAndClockInService();

        if (isBound && hrvService != null) {
            hrvService.startPostiRecording();
        }

        pstiHandler.removeCallbacks(pstiCountdownRunnable);
        pstiCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (isBound && hrvService != null && hrvService.isPostiTraceActive()) {
                    if (pstiRemainingSec > 0) {
                        pstiRemainingSec--;
                        float pstiProgress = (float) pstiRemainingSec / (float) POSTI_WINDOW_SEC;
                        
                        // Делегируем отображение стека PSTI менеджеру
                        if (progressManager != null) {
                            progressManager.startPostiStack(pstiProgress);
                        }
                        
                        updateRightPanelStatusWithCountdown(pstiRemainingSec);
                        pstiHandler.postDelayed(this, 1000);
                    }
                }
            }
        };
        pstiHandler.postDelayed(pstiCountdownRunnable, 1000);

        updateRightPanelStatusWithCountdown(pstiRemainingSec);
        onLog("[PSTI] Модуляция остановлена. Запуск 2-минутного окна замера индекса консолидации барорефлекса PSTI в сервисе...");
    }

    private void evaluateAndFinishPostiTrace() {
        pstiHandler.removeCallbacks(pstiCountdownRunnable);

        List<Integer> snapshotRr = new ArrayList<>();
        if (isBound && hrvService != null) {
            snapshotRr = hrvService.getPostiRrData();
            hrvService.stopPostiRecording();
        }

        if (!snapshotRr.isEmpty()) {
            List<Integer> baselineRr = new ArrayList<>();
            if (isBound && hrvService != null) {
                baselineRr = hrvService.getSessionRrData();
            }

            HrvCalculator.PstiResult pstiResult = HrvCalculator.calculatePsti(baselineRr, snapshotRr);
            if (pstiResult.isValid) {
                lastPstiScore = pstiResult.pstiScore;
                lastPstiRmssdRatio = pstiResult.pstiRmssdRatio;
                onLog(String.format(Locale.US, "[PSTI РЕЗУЛЬТАТ] Индекс консолидации барорефлекса PSTI: %.1f%% (RMSSD ratio: %.2f)",
                        lastPstiScore, lastPstiRmssdRatio));
            } else {
                HrvCalculator.Metrics postiMetrics = HrvCalculator.calculate(snapshotRr);
                if (postiMetrics != null) {
                    lastPstiScore = Math.min(100.0, (postiMetrics.rmssd / 50.0) * 50.0 + (Math.min(postiMetrics.totalPower, 2500.0) / 2500.0) * 50.0);
                    lastPstiRmssdRatio = 1.0;
                    onLog(String.format(Locale.US, "[PSTI РЕЗУЛЬТАТ (fallback)] Индекс консолидации PSTI: %.1f%%", lastPstiScore));
                }
            }
        } else {
            onLog("[PSTI] Окно замера завершено, но данные RR отсутствуют.");
        }

        updateRightPanelStatus();

        onLog("[PSTI] Завершено. Возобновление дыхательного контура и аудио...");
        
        if (progressManager != null) {
            progressManager.finishPostiStack();
        }

        completedCyclesInCurrentDump = 0;

        runOnUiThread(() -> {
            if (isRecording && isBound && hrvService != null) {
                startClockAndPacerAfterCalibration(0.0f);
            }
        });
    }

    private void stopMonitoring() {
        isRecording = false;
        isTrainingModeActive = false;
        isCalibrationCompleted = false;
        sessionDumpCount = 0;
        completedCyclesInCurrentDump = 0;
        pstiHandler.removeCallbacks(pstiCountdownRunnable);
        
        stopAudioAndClockInService();

        if (isBound && hrvService != null) {
            hrvService.stopSessionRecording();
            hrvService.stopPostiRecording();
        }
        
        if (polarManager != null) {
            polarManager.stopCollection();
        }

        piWindow.clear();
        
        if (progressManager != null) {
            progressManager.reset();
        }

        updateRightPanelStatus();
        if (ui.btnHeart != null) {
            ui.btnHeart.setAlpha(isProfileAuthorized ? 1.0f : 0.5f);
        }
        onLog("Мониторинг остановлен и сброшен.");
    }

    private void stopAudioAndClockInService() {
        pstiHandler.removeCallbacks(pstiCountdownRunnable);
        if (isBound && hrvService != null) {
            hrvService.stopAudioAndClock();
        }
    }

    private float getSmoothedPi(float rawPi) {
        piWindow.add(rawPi);
        if (piWindow.size() > PI_SMOOTHING_WINDOW) piWindow.poll();
        float sum = 0f;
        for (float val : piWindow) sum += val;
        return sum / piWindow.size();
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> {
            if (ui.tvLog != null) {
                ui.tvLog.append(message + "\n");
                if (ui.logScrollView != null) {
                    ui.logScrollView.post(() -> ui.logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            }
        });
    }

    @Override
    public void onDataReceived(byte[] data) {
        O2Parser.ParseResult res = O2Parser.parse(data);
        long now = System.currentTimeMillis();
        float smoothedPi = getSmoothedPi(res.pi);

        currentSpo2 = res.spo2;
        currentPi = smoothedPi;
        currentBattery = res.battery;

        boolean isPolarActive = polarManager != null && polarManager.isConnected();
        int hrToDisplay = isPolarActive ? currentPolarHr : res.hr;
        int polarBatt = polarManager != null ? polarManager.getBatteryLevel() : currentPolarBattery;

        runOnUiThread(() -> UiBuilder.updateStatusHeader(ui.tvLiveMetrics, currentSpo2, hrToDisplay, currentPi, currentBattery, polarBatt));

        if (!isPolarActive && res.isFingerOn && isRecording) {
            if (sessionStartTime == 0) sessionStartTime = now;
            int elapsedSec = (int) ((now - sessionStartTime) / 1000);
            String timestamp = timeFormat.format(new Date(now));
            DataPoint dp = new DataPoint(timestamp, elapsedSec, currentSpo2, res.hr, currentPi, 0);

            synchronized (sessionData) { sessionData.add(dp); }
            if (isBound && hrvService != null) {
                hrvService.addDataPoint(dp);
            }
            runOnUiThread(() -> ui.chartView.addDataPoint(dp));
        }
    }

    @Override
    public void onPolarDeviceFound(BluetoothDevice device) {
        if (polarManager != null) polarManager.connect(device);
    }

    private final PolarH10Manager.PolarCallback polarCallback = new PolarH10Manager.PolarCallback() {
        @Override
        public void onPolarLog(String message) { onLog(message); }

        @Override
        public void onPolarBatteryReceived(int battery) {
            currentPolarBattery = battery;
            int polarBatt = polarManager != null ? polarManager.getBatteryLevel() : currentPolarBattery;
            boolean isPolarActive = polarManager != null && polarManager.isConnected();
            int hrToDisplay = isPolarActive ? currentPolarHr : 0;
            runOnUiThread(() -> UiBuilder.updateStatusHeader(ui.tvLiveMetrics, currentSpo2, hrToDisplay, currentPi, currentBattery, polarBatt));
        }

        @Override
        public void onRrReceived(int rrMs, float instantHr) {
            if (isRecording) {
                boolean isArtifact = false;
                if (prevRrMs > 0) {
                    if (Math.abs(rrMs - prevRrMs) / (float) prevRrMs > 0.20) {
                        consecutiveArtifactsCount++;
                        if (consecutiveArtifactsCount < 3) {
                            isArtifact = true;
                            onLog("[АРТЕФАКТ] Скачок RR: исключен.");
                        }
                    }
                }
                if (!isArtifact) {
                    if (isBound && hrvService != null) {
                        hrvService.addRrInterval(rrMs);
                    }

                    consecutiveArtifactsCount = 0;
                    prevRrMs = rrMs;
                    currentPolarHr = Math.round(instantHr);

                    boolean isPostiActive = isBound && hrvService != null && hrvService.isPostiTraceActive();
                    if (isPostiActive) {
                        long elapsedPostiSec = (System.currentTimeMillis() - postiStartTime) / 1000;
                        if (elapsedPostiSec >= POSTI_WINDOW_SEC) {
                            evaluateAndFinishPostiTrace();
                        }
                    }

                    if (!isCalibrationCompleted && (!isBound || hrvService == null || !hrvService.isPostiTraceActive())) {
                        synchronized (phaseBuffer) {
                            phaseBuffer.add(rrMs);
                            int count = phaseBuffer.size();
                            float percent = Math.min(100.0f, (count / (float) CALIBRATION_TARGET) * 100.0f);

                            // Калибровочный прогресс через менеджер
                            if (progressManager != null) {
                                progressManager.setCalibrationProgress(percent);
                            }

                            if (count < CALIBRATION_TARGET) {
                                String statusText = String.format(Locale.US, "Идет сбор данных для фазовой автоподстройки: %.0f%%", percent);
                                runOnUiThread(() -> {
                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.setText(statusText);
                                    }
                                });
                            } else {
                                isCalibrationCompleted = true;
                                completedCyclesInCurrentDump = 0;

                                final float initialPhase = MayerWaveAnalyzer.calculateInitialPhase(phaseBuffer, fixedModulationPeriodMs);

                                onLog(String.format(Locale.US, "[Калибровка завершена] Вычисленная фаза Майера: %.2f", initialPhase));

                                runOnUiThread(() -> {
                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.setText("Сбор завершен");
                                    }
                                    
                                    if (isBound && hrvService != null) {
                                        hrvService.adjustServicePhase(initialPhase);
                                    }

                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.postDelayed(() -> updateRightPanelStatus(), 1000);
                                    }

                                    startClockAndPacerAfterCalibration(initialPhase);
                                });
                            }
                        }
                    }

                    long now = System.currentTimeMillis();
                    if (sessionStartTime == 0) sessionStartTime = now;
                    float elapsedSec = (now - sessionStartTime) / 1000f;
                    String timestamp = timeFormat.format(new Date(now));
                    DataPoint dp = new DataPoint(timestamp, (int) elapsedSec, currentSpo2, currentPolarHr, currentPi, rrMs);

                    synchronized (sessionData) { sessionData.add(dp); }
                    if (isBound && hrvService != null) {
                        hrvService.addDataPoint(dp);
                    }
                    int polarBatt = polarManager != null ? polarManager.getBatteryLevel() : currentPolarBattery;

                    runOnUiThread(() -> {
                        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, currentSpo2, currentPolarHr, currentPi, currentBattery, polarBatt);
                        ui.chartView.addDataPoint(dp);
                        animateHeartPulse();
                    });
                }
            }
        }

        @Override
        public void onPolarHrReceived(int hr, HrvCalculator.Metrics hrv, int rrCount) {
            int totalTarget = TARGET_RR_PER_DUMP; // 300
            float percent = Math.min(100.0f, (rrCount / (float) totalTarget) * 100.0f);
            int remainingBeats = Math.max(0, totalTarget - rrCount);

            int remainingSec = 0;
            if (hr > 0 && remainingBeats > 0) {
                remainingSec = (int) Math.round((remainingBeats * 60.0) / hr);
            }
            int remMin = remainingSec / 60;
            int remSec = remainingSec % 60;

            if (rrCount < totalTarget) {
                String rmssdStr = (hrv != null) ? String.format(Locale.US, "%.1f ms", hrv.rmssd) : "--";
                String logMsg = String.format(Locale.US,
                        "[Сбор дампа] %.1f%% (%d/%d) | До конца: %02d:%02d | HR: %d bpm | RMSSD: %s",
                        percent, rrCount, totalTarget, remMin, remSec, hr, rmssdStr);
                onLog(logMsg);

                if (isBound && hrvService != null) {
                    hrvService.updateNotificationText("HR: " + hr + " bpm | RMSSD: " + rmssdStr);
                }
            } else {
                String logMsg = String.format(Locale.US,
                        "[Дамп ГОТОВ 100%%] HR: %d bpm | RMSSD: %.1f ms | pNN50: %.1f%% | LF/HF: %.2f | TP: %.0f ms²",
                        hr, hrv.rmssd, hrv.pnn50, hrv.lfHfRatio, hrv.totalPower);
                onLog(logMsg);

                sessionDumpCount++;
                completedCyclesInCurrentDump = 0;

                onLog(String.format(Locale.US, "[Дамп #%d завершен]", sessionDumpCount));

                if (sessionDumpCount % POSTI_INTERVAL_DUMPS == 0) {
                    startPostiTraceWindow();
                    if (isBound && hrvService != null) {
                        hrvService.updateNotificationText("Запись завершена. Замер PSTI...");
                    }
                } else {
                    onLog("[Мониторинг] Продолжение сеанса дыхания (следующий замер PSTI через " + (POSTI_INTERVAL_DUMPS - (sessionDumpCount % POSTI_INTERVAL_DUMPS)) + " дамп(а))...");
                    if (isBound && hrvService != null) {
                        hrvService.updateNotificationText("HR: " + hr + " bpm | RMSSD: " + (hrv != null ? String.format(Locale.US, "%.1f ms", hrv.rmssd) : "--"));
                    }
                }

                if (hrv != null) {
                    String rmssdStr = String.format(Locale.US, "%.1f ms", hrv.rmssd);
                    String pnn50Str = String.format(Locale.US, "%.1f%%", hrv.pnn50);
                    String lfHfStr = String.format(Locale.US, "%.2f", hrv.lfHfRatio);
                    String tpStr = String.format(Locale.US, "%.0f ms²", hrv.totalPower);

                    runOnUiThread(() -> updateMetricsDisplay(rmssdStr, pnn50Str, lfHfStr, tpStr));
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        stopAudioAndClockInService();
        
        if (ui != null && ui.pacerView != null) {
            ui.pacerView.stopMonitoring();
        }
        
        if (bleManager != null) bleManager.close();
        if (polarManager != null) polarManager.disconnect();
    }
}
