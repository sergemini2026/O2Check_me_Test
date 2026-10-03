package com.local.o2test;

import java.util.ArrayList;
import java.util.List;

public class HrvCalculator {

    public static class Metrics {
        public double rmssd;
        public double pnn50;
        public double lfHfRatio;
        public double totalPower;
        public double artifactPct;
        public int artifactsDetected;

        public Metrics(double rmssd, double pnn50, double lfHfRatio, double totalPower, double artifactPct, int artifactsDetected) {
            this.rmssd = rmssd;
            this.pnn50 = pnn50;
            this.lfHfRatio = lfHfRatio;
            this.totalPower = totalPower;
            this.artifactPct = artifactPct;
            this.artifactsDetected = artifactsDetected;
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
        if ((flags & 0x08) != 0) offset += 2;

        while (offset + 1 < data.length) {
            int rrVal = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            int rrMs = Math.round(rrVal * 1000.0f / 1024.0f);
            
            if (rrMs >= 300 && rrMs <= 2000) {
                rrList.add(rrMs);
            }
            offset += 2;
        }
        return rrList;
    }

    public static Metrics calculate(List<Integer> rawRrBuffer) {
        if (rawRrBuffer == null || rawRrBuffer.size() < 12) {
            return new Metrics(0, 0, 0, 0, 0, 0);
        }

        // 1. Фильтрация артефактов (Malik 20% Threshold Filter)
        List<Integer> cleanRr = new ArrayList<>();
        int artifactsCount = 0;
        cleanRr.add(rawRrBuffer.get(0));

        for (int i = 1; i < rawRrBuffer.size(); i++) {
            int prev = cleanRr.get(cleanRr.size() - 1);
            int curr = rawRrBuffer.get(i);

            double diffRatio = Math.abs(curr - prev) / (double) prev;
            if (diffRatio > 0.20) {
                artifactsCount++;
                cleanRr.add(prev);
            } else {
                cleanRr.add(curr);
            }
        }

        double artifactPct = (artifactsCount * 100.0) / rawRrBuffer.size();

        // 2. Временной анализ (RMSSD, pNN50)
        double sumSqDiff = 0;
        int nn50 = 0;
        int countDiff = cleanRr.size() - 1;

        for (int i = 0; i < countDiff; i++) {
            double diff = cleanRr.get(i + 1) - cleanRr.get(i);
            sumSqDiff += diff * diff;
            if (Math.abs(diff) > 50) {
                nn50++;
            }
        }

        double rmssd = Math.sqrt(sumSqDiff / countDiff);
        double pnn50 = (nn50 * 100.0) / countDiff;

        // 3. Спектральный анализ (Удаление линейного тренда + Ресемплинг 4 Гц)
        int N = 256; 
        double fs = 4.0; // Частота дискретизации 4 Гц

        double[] interpolated = interpolateAndDetrend(cleanRr, N, fs);

        // Окно Ханна (Hanning Window) с нормировкой коэф. энергии
        double[] re = new double[N];
        double[] im = new double[N];
        double winSumSq = 0;

        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (N - 1)));
            re[i] = interpolated[i] * w;
            im[i] = 0;
            winSumSq += w * w;
        }

        fft(re, im, N);

        double df = fs / N;
        double vlf = 0, lf = 0, hf = 0;

        for (int k = 1; k < N / 2; k++) {
            double freq = k * df;
            // Двусторонняя спектральная плотность мощности (PSD)
            double power = (2.0 * (re[k] * re[k] + im[k] * im[k])) / (fs * winSumSq);

            if (freq >= 0.0033 && freq < 0.04) {
                vlf += power * df;
            } else if (freq >= 0.04 && freq < 0.15) {
                lf += power * df;
            } else if (freq >= 0.15 && freq <= 0.40) {
                hf += power * df;
            }
        }

        double totalPower = vlf + lf + hf;
        double lfHfRatio = (hf > 0) ? (lf / hf) : 0;

        return new Metrics(rmssd, pnn50, lfHfRatio, totalPower, artifactPct, artifactsCount);
    }

    private static double[] interpolateAndDetrend(List<Integer> rrList, int nPoints, double fs) {
        int sz = rrList.size();
        double[] time = new double[sz];
        double[] values = new double[sz];

        time[0] = 0;
        values[0] = rrList.get(0);
        for (int i = 1; i < sz; i++) {
            time[i] = time[i - 1] + (rrList.get(i) / 1000.0);
            values[i] = rrList.get(i);
        }

        // Удаление линейного тренда (Linear Detrending)
        double meanT = 0, meanY = 0;
        for (int i = 0; i < sz; i++) {
            meanT += time[i];
            meanY += values[i];
        }
        meanT /= sz;
        meanY /= sz;

        double num = 0, den = 0;
        for (int i = 0; i < sz; i++) {
            num += (time[i] - meanT) * (values[i] - meanY);
            den += (time[i] - meanT) * (time[i] - meanT);
        }
        double slope = (den != 0) ? num / den : 0;
        double intercept = meanY - slope * meanT;

        double[] resampled = new double[nPoints];
        double totalDuration = time[sz - 1];
        double dt = totalDuration / (nPoints - 1);

        int idx = 0;
        for (int i = 0; i < nPoints; i++) {
            double t = i * dt;
            while (idx < sz - 2 && time[idx + 1] < t) {
                idx++;
            }
            double t0 = time[idx];
            double t1 = time[idx + 1];
            double y0 = values[idx] - (slope * t0 + intercept);
            double y1 = values[idx + 1] - (slope * t1 + intercept);

            if (t1 == t0) {
                resampled[i] = y0;
            } else {
                resampled[i] = y0 + (y1 - y0) * (t - t0) / (t1 - t0);
            }
        }
        return resampled;
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
