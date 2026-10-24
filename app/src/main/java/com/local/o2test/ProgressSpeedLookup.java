package com.local.o2test;

public class ProgressSpeedLookup {
    // Индексы соответствуют периодам с шагом 0.5с от 6.0с (индекс 0) до 14.0с (индекс 16)
    public static final float[] TRAIN_SPEEDS_DEG_PER_SEC = {
        0.667f, 0.615f, 0.571f, 0.533f, 0.500f, 0.471f, 0.444f, 0.421f,
        0.400f, 0.381f, 0.364f, 0.348f, 0.333f, 0.320f, 0.308f, 0.296f, 0.286f
    };

    public static final float[] TEST_SPEEDS_DEG_PER_SEC = {
        2.000f, 1.846f, 1.714f, 1.600f, 1.500f, 1.412f, 1.333f, 1.263f,
        1.200f, 1.143f, 1.091f, 1.043f, 1.000f, 0.960f, 0.923f, 0.889f, 0.857f
    };

    /**
     * Получить скорость сокращения дуги (в град/сек)
     * @param periodSec текущий период MasterClock [6.0 ... 14.0]
     * @param isTestPhase true = фаза теста PSTI, false = фаза тренировки
     */
    public static float getDegPerSec(float periodSec, boolean isTestPhase) {
        float clampedPeriod = Math.max(6.0f, Math.min(14.0f, periodSec));
        int index = Math.round((clampedPeriod - 6.0f) / 0.5f);
        index = Math.max(0, Math.min(index, TRAIN_SPEEDS_DEG_PER_SEC.length - 1));
        return isTestPhase ? TEST_SPEEDS_DEG_PER_SEC[index] : TRAIN_SPEEDS_DEG_PER_SEC[index];
    }
}
