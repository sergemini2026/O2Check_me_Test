package com.local.o2test;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class HrvCalculatorTest {

    private static final int[] RAW_RR_DATASET = new int[]{
        867, 846, 798, 912, 945, 952, 926, 909, 875, 903, 890, 906, 867, 913, 924, 931, 907, 950, 970, 933, 953, 967, 960, 923, 960, 984, 920, 924,
        963, 912, 911, 936, 948, 904, 920, 943, 978, 914, 916, 967, 960, 897, 928, 946, 935, 897, 942, 971, 931, 925, 961, 969, 923, 944, 985, 964, 929, 982, 994, 935, 945, 960,
        942, 914, 959, 959, 913, 926, 977, 961, 936, 940, 985, 959, 929, 940, 961, 936, 921, 943, 983, 924, 928, 983, 930, 976, 1005, 941, 947, 1001, 1011, 977, 960, 987,
        979, 931, 953, 979, 884, 896, 936, 880, 892, 942, 927, 879, 917, 967, 950, 927, 967, 982, 921, 926, 983, 965, 923, 944, 995, 977, 928, 937, 1004, 988, 967, 992, 1001, 934,
        938, 989, 987, 926, 962, 962, 910, 900, 949, 891, 908, 959, 950, 928, 957, 986, 941, 956, 1009, 981, 960, 1014, 996, 933, 953, 1002, 990, 978, 954, 973, 1020, 972, 953,
        1007, 998, 955, 963, 1016, 1020, 1003, 980, 984, 1044, 1028, 984, 988, 1021, 995, 970, 1009, 1053, 998, 988, 1031, 1010, 964, 974, 1005, 979, 959, 992, 978, 890, 906,
        928, 888, 896, 944, 927, 879, 914, 964, 951, 907, 988, 980, 925, 936, 942, 867, 869, 915, 882, 849, 905, 866, 879, 863, 897, 919, 913, 916, 944, 962, 947, 965, 982, 961,
        966, 989, 995, 967, 977, 990, 958, 983, 1012, 964, 920, 912, 917, 902, 922, 937, 936, 938, 956, 968, 941, 928, 956, 968, 963, 950, 973, 987, 983, 962, 964, 999, 1018, 971,
        985, 1024, 1023, 983, 979, 946, 919, 949, 979, 983, 973, 1002, 1014, 982, 979, 1003, 984, 947, 980, 1019, 1024, 1002, 1013, 1006, 989, 1020, 1038, 989, 1010, 1021, 995,
        999, 1013, 977, 970, 976, 968, 930, 947
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
