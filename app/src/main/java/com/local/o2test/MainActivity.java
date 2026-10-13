package com.local.o2test;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.SessionEntity;

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
    private static final String KEY_MASTER_PASS = "master_password";
    private static final int PI_SMOOTHING_WINDOW = 5;

    private O2BleManager bleManager;
    private PolarH10Manager polarManager;
    private UiBuilder.Views ui;

    private HrvForegroundService hrvService;
    private boolean isBound = false;

    private boolean isRecording = false;
    private boolean isTrainingModeActive = false;
    private long sessionStartTime = 0;
    private String currentWorkoutMode = "Базовый мониторинг";
    private final List<DataPoint> sessionData = new ArrayList<>();
    private final Queue<Float> piWindow = new LinkedList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private int currentSpo2 = 0;
    private float currentPi = 0.0f;
    private int currentBattery = 0;
    private int currentPolarHr = 0;
    private int currentPolarBattery = 0;
    private int prevRrMs = 0;
    private int consecutiveArtifactsCount = 0;

    // --- GATEKEEPER & PROFILE STATE ---
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
            
            // Синхронизируем состояние записи с сервисом вместо жесткого сброса в false
            if (hrvService != null) {
                isRecording = hrvService.isRecording();
            } else {
                isRecording = false;
            }
            
            updateRightPanelStatus();
            if (ui.btnHeart != null) {
                ui.btnHeart.setAlpha(isRecording ? 1.0f : (isProfileAuthorized ? 1.0f : 0.5f));
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            hrvService = null;
        }
    };

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new O2BleManager(this, this);
        polarManager = new PolarH10Manager(this, polarCallback);

        ui = UiBuilder.buildUi(this);
        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, 0, 0, 0f, 0, 0);

        // ИСХОДНОЕ СОСТОЯНИЕ: блокируем все, кроме Профилей, Настроек и Выхода
        setInterfaceAuthorizedState(false);
        updateMetricsDisplay("--", "--", "--", "--");
        updateRightPanelStatus();

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
            onLog("Переподключение BLE устройств...");
            checkAndRequestPermissions();
        });

        ui.btnSave.setOnClickListener(v -> {
            if (!isProfileAuthorized) {
                Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
                return;
            }
            saveData();
        });

        ui.btnSettings.setOnClickListener(v -> onLog("Открытие настроек..."));
        ui.btnExit.setOnClickListener(v -> finish());

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

        // Проверка и запрос мастер-пароля при старте программы
        checkMasterPasswordOnStart();
    }

    private void updateRightPanelStatus() {
        if (ui.tvStatusValue == null) return;
        if (!isRecording) {
            ui.tvStatusValue.setText("");
        } else {
            if (isTrainingModeActive) {
                ui.tvStatusValue.setText(currentWorkoutMode);
            } else {
                ui.tvStatusValue.setText("Мониторинг");
            }
        }
    }

    private void checkMasterPasswordOnStart() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedPass = prefs.getString(KEY_MASTER_PASS, null);

        if (savedPass == null) {
            showCreateMasterPasswordDialog(prefs);
        } else {
            showEnterMasterPasswordDialog(savedPass);
        }
    }

    private void showCreateMasterPasswordDialog(SharedPreferences prefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Создание мастер-пароля системы"));
        builder.setMessage("Введите 4-значный пароль администратора для защиты доступа к данным.");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputPass = new EditText(this);
        inputPass.setHint("Пароль (ровно 4 символа)");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        final EditText inputConfirm = new EditText(this);
        inputConfirm.setHint("Подтвердите пароль (4 символа)");
        inputConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputConfirm);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String pass = inputPass.getText().toString().trim();
            String confirm = inputConfirm.getText().toString().trim();

            if (pass.length() != 4 || !pass.equals(confirm)) {
                Toast.makeText(this, "Ошибка! Пароль должен содержать ровно 4 символа и совпадать.", Toast.LENGTH_LONG).show();
                showCreateMasterPasswordDialog(prefs);
                return;
            }

            prefs.edit().putString(KEY_MASTER_PASS, pass).apply();
            Toast.makeText(this, "Мастер-пароль успешно создан!", Toast.LENGTH_SHORT).show();
            onLog("Система запущена. Выберите профиль для начала работы.");
        });
        builder.setNegativeButton("Выход", (dialog, which) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private void showEnterMasterPasswordDialog(String savedPass) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Авторизация администратора"));
        builder.setMessage("Введите 4-значный мастер-пароль для доступа к системе.");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputPass = new EditText(this);
        inputPass.setHint("4-значный пароль");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);
        builder.setPositiveButton("Войти", (dialog, which) -> {
            String pass = inputPass.getText().toString().trim();
            if (pass.equals(savedPass)) {
                Toast.makeText(this, "Доступ разрешен!", Toast.LENGTH_SHORT).show();
                onLog("Система запущена. Выберите профиль для начала работы.");
            } else {
                Toast.makeText(this, "Неверный пароль!", Toast.LENGTH_SHORT).show();
                showEnterMasterPasswordDialog(savedPass);
            }
        });
        builder.setNegativeButton("Выход", (dialog, which) -> finish());
        builder.setCancelable(false);
        builder.show();
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
                Toast.makeText(MainActivity.this, "Профиль активирован. Теперь доступна связь с датчиками.", Toast.LENGTH_SHORT).show();
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

    private void checkAndRequestPermissions() {
        if (!isProfileAuthorized) {
            Toast.makeText(this, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
            openProfilesManager();
            return;
        }
        if (PermissionHelper.checkAndRequestPermissions(this)) {
            startHrvForegroundService();
            bleManager.initAndStartScan();
        } else {
            onLog("Запрос разрешений BLE...");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                onLog("Разрешения получены, запуск сканирования...");
                startHrvForegroundService();
                bleManager.initAndStartScan();
            } else {
                onLog("Ошибка: разрешения Bluetooth не предоставлены!");
            }
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

        isRecording = true;
        if (isBound && hrvService != null) hrvService.startSessionRecording();
        
        if (polarManager != null) {
            polarManager.startCollection();
        }

        sessionStartTime = System.currentTimeMillis();
        synchronized (sessionData) { sessionData.clear(); }
        piWindow.clear();
        prevRrMs = 0;
        consecutiveArtifactsCount = 0;
        if (ui.chartView != null) ui.chartView.clearData();
        
        updateMetricsDisplay("--", "--", "--", "--");
        updateRightPanelStatus();
        if (ui.btnHeart != null) ui.btnHeart.setAlpha(1.0f);
        onLog("Мониторинг активен для профиля: " + activeProfileName);
    }

    private void stopMonitoring() {
        isRecording = false;
        isTrainingModeActive = false;
        if (isBound && hrvService != null) hrvService.stopSessionRecording();
        
        if (polarManager != null) {
            polarManager.stopCollection();
        }

        piWindow.clear();
        updateRightPanelStatus();
        if (ui.btnHeart != null) ui.btnHeart.setAlpha(isProfileAuthorized ? 1.0f : 0.5f);
        onLog("Мониторинг остановлен");
    }

    private void saveData() {
        onLog("Сохранение данных в CSV и БД...");
        List<DataPoint> copyForExport;
        synchronized (sessionData) { copyForExport = new ArrayList<>(sessionData); }
        
        CsvExporter.saveSessionToCsv(this, copyForExport, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                onLog("Успешно сохранено в CSV: " + fileName);
                saveSessionToDatabase(fileName, copyForExport);
            }
            @Override
            public void onError(String errorMessage) {
                onLog("Ошибка сохранения CSV: " + errorMessage);
            }
        });
    }

    private void saveSessionToDatabase(String fileName, List<DataPoint> points) {
        if (activeProfileId == -1L) return;
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                long endTime = System.currentTimeMillis();
                
                SessionEntity session = new SessionEntity(
                        0,
                        activeProfileId,
                        currentWorkoutMode,
                        sessionStartTime,
                        endTime,
                        currentPolarHr > 0 ? currentPolarHr : 75,
                        currentSpo2 > 0 ? currentSpo2 : 98,
                        45.0,
                        50.0,
                        1.2,
                        35.0
                );
                long sessionId = db.sessionDao().insertSessionSync(session);
                runOnUiThread(() -> onLog("Сессия #" + sessionId + " зарегистрирована в базе данных Room"));
            } catch (Exception e) {
                runOnUiThread(() -> onLog("Ошибка записи сессии в БД: " + e.getMessage()));
            }
        });
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
                    if (isBound && hrvService != null) hrvService.addRrInterval(rrMs);
                    consecutiveArtifactsCount = 0;
                    prevRrMs = rrMs;
                    currentPolarHr = Math.round(instantHr);

                    long now = System.currentTimeMillis();
                    if (sessionStartTime == 0) sessionStartTime = now;
                    float elapsedSec = (now - sessionStartTime) / 1000f;
                    String timestamp = timeFormat.format(new Date(now));
                    DataPoint dp = new DataPoint(timestamp, (int) elapsedSec, currentSpo2, currentPolarHr, currentPi, rrMs);

                    synchronized (sessionData) { sessionData.add(dp); }
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
            int totalTarget = 300;
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

                if (isBound && hrvService != null) {
                    hrvService.updateNotificationText("Запись завершена (300/300)");
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
        if (bleManager != null) bleManager.close();
        if (polarManager != null) polarManager.disconnect();
    }
}
