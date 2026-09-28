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
    private TextView tvLiveMetrics;
    private TextView logView;
    private TrendChartView chartView;

    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeChar;
    private final Set<String> discoveredDevices = new HashSet<>();
    private boolean isConnecting = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

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
        mainLayout.setPadding(20, 20, 20, 20);

        // Метрики реального времени
        tvLiveMetrics = new TextView(this);
        tvLiveMetrics.setTextSize(20);
        tvLiveMetrics.setGravity(Gravity.CENTER);
        tvLiveMetrics.setText("SpO2: -- % | Пульс: -- bpm | PI: -- % | Заряд: -- %");
        mainLayout.addView(tvLiveMetrics);

        // Панель кнопок управления
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(0, 10, 0, 10);

        Button btnMonitor = createButton("Панель монитора");
        Button btnStop = createButton("Стоп");
        Button btnSave = createButton("Сохранение данных");
        Button btnExit = createButton("Выход");

        btnBar.addView(btnMonitor);
        btnBar.addView(btnStop);
        btnBar.addView(btnSave);
        btnBar.addView(btnExit);
        mainLayout.addView(btnBar);

        // Клик-хэндлеры
        btnMonitor.setOnClickListener(v -> startMonitoringPanel());
        btnStop.setOnClickListener(v -> stopRecordingSession());
        btnSave.setOnClickListener(v -> saveCSVData());
        btnExit.setOnClickListener(v -> exitApp());

        // Встроенный холст для 3 графиков
        chartView = new TrendChartView(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500);
        chartParams.setMargins(0, 10, 0, 10);
        chartView.setLayoutParams(chartParams);
        mainLayout.addView(chartView);

        // Текстовый журнал
        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setText("Система готова. Выберите действие в меню.\n");
        scrollView.addView(logView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);
        checkAndRequestPermissions();
    }

    private Button createButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    // 1. Панель монитора (Запуск считывания и записи сеанса)
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

    // 2. Стоп (Остановка записи)
    private void stopRecordingSession() {
        if (isRecording) {
            isRecording = false;
            log("\n>>> ЗАПИСЬ ОСТАНОВЛЕНА <<<");
            generateReportSummary();
        } else {
            log("Запись не была активна.");
        }
    }

    // 3. Сохранение данных (CSV)
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
            log("Файл сохранён:\n" + file.getAbsolutePath());
            Toast.makeText(this, "Сохранено в CSV:\n" + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            log("Ошибка сохранения CSV: " + e.getMessage());
        }
    }

    // 4. Выход (Закрытие приложения)
    private void exitApp() {
        log("Завершение работы приложения...");
        stopTimer();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException ignored) {}
        }
        finishAndRemoveTask();
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

        log(String.format(Locale.US, "--- ИТОГОВЫЙ ОТЧЕТ СЕАНСА ---"));
        log(String.format(Locale.US, "Базовый PI (тонус): %.2f%%", initialPI));
        log(String.format(Locale.US, "Пик вазодилатации: %.2f%% (на %d-й сек)", maxPI, maxPITime));
        log(String.format(Locale.US, "Финишный PI: %.2f%%", endPI));
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
        if (scanner == null) {
            log("ОШИБКА: BLE-сканер недоступен.");
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
        } catch (SecurityException e) {
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
                        log("Канал готов. Запуск интервала опроса...");
                        startPeriodicRequest();
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    parseData(characteristic.getValue());
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
                    parseData(value);
                }
            });
        } catch (SecurityException e) {
            log("Ошибка подключения: " + e.getMessage());
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

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void sendRtDataRequest() {
        if (bluetoothGatt == null || writeChar == null) return;
        try {
            byte[] cmd = new byte[]{(byte) 0xAA, 0x17, (byte) 0xE8, 0x00, 0x00, 0x00, 0x00, 0x1B};
            writeChar.setValue(cmd);
            bluetoothGatt.writeCharacteristic(writeChar);
        } catch (SecurityException ignored) {}
    }

    private void parseData(byte[] data) {
        if (data == null || data.length < 11) return;

        if ((data[0] & 0xFF) == 0x55) {
            int spo2 = data[7] & 0xFF;
            int hr = data[8] & 0xFF;
            float pi = (data[10] & 0xFF) / 10.0f;
            int battery = (data.length > 14) ? (data[14] & 0xFF) : 0;

            if (spo2 > 0 && spo2 <= 100 && hr > 0 && hr < 250) {
                long now = System.currentTimeMillis();

                runOnUiThread(() -> {
                    tvLiveMetrics.setText(String.format(Locale.US,
                            "SpO2: %d %%  |  HR: %d bpm  |  PI: %.1f %%  |  Заряд: %d %%",
                            spo2, hr, pi, battery));
                });

                if (isRecording) {
                    int elapsedSec = (int) ((now - sessionStartTime) / 1000);
                    DataPoint dp = new DataPoint(now, elapsedSec, spo2, hr, pi);
                    sessionData.add(dp);
                    runOnUiThread(() -> chartView.addDataPoint(dp));
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
            } catch (SecurityException ignored) {}
        }
    }

    // Отрисовка графиков тренда
    public static class TrendChartView extends View {
        private final List<DataPoint> points = new ArrayList<>();
        private final Paint paintGrid = new Paint();
        private final Paint paintSpO2 = new Paint();
        private final Paint paintHR = new Paint();
        private final Paint paintPI = new Paint();

        public TrendChartView(Context context) {
            super(context);
            initPaints();
        }

        private void initPaints() {
            paintGrid.setColor(Color.DKGRAY);
            paintGrid.setStrokeWidth(1f);

            // Кислород — Голубой
            paintSpO2.setColor(Color.CYAN);
            paintSpO2.setStrokeWidth(4f);
            paintSpO2.setStyle(Paint.Style.STROKE);
            paintSpO2.setAntiAlias(true);

            // Пульс — Зеленый
            paintHR.setColor(Color.GREEN);
            paintHR.setStrokeWidth(4f);
            paintHR.setStyle(Paint.Style.STROKE);
            paintHR.setAntiAlias(true);

            // PI — Желтый
            paintPI.setColor(Color.YELLOW);
            paintPI.setStrokeWidth(4f);
            paintPI.setStyle(Paint.Style.STROKE);
            paintPI.setAntiAlias(true);
        }

        public void addDataPoint(DataPoint dp) {
            points.add(dp);
            invalidate();
        }

        public void clearData() {
            points.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#121212"));

            float w = getWidth();
            float h = getHeight();
            float padding = 60f;

            canvas.drawLine(padding, padding, padding, h - padding, paintGrid);
            canvas.drawLine(padding, h - padding, w - padding, h - padding, paintGrid);

            // Легенда с обновленными цветами
            canvas.drawText("Голубой: SpO2 (70-100%)", padding + 10, padding + 20, paintSpO2);
            canvas.drawText("Зеленый: Пульс (40-180 bpm)", padding + 320, padding + 20, paintHR);
            canvas.drawText("Желтый: PI (0-10%)", padding + 650, padding + 20, paintPI);

            if (points.size() < 2) return;

            Path pathSpO2 = new Path();
            Path pathHR = new Path();
            Path pathPI = new Path();

            float maxTime = Math.max(60, points.get(points.size() - 1).elapsedSec);
            float plotW = w - 2 * padding;
            float plotH = h - 2 * padding;

            for (int i = 0; i < points.size(); i++) {
                DataPoint dp = points.get(i);
                float x = padding + (dp.elapsedSec / maxTime) * plotW;

                float ySpO2 = (h - padding) - ((Math.max(70, dp.spo2) - 70) / 30f) * plotH;
                float yHR = (h - padding) - ((Math.max(40, dp.hr) - 40) / 140f) * plotH;
                float yPI = (h - padding) - (Math.min(10f, dp.pi) / 10f) * plotH;

                if (i == 0) {
                    pathSpO2.moveTo(x, ySpO2);
                    pathHR.moveTo(x, yHR);
                    pathPI.moveTo(x, yPI);
                } else {
                    pathSpO2.lineTo(x, ySpO2);
                    pathHR.lineTo(x, yHR);
                    pathPI.lineTo(x, yPI);
                }
            }

            canvas.drawPath(pathSpO2, paintSpO2);
            canvas.drawPath(pathHR, paintHR);
            canvas.drawPath(pathPI, paintPI);
        }
    }
}
