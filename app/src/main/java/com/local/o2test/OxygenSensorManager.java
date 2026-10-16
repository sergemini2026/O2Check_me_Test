package com.local.o2test;

import java.util.Locale;

public class OxygenSensorManager {

    private final TrendChartView chartView;

    private float currentMix = 0.0f;
    private float currentHipo = 0.0f;
    private float currentHiper = 0.0f;

    public OxygenSensorManager(UiBuilder.Views ui) {
        this.chartView = ui.chartView;
        updateDisplay();
    }

    /**
     * Обновление данных кислородных датчиков.
     * Передает значения напрямую на холст графика для отображения по верху канваса.
     */
    public void updateSensorsData(float mixVal, float hipoVal, float hiperVal) {
        this.currentMix = mixVal;
        this.currentHipo = hipoVal;
        this.currentHiper = hiperVal;
        updateDisplay();
    }

    private void updateDisplay() {
        if (chartView != null) {
            chartView.updateGasMetrics(currentMix, currentHipo, currentHiper);
        }
    }
}
