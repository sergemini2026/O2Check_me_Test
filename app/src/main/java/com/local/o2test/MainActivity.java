package com.local.o2test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends Activity implements O2BleManager.BleListener {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int PI_SMOOTHING_WINDOW = 5;

    private O2BleManager bleManager;
    private PolarH10Manager polarManager;
    private TextView tvLiveMetrics;
    private TrendChartView chartView;
    private TextView tvHrvMetrics;
    private TextView tvLog;
    private ScrollView logScrollView;
    private ImageButton btnHeart;

    private boolean isRecording = false;
    private long sessionStartTime = 0;
    private final List<DataPoint> sessionData = new ArrayList<>();
    
    private final Queue<Float> piWindow = new LinkedList<>();
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private int currentSpo2 = 0;
    private float currentPi = 0.0f;
    private int currentBattery = 0;
    private int currentPolarHr = 0;
    private int prevRrMs = 0;
    private int consecutiveArtifactsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new O2BleManager(this, this);
        polarManager = new PolarH10Manager(this, polarCallback);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        tvLiveMetrics = new TextView(this);
        tvLiveMetrics.setTextSize(18);
        tvLiveMetrics.setGravity(Gravity.CENTER);
        updateStatusHeader(0, 0, 0f, 0);
        mainLayout.addView(tvLiveMetrics);

        // --- ВЕРХНЯЯ ПАНЕЛЬ КНОПОК С ИНФОГРАФИКОЙ ---
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(0, 10, 0, 10);

        // Фиксируем высоту панели кнопок (52dp)
        int btnBarHeightPx = (int) (52 * getResources().getDisplayMetrics().density);
        btnBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, btnBarHeightPx));

        Button btnReconnect = createButton("Обновить подключение", android.R.drawable.ic_popup_sync);
        Button btnSave = createButton("Сохранение данных", android.R.drawable.ic_menu_save);
        Button btnSettings = createButton("Настройки", android.R.drawable.ic_menu_preferences);
        Button btnExit = createButton("Выход", android.R.drawable.ic_menu_close_clear_cancel);

        btnBar.addView(btnReconnect);
        btnBar.addView(btnSave);
        btnBar.addView(btnSettings);
        btnBar.addView(btnExit);
        mainLayout.addView(btnBar);

        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        // --- БЛОК ВСР ---
        tvHrvMetrics = new TextView(this);
        tvHrvMetrics.setTextSize(16);
        tvHrvMetrics.setTextColor(Color.WHITE);
        tvHrvMetrics.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams hrvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrvParams.setMargins(0, 8, 0, 4);
        tvHrvMetrics.setLayoutParams(hrvParams);
        tvHrvMetrics.setText("RMSSD: -- | pNN50: -- | LF/HF: -- | TP: --");
        mainLayout.addView(tvHrvMetrics);

        // --- БЛОК УПРАВЛЕНИЯ МОНИТОРИНГОМ (Надпись + Кнопка Сердце) ---
        LinearLayout heartContainer = new LinearLayout(this);
        heartContainer.setOrientation(LinearLayout.HORIZONTAL);
        heartContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams heartContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heartContainerParams.setMargins(0, 0, 12, 4);
        heartContainer.setLayoutParams(heartContainerParams);

        TextView tvMonitorLabel = new TextView(this);
        tvMonitorLabel.setText("Панель монитора");
        tvMonitorLabel.setTextSize(13);
        tvMonitorLabel.setTextColor(Color.WHITE);
        tvMonitorLabel.setGravity(Gravity.CENTER_VERTICAL);
        
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 0, 10, 0);
        tvMonitorLabel.setLayoutParams(labelParams);

        btnHeart = new ImageButton(this);
        btnHeart.setBackgroundColor(Color.TRANSPARENT);
        btnHeart.setImageResource(R.drawable.ic_heart_pulse);
        btnHeart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btnHeart.setAlpha(0.5f); // Исходно неактивное состояние

        int heartSizePx = (int) (60 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(heartSizePx, heartSizePx);
        btnHeart.setLayoutParams(heartParams);

        btnHeart.setOnClickListener(v -> {
            if (!isRecording) {
                startMonitoringPanel();
            } else {
                stopMonitoring();
            }
        });

        heartContainer.addView(tvMonitorLabel);
        heartContainer.addView(btnHeart);
        mainLayout.addView(heartContainer);

        tvLog = new TextView(this);
        tvLog.setTextSize(11);

        logScrollView = new ScrollView(this);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        logParams.topMargin = 4;
        logScrollView.setLayoutParams(logParams);
        logScrollView.addView(tvLog);
        mainLayout.addView(logScrollView);

        setContentView(mainLayout);

        btnReconnect.setOnClickListener(v -> {
            onLog("Переподключение BLE устройств...");
            checkAndRequestPermissions();
        });
        btnSave.setOnClickListener(v -> saveData());
        btnSettings.setOnClickListener(v -> onLog("Открытие настроек..."));
        btnExit.setOnClickListener(v -> finish());

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            onLog("Запрос разрешений BLE...");
            requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            bleManager.initAndStartScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                onLog("Разрешения получены, запуск сканирования...");
                bleManager.initAndStartScan();
            } else {
                onLog("Ошибка: разрешения Bluetooth не предоставлены!");
            }
        }
    }

    private Button createButton(String text, int iconRes) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        
        // Размер шрифта уменьшен на 1п (с 14 до 13)
        btn.setTextSize(13); 

        // Возвращена иконографика на кнопку
        if (iconRes != 0) {
            btn.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        }

        // Отступы внутри кнопки от граней
        int paddingHorizPx = (int) (8 * getResources().getDisplayMetrics().density);
        int paddingVertPx = (int) (4 * getResources().getDisplayMetrics().density);
        btn.setPadding(paddingHorizPx, paddingVertPx, paddingHorizPx, paddingVertPx);

        // Отступ между текстом и иконкой
        int drawablePaddingPx = (int) (6 * getResources().getDisplayMetrics().density);
        btn.setCompoundDrawablePadding(drawablePaddingPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void animateHeartPulse() {
        if (btnHeart == null || !isRecording) return;

        btnHeart.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(110)
            .withEndAction(() -> {
                if (btnHeart != null) {
                    btnHeart.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(110)
                        .start();
                }
            })
            .start();
    }

    private void updateStatusHeader(int spo2, int hr, float pi, int battery) {
        if (tvLiveMetrics == null) return;

        String partO2 = String.format(Locale.US, "SpO2: %d%%", spo2);
        String partHR = String.format(Locale.US, "  |  HR: %d bpm", hr);
        String partPI = String.format(Locale.US, "  |  PI: %.1f%%", pi);
        String partPower = String.format(Locale.US, "  |  Power: %d%%", battery);

        SpannableStringBuilder builder = new SpannableStringBuilder();

        int start = 0;
        builder.append(partO2);
        builder.setSpan(new ForegroundColorSpan(Color.CYAN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partHR);
        builder.setSpan(new ForegroundColorSpan(Color.GREEN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partPI);
        builder.setSpan(new ForegroundColorSpan(Color.YELLOW), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partPower);
        builder.setSpan(new ForegroundColorSpan(Color.RED), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvLiveMetrics.setText(builder);
    }

    private void startMonitoringPanel() {
        isRecording = true;
        sessionStartTime = System.currentTimeMillis();
        synchronized (sessionData) {
            sessionData.clear();
        }
        piWindow.clear();
        prevRrMs = 0;
        consecutiveArtifactsCount = 0;
        if (chartView != null) {
            chartView.clearData();
        }
        if (tvHrvMetrics != null) {
            tvHrvMetrics.setText("RMSSD: -- | pNN50: -- | LF/HF: -- | TP: --");
        }
        if (btnHeart != null) {
            btnHeart.setAlpha(1.0f);
        }
        onLog("Панель монитора активна");
    }

    private void stopMonitoring() {
        isRecording = false;
        piWindow.clear();
        if (btnHeart != null) {
            btnHeart.setAlpha(0.5f);
        }
        onLog("Мониторинг остановлен");
    }

    private void saveData() {
        onLog("Сохранение данных...");
        List<DataPoint> copyForExport;
        synchronized (sessionData) {
            copyForExport = new ArrayList<>(sessionData);
        }
        CsvExporter.saveSessionToCsv(this, copyForExport, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                onLog("Успешно сохранено: " + fileName);
            }

            @Override
            public void onError(String errorMessage) {
                onLog("Ошибка: " + errorMessage);
            }
        });
    }

    private float getSmoothedPi(float rawPi) {
        piWindow.add(rawPi);
        if (piWindow.size() > PI_SMOOTHING_WINDOW) {
            piWindow.poll();
        }
        float sum = 0f;
        for (float val : piWindow) {
            sum += val;
        }
        return sum / piWindow.size();
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> {
            if (tvLog != null) {
                tvLog.append(message + "\n");
                if (logScrollView != null) {
                    logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
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

        runOnUiThread(() -> updateStatusHeader(currentSpo2, hrToDisplay, currentPi, currentBattery));

        if (!isPolarActive && res.isFingerOn && isRecording) {
            if (sessionStartTime == 0) sessionStartTime = now;
            int elapsedSec = (int) ((now - sessionStartTime) / 1000);

            String timestamp = timeFormat.format(new Date(now));
            DataPoint dp = new DataPoint(timestamp, elapsedSec, currentSpo2, res.hr, currentPi, 0);

            synchronized (sessionData) {
                sessionData.add(dp);
            }
            runOnUiThread(() -> chartView.addDataPoint(dp));
        }
    }

    @Override
    public void onPolarDeviceFound(BluetoothDevice device) {
        if (polarManager != null) {
            polarManager.connect(device);
        }
    }

    private final PolarH10Manager.PolarCallback polarCallback = new PolarH10Manager.PolarCallback() {
        @Override
        public void onPolarLog(String message) {
            onLog(message);
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
                            onLog("[АРТЕФАКТ] Скачок RR: " + prevRrMs + "мс -> " + rrMs + "мс. Исключен из графика.");
                        }
                    }
                }

                if (!isArtifact) {
                    consecutiveArtifactsCount = 0;
                    prevRrMs = rrMs;
                    currentPolarHr = Math.round(instantHr);

                    long now = System.currentTimeMillis();
                    if (sessionStartTime == 0) sessionStartTime = now;

                    float elapsedSec = (now - sessionStartTime) / 1000f;
                    String timestamp = timeFormat.format(new Date(now));

                    DataPoint dp = new DataPoint(timestamp, (int) elapsedSec, currentSpo2, currentPolarHr, currentPi, rrMs);

                    synchronized (sessionData) {
                        sessionData.add(dp);
                    }

                    runOnUiThread(() -> {
                        updateStatusHeader(currentSpo2, currentPolarHr, currentPi, currentBattery);
                        chartView.addDataPoint(dp);
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
            } else {
                String logMsg = String.format(Locale.US,
                        "[Дамп ГОТОВ 100%%] HR: %d bpm | RMSSD: %.1f ms | pNN50: %.1f%% | LF/HF: %.2f | TP: %.0f ms²",
                        hr, hrv.rmssd, hrv.pnn50, hrv.lfHfRatio, hrv.totalPower);
                onLog(logMsg);

                if (hrv != null) {
                    String hrvDisplay = String.format(Locale.US,
                            "RMSSD: %.1f ms  |  pNN50: %.1f%%  |  LF/HF: %.2f  |  TP: %.0f ms²",
                            hrv.rmssd, hrv.pnn50, hrv.lfHfRatio, hrv.totalPower);

                    runOnUiThread(() -> {
                        if (tvHrvMetrics != null) {
                            tvHrvMetrics.setText(hrvDisplay);
                        }
                    });
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.close();
        }
        if (polarManager != null) {
            polarManager.disconnect();
        }
    }
}
