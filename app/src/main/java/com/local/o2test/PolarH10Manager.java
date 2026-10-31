package com.local.o2test;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class PolarH10Manager {

    private static final boolean DEBUG = true;
    private static final String TAG = "PolarH10Manager";

    // Включатель режимов: false — реальный датчик, true — эмуляция
    public static final boolean USE_MOCK_DATA = false;

    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_CHAR_UUID    = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    public interface PolarCallback {
        void onPolarLog(String message);
        void onRrReceived(int rrMs, float instantHr);
        default void onPolarBatteryReceived(int battery) {}
    }

    private final Context context;
    private final PolarCallback callback;
    private BluetoothGatt gatt;
    
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean isCollecting = false; // Флаг разрешения сбора данных (управляется Панелью монитора)
    private int batteryLevel = 0;

    public PolarH10Manager(Context context, PolarCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void connect(BluetoothDevice device) {
        log("Polar H10: Подключение к " + device.getAddress());
        isConnecting = true;
        gatt = device.connectGatt(context, false, gattCallback);
    }

    /**
     * Запуск сбора данных (вызывается строго при активации Панели монитора)
     */
    public void startCollection() {
        isCollecting = true;
        if (DEBUG) {
            Log.d(TAG, "[POLAR_TRANSPORT] Старт трансляции сырых RR-интервалов...");
        }
        log("Polar H10: Старт сбора данных...");
    }

    /**
     * Остановка сбора данных
     */
    public void stopCollection() {
        isCollecting = false;
        if (DEBUG) {
            Log.d(TAG, "[POLAR_TRANSPORT] Трансляция сырых RR-интервалов остановлена.");
        }
        log("Polar H10: Сбор данных остановлен.");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                isConnecting = false;
                log("Polar H10: Подключен.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                isConnecting = false;
                isCollecting = false;
                log("Polar H10: Отключен.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(HR_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(HR_CHAR_UUID);
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
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
                BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE_UUID);
                if (batteryService != null) {
                    BluetoothGattCharacteristic batteryChar = batteryService.getCharacteristic(BATTERY_CHAR_UUID);
                    if (batteryChar != null) {
                        gatt.readCharacteristic(batteryChar);
                        gatt.setCharacteristicNotification(batteryChar, true);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    batteryLevel = data[0] & 0xFF;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) {
                            callback.onPolarBatteryReceived(batteryLevel);
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicData(characteristic.getUuid(), characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleCharacteristicData(characteristic.getUuid(), value);
        }

        private void handleCharacteristicData(UUID uuid, byte[] data) {
            if (BATTERY_CHAR_UUID.equals(uuid)) {
                if (data != null && data.length > 0) {
                    batteryLevel = data[0] & 0xFF;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) {
                            callback.onPolarBatteryReceived(batteryLevel);
                        }
                    });
                }
                return;
            }

            if (HR_CHAR_UUID.equals(uuid)) {
                if (data != null && data.length > 1) {
                    // ЕСЛИ ПАНЕЛЬ МОНИТОРА НЕ АКТИВНА — ИГНОРИРУЕМ ВХОДЯЩИЙ ПОТОК ДАННЫХ
                    if (!isCollecting) return;

                    List<Integer> newRrList = HrvCalculator.parseRrIntervals(data);
                    for (int rr : newRrList) {
                        if (rr < 300 || rr > 2000) continue;

                        final int rrMs = rr;
                        final float instantHr = 60000.0f / rrMs;

                        if (DEBUG) {
                            Log.d(TAG, "[POLAR_TRANSPORT] Транзит сырого rrMs: " + rrMs + " ms, мгновенный ЧСС: " + instantHr);
                        }

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (callback != null) {
                                callback.onRrReceived(rrMs, instantHr);
                            }
                        });
                    }
                }
            }
        }
    };

    private void log(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (callback != null) callback.onPolarLog(msg);
        });
    }

    public void disconnect() {
        isCollecting = false;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }
}
