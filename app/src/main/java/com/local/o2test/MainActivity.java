package com.local.o2test;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements O2BleManager.BleListener {

    private TextView tvLiveMetrics;
    private TextView logView;

    private static final int MAX_LOG_LINES = 150;
    private final LinkedList<String> logBuffer = new LinkedList<>();

    private TrendChartView chartView;
    private O2BleManager bleManager;

    private boolean isRecording = false;
    private long sessionStartTime = 0;
    private final List<DataPoint> sessionData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new O2BleManager(this, this);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        tvLiveMetrics = new TextView(this);
        tvLiveMetrics.setTextSize(20);
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

        btnMonitor.setOnClickListener(v -> startMonitoringPanel());
        btnStop.setOnClickListener(v -> stopRecordingSession());
        btnSave.setOnClickListener(v -> saveCSVData());
        btnExit.setOnClickListener(v -> exitApp());

        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        chartParams.setMargins(0, 10, 0, 10);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        scrollView.addView(logView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);
        log("Система готова. Выберите действие в меню.");
        checkAndRequestPermissions();
    }

    private Button createButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void startMonitoringPanel() {
        isRecording = true;
        sessionStartTime = System.currentTimeMillis();
        sessionData.clear();
        chartView.clearData();
        log("\n>>> ПАНЕЛЬ МОНИТОРА: Запущен новый сеанс записи <<<");

        if (!bleManager.isConnected()) {
            bleManager.initAndStartScan();
        }
    }

    private void stopRecordingSession() {
        if (isRecording) {
            isRecording = false;
            log("\n>>> ЗАПИСЬ ОСТАНОВЛЕНА <<<");
            generateReportSummary();
        } else {
            log("Запись не была активна.");
        }
    }

    private void saveCSVData() {
        CsvExporter.saveSessionToCsv(this, sessionData, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                log("Файл сохранён:\n" + filePath);
                Toast.makeText(MainActivity.this, "Сохранено в CSV:\n" + fileName, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                log(errorMessage);
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exitApp() {
        log("Завершение работы приложения...");
        bleManager.close();
        finishAndRemoveTask();
    }

    private void generateReportSummary() {
        if (sessionData.isEmpty()) return;

        float initialPI = sessionData.get(0).pi;
        float maxPI = initialPI;
        int maxPITime = 0;

        for (DataPoint dp : sessionData) {
            if (dp.pi > maxPI) {
                maxPI = dp.pi;
                maxPITime = dp.elapsedSec;
            }
        }
        float endPI = sessionData.get(sessionData.size() - 1).pi;

        log("--- ИТОГОВЫЙ ОТЧЕТ СЕАНСА ---");
        log(String.format(Locale.US, "Базовый PI (тонус): %.2f%%", initialPI));
        log(String.format(Locale.US, "Пик вазодилатации: %.2f%% (на %d-й сек)", maxPI, maxPITime));
        log(String.format(Locale.US, "Финишный PI: %.2f%%", endPI));
    }

    private void log(String text) {
        runOnUiThread(() -> {
            logBuffer.add(text);
            while (logBuffer.size() > MAX_LOG_LINES) {
                logBuffer.removeFirst();
            }
            StringBuilder sb = new StringBuilder();
            for (String line : logBuffer) {
                sb.append(line).append("\n");
            }
            if (logView != null) {
                logView.setText(sb.toString());
            }
        });
    }

    private void updateStatusHeader(int spo2, int hr, float pi, int battery) {
        String formattedHtml = String.format(Locale.US,
                "<font color='#00FFFF'><b>SpO2: %d%%</b></font> &nbsp;|&nbsp; " +
                "<font color='#00FF00'><b>HR: %d bpm</b></font> &nbsp;|&nbsp; " +
                "<font color='#FFFF00'><b>PI: %.1f%%</b></font> &nbsp;|&nbsp; " +
                "<font color='#AAAAAA'>Заряд: %d%%</font>",
                spo2, hr, pi, battery);

        if (tvLiveMetrics != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tvLiveMetrics.setText(Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvLiveMetrics.setText(Html.fromHtml(formattedHtml));
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            }, 101);
        } else {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
            }, 101);
        }
    }

    @Override
    public void onLog(String message) {
        log(message);
    }

    @Override
    public void onDataReceived(byte[] data) {
        // Логирование сырых HEX пакетов
        log("RX [" + (data != null ? data.length : 0) + "]: " + O2Parser.bytesToHex(data));

        O2Parser.ParseResult result = O2Parser.parse(data);
        if (result.isValid) {
            long now = System.currentTimeMillis();

            runOnUiThread(() -> updateStatusHeader(result.spo2, result.hr, result.pi, result.battery));

            if (isRecording) {
                int elapsedSec = (int) ((now - sessionStartTime) / 1000);
                DataPoint dp = new DataPoint(now, elapsedSec, result.spo2, result.hr, result.pi);
                sessionData.add(dp);
                runOnUiThread(() -> chartView.addDataPoint(dp));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.close();
        }
    }
}
