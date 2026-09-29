package com.local.o2test;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class O2BleManager {

    public interface BleListener {
        void onLog(String message);
        void onDataReceived(byte[] data);
    }

    private static final UUID SERVICE_UUID = UUID.fromString("14839ac4-7d7e-415c-9a42-167340cf2339");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("8b00ace7-eb0b-49b0-bbe9-9aee0a26e1a3");
    private static final UUID NOTIFY_CHAR_UUID = UUID.fromString("0734594a-a8e7-4b1a-a6b1-cd5243059a57");
    private static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BleListener listener;

    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeChar;
    private final Set<String> discoveredDevices = new HashSet<>();
    private boolean isConnecting = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    public O2BleManager(Context context, BleListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    public void initAndStartScan() {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;

        if (adapter == null || !adapter.isEnabled()) {
            listener.onLog("ОШИБКА: Bluetooth выключен!");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onLog("ОШИБКА: BLE-сканер недоступен.");
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

                    if (discoveredDevices.add(address)) {
                        listener.onLog("Найдено: " + name + " [" + address + "]");
                    }

                    if (!isConnecting && (name.contains("O2") || name.contains("Viatom") || name.contains("Checkme"))) {
                        isConnecting = true;
                        listener.onLog(">>> ДАТЧИК ОБНАРУЖЕН: " + name + " <<<");
                        scanner.stopScan(this);
                        connectToDevice(device);
                    }
                }
            });
        } catch (SecurityException e) {
            listener.onLog("Ошибка разрешений: " + e.getMessage());
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        listener.onLog("Соединение установлено. Поиск сервисов...");
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        listener.onLog("Соединение разорвано.");
                        isConnecting = false;
                        stopTimer();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        BluetoothGattService service = gatt.getService(SERVICE_UUID);
                        if (service != null) {
                            writeChar = service.getCharacteristic(WRITE_CHAR_UUID);
                            BluetoothGattCharacteristic notifyChar = service.getCharacteristic(NOTIFY_CHAR_UUID);

                            if (notifyChar != null) {
                                listener.onLog("Подписка на поток данных...");
                                gatt.setCharacteristicNotification(notifyChar, true);
                                BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                                if (descriptor != null) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        listener.onLog("Канал готов. Запуск интервала опроса...");
                        startPeriodicRequest();
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    listener.onDataReceived(characteristic.getValue());
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
                    listener.onDataReceived(value);
                }
            });
        } catch (SecurityException e) {
            listener.onLog("Ошибка подключения: " + e.getMessage());
        }
    }

    private void startPeriodicRequest() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                sendRtDataRequest();
                timerHandler.postDelayed(this, 3000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    public void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    public void sendRtDataRequest() {
        if (bluetoothGatt == null || writeChar == null) return;
        try {
            byte[] cmd = new byte[]{(byte) 0xAA, 0x17, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x1B};
            writeChar.setValue(cmd);
            bluetoothGatt.writeCharacteristic(writeChar);
        } catch (SecurityException ignored) {}
    }

    public void close() {
        stopTimer();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException ignored) {}
            bluetoothGatt = null;
        }
    }
}
