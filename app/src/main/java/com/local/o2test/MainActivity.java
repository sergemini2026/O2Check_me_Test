package com.local.o2test;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ScrollView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends Activity implements O2BleManager.BleListener {

    private static final int PI_SMOOTHING_WINDOW = 5;

    private O2BleManager bleManager;
    private PolarH10Manager polarManager;
    private UiBuilder.Views ui;

    private boolean isRecording = false;
    private long sessionStartTime = 0;
    private final List<DataPoint> sessionData = new ArrayList<>();
    private final Queue<Float> piWindow = new LinkedList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private int currentSpo2 = 0;
    private float currentPi = 0.0f;
    private int currentBattery = 0;
    private int currentPolarHr = 0;
    private int currentPolarBattery = 0;
    private int prevRrMs = 0;
    private int consecutiveArtifactsCount = 0;

    private final PolarH10Manager.PolarCallback polarCallback = new PolarH10Manager.PolarCallback() {
        @Override
        public void onHrDataReceived(int hr, int rrMs) {
            currentPolarHr = hr;
            if (rrMs > 0) {
                prevRrMs = rrMs;
            }
            updateUiMetrics();
        }

        @Override
        public void onBatteryReceived(int batteryLevel) {
            currentPolarBattery = batteryLevel;
            updateUiMetrics();
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

        ui = UiBuilder.buildUi(this);
        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, 0, 0, 0f, 0, 0);

        ui.btnHeart.setOnClickListener(v -> {
            if (!isRecording) {
                startMonitoringPanel();
            } else {
                stopMonitoring();
            }
        });

        ui.btnReconnect.setOnClickListener(v -> {
            onLog("Переподключение BLE устройств...");
            checkAndRequestPermissions();
        });

        ui.btnSave.setOnClickListener(v -> saveData());
        ui.btnSettings.setOnClickListener(v -> onLog("Открытие настроек..."));
        ui.btnExit.setOnClickListener(v -> finish());
    }

    private void startMonitoringPanel() {
        isRecording = true;
        sessionStartTime = System.currentTimeMillis();
        sessionData.clear();
        onLog("Мониторинг запущен");

        // Запуск передней службы (Foreground Service)
        Intent serviceIntent = new Intent(this, HrvForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopMonitoring() {
        isRecording = false;
        onLog("Мониторинг остановлен");

        // Остановка передней службы
        Intent serviceIntent = new Intent(this, HrvForegroundService.class);
        stopService(serviceIntent);
    }

    private void saveData() {
        if (sessionData.isEmpty()) {
            onLog("Нет данных для сохранения");
            return;
        }
        CsvExporter.exportToCsv(this, sessionData);
    }

    private void checkAndRequestPermissions() {
        if (PermissionHelper.hasPermissions(this)) {
            bleManager.startScan();
            polarManager.connect();
        } else {
            PermissionHelper.requestPermissions(this);
        }
    }

    private void updateUiMetrics() {
        runOnUiThread(() -> {
            UiBuilder.updateStatusHeader(
                    ui.tvLiveMetrics,
                    currentSpo2,
                    currentBattery,
                    currentPi,
                    currentPolarHr,
                    currentPolarBattery
            );
        });
    }

    public void onLog(String message) {
        runOnUiThread(() -> {
            if (ui != null && ui.tvLog != null) {
                String time = timeFormat.format(new Date());
                ui.tvLog.append("[" + time + "] " + message + "\n");
                if (ui.scrollLog != null) {
                    ui.scrollLog.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }
        });
    }

    @Override
    public void onO2DataReceived(int spo2, float pi, int battery) {
        currentSpo2 = spo2;
        currentPi = pi;
        currentBattery = battery;
        updateUiMetrics();
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        onLog("Подключено O2 устройство: " + device.getName());
    }

    @Override
    public void onDeviceDisconnected() {
        onLog("Отключено O2 устройство");
    }
}
