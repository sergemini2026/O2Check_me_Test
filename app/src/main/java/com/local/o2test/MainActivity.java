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
    private TextView tvLog;
    private ScrollView logScrollView;

    private boolean isRecording = false;
    private long sessionStartTime = 0;
    private final List<DataPoint> sessionData = new ArrayList<>();
    
    private final Queue<Float> piWindow = new LinkedList<>();
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private int currentSpo2 = 95;
    private float currentPi = 0.3f;
    private int currentBattery = 100;
    private int currentPolarHr = 0; // Мгновенная ЧСС от H10
    private int prevRrMs = 0; // Трекер для фильтрации артефактов на графике

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

        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(0, 10, 0, 10);

        Button btnMonitor = createButton("Панель монитора");
        Button btnStop = createButton("Стоп");
        Button btnSave = createButton("Сохранение данных");
        Button btnExit = createButton("Выход");

        btnBar.addView(btnMonitor);
        btnBar.addView(btnStop);
        btnBar.addView(btnSave);
        btnBar.addView(btnExit);
        mainLayout.addView(btnBar);

        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        tvLog = new TextView(this);
        tvLog.setTextSize(11);

        logScrollView = new ScrollView(this);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        logParams.topMargin = 24;
        logScrollView.setLayoutParams(logParams);
        logScrollView.addView(tvLog);
        mainLayout.addView(logScrollView);

        setContentView(mainLayout);

        btnMonitor.setOnClickListener(v -> startMonitoringPanel());
        btnStop.setOnClickListener(v -> stopMonitoring());
        btnSave.setOnClickListener(v -> saveData());
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

    private Button createButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        return btn;
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
        sessionData.clear();
        piWindow.clear();
        prevRrMs = 0; // Сброс трекинга артефактов
        if (chartView != null) {
            chartView.clearData();
        }
        onLog("Панель монитора активна");
    }

    private void stopMonitoring() {
        isRecording = false;
        piWindow.clear();
        onLog("Мониторинг остановлен");
    }

    private void saveData() {
        onLog("Сохранение данных...");
        CsvExporter.saveSessionToCsv(this, sessionData, new CsvExporter.ExportCallback() {
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

        runOnUiThread(() -> updateStatusHeader(currentSpo2, currentPolarHr, currentPi, currentBattery));

        if (res.isFingerOn && isRecording) {
            if (sessionStartTime == 0) sessionStartTime = now;
            int elapsedSec = (int) ((now - sessionStartTime) / 1000);

            String timestamp = timeFormat.format(new Date(now));
            DataPoint dp = new DataPoint(timestamp, elapsedSec, currentSpo2, currentPolarHr, currentPi, 0);

            sessionData.add(dp);
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
                // Жесткая фильтрация артефактов для графика (Malik 20% Threshold)
                boolean isArtifact = false;
                if (prevRrMs > 0) {
                    if (Math.abs(rrMs - prevRrMs) / (float)prevRrMs > 0.20) {
                        isArtifact = true;
                        onLog("[АРТЕФАКТ] Скачок RR: " + prevRrMs + "мс -> " + rrMs + "мс. Исключен из графика.");
                    }
                }

                if (!isArtifact) {
                    prevRrMs = rrMs; // Запоминаем только чистый интервал
                    currentPolarHr = Math.round(instantHr); 

                    long now = System.currentTimeMillis();
                    if (sessionStartTime == 0) sessionStartTime = now;
                    
                    // Используем float для миллисекундной точности на графике
                    float elapsedSec = (now - sessionStartTime) / 1000f; 

                    String timestamp = timeFormat.format(new Date(now));
                    
                    DataPoint dp = new DataPoint(timestamp, (int) elapsedSec, currentSpo2, currentPolarHr, currentPi, rrMs);
                    sessionData.add(dp);

                    
                    

                    runOnUiThread(() -> {
                        updateStatusHeader(currentSpo2, currentPolarHr, currentPi, currentBattery);
                        chartView.addDataPoint(dp);
                    });
                }
            }
        }

        @Override
        public void onPolarHrReceived(int hr, HrvCalculator.Metrics hrv, int rrCount) {
            if (hrv == null) return;

            if (rrCount < 60) {
                String logMsg = String.format(Locale.US,
                        "[Polar H10] HR: %d bpm | RMSSD: %.1f ms | pNN50: %.1f%% | Арт: %d (Накопление: %d/300)",
                        hr, hrv.rmssd, hrv.pnn50, hrv.artifactsDetected, rrCount);
                onLog(logMsg);
            } else {
                String logMsg = String.format(Locale.US,
                        "[Polar H10] HR: %d bpm | RMSSD: %.1f ms | pNN50: %.1f%% | LF/HF: %.2f | TP: %.0f ms² | Арт: %d (RR: %d/300)",
                        hr, hrv.rmssd, hrv.pnn50, hrv.lfHfRatio, hrv.totalPower, hrv.artifactsDetected, rrCount);
                onLog(logMsg);
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
