package com.local.o2test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView logView;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private final Set<String> discoveredDevices = new HashSet<>();
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(16);
        logView.setPadding(30, 30, 30, 30);
        logView.setText("Запуск BLE-сканера...\n");
        scrollView.addView(logView);
        setContentView(scrollView);

        checkAndRequestPermissions();
    }

    private void log(String text) {
        runOnUiThread(() -> logView.append(text + "\n"));
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        log("Права получены. Поиск пульсоксиметра...");
        initBLE();
    }

    private void initBLE() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;

        if (adapter == null || !adapter.isEnabled()) {
            log("ОШИБКА: Bluetooth выключен!");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            log("ОШИБКА: Сканер недоступен.");
            return;
        }

        startScanning();
    }

    private void startScanning() {
        discoveredDevices.clear();
        try {
            scanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    if (device == null) return;

                    String address = device.getAddress();
                    String name = device.getName();

                    if (name == null) return;

                    // Выводим в лог новое устройство (без повторов)
                    if (discoveredDevices.add(address)) {
                        log("Найдено: " + name + " [" + address + "]");
                    }

                    // Если нашли наше устройство O2 / Viatom / Checkme
                    if (!isConnecting && (name.contains("O2") || name.contains("Viatom") || name.contains("Checkme"))) {
                        isConnecting = true;
                        log("\n>>> ЦЕЛЬ НАЙДЕНА: " + name + " <<<");
                        log("Останавливаем сканирование и подключаемся...");
                        scanner.stopScan(this);
                        connectToDevice(device);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    log("Ошибка сканирования: " + errorCode);
                }
            });
        } catch (SecurityException e) {
            log("Ошибка доступов: " + e.getMessage());
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        log("УСПЕХ: Подключено к " + device.getName() + "!");
                        log("Ищем сервисы и характеристики данных...");
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        log("Отключено от устройства.");
                        isConnecting = false;
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("Сервисы найдены:");
                        for (BluetoothGattService service : gatt.getServices()) {
                            log(" Service: " + service.getUuid().toString());
                            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                                log("   └ Char: " + c.getUuid().toString());
                            }
                        }
                    } else {
                        log("Ошибка поиска сервисов: " + status);
                    }
                }
            });
        } catch (SecurityException e) {
            log("Ошибка подключения: " + e.getMessage());
        }
    }
}
