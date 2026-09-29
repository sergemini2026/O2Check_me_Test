package com.local.o2test;

public class O2Parser {

    public static class ParseResult {
        public int spo2;
        public int hr;
        public float pi;
        public int battery;
        public boolean isValid;

        public ParseResult(int spo2, int hr, float pi, int battery, boolean isValid) {
            this.spo2 = spo2;
            this.hr = hr;
            this.pi = pi;
            this.battery = battery;
            this.isValid = isValid;
        }
    }

    public static ParseResult parse(byte[] data) {
        if (data == null || data.length < 15) {
            return new ParseResult(0, 0, 0f, 0, false);
        }

        // Проверка заголовка пакета Viatom/Wellue (55 00 FF 00)
        if ((data[0] & 0xFF) == 0x55 && (data[2] & 0xFF) == 0xFF) {
            int spo2 = data[6] & 0xFF;
            int hr = data[7] & 0xFF;
            int battery = data[12] & 0xFF;
            float pi = (data[14] & 0xFF) / 10.0f;

            // Валидация полученных данных
            boolean isValid = spo2 > 0 && spo2 <= 100 && hr > 0 && hr < 250;

            return new ParseResult(spo2, hr, pi, battery, isValid);
        }

        return new ParseResult(0, 0, 0f, 0, false);
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
