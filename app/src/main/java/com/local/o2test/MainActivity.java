package com.local.o2test;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements O2BleManager.BleListener {

    private O2BleManager bleManager;
    private TextView tvLiveMetrics;
    private TrendChartView chartView;
    private TextView tvLog;
    private ScrollView logScrollView;

    private long sessionStartTime = 0;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bleManager = new O2BleManager(this, this);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        // Шапка показателей
        tvLiveMetrics = new TextView(this);
        tvLiveMetrics.setTextSize(18);
        tvLiveMetrics.setGravity(Gravity.CENTER);
        updateStatusHeader(0, 0, 0f, 0);
        mainLayout.addView(tvLiveMetrics);

        // Панель кнопок
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

        // График
        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        // Текстовый лог
        tvLog = new TextView(this);
        tvLog.setTextSize(11);

        logScrollView = new ScrollView(this);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        logScrollView.setLayoutParams(logParams);
        logScrollView.addView(tvLog);
        mainLayout.addView(logScrollView);

        setContentView(mainLayout);

        // Обработчики кнопок
        btnMonitor.setOnClickListener(v -> startMonitoringPanel());
        btnStop.setOnClickListener(v -> stopMonitoring());
        btnSave.setOnClickListener(v -> saveData());
        btnExit.setOnClickListener(v -> finish());

        // Запуск BLE сканера
        bleManager.initAndStartScan();
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
        if (tvLiveMetrics != null) {
            tvLiveMetrics.setText(String.format(Locale.US,
                    "SpO2: %d%%  |  HR: %d bpm  |  PI: %.1f%%  |  Заряд: %d%%",
                    spo2, hr, pi, battery));
        }
    }

    private void startMonitoringPanel() {
        sessionStartTime = System.currentTimeMillis();
        if (chartView != null) {
            chartView.clearData();
        }
        onLog("Панель монитора активна");
    }

    private void stopMonitoring() {
        onLog("Мониторинг остановлен");
    }

    private void saveData() {
        onLog("Сохранение данных...");
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
        onLog("RX [" + (data != null ? data.length : 0) + "]: " + O2Parser.bytesToHex(data));

        O2Parser.ParseResult result = O2Parser.parse(data);
        if (result != null && result.isValid) {
            if (sessionStartTime == 0) {
                sessionStartTime = System.currentTimeMillis();
            }
            int elapsedSec = (int) ((System.currentTimeMillis() - sessionStartTime) / 1000);
            String timestamp = timeFormat.format(new Date());

            runOnUiThread(() -> {
                updateStatusHeader(result.spo2, result.hr, result.pi, result.battery);

                if (chartView != null) {
                    DataPoint dp = new DataPoint(timestamp, elapsedSec, result.spo2, result.hr, result.pi);
                    chartView.addDataPoint(dp);
                }
            });
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
