package com.local.o2test;

import java.util.ArrayList;
import java.util.List;

public class HrvCalculator {

    public static class Metrics {
        public double rmssd;
        public double pnn50;
        public double lfHfRatio;
        public double totalPower; // TP в мс² (норма: 1000 - 6000 мс²)

        public Metrics(double rmssd, double pnn50, double lfHfRatio, double totalPower) {
            this.rmssd = rmssd;
            this.pnn50 = pnn50;
            this.lfHfRatio = lfHfRatio;
            this.totalPower = totalPower;
        }
    }

    public static List<Integer> parseRrIntervals(byte[] data) {
        List<Integer> rrList = new ArrayList<>();
        if (data == null || data.length < 2) return rrList;

        byte flags = data[0];
        boolean is16Bit = (flags & 0x01) != 0;
        boolean rrPresent = (flags & 0x10) != 0;

        if (!rrPresent) return rrList;

        int offset = is16Bit ? 3 : 2;
        if ((flags & 0x08) != 0) offset += 2; // Пропуск Energy Expended при наличии

        while (offset + 1 < data.length) {
            int rrVal = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            // Перевод из тиков 1/1024 сек в миллисекунды
            int rrMs = Math.round(rrVal * 1000.0f / 1024.0f);
            
            // Фильтрация артефактов и экстрасистол (300ms - 2000ms)
            if (rrMs >= 300 && rrMs <= 2000) {
                rrList.add(rrMs);
            }
            offset += 2;
        }
        return rrList;
    }

    public static Metrics calculate(List<Integer> rrBuffer) {
        if (rrBuffer == null || rrBuffer.size() < 10) {
            return new Metrics(0, 0, 0, 0);
        }

        // 1. Временной анализ (RMSSD, pNN50)
        double sumSqDiff = 0;
        int nn50 = 0;
        int countDiff = rrBuffer.size() - 1;

        for (int i = 0; i < countDiff; i++) {
            double diff = rrBuffer.get(i + 1) - rrBuffer.get(i);
            sumSqDiff += diff * diff;
            if (Math.abs(diff) > 50) {
                nn50++;
            }
        }

        double rmssd = Math.sqrt(sumSqDiff / countDiff);
        double pnn50 = (nn50 * 100.0) / countDiff;

        // 2. Спектральный анализ (LF, HF, TP)
        int N = 256; 
        double fs = 4.0; // Частота дискретизации 4 Гц

        double[] interpolated = interpolateRr(rrBuffer, N);

        // Удаление постоянной составляющей (DC offset)
        double mean = 0;
        for (double val : interpolated) mean += val;
        mean /= N;

        double[] re = new double[N];
        double[] im = new double[N];

        // Окно Ханна и вычисление его суммарной энергии
        double winPowerSum = 0;
        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (N - 1)));
            re[i] = (interpolated[i] - mean) * w;
            im[i] = 0;
            winPowerSum += w * w;
        }

        // БПФ
        fft(re, im, N);

        // Расчет мощности по частотным диапазонам
        double df = fs / N; // Шаг частоты = 0.015625 Гц
        double vlf = 0, lf = 0, hf = 0;

        for (int k = 1; k < N / 2; k++) {
            double freq = k * df;
            double magSq = re[k] * re[k] + im[k] * im[k];

            // ТОЧНАЯ НОРМИРОВКА МОЩНОСТИ (мс²)
            double power = (2.0 * magSq) / (N * winPowerSum);

            if (freq >= 0.0033 && freq < 0.04) {
                vlf += power;
            } else if (freq >= 0.04 && freq < 0.15) {
                lf += power;
            } else if (freq >= 0.15 && freq <= 0.40) {
                hf += power;
            }
        }

        double totalPower = vlf + lf + hf;
        double lfHfRatio = (hf > 0) ? (lf / hf) : 0;

        return new Metrics(rmssd, pnn50, lfHfRatio, totalPower);
    }

    private static double[] interpolateRr(List<Integer> rrList, int nPoints) {
        double[] resamp = new double[nPoints];
        double[] time = new double[rrList.size()];
        
        time[0] = rrList.get(0) / 1000.0;
        for (int i = 1; i < rrList.size(); i++) {
            time[i] = time[i - 1] + (rrList.get(i) / 1000.0);
        }

        double totalTime = time[time.length - 1];
        double dt = totalTime / (nPoints - 1);

        int idx = 0;
        for (int i = 0; i < nPoints; i++) {
            double t = i * dt;
            while (idx < time.length - 2 && time[idx + 1] < t) {
                idx++;
            }
            double t0 = time[idx];
            double t1 = time[idx + 1];
            double y0 = rrList.get(idx);
            double y1 = rrList.get(idx + 1);

            if (t1 == t0) {
                resamp[i] = y0;
            } else {
                resamp[i] = y0 + (y1 - y0) * (t - t0) / (t1 - t0);
            }
        }
        return resamp;
    }

    private static void fft(double[] re, double[] im, int n) {
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                double tempR = re[i]; re[i] = re[j]; re[j] = tempR;
                double tempI = im[i]; im[i] = im[j]; im[j] = tempI;
            }
            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int m = 0; m < len / 2; m++) {
                    int u = i + m;
                    int v = i + m + len / 2;
                    double vr = re[v] * wR - im[v] * wI;
                    double vi = re[v] * wI + im[v] * wR;

                    re[v] = re[u] - vr;
                    im[v] = im[u] - vi;
                    re[u] += vr;
                    im[u] += vi;

                    double nextWR = wR * wlenR - wI * wlenI;
                    double nextWI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                    wI = nextWI;
                }
            }
        }
    }
}
