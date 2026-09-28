package com.local.o2test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView logView;
    private BluetoothLeScanner scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(18);
        logView.setPadding(30, 30, 30, 30);
        logView.setText("Запуск BLE-сканера O2...\n");
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
        log("Права получены! Начинаем поиск...");
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

        log("Поиск устройств Viatom/Checkme/O2...");
        try {
            scanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result.getDevice() != null) {
                        String name = result.getDevice().getName();
                        String address = result.getDevice().getAddress();
                        if (name != null) {
                            log("Найдено: " + name + " [" + address + "]");
                        }
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
}
