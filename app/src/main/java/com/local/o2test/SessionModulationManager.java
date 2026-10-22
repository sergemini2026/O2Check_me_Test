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
     * Применение нового периода модуляции к активному сервису и часам
     */
    public void applyModulation(HrvForegroundService hrvService, boolean isBound) {
        if (isBound && hrvService != null) {
            hrvService.setModulationPeriod(currentModulationPeriodMs);
        }
    }
}
