package com.local.o2test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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

    private int chartXIndex = 0;

    // UUIDs Polar H10
    private static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

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
            log("Запуск сканирования BLE...");
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

        setECG = createDataSet("Polar Pulse (BPM)", Color.GREEN);
        setPPG = createDataSet("Checkme PPG Wave", Color.RED);
        setSpO2 = createDataSet("Checkme SpO2 %", Color.CYAN);
        setRR = createDataSet("R-R Interval (ms)", Color.YELLOW);

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
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
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
            log("Запрос разрешений BLE...");
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            log("Разрешения активны. Начинаем поиск.");
            startBleScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startBleScan();
        }
    }

    private void startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            log("Включите Bluetooth!");
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null || isScanning) return;

        isScanning = true;
        log("Поиск BLE-датчиков...");

        try {
            bleScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            log("Ошибка сканирования: " + e.getMessage());
        }

        mainHandler.postDelayed(() -> {
            if (isScanning) {
                try {
                    bleScanner.stopScan(scanCallback);
                } catch (SecurityException ignored) {}
                isScanning = false;
                log("Сканирование завершено.");
            }
        }, 12000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String name = device.getName();
                if (name != null) {
                    if (name.contains("Polar") || name.contains("H10")) {
                        log("Найден Polar H10 [" + device.getAddress() + "]. Подключение...");
                        connectToDevice(device, true);
                    } else if (name.contains("Checkme") || name.contains("O2") || name.contains("Viatom") || name.contains("POD")) {
                        log("Найден Checkme O2 [" + device.getAddress() + "]. Подключение...");
                        connectToDevice(device, false);
                    }
                }
            } catch (SecurityException ignored) {}
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

    // Обработчик Polar H10
    private final BluetoothGattCallback polarGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Polar H10 подсоединен. Ищем характеристики...");
                try { gatt.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Polar H10 отключен.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(HEART_RATE_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic charac = service.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR);
                    if (charac != null) {
                        enableNotification(gatt, charac);
                        log("Подписка на пульс Polar H10 АКТИВИРОВАНА!");
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (HEART_RATE_MEASUREMENT_CHAR.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length > 1) {
                    int flags = value[0];
                    int hr = ((flags & 0x01) == 0) ? (value[1] & 0xFF) : (((value[2] & 0xFF) << 8) | (value[1] & 0xFF));

                    mainHandler.post(() -> {
                        tvHR.setText("HR: " + hr + " bpm");

                        chartXIndex++;
                        setECG.addEntry(new Entry(chartXIndex, hr));
                        if (setECG.getEntryCount() > 100) setECG.removeFirst();
                        dataECG.notifyDataChanged();
                        chartECG.notifyDataSetChanged();
                        chartECG.invalidate();
                    });
                }
            }
        }
    };

    // Обработчик Checkme O2 / Viatom
    private final BluetoothGattCallback checkmeGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Checkme O2 ПОДКЛЮЧЕН! Поиск сервисов...");
                try { gatt.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Checkme O2 отключен.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Сервисы Checkme O2 найдены. Включаем подписку...");
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic charac : service.getCharacteristics()) {
                        if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            enableNotification(gatt, charac);
                            log("Подписка Checkme O2 на UUID: " + charac.getUuid().toString().substring(0, 8));
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null && value.length > 0) {
                parseCheckmePacket(value);
            }
        }
    };

    private void parseCheckmePacket(byte[] data) {
        // Парсинг пакетов Viatom / Checkme O2
        int spo2 = -1;
        int pr = -1;
        float pi = -1f;

        if (data.length >= 8) {
            // Формат телеметрии Viatom / Checkme O2
            spo2 = data[0] & 0xFF;
            pr = data[1] & 0xFF;
            if (data.length >= 10) {
                pi = ((float)(data[2] & 0xFF)) / 10.0f;
            }
        }

        final int finalSpo2 = spo2;
        final int finalPr = pr;
        final float finalPi = pi;

        if (finalSpo2 > 50 && finalSpo2 <= 100) {
            mainHandler.post(() -> {
                tvSpO2.setText("SpO2: " + finalSpo2 + "%");
                if (finalPi > 0) tvPI.setText(String.format(Locale.US, "PI: %.1f%%", finalPi));

                setSpO2.addEntry(new Entry(chartXIndex, finalSpo2));
                if (setSpO2.getEntryCount() > 100) setSpO2.removeFirst();
                dataSpO2.notifyDataChanged();
                chartSpO2.notifyDataSetChanged();
                chartSpO2.invalidate();

                // ФПГ (PPG) волна
                if (finalPr > 0) {
                    setPPG.addEntry(new Entry(chartXIndex, finalPr));
                    if (setPPG.getEntryCount() > 100) setPPG.removeFirst();
                    dataPPG.notifyDataChanged();
                    chartPPG.notifyDataSetChanged();
                    chartPPG.invalidate();
                }
            });
        }
    }

    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        } catch (SecurityException e) {
            log("Ошибка подписки: " + e.getMessage());
        }
    }
}
