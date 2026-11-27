package com.local.o2test;

import android.content.Context;

public class SessionModulationManager {
    private static final long DEFAULT_MODULATION_PERIOD_MS = 12000L;
    private static final long MIN_MODULATION_PERIOD_MS = 6000L;
    private static final long MAX_MODULATION_PERIOD_MS = 18000L;
    private static final float MIN_MODULATION_FREQUENCY_HZ = 0.0556f;
    private static final float MAX_MODULATION_FREQUENCY_HZ = 0.1667f;

    private long currentModulationPeriodMs = DEFAULT_MODULATION_PERIOD_MS; // По умолчанию 12 секунд (12000 мс)
    private final Context context;

    public SessionModulationManager(Context context) {
        this.context = context;
    }

    public void setModulationPeriodMs(long periodMs) {
        this.currentModulationPeriodMs = Math.max(MIN_MODULATION_PERIOD_MS, Math.min(MAX_MODULATION_PERIOD_MS, periodMs));
    }

    public long getModulationPeriodMs() {
        return currentModulationPeriodMs;
    }

    public float getModulationPeriodSec() {
        return currentModulationPeriodMs / 1000.0f;
    }

    /**
     * Установка периода на основе частоты в Герцах (с ограничением в пределах диапазона 6-18 секунд)
     */
    public void setModulationFrequencyHz(float frequencyHz) {
        if (Float.isNaN(frequencyHz) || Float.isInfinite(frequencyHz) || frequencyHz <= 0f) {
            this.currentModulationPeriodMs = DEFAULT_MODULATION_PERIOD_MS;
            return;
        }
        float clampedHz = Math.max(MIN_MODULATION_FREQUENCY_HZ, Math.min(MAX_MODULATION_FREQUENCY_HZ, frequencyHz));
        this.currentModulationPeriodMs = Math.max(
                MIN_MODULATION_PERIOD_MS,
                Math.min(MAX_MODULATION_PERIOD_MS, Math.round(1000.0f / clampedHz))
        );
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
