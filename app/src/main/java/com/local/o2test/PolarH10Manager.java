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

    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long UPDATE_INTERVAL_MS = 30000; // Задержка вывода метрик 30 сек
    private static final int MAX_RR_COUNT = 300; // Расчет по последним 300 интервалам

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
    private final List<Integer> hrWindow = new ArrayList<>();
    private long lastReportTime = 0;

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
            hrWindow.clear();
            lastReportTime = 0;
        }
        gatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                isConnecting = false;
                log("Polar H10: Подключен. Накопление данных...");
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
                            log("Polar H10: Старт накопления буфера (300 стабильных RR-интервалов)...");
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

                    long now = System.currentTimeMillis();

                    synchronized (rrBuffer) {
                        hrWindow.add(currentHr);

                        for (int rr : newRrList) {
                            rrBuffer.add(rr);

                            // Поддерживаем ровно 300 последних валидных интервалов
                            while (rrBuffer.size() > MAX_RR_COUNT) {
                                rrBuffer.poll();
                            }

                            final int rrMs = rr;
                            final float fHr = currentHr;
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (callback != null) {
                                    callback.onRrReceived(rrMs, fHr);
                                }
                            });
                        }

                        if (lastReportTime == 0) {
                            lastReportTime = now;
                        }

                        if (now - lastReportTime >= UPDATE_INTERVAL_MS) {
                            lastReportTime = now;

                            int avgHr = currentHr;
                            if (!hrWindow.isEmpty()) {
                                int sumHr = 0;
                                for (int hr : hrWindow) sumHr += hr;
                                avgHr = Math.round((float) sumHr / hrWindow.size());
                                hrWindow.clear();
                            }

                            List<Integer> snapshot = new ArrayList<>(rrBuffer);
                            HrvCalculator.Metrics hrv = HrvCalculator.calculate(snapshot);
                            int count = snapshot.size();

                            final int finalAvgHr = avgHr;
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (callback != null) {
                                    callback.onPolarHrReceived(finalAvgHr, hrv, count);
                                }
                            });
                        }
                    }
                }
            }
        }
    };

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
