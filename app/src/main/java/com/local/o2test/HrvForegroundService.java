package com.local.o2test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HrvForegroundService extends Service {

    public static final String CHANNEL_ID = "HrvServiceChannel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND";

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;

    private final List<Integer> rrSessionList = Collections.synchronizedList(new ArrayList<>());
    private boolean isRecording = false;

    private PolarH10Manager polarManager;
    private O2BleManager o2Manager;
    private ServiceCallback callback;

    public interface ServiceCallback {
        void onRrDataReceived(int rrMs, int hr);
        void onO2DataReceived(int spo2, int hr);
        void onConnectionStatusChanged(String deviceType, boolean isConnected);
    }

    public class LocalBinder extends Binder {
        public HrvForegroundService getService() {
            return HrvForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "O2Test::HrvWakeLock");
        }

        initBleManagers();
    }

    private void initBleManagers() {
        polarManager = new PolarH10Manager(getApplicationContext(), new PolarH10Manager.PolarCallback() {
            @Override
            public void onRrData(int rrMs, int hr) {
                if (isRecording && rrMs > 0) {
                    rrSessionList.add(rrMs);
                }
                if (callback != null) {
                    callback.onRrDataReceived(rrMs, hr);
                }
                updateNotificationText("Пульс: " + hr + " уд/мин | RR: " + rrMs + " ms");
            }

            @Override
            public void onStatusChanged(boolean isConnected) {
                if (callback != null) {
                    callback.onConnectionStatusChanged("Polar H10", isConnected);
                }
            }
        });

        o2Manager = new O2BleManager(getApplicationContext(), new O2BleManager.O2Callback() {
            @Override
            public void onDataReceived(int spo2, int hr, List<Integer> rrList) {
                if (isRecording && rrList != null) {
                    for (int rr : rrList) {
                        if (rr > 0) {
                            rrSessionList.add(rr);
                        }
                    }
                }
                if (callback != null) {
                    callback.onO2DataReceived(spo2, hr);
                    if (rrList != null && !rrList.isEmpty()) {
                        for (int rr : rrList) {
                            callback.onRrDataReceived(rr, hr);
                        }
                    }
                }
                if (spo2 > 0) {
                    updateNotificationText("SpO2: " + spo2 + "% | HR: " + hr + " уд/мин");
                }
            }

            @Override
            public void onStatusChanged(boolean isConnected) {
                if (callback != null) {
                    callback.onConnectionStatusChanged("O2 Ring", isConnected);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_START_FOREGROUND.equals(action)) {
                startForegroundServiceInternal();
            } else if (ACTION_STOP_FOREGROUND.equals(action)) {
                stopForegroundServiceInternal();
            }
        }
        return START_STICKY;
    }

    private void startForegroundServiceInternal() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        Notification notification = buildNotification("Мониторинг ВСР активен...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundServiceInternal() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
        stopSelf();
    }

    public void updateNotificationText(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("O2Test — Запись сессии")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "HRV Monitoring Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    // --- Управление накопительным буфером сессии ---

    public void startSessionRecording() {
        synchronized (rrSessionList) {
            rrSessionList.clear();
        }
        isRecording = true;
        updateNotificationText("Идет запись сессии ВСР...");
    }

    public void stopSessionRecording() {
        isRecording = false;
        updateNotificationText("Мониторинг активен (запись остановлена)");
    }

    public void addRrInterval(int rrMs) {
        if (isRecording && rrMs > 0) {
            rrSessionList.add(rrMs);
        }
    }

    public List<Integer> getSessionRrData() {
        synchronized (rrSessionList) {
            return new ArrayList<>(rrSessionList);
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public PolarH10Manager getPolarManager() {
        return polarManager;
    }

    public O2BleManager getO2Manager() {
        return o2Manager;
    }

    @Override
    public void onDestroy() {
        if (polarManager != null) {
            polarManager.disconnect();
        }
        if (o2Manager != null) {
            o2Manager.disconnect();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
