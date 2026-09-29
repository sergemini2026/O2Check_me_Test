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

        // Ищем маркер 0D 00 для стабильного вычленения SpO2 и HR
        int spo2 = 0;
        int hr = 0;
        for (int i = 0; i <= data.length - 4; i++) {
            if ((data[i] & 0xFF) == 0x0D && (data[i+1] & 0xFF) == 0x00) {
                spo2 = data[i+2] & 0xFF;
                hr = data[i+3] & 0xFF;
                break;
            }
        }

        // Если маркер не найден или значения некорректны — отбой
        if (spo2 <= 0 || spo2 > 100 || hr <= 0) {
            return new ParseResult(0, 0, 0f, 0, false);
        }

        // Заряд и PI в длинных пакетах Viatom/Wellue лежат ближе к концу хвоста (обычно за пару байтов до конца)
        int battery = 0;
        float pi = 0f;

        for (int j = 6; j < data.length; j++) {
            // Ищем байт, похожий на реальный процент заряда (от 1 до 100) перед которым идут нули, 
            // либо берем фиксированные индексы из ваших прошлых успешных дампов:
            // В прошлых логах заряд стабильно шел незадолго до конца пакета.
        }

        // Безопасно извлекаем по индексам, которые реально светились в ваших логах с не нулевыми значениями:
        // Заряд часто на пред-пред-последних байтах, либо ищем байт в диапазоне 1-100 ближе к хвосту
        for (int j = data.length - 6; j < data.length; j++) {
            int val = data[j] & 0xFF;
            if (val > 10 && val <= 100 && battery == 0) {
                // Нашли кандидата на батарею
                // battery = val;
            }
        }

        // Давайте обратимся к вашим же логам: в строках типа 55 00 FF ... 5A ... 0A
        // 5A (90 в шестнадцатеричной = 90%) — это батарея. 0A (10 = 1.0%) — это PI.
        // Просканируем весь массив на предмет байта заряда (обычно от 10 до 100) и байта PI (обычно от 1 до 50)
        
        for (int k = 6; k < data.length - 1; k++) {
            int bVal = data[k] & 0xFF;
            // Поиск процента батареи (например, 5A это 90, 59 это 89 и т.д. в диапазоне 10..100)
            if (bVal >= 10 && bVal <= 100 && battery == 0) {
                // Убедимся, что это не SpO2 и не пульс
                if (bVal != spo2 && bVal != hr) {
                    battery = bVal;
                }
            }
            // Поиск PI (обычно маленькие значения от 1 до 50, то есть 0.1% - 5.0%)
            int piVal = data[k+2] & 0xFF;
            if (piVal > 0 && piVal < 50 && pi == 0f) {
                // Проверим соседний байт на нуль, как в структуре пакета
                if ((data[k+1] & 0xFF) == 0x00) {
                    pi = piVal / 10.0f;
                }
            }
        }

        return new ParseResult(spo2, hr, pi, battery, true);
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
