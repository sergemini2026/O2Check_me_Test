package com.local.o2test;

import android.content.Context;
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
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

public class MultiBleManager {

    public interface BleListener {
        void onLog(String message);
        void onO2DataReceived(byte[] data);
        void onPolarDataReceived(int hr, float rrMs);
    }

    private final Context context;
    private final BleListener listener;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt o2Gatt;
    private BluetoothGatt polarGatt;

    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // UUIDs Polar H10
    private static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UUIDs Checkme O2 / Viatom
    private static final UUID CHECKME_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CHECKME_WRITE_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    public MultiBleManager(Context context, BleListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void initAndStartScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            log("Внимание: Bluetooth выключен!");
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null || isScanning) return;

        isScanning = true;
        log("Запуск сканирования BLE устройств...");

        try {
            bleScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            log("Ошибка запуска сканера: " + e.getMessage());
        }

        handler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
                log("Сканирование завершено по таймауту.");
            }
        }, 15000);
    }

    private void stopScan() {
        if (bleScanner != null && isScanning) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (SecurityException ignored) {}
            isScanning = false;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String name = device.getName();
                if (name != null) {
                    if ((name.contains("Checkme") || name.contains("O2") || name.contains("Viatom") || name.contains("POD")) && o2Gatt == null) {
                        log("Найден Checkme O2 [" + device.getAddress() + "]. Подключение...");
                        connectO2(device);
                    } else if ((name.contains("Polar") || name.contains("H10")) && polarGatt == null) {
                        log("Найден Polar H10 [" + device.getAddress() + "]. Подключение...");
                        connectPolar(device);
                    }
                }
            } catch (SecurityException ignored) {}
        }
    };

    private void connectO2(BluetoothDevice device) {
        try {
            o2Gatt = device.connectGatt(context, false, o2GattCallback);
        } catch (SecurityException e) {
            log("Ошибка подключения O2: " + e.getMessage());
        }
    }

    private void connectPolar(BluetoothDevice device) {
        try {
            polarGatt = device.connectGatt(context, false, polarGattCallback);
        } catch (SecurityException e) {
            log("Ошибка подключения Polar: " + e.getMessage());
        }
    }

    // --- ОБРАБОТЧИК CHECKME O2 ---
    private final BluetoothGattCallback o2GattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Checkme O2 подключен. Поиск сервисов...");
                try { gatt.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Checkme O2 отключен.");
                o2Gatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Сервисы Checkme O2 найдены.");
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic charac : service.getCharacteristics()) {
                        if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            enableNotification(gatt, charac);
                        }
                        if ((charac.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                            sendO2StartCommand(gatt, charac);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && listener != null) {
                listener.onO2DataReceived(data);
            }
        }
    };

    // --- ОБРАБОТЧИК POLAR H10 ---
    private final BluetoothGattCallback polarGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Polar H10 подключен. Поиск сервисов...");
                try { gatt.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Polar H10 отключен.");
                polarGatt = null;
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
                        log("Polar H10: подписка на пульс активирована!");
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

                    float rrMs = 0f;
                    int offset = ((flags & 0x01) == 0) ? 2 : 3;
                    if ((flags & 0x08) != 0 && value.length >= offset + 2) {
                        int rrVal = ((value[offset + 1] & 0xFF) << 8) | (value[offset] & 0xFF);
                        rrMs = (rrVal / 1024.0f) * 1000.0f;
                    }

                    if (listener != null) {
                        listener.onPolarDataReceived(hr, rrMs);
                    }
                }
            }
        }
    };

    private void sendO2StartCommand(BluetoothGatt gatt, BluetoothGattCharacteristic charac) {
        byte[] cmd = new byte[]{(byte) 0xAA, 0x17, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x1B};
        try {
            charac.setValue(cmd);
            gatt.writeCharacteristic(charac);
            log("Checkme O2: стартовая команда отправлена.");
        } catch (SecurityException ignored) {}
    }

    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        } catch (SecurityException ignored) {}
    }

    private void log(String message) {
        if (listener != null) {
            listener.onLog(message);
        }
    }

    public void close() {
        stopScan();
        try {
            if (o2Gatt != null) { o2Gatt.disconnect(); o2Gatt.close(); o2Gatt = null; }
            if (polarGatt != null) { polarGatt.disconnect(); polarGatt.close(); polarGatt = null; }
        } catch (SecurityException ignored) {}
    }
}
