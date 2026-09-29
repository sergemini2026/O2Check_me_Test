package com.local.o2test;

public class O2Parser {

    public static class ParseResult {
        public boolean isValid;
        public int spo2;
        public int hr;
        public float pi;
        public int battery;

        public ParseResult(boolean isValid, int spo2, int hr, float pi, int battery) {
            this.isValid = isValid;
            this.spo2 = spo2;
            this.hr = hr;
            this.pi = pi;
            this.battery = battery;
        }
    }

    public static ParseResult parse(byte[] data) {
        if (data == null || data.length < 14) {
            return new ParseResult(false, 0, 0, 0f, 0);
        }

        // Проверка заголовка 0x55
        if ((data[0] & 0xFF) != 0x55) {
            return new ParseResult(false, 0, 0, 0f, 0);
        }

        int spo2 = (data.length > 7) ? (data[7] & 0xFF) : 0;
        int hr = (data.length > 8) ? (data[8] & 0xFF) : 0;
        
        // Заряд аккумулятора находится в data[13]
        int battery = (data.length > 13) ? (data[13] & 0xFF) : 0;

        // Расчёт PI из data[15]
        float pi = 0f;
        if (data.length > 15) {
            int rawPi = data[15] & 0xFF;
            pi = rawPi / 10.0f;
        }

        return new ParseResult(true, spo2, hr, pi, battery);
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
