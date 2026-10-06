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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class PolarH10Manager {

    // Включатель режимов: false — реальный датчик, true — эмуляция
    public static final boolean USE_MOCK_DATA = false;

    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_CHAR_UUID    = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private static final int MAX_RR_COUNT = 300; 

    public interface PolarCallback {
        void onPolarLog(String message);
        void onPolarHrReceived(int avgHr, HrvCalculator.Metrics hrv, int rrCount);
        void onRrReceived(int rrMs, float instantHr);
        default void onPolarBatteryReceived(int battery) {}
    }

    private final Context context;
    private final PolarCallback callback;
    private BluetoothGatt gatt;
    
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private int batteryLevel = 0;
    
    private final Queue<Integer> rrBuffer = new LinkedList<>();
    private final List<Integer> hrWindow = new ArrayList<>();

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
        synchronized (rrBuffer) {
            rrBuffer.clear();
            hrWindow.clear();
        }
        gatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                isConnecting = false;
                log("Polar H10: Подключен. Старт накопления данных (300 RR)...");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                isConnecting = false;
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
            if (BATTERY_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
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

            if (HR_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 1) {
                    int currentHr = parseHeartRate(data);
                    List<Integer> newRrList = HrvCalculator.parseRrIntervals(data);

                    synchronized (rrBuffer) {
                        hrWindow.add(currentHr);

                        for (int rr : newRrList) {
                            if (rr < 300 || rr > 2000) continue;

                            rrBuffer.add(rr);

                            final int rrMs = rr;
                            final float instantHr = 60000.0f / rrMs;

                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (callback != null) {
                                    callback.onRrReceived(rrMs, instantHr);
                                }
                            });

                            int currentCount = rrBuffer.size();

                            // 1. При полном заполнении (300) — полный расчет и сброс
                            if (currentCount == MAX_RR_COUNT) {
                                List<Integer> snapshot = new ArrayList<>(rrBuffer);
                                HrvCalculator.Metrics hrv = HrvCalculator.calculate(snapshot);
                                
                                int avgHr = getAverageHr();
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (callback != null) {
                                        callback.onPolarHrReceived(avgHr, hrv, MAX_RR_COUNT);
                                    }
                                });

                                rrBuffer.clear();
                                hrWindow.clear();

                            // 2. В процессе сбора (каждые 10 ударов) — отправляем промежуточный статус
                            } else if (currentCount % 10 == 0) {
                                List<Integer> snapshot = new ArrayList<>(rrBuffer);
                                // Промежуточный расчет быстрых метрик, если набралось хотя бы 30 ударов
                                HrvCalculator.Metrics hrv = currentCount >= 30 ? HrvCalculator.calculate(snapshot) : null;
                                int avgHr = getAverageHr();
                                
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (callback != null) {
                                        callback.onPolarHrReceived(avgHr, hrv, currentCount);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    };

    private int getAverageHr() {
        if (hrWindow.isEmpty()) return 0;
        int sum = 0;
        for (int hr : hrWindow) {
            sum += hr;
        }
        return Math.round((float) sum / hrWindow.size());
    }

    private int parseHeartRate(byte[] data) {
        byte flags = data[0];
        boolean is16Bit = (flags & 0x01) != 0;
        if (is16Bit && data.length >= 3) {
            return ((data[2] & 0xFF) << 8) | (data[1] & 0xFF);
        } else {
            return data[1] & 0xFF;
        }
    }

    private void log(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (callback != null) callback.onPolarLog(msg);
        });
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }
}
