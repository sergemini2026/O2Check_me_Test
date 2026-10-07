package com.local.o2test;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity implements HrvForegroundService.ServiceCallback {

    private HrvForegroundService hrvService;
    private boolean isBound = false;

    private UiBuilder uiBuilder;
    private TrendChartView trendChartView;
    private TextView tvHeartRate;
    private TextView tvSpo2;
    private TextView tvHrvMetrics;
    private TextView tvStatus;
    private Button btnConnectPolar;
    private Button btnConnectO2;
    private Button btnToggleRecord;
    private Button btnExportCsv;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            HrvForegroundService.LocalBinder binder = (HrvForegroundService.LocalBinder) service;
            hrvService = binder.getService();
            isBound = true;
            hrvService.setCallback(MainActivity.this);

            restoreSessionState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            if (hrvService != null) {
                hrvService.setCallback(null);
            }
            hrvService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiBuilder = new UiBuilder(this);
        View rootView = uiBuilder.buildUi();
        setContentView(rootView);

        bindUiComponents();
        setupListeners();

        if (PermissionHelper.hasPermissions(this)) {
            startAndBindService();
        } else {
            PermissionHelper.requestPermissions(this);
        }
    }

    private void bindUiComponents() {
        trendChartView = uiBuilder.getTrendChartView();
        tvHeartRate = uiBuilder.getTvHeartRate();
        tvSpo2 = uiBuilder.getTvSpo2();
        tvHrvMetrics = uiBuilder.getTvHrvMetrics();
        tvStatus = uiBuilder.getTvStatus();
        btnConnectPolar = uiBuilder.getBtnConnectPolar();
        btnConnectO2 = uiBuilder.getBtnConnectO2();
        btnToggleRecord = uiBuilder.getBtnToggleRecord();
        btnExportCsv = uiBuilder.getBtnExportCsv();
    }

    private void setupListeners() {
        if (btnConnectPolar != null) {
            btnConnectPolar.setOnClickListener(v -> {
                if (isBound && hrvService != null && hrvService.getPolarManager() != null) {
                    hrvService.getPolarManager().connect();
                    if (tvStatus != null) {
                        tvStatus.setText("Подключение к Polar H10...");
                    }
                }
            });
        }

        if (btnConnectO2 != null) {
            btnConnectO2.setOnClickListener(v -> {
                if (isBound && hrvService != null && hrvService.getO2Manager() != null) {
                    hrvService.getO2Manager().connect();
                    if (tvStatus != null) {
                        tvStatus.setText("Подключение к O2 Ring...");
                    }
                }
            });
        }

        if (btnToggleRecord != null) {
            btnToggleRecord.setOnClickListener(v -> toggleRecording());
        }

        if (btnExportCsv != null) {
            btnExportCsv.setOnClickListener(v -> exportSessionToCsv());
        }
    }

    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, HrvForegroundService.class);
        serviceIntent.setAction(HrvForegroundService.ACTION_START_FOREGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void restoreSessionState() {
        if (!isBound || hrvService == null) return;

        boolean isRecording = hrvService.isRecording();
        if (btnToggleRecord != null) {
            btnToggleRecord.setText(isRecording ? "Стоп и Сохранить" : "Старт Замера");
        }

        List<Integer> existingRrList = hrvService.getSessionRrData();
        if (existingRrList != null && !existingRrList.isEmpty()) {
            if (trendChartView != null) {
                trendChartView.clearChart();
                for (int rr : existingRrList) {
                    trendChartView.addRrPoint(rr);
                }
            }
            updateHrvMetrics(existingRrList);
        }
    }

    private void toggleRecording() {
        if (!isBound || hrvService == null) {
            Toast.makeText(this, "Сервис не подключен", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hrvService.isRecording()) {
            hrvService.stopSessionRecording();
            if (btnToggleRecord != null) {
                btnToggleRecord.setText("Старт Замера");
            }
            if (tvStatus != null) {
                tvStatus.setText("Запись остановлена");
            }
            List<Integer> fullSessionRr = hrvService.getSessionRrData();
            updateHrvMetrics(fullSessionRr);
            Toast.makeText(this, "Сессия записана. Точек: " + fullSessionRr.size(), Toast.LENGTH_SHORT).show();
        } else {
            if (trendChartView != null) {
                trendChartView.clearChart();
            }
            hrvService.startSessionRecording();
            if (btnToggleRecord != null) {
                btnToggleRecord.setText("Стоп и Сохранить");
            }
            if (tvStatus != null) {
                tvStatus.setText("Идет запись сессии...");
            }
        }
    }

    private void exportSessionToCsv() {
        if (!isBound || hrvService == null) {
            Toast.makeText(this, "Сервис не подключен", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> rrData = hrvService.getSessionRrData();
        if (rrData.isEmpty()) {
            Toast.makeText(this, "Нет данных RR для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = CsvExporter.exportRrSession(this, rrData);
        if (success) {
            Toast.makeText(this, "Данные успешно экспортированы в CSV", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Ошибка при экспорте CSV", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHrvMetrics(List<Integer> rrList) {
        if (rrList == null || rrList.size() < 5) {
            if (tvHrvMetrics != null) {
                tvHrvMetrics.setText("Метрики ВСР: недостаточно данных");
            }
            return;
        }

        HrvCalculator.HrvResult result = HrvCalculator.calculate(rrList);
        if (result != null && tvHrvMetrics != null) {
            String metricsText = String.format(
                    "SDNN: %.1f ms | RMSSD: %.1f ms | pNN50: %.1f%% | SI: %.1f | TP: %.1f ms²",
                    result.sdnn, result.rmssd, result.pnn50, result.stressIndex, result.totalPower
            );
            tvHrvMetrics.setText(metricsText);
        }
    }

    @Override
    public void onRrDataReceived(int rrMs, int hr) {
        runOnUiThread(() -> {
            if (tvHeartRate != null && hr > 0) {
                tvHeartRate.setText("Пульс: " + hr + " уд/мин (RR: " + rrMs + " ms)");
            }
            if (trendChartView != null && rrMs > 0) {
                trendChartView.addRrPoint(rrMs);
            }
            if (isBound && hrvService != null && hrvService.isRecording()) {
                updateHrvMetrics(hrvService.getSessionRrData());
            }
        });
    }

    @Override
    public void onO2DataReceived(int spo2, int hr) {
        runOnUiThread(() -> {
            if (tvSpo2 != null && spo2 > 0) {
                tvSpo2.setText("SpO2: " + spo2 + "%");
            }
            if (tvHeartRate != null && hr > 0) {
                tvHeartRate.setText("Пульс: " + hr + " уд/мин");
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(String deviceType, boolean isConnected) {
        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText(deviceType + ": " + (isConnected ? "Подключено" : "Отключено"));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PermissionHelper.hasPermissions(this) && !isBound) {
            Intent intent = new Intent(this, HrvForegroundService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            if (hrvService != null) {
                hrvService.setCallback(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                startAndBindService();
            } else {
                Toast.makeText(this, "Для работы приложения необходимы запрашиваемые разрешения", Toast.LENGTH_LONG).show();
            }
        }
    }
}
