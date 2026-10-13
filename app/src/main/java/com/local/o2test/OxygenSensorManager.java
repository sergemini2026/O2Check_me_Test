package com.local.o2test;

import android.widget.TextView;
import java.util.Locale;

public class OxygenSensorManager {

    private final TextView tvPo2Mix;
    private final TextView tvPo2Hipo;
    private final TextView tvPo2Hiper;

    private float currentMix = 0.0f;
    private float currentHipo = 0.0f;
    private float currentHiper = 0.0f;

    public OxygenSensorManager(UiBuilder.Views ui) {
        this.tvPo2Mix = ui.tvPo2Mix;
        this.tvPo2Hipo = ui.tvPo2Hipo;
        this.tvPo2Hiper = ui.tvPo2Hiper;
        updateDisplay();
    }

    /**
     * Обновление данных кислородных датчиков.
     * Метод-заглушка для будущей интеграции с аппаратной частью (Master ESP32 / UART / BLE).
     */
    public void updateSensorsData(float mixVal, float hipoVal, float hiperVal) {
        this.currentMix = mixVal;
        this.currentHipo = hipoVal;
        this.currentHiper = hiperVal;
        updateDisplay();
    }

    private void updateDisplay() {
        if (tvPo2Mix != null) {
            tvPo2Mix.setText(String.format(Locale.US, "PO2Mix [%.1f]", currentMix));
        }
        if (tvPo2Hipo != null) {
            tvPo2Hipo.setText(String.format(Locale.US, "PO2Hipo [%.1f]", currentHipo));
        }
        if (tvPo2Hiper != null) {
            tvPo2Hiper.setText(String.format(Locale.US, "PO2Hiper [%.1f]", currentHiper));
        }
    }
}
