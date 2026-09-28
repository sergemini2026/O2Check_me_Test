package com.local.o2test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice targetDevice;

    private static final String TARGET_DEVICE_NAME = "Checkme O2"; // или ваше имя устройства
    
    // UUID сервисов Viatom (стандартные)
    private static final UUID UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHAR_NOTIFY = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHAR_WRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView tvStatus, tvMetrics;
    private Button btnConnect, btnStop, btnSave, btnExit;

    private boolean isMonitoring = false;
    private List<DataPoint> sessionData = new ArrayList<>();
    private long sessionStartTime = 0;

    private static class DataPoint {
        String timestamp;
        long elapsedSec;
        int spo2;
        int hr;
        int pi;

        DataPoint(String timestamp, long elapsedSec, int spo2, int hr, int pi) {
            this.timestamp = timestamp;
            this.elapsedSec = elapsedSec;
            this.spo2 = spo2;
            this.hr = hr;
            this.pi = pi;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvMetrics = findViewById(R.id.tvMetrics);
        btnConnect = findViewById(R.id.btnConnect);
        btnStop = findViewById(R.id.btnStop);
        btnSave = findViewById(R.id.btnSave);
        btnExit = findViewById(R.id.btnExit);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        btnConnect.setOnClickListener(v -> startMonitoringSession());
        btnStop.setOnClickListener(v -> stopMonitoringSession());
        btnSave.setOnClickListener(v -> saveCsvFile());
        btnExit.setOnClickListener(v -> finish());
    }

    private void startMonitoringSession() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Включите Bluetooth!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
            return;
        }

        tvStatus.setText("Поиск устройства...");
        sessionData.clear();
        isMonitoring = true;
        sessionStartTime = System.currentTimeMillis();

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            String name = device.getName();
            if (name != null && (name.contains("Checkme") || name.contains("O2"))) {
                targetDevice = device;
                break;
            }
        }

        if (targetDevice == null) {
            tvStatus.setText("Устройство не найдено в сопряженных!");
            isMonitoring = false;
            return;
        }

        tvStatus.setText("Подключение к " + targetDevice.getName() + "...");
        bluetoothGatt = targetDevice.connectGatt(this, false, gattCallback);
    }
        private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> tvStatus.setText("Подключено. Чтение..."));
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> tvStatus.setText("Отключено"));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattCharacteristic characteristic = gatt.getService(UUID_SERVICE).getCharacteristic(UUID_CHAR_WRITE);
                if (characteristic != null) {
                    characteristic.setValue(new byte[]{0x02, 0x7b, 0x01, 0x42}); // Команда опроса
                    gatt.writeCharacteristic(characteristic);
                }
                
                BluetoothGattCharacteristic notifyChar = gatt.getService(UUID_SERVICE).getCharacteristic(UUID_CHAR_NOTIFY);
                if (notifyChar != null) {
                    gatt.setCharacteristicNotification(notifyChar, true);
                    BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(UUID_DESCRIPTOR);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 5) {
                int spo2 = data[3] & 0xFF;
                int hr = data[4] & 0xFF;
                int pi = data[5] & 0xFF;

                long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
                String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                if (isMonitoring) {
                    sessionData.add(new DataPoint(timeStr, elapsed, spo2, hr, pi));
                }

                runOnUiThread(() -> tvMetrics.setText(String.format("SpO2: %d%% | HR: %d | PI: %d", spo2, hr, pi)));
            }
        }
    };

    private void stopMonitoringSession() {
        isMonitoring = false;
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        tvStatus.setText("Мониторинг остановлен");
    }

    private void saveCsvFile() {
        if (sessionData.isEmpty()) {
            Toast.makeText(this, "Нет данных для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File dir = getExternalFilesDir(null);
            File file = new File(dir, "O2_Session_" + System.currentTimeMillis() + ".csv");
            FileWriter writer = new FileWriter(file);
            
            writer.append("Timestamp,ElapsedSec,SpO2,HR,PI\n");
            for (DataPoint dp : sessionData) {
                writer.append(dp.timestamp).append(",")
                      .append(String.valueOf(dp.elapsedSec)).append(",")
                      .append(String.valueOf(dp.spo2)).append(",")
                      .append(String.valueOf(dp.hr)).append(",")
                      .append(String.valueOf(dp.pi)).append("\n");
            }
            writer.flush();
            writer.close();
            
            Toast.makeText(this, "Сохранено в: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoringSession();
    }
}
