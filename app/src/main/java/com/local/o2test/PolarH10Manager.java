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
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class PolarH10Manager {

    // Включатель режимов: false — реальный датчик, true — эмуляция
    public static final boolean USE_MOCK_DATA = false;

    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int MAX_RR_COUNT = 300; 

    public interface PolarCallback {
        void onPolarLog(String message);
        void onPolarHrReceived(int avgHr, HrvCalculator.Metrics hrv, int rrCount);
        void onRrReceived(int rrMs, float instantHr);
    }

    private final Context context;
    private final PolarCallback callback;
    private BluetoothGatt gatt;
    
    private boolean isConnected = false;
    private boolean isConnecting = false;
    
    private final Queue<Integer> rrBuffer = new LinkedList<>();
    private final List<Integer> rawGoldenBuffer = new ArrayList<>(); // Буфер для Golden Dataset
    private final List<Integer> hrWindow = new ArrayList<>();

    public PolarH10Manager(Context context, PolarCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connect(BluetoothDevice device) {
        log("Polar H10: Подключение к " + device.getAddress());
        isConnecting = true;
        synchronized (rrBuffer) {
            rrBuffer.clear();
            rawGoldenBuffer.clear();
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
                log("Polar H10: Подключен. Запись сырого датасета (300 RR)...");
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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
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
                            
                            // Сохраняем сырой интервал для сборки Golden Dataset
                            if (rawGoldenBuffer.size() < MAX_RR_COUNT) {
                                rawGoldenBuffer.add(rr);
                                
                                // Когда накопилось ровно 300 сырых RR — печатаем готовый массив в лог
                                if (rawGoldenBuffer.size() == MAX_RR_COUNT) {
                                    dumpGoldenDataset();
                                }
                            }

                            final int rrMs = rr;
                            final float instantHr = 60000.0f / rrMs;

                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (callback != null) {
                                    callback.onRrReceived(rrMs, instantHr);
                                }
                            });

                            // При достижении 300 интервалов считаем итоговые метрики
                            if (rrBuffer.size() == MAX_RR_COUNT) {
                                List<Integer> snapshot = new ArrayList<>(rrBuffer);
                                HrvCalculator.Metrics hrv = HrvCalculator.calculate(snapshot);
                                
                                int avgHr = currentHr;
                                if (!hrWindow.isEmpty()) {
                                    int sumHr = 0;
                                    for (int hr : hrWindow) sumHr += hr;
                                    avgHr = Math.round((float) sumHr / hrWindow.size());
                                    hrWindow.clear();
                                }

                                final int finalAvgHr = avgHr;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (callback != null) {
                                        callback.onPolarHrReceived(finalAvgHr, hrv, MAX_RR_COUNT);
                                    }
                                });

                                rrBuffer.clear();
                            }
                        }
                    }
                }
            }
        }
    };

    private void dumpGoldenDataset() {
        StringBuilder sb = new StringBuilder();
        sb.append("GOLDEN_DATASET = new int[]{");
        for (int i = 0; i < rawGoldenBuffer.size(); i++) {
            sb.append(rawGoldenBuffer.get(i));
            if (i < rawGoldenBuffer.size() - 1) sb.append(", ");
        }
        sb.append("};");

        log("=== СКОПИРУЙТЕ ЭТОТ МАССИВ В JUNIT ТЕСТ ===");
        log(sb.toString());
        log("==========================================");
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
