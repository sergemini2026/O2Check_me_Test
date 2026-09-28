package com.local.o2test;

import android.Manifest;
import android.app.Activity;
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private TextView tvSpO2, tvHR, tvPI, tvBattery;
    private TextView logView;
    private TrendChartView chartView;

    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeChar;
    private final Set<String> discoveredDevices = new HashSet<>();
    private boolean isConnecting = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int pollTickCounter = 0;

    private boolean isRecording = false;
    private long sessionStartTime = 0;
    private final List<DataPoint> sessionData = new ArrayList<>();

    private static final UUID SERVICE_UUID = UUID.fromString("14839ac4-7d7e-415c-9a42-167340cf2339");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("8b00ace7-eb0b-49b0-bbe9-9aee0a26e1a3");
    private static final UUID NOTIFY_CHAR_UUID = UUID.fromString("0734594a-a8e7-4b1a-a6b1-cd5243059a57");
    private static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static class DataPoint {
        public long timestamp;
        public int elapsedSec;
        public int spo2;
        public int hr;
        public float pi;

        public DataPoint(long timestamp, int elapsedSec, int spo2, int hr, float pi) {
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

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(15, 15, 15, 15);

        // Верхняя панель метрик
        LinearLayout metricsLayout = new LinearLayout(this);
        metricsLayout.setOrientation(LinearLayout.HORIZONTAL);
        metricsLayout.setGravity(Gravity.CENTER);
        metricsLayout.setPadding(0, 10, 0, 10);

        tvSpO2 = createMetricTextView("#00FFFF");   // Голубой O2
        tvHR = createMetricTextView("#00FF00");     // Зеленый Pulse
        tvPI = createMetricTextView("#FFFF00");     // Желтый PI
        tvBattery = createMetricTextView("#CCCCCC");// Серый Заряд

        tvSpO2.setText("O2: --%");
        tvHR.setText("Pulse: -- bpm");
        tvPI.setText("PI: --%");
        tvBattery.setText("Заряд: --%");

        metricsLayout.addView(tvSpO2);
        metricsLayout.addView(tvHR);
        metricsLayout.addView(tvPI);
        metricsLayout.addView(tvBattery);
        mainLayout.addView(metricsLayout);

        // Кнопки управления
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(0, 5, 0, 5);

        Button btnMonitor = createButton("Панель монитора");
        Button btnStop = createButton("Стоп");
        Button btnSave = createButton("Сохранение данных");
        Button btnExit = createButton("Выход");

        btnBar.addView(btnMonitor);
        btnBar.addView(btnStop);
        btnBar.addView(btnSave);
        btnBar.addView(btnExit);
        mainLayout.addView(btnBar);

        btnMonitor.setOnClickListener(v -> startMonitoringPanel());
        btnStop.setOnClickListener(v -> stopRecordingSession());
        btnSave.setOnClickListener(v -> saveCSVData());
        btnExit.setOnClickListener(v -> exitApp());

        // Холст графика
        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 650);
        chartParams.setMargins(0, 10, 0, 10);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        // Лог событий
        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setText("Система готова. Выберите действие в меню.\n");
        scrollView.addView(logView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);
        checkAndRequestPermissions();
    }

    private TextView createMetricTextView(String colorHex) {
        TextView tv = new TextView(this);
        tv.setTextSize(17);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setPadding(12, 0, 12, 0);
        return tv;
    }

    private Button createButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(11);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void startMonitoringPanel() {
        isRecording = true;
        sessionStartTime = System.currentTimeMillis();
        sessionData.clear();
        chartView.clearData();
        log("\n>>> ПАНЕЛЬ МОНИТОРА: Запущен новый сеанс записи <<<");

        if (bluetoothGatt == null) {
            initBLE();
        }
    }

    private void stopRecordingSession() {
        if (isRecording) {
            isRecording = false;
            log("\n>>> ЗАПИСЬ ОСТАНОВЛЕНА <<<");
            generateReportSummary();
        } else {
            log("Запись не активна.");
        }
    }

    private void saveCSVData() {
        if (sessionData.isEmpty()) {
            log("Ошибка: Нет данных для сохранения.");
            Toast.makeText(this, "Нет данных для сохранения!", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "O2_Session_" + timeStamp + ".csv";

        File docDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docDir != null && !docDir.exists()) docDir.mkdirs();
        File file = new File(docDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Timestamp,Elapsed_Sec,SpO2,HR,PI\n");
            for (DataPoint dp : sessionData) {
                writer.append(String.format(Locale.US, "%d,%d,%d,%d,%.2f\n",
                        dp.timestamp, dp.elapsedSec, dp.spo2, dp.hr, dp.pi));
            }
            log("Файл сохранен:\n" + file.getAbsolutePath());
            Toast.makeText(this, "Сохранено в CSV:\n" + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            log("Ошибка сохранения CSV: " + e.getMessage());
        }
    }

    private void exitApp() {
        stopTimer();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {}
        }
        finish();
    }

    private void generateReportSummary() {
        if (sessionData.isEmpty()) return;

        float initialPI = sessionData.get(0).pi;
        float maxPI = initialPI;
        int maxPITime = 0;

        for (DataPoint dp : sessionData) {
            if (dp.pi > maxPI) {
                maxPI = dp.pi;
                maxPITime = dp.elapsedSec;
            }
        }
        float endPI = sessionData.get(sessionData.size() - 1).pi;

        log("--- ИТОГОВЫЙ ОТЧЕТ СЕАНСА ---");
        log(String.format(Locale.US, "Базовый PI (тонус): %.2f%%", initialPI));
        log(String.format(Locale.US, "Пик вазодилатации: %.2f%% (на %d-й сек)", maxPI, maxPITime));
        log(String.format(Locale.US, "Финишный PI: %.2f%%", endPI));
    }

    private void log(String text) {
        runOnUiThread(() -> logView.append(text + "\n"));
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
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
        log("Права получены.");
    }

    private void initBLE() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;

        if (adapter == null || !adapter.isEnabled()) {
            log("ОШИБКА: Bluetooth выключен!");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner != null) {
            startScanning();
        }
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
                        log("Найдено: " + name + " [" + address + "]");
                    }

                    if (!isConnecting && (name.contains("O2") || name.contains("Viatom") || name.contains("Checkme"))) {
                        isConnecting = true;
                        log(">>> ДАТЧИК ОБНАРУЖЕН: " + name + " <<<");
                        scanner.stopScan(this);
                        connectToDevice(device);
                    }
                }
            });
        } catch (Exception e) {
            log("Ошибка разрешений: " + e.getMessage());
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        log("Соединение установлено. Поиск сервисов...");
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        log("Соединение разорвано.");
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
                                log("Подписка на поток данных...");
                                gatt.setCharacteristicNotification(notifyChar, true);
                                BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                                if (descriptor != null) {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    } else {
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        gatt.writeDescriptor(descriptor);
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("Канал готов. Запуск интервала опроса...");
                        startPeriodicRequest();
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    if (characteristic != null) {
                        parseData(characteristic.getValue());
                    }
                }

                // Переопределение для совместимости с Android 13+
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
                    parseData(value);
                }
            });
        } catch (Exception e) {
            log("Ошибка подключения: " + e.getMessage());
        }
    }

    private void startPeriodicRequest() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                sendRtDataRequest();
                pollTickCounter++;
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void sendRtDataRequest() {
        if (bluetoothGatt == null || writeChar == null) return;
        try {
            byte[] cmd = new byte[]{(byte) 0xAA, 0x17, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x1B};
            if (Build.VERSION.SDK_INT >= 33) {
                bluetoothGatt.writeCharacteristic(writeChar, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                writeChar.setValue(cmd);
                bluetoothGatt.writeCharacteristic(writeChar);
            }
        } catch (Exception ignored) {}
    }

    private void parseData(byte[] data) {
        if (data == null || data.length < 11) return;

        if ((data[0] & 0xFF) == 0x55) {
            int spo2 = data[7] & 0xFF;
            int hr = data[8] & 0xFF;

            float rawPi = (data[10] & 0xFF) / 10.0f;
            if (rawPi == 0 && data.length > 11 && (data[11] & 0xFF) > 0) {
                rawPi = (data[11] & 0xFF) / 10.0f;
            }
            final float pi = rawPi;
            final int battery = (data.length > 14) ? (data[14] & 0xFF) : 0;

            if (spo2 > 0 && spo2 <= 100 && hr > 0 && hr < 250) {
                long now = System.currentTimeMillis();

                if (pollTickCounter % 3 == 0) {
                    runOnUiThread(() -> {
                        tvSpO2.setText(String.format(Locale.US, "O2: %d%%", spo2));
                        tvHR.setText(String.format(Locale.US, "Pulse: %d bpm", hr));
                        tvPI.setText(String.format(Locale.US, "PI: %.1f%%", pi));
                        tvBattery.setText(String.format(Locale.US, "Заряд: %d%%", battery));
                    });
                }

                if (isRecording) {
                    int elapsedSec = (int) ((now - sessionStartTime) / 1000);
                    DataPoint dp = new DataPoint(now, elapsedSec, spo2, hr, pi);
                    sessionData.add(dp);
                    runOnUiThread(() -> chartView.updateCurrentMetrics(dp));
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {}
        }
    }

    public static class TrendChartView extends View {
        private final List<DataPoint> points = new ArrayList<>();
        private DataPoint latestPoint = null;

        private final Paint paintGrid = new Paint();
        private final Paint paintDash = new Paint();
        private final Paint paintSpO2 = new Paint();
        private final Paint paintHR = new Paint();
        private final Paint paintPI = new Paint();
        private final Paint paintText = new Paint();

        public TrendChartView(Context context) {
            super(context);
            initPaints();
        }

        private void initPaints() {
            paintGrid.setColor(Color.DKGRAY);
            paintGrid.setStrokeWidth(1.5f);

            paintDash.setColor(Color.parseColor("#333333"));
            paintDash.setStrokeWidth(1f);
            paintDash.setStyle(Paint.Style.STROKE);
            paintDash.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

            paintSpO2.setColor(Color.CYAN);
            paintSpO2.setStrokeWidth(3.5f);
            paintSpO2.setStyle(Paint.Style.STROKE);
            paintSpO2.setAntiAlias(true);

            paintHR.setColor(Color.GREEN);
            paintHR.setStrokeWidth(3.5f);
            paintHR.setStyle(Paint.Style.STROKE);
            paintHR.setAntiAlias(true);

            paintPI.setColor(Color.YELLOW);
            paintPI.setStrokeWidth(3.5f);
            paintPI.setStyle(Paint.Style.STROKE);
            paintPI.setAntiAlias(true);

            paintText.setTextSize(22f);
            paintText.setAntiAlias(true);
            paintText.setFakeBoldText(true);
        }

        public void updateCurrentMetrics(DataPoint dp) {
            this.latestPoint = dp;
            this.points.add(dp);
            invalidate();
        }

        public void clearData() {
            points.clear();
            latestPoint = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#0F0F0F"));

            float w = getWidth();
            float h = getHeight();
            float leftPad = 80f;
      
