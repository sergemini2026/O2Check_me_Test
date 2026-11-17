package com.local.o2test;

import android.content.Context;
import java.util.Locale;

public class SessionModulationManager {
    private long currentModulationPeriodMs = 10000; // По умолчанию 10 секунд (10000 мс)
    private final Context context;

    public SessionModulationManager(Context context) {
        this.context = context;
    }

    public void setModulationPeriodMs(long periodMs) {
        this.currentModulationPeriodMs = Math.max(6000, Math.min(14000, periodMs));
    }

    public long getModulationPeriodMs() {
        return currentModulationPeriodMs;
    }

    public float getModulationPeriodSec() {
        return currentModulationPeriodMs / 1000.0f;
    }

    /**
     * Установка периода на основе частоты в Герцах (с ограничением в пределах диапазона 6-14 секунд)
     */
    public void setModulationFrequencyHz(float frequencyHz) {
        float clampedHz = Math.max(0.0714f, Math.min(0.1667f, frequencyHz));
        this.currentModulationPeriodMs = (long) (1000.0f / clampedHz);
    }

    /**
     * Получение текущей частоты в Герцах
     */
    public float getModulationFrequencyHz() {
        return 1000.0f / currentModulationPeriodMs;
    }

    /**
     * Применение нового периода модуляции к активному сервису и часам
     */
    public void applyModulation(HrvForegroundService hrvService, boolean isBound) {
        if (isBound && hrvService != null) {
            hrvService.setModulationPeriod(currentModulationPeriodMs);
        }
    }
}
