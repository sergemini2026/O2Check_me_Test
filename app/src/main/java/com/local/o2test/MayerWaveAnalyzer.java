package com.local.o2test;

import java.util.List;

public class MayerWaveAnalyzer {

    /**
     * Анализирует массив RR-интервалов и вычисляет начальную фазу волн Майера на основе частоты MasterClock.
     * @param rrList список RR-интервалов в миллисекундах
     * @return нормализованная фаза в диапазоне [0.0, 1.0), где 0.0 - начало вдоха, 0.5 - начало выдоха
     */
    public static float calculateInitialPhase(List<Integer> rrList) {
        if (rrList == null || rrList.size() < 30) {
            return 0.0f;
        }

        // Динамический расчет целевой частоты на основе единого периода из MasterClock (7.83 с)
        double targetFrequency = 1000.0 / MasterClock.BASE_DURATION_MS;
        double sumCos = 0.0;
        double sumSin = 0.0;

        double currentTime = 0.0;
        for (int rr : rrList) {
            double tSec = rr / 1000.0;
            currentTime += tSec;
            double angle = 2.0 * Math.PI * targetFrequency * currentTime;

            // Корреляция сигнала с опорной частотой
            sumCos += rr * Math.cos(angle);
            sumSin += rr * Math.sin(angle);
        }

        // Вычисляем фазовый угол [-PI, PI]
        double phaseRad = Math.atan2(sumSin, sumCos);

        // Переводим в диапазон [0.0, 1.0)
        float normalizedPhase = (float) ((phaseRad + Math.PI) / (2.0 * Math.PI));
        return normalizedPhase;
    }
}
