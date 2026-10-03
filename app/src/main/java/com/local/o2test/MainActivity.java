package com.local.o2test;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements PolarH10Manager.PolarCallback {

    // Элементы UI
    private TextView tvSpo2, tvHr, tvRmssd, tvPi, tvLog;
    private ProgressBar hrvProgressBar;
    private ImageView heartPulseView;
    private ObjectAnimator pulseAnimator;

    // Графики
    private LineChart chartSpo2, chartHr, chartRmssd, chartPi;

    // Сглаживание PI (окно скользящего среднего на 15 отсчетов)
    private final Queue<Float> piBuffer = new LinkedList<>();
    private static final int PI_SMOOTH_WINDOW = 15;
    private float piSum = 0f;

    // Счётчик секунд для оси X
    private int timeSecond = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация UI компонентов
        tvSpo2 = findViewById(R.id.tvSpo2);
        tvHr = findViewById(R.id.tvHr);
        tvRmssd = findViewById(R.id.tvRmssd);
        tvPi = findViewById(R.id.tvPi);
        tvLog = findViewById(R.id.tvLog);
        hrvProgressBar = findViewById(R.id.hrvProgressBar);
        heartPulseView = findViewById(R.id.heartPulseView);

        // Инициализация графиков
        chartSpo2 = findViewById(R.id.chartSpo2);
        chartHr = findViewById(R.id.chartHr);
        chartRmssd = findViewById(R.id.chartRmssd);
        chartPi = findViewById(R.id.chartPi);

        setupChart(chartSpo2, "SpO2 (%)", Color.parseColor("#00E676"));
        setupChart(chartHr, "HR (bpm)", Color.parseColor("#FF5252"));
        setupChart(chartRmssd, "RMSSD (ms)", Color.parseColor("#448AFF"));
        setupChart(chartPi, "PI Smoothed (%)", Color.parseColor("#FFD700"));

        setupHeartAnimation();
    }

    // Базовая настройка внешнего вида и производительности графика
    private void setupChart(LineChart chart, String label, int color) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.getAxisRight().setEnabled(false);

        // Настройка осей под тёмную тему
        chart.getXAxis().setTextColor(Color.parseColor("#888888"));
        chart.getXAxis().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(Color.parseColor("#CCCCCC"));
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#222222"));

        // Инициализация датасета
        LineDataSet dataSet = new LineDataSet(null, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Плавные скругленные линии

        LineData data = new LineData(dataSet);
        chart.setData(data);
    }

    // Добавление точки на график со сдвигом окна (показываем последние 60 секунд)
    private void addChartEntry(LineChart chart, float value) {
        LineData data = chart.getData();
        if (data != null) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = new LineDataSet(null, "");
                data.addDataSet(set);
            }

            data.addEntry(new Entry(timeSecond, value), 0);
            data.notifyDataChanged();
            chart.notifyDataSetChanged();

            // Ограничение видимого окна до 60 секунд
            chart.setVisibleXRangeMaximum(60);
            chart.moveViewToX(data.getEntryCount());
        }
    }

    // Настройка анимированной пульсации сердца
    private void setupHeartAnimation() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.22f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.22f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0.75f, 1.0f);

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(heartPulseView, scaleX, scaleY, alpha);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);

        updatePulseRate(60);
        pulseAnimator.start();
    }

    private void updatePulseRate(int hr) {
        if (hr <= 0) return;
        long halfBeatMs = Math.max(200, (60000 / hr) / 2);
        if (pulseAnimator != null) {
            pulseAnimator.setDuration(halfBeatMs);
        }
    }

    // Фильтр сглаживания PI (скользящее среднее)
    private float smoothPi(float rawPi) {
        piBuffer.add(rawPi);
        piSum += rawPi;
        if (piBuffer.size() > PI_SMOOTH_WINDOW) {
            piSum -= piBuffer.poll();
        }
        return piSum / piBuffer.size();
    }

    // Колбэк с Polar H10
    @Override
    public void onPolarHrReceived(int hr, HrvCalculator.Metrics hrv, int rrCount) {
        runOnUiThread(() -> {
            timeSecond++;
            updatePulseRate(hr);

            int progress = Math.min(rrCount, 30);
            hrvProgressBar.setProgress(progress);

            if (rrCount < 30) {
                hrvProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
                tvHr.setText(String.format(Locale.US, "HR: %d bpm", hr));
                tvRmssd.setText(String.format(Locale.US, "Буфер: %d/30", rrCount));
                
                addChartEntry(chartHr, hr);
            } else {
                hrvProgressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00E676")));
                tvHr.setText(String.format(Locale.US, "HR: %d bpm", hr));
                tvRmssd.setText(String.format(Locale.US, "RMSSD: %.1f ms", hrv.rmssd));

                // Обновляем графики пульса и RMSSD
                addChartEntry(chartHr, hr);
                addChartEntry(chartRmssd, hrv.rmssd);
            }
        });
    }

    // Вызывать при получении кадра данных от Checkme O2
    public void onCheckmeDataReceived(int spo2, float rawPi) {
        runOnUiThread(() -> {
            float smoothedPi = smoothPi(rawPi);

            tvSpo2.setText(String.format(Locale.US, "SpO2: %d%%", spo2));
            tvPi.setText(String.format(Locale.US, "PI: %.2f%%", smoothedPi));

            addChartEntry(chartSpo2, spo2);
            addChartEntry(chartPi, smoothedPi);
        });
    }

    @Override
    public void onPolarLog(String message) {
        runOnUiThread(() -> tvLog.append("\n" + message));
    }
}
