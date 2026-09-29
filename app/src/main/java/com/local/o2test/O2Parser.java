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

    public static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder hex = new StringBuilder();
        for (byte b : data) {
            hex.append(String.format("%02X ", b));
        }
        return hex.toString().trim();
    }

    public static ParseResult parse(byte[] data) {
        if (data == null || data.length < 11) {
            return new ParseResult(0, 0, 0f, 0, false);
        }

        if ((data[0] & 0xFF) == 0x55) {
            int spo2 = data[7] & 0xFF;
            int hr = data[8] & 0xFF;
            float pi = (data[10] & 0xFF) / 10.0f;
            int battery = (data.length > 14) ? (data[14] & 0xFF) : 0;

            boolean isValid = (spo2 > 0 && spo2 <= 100 && hr > 0 && hr < 250);
            return new ParseResult(spo2, hr, pi, battery, isValid);
        }

        return new ParseResult(0, 0, 0f, 0, false);
    }
}
