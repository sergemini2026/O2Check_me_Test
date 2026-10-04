package com.local.o2test;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class HrvCalculatorTest {

    // Сюда вы вставите скопированную из лога строку после 5-минутного замера
    private static final int[] RAW_RR_DATASET = new int[]{
        1012, 998, 1005, 1010, 995, 1002, 988, 1015, 1000, 992,
        1008, 1011, 994, 989, 1003, 1014, 997, 1001, 1006, 991
        // ... оставшиеся из 300 интервалов
    };

    @Test
    public void testHrvCalibrationAgainstEliteHrv() {
        List<Integer> rawList = new ArrayList<>();
        for (int rr : RAW_RR_DATASET) {
            rawList.add(rr);
        }

        // Прогоняем наш датасет через HrvCalculator
        HrvCalculator.Metrics metrics = HrvCalculator.calculate(rawList);

        System.out.println("==========================================");
        System.out.println("     РЕЗУЛЬТАТЫ РАСЧЕТА НА MOCK-ДАННЫХ    ");
        System.out.println("==========================================");
        System.out.printf("RMSSD:         %.2f ms\n", metrics.rmssd);
        System.out.printf("pNN50:         %.2f %%\n", metrics.pnn50);
        System.out.printf("LF/HF Ratio:   %.2f\n", metrics.lfHfRatio);
        System.out.printf("Total Power:   %.0f ms²\n", metrics.totalPower);
        System.out.printf("Артефакты:     %d (%.1f%%)\n", metrics.artifactsDetected, metrics.artifactPct);
        System.out.println("==========================================");

        // Проверки базовой валидности
        assertTrue("RMSSD должен быть больше 0", metrics.rmssd > 0);
        assertTrue("pNN50 должен быть от 0 до 100%", metrics.pnn50 >= 0 && metrics.pnn50 <= 100);
    }
}
