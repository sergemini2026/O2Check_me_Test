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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.HrvSnapshotEntity;
import com.local.o2test.db.entity.SessionEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
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
    private static final int CALIBRATION_TARGET = 100; // Целевое число интервалов для автоподстройки (90-100)
    private static final int POSTI_WINDOW_SEC = 120; // Окно оценки следовой когерентности (2 минуты)
    private static final int POSTI_INTERVAL_DUMPS = 3; // Кратность: запускать замер PSTI каждый 3-й завершенный дамп

    private O2BleManager bleManager;
    private PolarH10Manager polarManager;
    private UiBuilder.Views ui;
    private OxygenSensorManager oxygenSensorManager;

    private HrvForegroundService hrvService;
    private boolean isBound = false;

    private boolean isRecording = false;
    private boolean isTrainingModeActive = false;
    private boolean isCalibrationCompleted = false;
    private int sessionDumpCount = 0; // Счетчик завершенных дампов для регулирования кратности
    private long sessionStartTime = 0;
    private long postiStartTime = 0;
    private String currentWorkoutMode = "Базовый мониторинг";
    private final List<DataPoint> sessionData = new ArrayList<>();
    private final List<Integer> phaseBuffer = new ArrayList<>();
    private final Queue<Float> piWindow = new LinkedList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // Таймер обратного отсчета для статуса PSTI
    private final Handler pstiHandler = new Handler(Looper.getMainLooper());
    private Runnable pstiCountdownRunnable;
    private int pstiRemainingSec = POSTI_WINDOW_SEC;

    // --- ПОКАЗАТЕЛИ СЛЕДОВОЙ КОГЕРЕНТНОСТИ (PSTI) ---
    private double lastPstiScore = 0.0;
    private double lastPstiRmssdRatio = 0.0;

    // --- МУЛЬТИМОДАЛЬНЫЙ АУДИО И ТАКТОВЫЙ КОНТУР ---
    private MasterClock masterClock;
    private AudioPacerEngine audioEngine;

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
            
            if (hrvService != null) {
                hrvService.stopSessionRecording();
            }
            isRecording = false;
            
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
        oxygenSensorManager = new OxygenSensorManager(ui);
        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, 0, 0, 0f, 0, 0);

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
                openProfilesManager();
                return;
            }
            saveSessionAutomatically();
        });

        ui.btnSettings.setOnClickListener(v -> {
            showSettingsDialog();
        });

        ui.btnExit.setOnClickListener(v -> {
            stopAudioAndClock();
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
        checkMasterPasswordOnStart();
    }

    private void saveSessionAutomatically() {
        onLog("Сохранение сессии в CSV и PDF...");
        List<DataPoint> copyForExport;
        synchronized (sessionData) { copyForExport = new ArrayList<>(sessionData); }
        
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String customPath = prefs.getString("custom_export_path", null);
        
        CsvExporter.saveSessionToCsv(this, copyForExport, customPath, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                onLog("CSV успешно сохранен: " + fileName);
                saveSessionToDatabase(copyForExport, filePath);
            }
            @Override
            public void onError(String errorMessage) {
                onLog("Ошибка сохранения CSV: " + errorMessage);
                saveSessionToDatabase(copyForExport, "");
            }
        });
    }

    /**
     * Интегрированное меню «Настройки» с подразделами А, Б, В, Г
     */
    private void showSettingsDialog() {
        CharSequence[] menuItems = {
            "Сохранение сессий",
            "Аудио",
            "Видео",
            "Резервирование"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Настройки системы"));
        builder.setItems(menuItems, (dialog, which) -> {
            if (which == 0) {
                showFolderSettingsDialog();
            } else if (which == 1) {
                showAudioSettingsDialog();
            } else if (which == 2) {
                showVideoSettingsDialog();
            } else if (which == 3) {
                showBackupSettingsDialog();
            }
        });
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    /**
     * п. А: Настройки сохранения сессий (Documents / Downloads -> O2Test -> Profile)
     */
    private void showFolderSettingsDialog() {
        CharSequence[] paths = {"Папка Документы (Documents)", "Папка Загрузки (Downloads)"};
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Выбор корневой папки отчетов"));
        builder.setItems(paths, (dialog, which) -> {
            if (!isProfileAuthorized) {
                Toast.makeText(this, "Сначала выберите профиль для создания папки!", Toast.LENGTH_SHORT).show();
                openProfilesManager();
                return;
            }

            File baseDir = (which == 0) 
                    ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Создаем папку O2Test
            File o2TestDir = new File(baseDir, "O2Test");
            if (!o2TestDir.exists()) {
                o2TestDir.mkdirs();
            }

            // Создаем вложенную папку текущего профиля
            File profileDir = new File(o2TestDir, activeProfileName);
            if (!profileDir.exists()) {
                profileDir.mkdirs();
            }

            String finalPath = profileDir.getAbsolutePath();
            prefs.edit().putString("custom_export_path", finalPath).apply();
            onLog("[НАСТРОЙКИ] Путь сохранения сессий установлен: " + finalPath);
            Toast.makeText(this, "Папка создана: " + activeProfileName, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * п. Б: Выбор аудиодорожки (1-4)
     */
    private void showAudioSettingsDialog() {
        CharSequence[] audioOptions = {
            "Спокойствие",
            "Безмятежность",
            "Мечтательность",
            "Удовлетворение",
            " "
        };
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Выбор аудио сопровождения"));
        builder.setItems(audioOptions, (dialog, which) -> {
            int selectedResId = R.raw.o2test_01;
            String trackName = "Спокойствие";
            switch (which) {
                case 0:
                    selectedResId = R.raw.o2test_01;
                    trackName = "Спокойствие";
                    break;
                case 1:
                    selectedResId = R.raw.o2test_02;
                    trackName = "Безмятежность";
                    break;
                case 2:
                    selectedResId = R.raw.o2test_03;
                    trackName = "Мечтательность";
                    break;
                case 3:
                    selectedResId = R.raw.o2test_04;
                    trackName = "Удовлетворение";
                    break;
            }
            prefs.edit().putInt("selected_audio_res", selectedResId).putString("selected_audio_name", trackName).apply();
            onLog("[НАСТРОЙКИ] Выбрана аудиодорожка: " + trackName);
            Toast.makeText(this, "Аудио: " + trackName, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * п. В: Режимы видео (Одиночный / Трансляция)
     */
    private void showVideoSettingsDialog() {
        CharSequence[] videoModes = {
            "Одиночный",
            "Трансляция"
        };
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Режим визуализации видео"));
        builder.setItems(videoModes, (dialog, which) -> {
            String modeStr = (which == 0) ? "single" : "broadcast";
            prefs.edit().putString("video_render_mode", modeStr).apply();
            String label = (which == 0) ? "Одиночный" : "Трансляция";
            onLog("[НАСТРОЙКИ] Режим видео изменен на: " + label);
            Toast.makeText(this, "Режим видео: " + label, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * п. Г: Резервирование (Экспорт / Импорт бэкапа базы данных)
     */
    private void showBackupSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String customPath = prefs.getString("custom_export_path", null);

        if (customPath == null || customPath.isEmpty()) {
            Toast.makeText(this, "Сначала задайте путь сохранения в пункте 'Сохранение сессий'!", Toast.LENGTH_LONG).show();
            return;
        }

        File exportDir = new File(customPath);
        File backupFile = new File(exportDir, "o2test_backup.db");
        boolean hasBackup = backupFile.exists();

        CharSequence[] backupOptions = {
            "Экспорт базы данных (Создать бэкап)",
            hasBackup ? "Импорт базы данных (Восстановить бэкап)" : "Импорт базы данных (Недоступно: нет файла)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getCenteredTitle("Резервное копирование базы данных"));
        builder.setItems(backupOptions, (dialog, which) -> {
            if (which == 0) {
                exportDatabaseBackup(backupFile);
            } else if (which == 1) {
                if (!hasBackup) {
                    Toast.makeText(this, "Файл бэкапа не найден в папке сессий!", Toast.LENGTH_SHORT).show();
                    return;
                }
                importDatabaseBackup(backupFile);
            }
        });
        builder.show();
    }

    private void exportDatabaseBackup(File backupFile) {
        dbExecutor.execute(() -> {
            try {
                File dbFile = getDatabasePath("o2test_db");
                if (!dbFile.exists()) {
                    // Альтернативное имя файла Room базы
                    dbFile = getDatabasePath("app_database");
                }
                if (!dbFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "Файл базы данных не найден!", Toast.LENGTH_SHORT).show());
                    return;
                }

                FileChannel src = new FileInputStream(dbFile).getChannel();
                FileChannel dst = new FileOutputStream(backupFile).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();

                runOnUiThread(() -> {
                    onLog("[БЭКАП] База данных успешно экспортирована: " + backupFile.getAbsolutePath());
                    Toast.makeText(this, "Экспорт бэкапа выполнен успешно", Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void importDatabaseBackup(File backupFile) {
        new AlertDialog.Builder(this)
            .setTitle("Внимание!")
            .setMessage("Импорт базы данных перезапишет текущие локальные данные. Продолжить?")
            .setPositiveButton("Да", (d, w) -> {
                dbExecutor.execute(() -> {
                    try {
                        File dbFile = getDatabasePath("o2test_db");
                        if (!dbFile.exists()) {
                            dbFile = getDatabasePath("app_database");
                        }

                        // Закрываем инстанс базы перед перезаписью
                        AppDatabase.getInstance(getApplicationContext()).close();

                        FileChannel src = new FileInputStream(backupFile).getChannel();
                        FileChannel dst = new FileOutputStream(dbFile).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();

                        runOnUiThread(() -> {
                            onLog("[БЭКАП] База данных успешно восстановлена из бэкапа.");
                            Toast.makeText(this, "Импорт выполнен. Перезапустите приложение.", Toast.LENGTH_LONG).show();
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(this, "Ошибка импорта: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void updateRightPanelStatus() {
        updateRightPanelStatusWithCountdown(pstiRemainingSec);
    }

    private void updateRightPanelStatusWithCountdown(int seconds) {
        if (ui.tvStatusValue == null) return;
        boolean isPostiActive = isBound && hrvService != null && hrvService.isPostiTraceActive();
        if (!isRecording) {
            ui.tvStatusValue.setText("Статус: Ожидание");
        } else {
            if (isPostiActive) {
                ui.tvStatusValue.setText(String.format(Locale.US, "Статус: Замер следа (PSTI) - %d сек", seconds));
            } else if (isTrainingModeActive) {
                ui.tvStatusValue.setText("Статус: " + currentWorkoutMode);
            } else {
                ui.tvStatusValue.setText("Статус: Мониторинг");
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
        inputPass.setHint("Пароль (4 символа)");
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
                Toast.makeText(this, "Ошибка! Пароль должен содержать 4 символа и совпадать.", Toast.LENGTH_LONG).show();
                showCreateMasterPasswordDialog(prefs);
                return;
            }

            prefs.edit().putString(KEY_MASTER_PASS, pass).apply();
            Toast.makeText(this, "Мастер-пароль успешно создан!", Toast.LENGTH_SHORT).show();
            onLog("Система запущена. Выберите профиль для начала работы.");
        });
        builder.setNegativeButton("Выход", (dialog, which) -> {
            stopAudioAndClock();
            if (ui != null && ui.pacerView != null) ui.pacerView.stopMonitoring();
            finish();
        });
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
        builder.setNegativeButton("Выход", (dialog, which) -> {
            stopAudioAndClock();
            if (ui != null && ui.pacerView != null) ui.pacerView.stopMonitoring();
            finish();
        });
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
        if (isRecording) {
            stopMonitoring();
        }
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

        stopAudioAndClock();

        isRecording = true;
        isCalibrationCompleted = false;
        sessionDumpCount = 0;
        lastPstiScore = 0.0;
        lastPstiRmssdRatio = 0.0;

        if (isBound && hrvService != null) {
            hrvService.stopPostiRecording();
            hrvService.startSessionRecording();
            
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
                        if (masterClock != null && isRecording) {
                            masterClock.adjustPhase(correctionDelta);
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
        
        if (ui.pacerView != null) {
            ui.pacerView.stopMonitoring();
        }

        // Загружаем выбранную в настройках аудиодорожку (по умолчанию R.raw.o2test_01)
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int selectedAudioRes = prefs.getInt("selected_audio_res", R.raw.o2test_01);

        audioEngine = new AudioPacerEngine(MainActivity.this, selectedAudioRes);
        audioEngine.prepareAsync(null);
        
        updateMetricsDisplay("--", "--", "--", "--");
        updateRightPanelStatus();
        if (ui.btnHeart != null) ui.btnHeart.setAlpha(1.0f);
        onLog("Мониторинг активен для профиля: " + activeProfileName);
    }

    private void startPostiTraceWindow() {
        postiStartTime = System.currentTimeMillis();
        pstiRemainingSec = POSTI_WINDOW_SEC;
        
        if (audioEngine != null) {
            audioEngine.stop();
            audioEngine = null;
        }
        if (masterClock != null) {
            masterClock.stop();
            masterClock = null;
        }
        if (ui != null && ui.pacerView != null) {
            ui.pacerView.stopMonitoring();
        }

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
        runOnUiThread(() -> {
            if (isRecording) {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                int selectedAudioRes = prefs.getInt("selected_audio_res", R.raw.o2test_01);

                audioEngine = new AudioPacerEngine(MainActivity.this, selectedAudioRes);
                audioEngine.prepareAsync(() -> {
                    if (audioEngine != null) audioEngine.start();
                });

                masterClock = new MasterClock(phase -> {
                    if (ui.pacerView != null) {
                        ui.pacerView.updatePhaseExternal(phase);
                    }
                    if (audioEngine != null) {
                        audioEngine.updatePhase(phase);
                    }
                });
                masterClock.start(0.0f);
            }
        });
    }

    private void stopMonitoring() {
        isRecording = false;
        isTrainingModeActive = false;
        isCalibrationCompleted = false;
        sessionDumpCount = 0;
        pstiHandler.removeCallbacks(pstiCountdownRunnable);
        
        stopAudioAndClock();

        if (isBound && hrvService != null) {
            hrvService.stopSessionRecording();
            hrvService.stopPostiRecording();
        }
        
        if (polarManager != null) {
            polarManager.stopCollection();
        }

        piWindow.clear();
        
        if (ui.pacerView != null) {
            ui.pacerView.stopMonitoring();
        }

        updateRightPanelStatus();
        if (ui.btnHeart != null) {
            ui.btnHeart.setAlpha(isProfileAuthorized ? 1.0f : 0.5f);
        }
        onLog("Мониторинг остановлен и сброшен.");
    }

    private void stopAudioAndClock() {
        pstiHandler.removeCallbacks(pstiCountdownRunnable);
        if (masterClock != null) {
            masterClock.stop();
            masterClock = null;
        }
        if (audioEngine != null) {
            audioEngine.stop();
            audioEngine = null;
        }
    }

    private void saveSessionToDatabase(List<DataPoint> points, String csvPath) {
        if (activeProfileId == -1L) return;

        final List<SessionSnapshot> capturedSnapshots;
        if (isBound && hrvService != null) {
            capturedSnapshots = hrvService.flushSnapshotBuffer();
        } else {
            capturedSnapshots = new ArrayList<>();
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String customPath = prefs.getString("custom_export_path", null);

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
                        35.0,
                        "",
                        csvPath,
                        lastPstiScore,
                        lastPstiRmssdRatio
                );
                long sessionId = db.sessionDao().insertSessionSync(session);

                List<HrvSnapshotEntity> entityList = new ArrayList<>();
                if (!capturedSnapshots.isEmpty()) {
                    for (SessionSnapshot snapshot : capturedSnapshots) {
                        HrvSnapshotEntity entity = new HrvSnapshotEntity(snapshot.getTimestamp(), snapshot.getMetrics());
                        if (entity.getBaevskySi() > 0 && entity.getBaevskySi() <= 300) {
                            entityList.add(entity);
                        }
                    }
                    if (!entityList.isEmpty()) {
                        db.hrvSnapshotDao().insertAll(entityList);
                    }
                }

                List<Integer> sessionRrList = new ArrayList<>();
                for (DataPoint dp : points) {
                    if (dp.rrMs > 0) {
                        sessionRrList.add(dp.rrMs);
                    }
                }

                runOnUiThread(() -> {
                    onLog("Сессия #" + sessionId + " зарегистрирована в БД. Индекс PSTI: " + String.format(Locale.US, "%.1f%%", lastPstiScore));
                    PdfExporter.generateHrvPdfReport(
                            MainActivity.this,
                            activeProfileName,
                            currentWorkoutMode,
                            sessionStartTime,
                            endTime,
                            entityList,
                            sessionRrList,
                            points,
                            customPath,
                            lastPstiScore,
                            lastPstiRmssdRatio,
                            new PdfExporter.PdfExportCallback() {
                                @Override
                                public void onSuccess(String pdfFilePath, String fileNamePdf) {
                                    dbExecutor.execute(() -> {
                                        db.sessionDao().updatePathsSync(sessionId, pdfFilePath, csvPath);
                                    });

                                    onLog("PDF успешно создан: " + fileNamePdf);
                                    Toast.makeText(MainActivity.this, "Сессия и отчеты успешно сохранены", Toast.LENGTH_LONG).show();
                                }
                                @Override
                                public void onError(String errorMessage) {
                                    onLog("Ошибка создания PDF: " + errorMessage);
                                }
                            }
                    );
                });

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

                            if (count < CALIBRATION_TARGET) {
                                String statusText = String.format(Locale.US, "Идет сбор данных для фазовой автоподстройки: %.0f%%", percent);
                                runOnUiThread(() -> {
                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.setText(statusText);
                                    }
                                });
                            } else {
                                isCalibrationCompleted = true;
                                final float initialPhase = MayerWaveAnalyzer.calculateInitialPhase(phaseBuffer);
                                
                                onLog(String.format(Locale.US, "[Калибровка завершена] Вычисленная фаза Майера: %.2f", initialPhase));

                                runOnUiThread(() -> {
                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.setText("Сбор завершен");
                                    }
                                    
                                    if (audioEngine != null) {
                                        audioEngine.start();
                                    }

                                    masterClock = new MasterClock(phase -> {
                                        if (ui.pacerView != null) {
                                            ui.pacerView.updatePhaseExternal(phase);
                                        }
                                        if (audioEngine != null) {
                                            audioEngine.updatePhase(phase);
                                        }
                                    });
                                    masterClock.start(initialPhase);

                                    if (ui.tvStatusValue != null) {
                                        ui.tvStatusValue.postDelayed(() -> updateRightPanelStatus(), 1000);
                                    }
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

                sessionDumpCount++;
                onLog(String.format(Locale.US, "[Дамп #%d завершен]", sessionDumpCount));

                // Запуск PSTI с учетом кратности равной 3
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
        stopAudioAndClock();
        
        if (ui != null && ui.pacerView != null) {
            ui.pacerView.stopMonitoring();
        }
        
        if (bleManager != null) bleManager.close();
        if (polarManager != null) polarManager.disconnect();
    }
}
