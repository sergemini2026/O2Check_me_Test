package com.local.o2test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;

    private TextView tvSpO2, tvHR, tvRMSSD, tvPI, tvLogConsole;
    private ScrollView scrollLog;
    private FloatingActionButton fabScan;

    private LineChart chartECG, chartPPG, chartSpO2, chartRR;
    private LineData dataECG, dataPPG, dataSpO2, dataRR;
    private LineDataSet setECG, setPPG, setSpO2, setRR;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt polarGatt, checkmeGatt;

    private boolean isScanning = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // UUIDs для Polar H10 и Checkme O2
    private static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        initCharts();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        checkAndRequestPermissions();
    }

    private void initUI() {
        tvSpO2 = findViewById(R.id.tvSpO2);
        tvHR = findViewById(R.id.tvHR);
        tvRMSSD = findViewById(R.id.tvRMSSD);
        tvPI = findViewById(R.id.tvPI);
        tvLogConsole = findViewById(R.id.tvLogConsole);
        scrollLog = findViewById(R.id.scrollLog);
        fabScan = findViewById(R.id.fabScan);

        fabScan.setOnClickListener(v -> {
            log("Ручной запуск сканирования BLE...");
            startBleScan();
        });
    }

    private void log(String message) {
        mainHandler.post(() -> {
            String time = timeFormat.format(new Date());
            tvLogConsole.append("[" + time + "] " + message + "\n");
            scrollLog.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void initCharts() {
        chartECG = findViewById(R.id.chartECG);
        chartPPG = findViewById(R.id.chartPPG);
        chartSpO2 = findViewById(R.id.chartSpO2);
        chartRR = findViewById(R.id.chartRR);

        setECG = createDataSet("ECG / ЭКГ", Color.GREEN);
        setPPG = createDataSet("PPG / ФПГ", Color.RED);
        setSpO2 = createDataSet("SpO2 %", Color.CYAN);
        setRR = createDataSet("R-R Интервал", Color.YELLOW);

        dataECG = new LineData(setECG);
        dataPPG = new LineData(setPPG);
        dataSpO2 = new LineData(setSpO2);
        dataRR = new LineData(setRR);

        setupChart(chartECG, dataECG);
        setupChart(chartPPG, dataPPG);
        setupChart(chartSpO2, dataSpO2);
        setupChart(chartRR, dataRR);
    }

    private LineDataSet createDataSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setColor(color);
        set.setLineWidth(1.5f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        return set;
    }

    private void setupChart(LineChart chart, LineData data) {
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.invalidate();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        List<String> neededPermissions = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(perm);
            }
        }

        if (!neededPermissions.isEmpty()) {
            log("Запрос разрешений BLE у пользователя...");
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            log("Все разрешения получены.");
            startBleScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                log("Разрешения успешно предоставлены!");
                startBleScan();
            } else {
                log("ОШИБКА: Разрешения не предоставлены. Сканирование невозможно.");
            }
        }
    }

    private void startBleScan() {
        if (bluetoothAdapter == null) {
            log("ОШИБКА: Устройство не поддерживает Bluetooth!");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            log("ВНИМАНИЕ: Bluetooth выключен! Пожалуйста, включите Bluetooth.");
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            log("ОШИБКА: Сканер BLE недоступен.");
            return;
        }

        if (isScanning) {
            log("Сканирование уже идет...");
            return;
        }

        isScanning = true;
        log("Поиск BLE-датчиков запущен...");

        try {
            bleScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            log("Ошибка безопасности при запуске сканера: " + e.getMessage());
        }

        mainHandler.postDelayed(() -> {
            if (isScanning) {
                try {
                    bleScanner.stopScan(scanCallback);
                } catch (SecurityException ignored) {}
                isScanning = false;
                log("Таймаут сканирования завершен.");
            }
        }, 15000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String name = device.getName();
                String address = device.getAddress();

                if (name != null) {
                    log("Найден девайс: " + name + " [" + address + "]");

                    if (name.contains("Polar") || name.contains("H10")) {
                        log(" Обнаружен Polar H10! Подключаемся...");
                        connectToDevice(device, true);
                    } else if (name.contains("Checkme") || name.contains("O2") || name.contains("Viatom")) {
                        log(" Обнаружен Checkme O2! Подключаемся...");
                        connectToDevice(device, false);
                    }
                }
            } catch (SecurityException e) {
                log("Ошибка доступа к имени девайса: " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            log("ОШИБКА сканирования BLE, код: " + errorCode);
            isScanning = false;
        }
    };

    private void connectToDevice(BluetoothDevice device, boolean isPolar) {
        try {
            if (isPolar) {
                polarGatt = device.connectGatt(this, false, polarGattCallback);
            } else {
                checkmeGatt = device.connectGatt(this, false, checkmeGattCallback);
            }
        } catch (SecurityException e) {
            log("Ошибка подключения: " + e.getMessage());
        }
    }

    private final BluetoothGattCallback polarGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Polar H10 ПОДКЛЮЧЕН! Поиск сервисов...");
                try {
                    gatt.discoverServices();
                } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Polar H10 ОТКЛЮЧЕН.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Сервисы Polar H10 найдены.");
            }
        }
    };

    private final BluetoothGattCallback checkmeGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Checkme O2 ПОДКЛЮЧЕН! Поиск сервисов...");
                try {
                    gatt.discoverServices();
                } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Checkme O2 ОТКЛЮЧЕН.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Сервисы Checkme O2 найдены.");
            }
        }
    };
}
