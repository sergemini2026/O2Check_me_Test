package com.local.o2test;

public class O2Parser {

    public static class ParseResult {
        public int spo2;
        public int hr;
        public float pi;
        public int battery;
        public boolean isFingerOn;

        public ParseResult(int spo2, int hr, float pi, int battery, boolean isFingerOn) {
            this.spo2 = spo2;
            this.hr = hr;
            this.pi = pi;
            this.battery = battery;
            this.isFingerOn = isFingerOn;
        }
    }

    public static ParseResult parse(byte[] data) {
        if (data == null || data.length < 15) {
            return new ParseResult(0, 0, 0f, 0, false);
        }

        if ((data[0] & 0xFF) == 0x55) {
            int rawSpo2 = data[7] & 0xFF;
            int rawHr = data[8] & 0xFF;

            int battery = (data.length > 14) ? (data[14] & 0xFF) : 0;

            int rawPi = (data.length > 16) ? (data[16] & 0xFF) : ((data.length > 10) ? (data[10] & 0xFF) : 0);
            float pi = rawPi / 10.0f;

            boolean isFingerOn = (rawSpo2 > 0 && rawSpo2 <= 100) && (rawHr > 0 && rawHr < 250);

            int spo2 = isFingerOn ? rawSpo2 : 0;
            int hr = isFingerOn ? rawHr : 0;

            return new ParseResult(spo2, hr, pi, battery, isFingerOn);
        }

        return new ParseResult(0, 0, 0f, 0, false);
    }

    /**
     * Проверка, является ли входящий пакет ответом с данными PPG (команда 0x14)
     */
    public static boolean isPpgPacket(byte[] data) {
        return data != null && data.length >= 4 
                && ((data[0] & 0xFF) == 0x55 || (data[0] & 0xFF) == 0xAA)
                && (data[1] & 0xFF) == 0x14;
    }

    /**
     * Парсинг 16-битных отсчетов PPG волны
     */
    public static int[] parsePpgPacket(byte[] data) {
        if (!isPpgPacket(data)) return new int[0];

        int startIdx = ((data[0] & 0xFF) == 0x55 && data.length >= 8) ? 7 : 3;
        int sampleCount = (data.length - startIdx - 1) / 2;
        if (sampleCount <= 0) return new int[0];

        int[] samples = new int[sampleCount];
        int idx = 0;

        for (int i = startIdx; i < data.length - 1; i += 2) {
            int sample = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            samples[idx++] = sample;
        }

        return samples;
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
