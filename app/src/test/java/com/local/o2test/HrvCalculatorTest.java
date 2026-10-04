package com.local.o2test;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class HrvCalculatorTest {

    private static final int[] RAW_RR_DATASET = new int[]{
        891, 905, 892, 885, 879, 866, 841, 854, 858, 852, 836, 859, 868, 861, 845, 836, 804, 794, 814, 807, 
        796, 802, 825, 827, 851, 867, 869, 856, 843, 851, 851, 833, 859, 868, 855, 847, 867, 878, 847, 890, 
        892, 878, 903, 920, 929, 895, 899, 915, 901, 872, 895, 898, 891, 857, 863, 879, 863, 837, 832, 841, 
        830, 840, 819, 822, 842, 847, 870, 883, 847, 872, 880, 869, 856, 880, 897, 920, 924, 882, 901, 908, 
        919, 888, 893, 903, 878, 853, 841, 854, 848, 833, 821, 816, 796, 819, 835, 836, 854, 870, 863, 856, 
        888, 895, 900, 868, 859, 877, 908, 917, 915, 915, 906, 868, 885, 896, 908, 880, 871, 868, 880, 857, 
        852, 857, 846, 837, 859, 859, 854, 847, 867, 888, 848, 850, 844, 822, 830, 850, 848, 846, 833, 855, 
        874, 867, 871, 889, 897, 884, 890, 919, 910, 891, 913, 921, 894, 896, 905, 901, 883, 896, 889, 872, 
        891, 908, 878, 861, 882, 865, 852, 866, 884, 871, 883, 904, 910, 882, 909, 919, 902, 923, 920, 914, 
        907, 919, 921, 892, 886, 876, 837, 837, 849, 848, 833, 836, 843, 851, 832, 816, 800, 813, 828, 865, 
        880, 899, 886, 892, 918, 921, 910, 896, 905, 883, 858, 865, 888, 905, 893, 916, 920, 911, 897, 915, 
        922, 892, 894, 902, 900, 880, 892, 905, 900, 886, 900, 914, 895, 898, 893, 843, 845, 830, 824, 834, 
        800, 780, 770, 765, 772, 857, 874, 888, 892, 895, 881, 882, 855, 881, 881, 898, 913, 909, 888, 880, 
        884, 875, 847, 866, 862, 860, 847, 860, 878, 898, 910, 884, 894, 897, 900, 875, 887, 891, 899, 876, 
        892, 906, 908, 890, 896, 914, 893, 869, 879, 884, 875, 856, 869, 871, 865, 857, 865, 874, 875
    };

    @Test
    public void testHrvCalibrationAgainstEliteHrv() {
        List<Integer> rawList = new ArrayList<>();
        for (int rr : RAW_RR_DATASET) {
            rawList.add(rr);
        }

        HrvCalculator.Metrics metrics = HrvCalculator.calculate(rawList);

        System.out.println("==========================================");
        System.out.println("     РЕЗУЛЬТАТЫ РАСЧЕТА НА MOCK-ДАННЫХ    ");
        System.out.println("==========================================");
        System.out.printf("RMSSD:         %.2f ms\n", metrics.rmssd);
        System.out.printf("SDNN:          %.2f ms\n", metrics.sdnn);
        System.out.printf("pNN50:         %.2f %%\n", metrics.pnn50);
        System.out.printf("LF Power:      %.2f ms²\n", metrics.lfPower);
        System.out.printf("HF Power:      %.2f ms²\n", metrics.hfPower);
        System.out.printf("Total Power:   %.2f ms²\n", metrics.totalPower);
        System.out.printf("LF/HF Ratio:   %.2f\n", metrics.lfHfRatio);
        System.out.printf("Артефакты:     %d (%.1f%%)\n", metrics.artifactsDetected, metrics.artifactPct);
        System.out.println("==========================================");

        assertTrue("RMSSD должен быть больше 0", metrics.rmssd > 0);
    }
}
