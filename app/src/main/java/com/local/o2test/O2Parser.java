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
        if (data == null || data.length < 12) {
            return new ParseResult(0, 0, 0f, 0, false);
        }

        // Скользим по массиву и ищем маркер полезных данных: 0x0D 0x00
        for (int i = 0; i <= data.length - 11; i++) {
            if (data[i] == 0x0D && data[i+1] == 0x00) {
                
                int spo2 = data[i+2] & 0xFF;
                int hr = data[i+3] & 0xFF;
                
                // Заряд всегда идет через 8 байт от начала маркера
                int battery = (i + 8 < data.length) ? (data[i+8] & 0xFF) : 0;
                
                // PI всегда идет через 10 байт от начала маркера
                float pi = (i + 10 < data.length) ? ((data[i+10] & 0xFF) / 10.0f) : 0f;

                // Если SpO2 и пульс в норме — пакет успешный
                if (spo2 > 0 && spo2 <= 100 && hr > 0) {
                    return new ParseResult(spo2, hr, pi, battery, true);
                }
            }
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
