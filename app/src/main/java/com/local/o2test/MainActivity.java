package com.local.o2test;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
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

        ui = UiBuilder.buildUi(this);
        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, 0, 0, 0f, 0);

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

        setContentView(ui.mainLayout);
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (PermissionHelper.checkAndRequestPermissions(this)) {
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

    private void animateHeartPulse() {
        if (ui.btnHeart == null || !isRecording) return;

        ui.btnHeart.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(110)
            .withEndAction(() -> {
                if (ui.btnHeart != null) {
                    ui.btnHeart.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(110)
                        .start();
                }
            })
            .start();
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
        if (ui.chartView != null) {
            ui.chartView.clearData();
        }
        if (ui.tvHrvMetrics != null) {
            ui.tvHrvMetrics.setText("RMSSD: -- | pNN50: -- | LF/HF: -- | TP: --");
        }
        if (ui.btnHeart != null) {
            ui.btnHeart.setAlpha(1.0f);
        }
        onLog("Панель монитора активна");
    }

    private void stopMonitoring() {
        isRecording = false;
        piWindow.clear();
        if (ui.btnHeart != null) {
            ui.btnHeart.setAlpha(0.5f);
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

        runOnUiThread(() -> UiBuilder.updateStatusHeader(ui.tvLiveMetrics, currentSpo2, hrToDisplay, currentPi, currentBattery));

        if (!isPolarActive && res.isFingerOn && isRecording) {
            if (sessionStartTime == 0) sessionStartTime = now;
            int elapsedSec = (int) ((now - sessionStartTime) / 1000);

            String timestamp = timeFormat.format(new Date(now));
            DataPoint dp = new DataPoint(timestamp, elapsedSec, currentSpo2, res.hr, currentPi, 0);

            synchronized (sessionData) {
                sessionData.add(dp);
            }
            runOnUiThread(() -> ui.chartView.addDataPoint(dp));
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
                        UiBuilder.updateStatusHeader(ui.tvLiveMetrics, currentSpo2, currentPolarHr, currentPi, currentBattery);
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
                        if (ui.tvHrvMetrics != null) {
                            ui.tvHrvMetrics.setText(hrvDisplay);
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
