package com.local.o2test;

import java.util.ArrayList;
import java.util.List;

public class HrvCalculator {

    public static class Metrics {
        public final double rmssd;
        public final double sdnn;
        public final double pnn50;
        public final double lfPower;
        public final double hfPower;
        public final double totalPower;
        public final double lfHfRatio;
        public final int artifactsDetected;
        public final double artifactPct;

        public Metrics(double rmssd, double sdnn, double pnn50, double lfPower, 
                       double hfPower, double totalPower, double lfHfRatio, 
                       int artifactsDetected, double artifactPct) {
            this.rmssd = rmssd;
            this.sdnn = sdnn;
            this.pnn50 = pnn50;
            this.lfPower = lfPower;
            this.hfPower = hfPower;
            this.totalPower = totalPower;
            this.lfHfRatio = lfHfRatio;
            this.artifactsDetected = artifactsDetected;
            this.artifactPct = artifactPct;
        }
    }

    public static Metrics calculate(List<Integer> rawRrList) {
        if (rawRrList == null || rawRrList.size() < 10) {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // 1. Фильтрация выбросов (артефактов)
        List<Double> cleanRr = new ArrayList<>();
        int artifacts = 0;

        for (int i = 0; i < rawRrList.size(); i++) {
            double rr = rawRrList.get(i);
            if (rr < 300 || rr > 2000) {
                artifacts++;
                continue;
            }
            if (i > 0) {
                double prev = cleanRr.get(cleanRr.size() - 1);
                if (Math.abs(rr - prev) / prev > 0.25) {
                    artifacts++;
                    continue;
                }
            }
            cleanRr.add(rr);
        }

        if (cleanRr.size() < 10) {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, artifacts, 100.0);
        }

        int N = cleanRr.size();
        double artifactPct = ((double) artifacts / rawRrList.size()) * 100.0;

        // ==========================================
        // 2. ВРЕМЕННОЙ АНАЛИЗ (Time-Domain) с Linear Detrending
        // ==========================================
        
        // --- Mean RR ---
        double sumRr = 0.0;
        for (double rr : cleanRr) {
            sumRr += rr;
        }
        double meanRr = sumRr / N;

        // --- Linear Detrending для расчета SDNN ---
        double meanX = (N - 1) / 2.0;
        double numSlope = 0.0;
        double denSlope = 0.0;

        for (int i = 0; i < N; i++) {
            double xDiff = i - meanX;
            double yDiff = cleanRr.get(i) - meanRr;
            numSlope += xDiff * yDiff;
            denSlope += xDiff * xDiff;
        }

        double slope = (denSlope != 0.0) ? numSlope / denSlope : 0.0;

        // SDNN по отфильтрованному от линейного тренда ряду
        double sumSdnnSq = 0.0;
        for (int i = 0; i < N; i++) {
            double trendValue = meanRr + slope * (i - meanX);
            double detrendedRr = cleanRr.get(i) - trendValue;
            sumSdnnSq += detrendedRr * detrendedRr;
        }
        double sdnn = Math.sqrt(sumSdnnSq / (N - 1));

        // --- RMSSD & pNN50 ---
        double sumDiffSq = 0.0;
        int nn50Count = 0;
        int totalPairs = N - 1;

        for (int i = 0; i < totalPairs; i++) {
            double diff = Math.abs(cleanRr.get(i + 1) - cleanRr.get(i));
            sumDiffSq += diff * diff;
            if (diff > 50.0) {
                nn50Count++;
            }
        }

        double rmssd = Math.sqrt(sumDiffSq / totalPairs);
        double pnn50 = ((double) nn50Count / totalPairs) * 100.0;

        // 3. Ресемплирование 4 Гц
        double[] timeStamps = new double[N];
        double currentTime = 0;
        for (int i = 0; i < N; i++) {
            timeStamps[i] = currentTime;
            currentTime += cleanRr.get(i) / 1000.0;
        }

        double samplingFreq = 4.0;
        double dt = 1.0 / samplingFreq; // 0.25 сек
        int numSamples = (int) Math.floor(currentTime * samplingFreq);

        int fftSize = 1;
        while (fftSize < numSamples) fftSize <<= 1;
        if (fftSize < 256) fftSize = 256;

        double[] resampled = new double[fftSize];
        int rrIndex = 0;
        for (int i = 0; i < numSamples; i++) {
            double t = i * dt;
            while (rrIndex < N - 2 && timeStamps[rrIndex + 1] < t) {
                rrIndex++;
            }
            double t0 = timeStamps[rrIndex];
            double t1 = timeStamps[rrIndex + 1];
            double v0 = cleanRr.get(rrIndex);
            double v1 = cleanRr.get(rrIndex + 1);

            resampled[i] = v0 + (v1 - v0) * ((t - t0) / (t1 - t0));
        }

        // 4. Двухпроходный High-Pass Детрендинг (ФВЧ 2-го порядка, 12 дБ/окт)
        double fc = 0.042;
        double rc = 1.0 / (2.0 * Math.PI * fc);
        double alpha = rc / (rc + dt);

        // Первый проход ФВЧ
        double[] hpPass1 = new double[numSamples];
        hpPass1[0] = 0;
        for (int i = 1; i < numSamples; i++) {
            hpPass1[i] = alpha * (hpPass1[i - 1] + resampled[i] - resampled[i - 1]);
        }

        // Второй проход ФВЧ (подавляет утечку VLF с крутизной 12 дБ/окт)
        double[] hpFiltered = new double[numSamples];
        hpFiltered[0] = 0;
        for (int i = 1; i < numSamples; i++) {
            hpFiltered[i] = alpha * (hpFiltered[i - 1] + hpPass1[i] - hpPass1[i - 1]);
        }

        // 5. Окно Ханна перед БПФ
        double[] detrended = new double[fftSize];
        for (int i = 0; i < numSamples; i++) {
            double hann = 0.5 * (1 - Math.cos(2 * Math.PI * i / (numSamples - 1)));
            detrended[i] = hpFiltered[i] * hann;
        }

        // 6. БПФ (FFT)
        double[] real = detrended;
        double[] imag = new double[fftSize];
        fft(real, imag);

        double df = samplingFreq / fftSize;
        double vlfPower = 0;
        double lfPower = 0;
        double hfPower = 0;

        // Нормировочный коэффициент с учетом окна Ханна (0.375)
        double normFactor = 2.0 / (numSamples * samplingFreq * 0.375);

        for (int i = 0; i < fftSize / 2; i++) {
            double freq = i * df;
            double power = (real[i] * real[i] + imag[i] * imag[i]) * normFactor * df;

            if (freq >= 0.0033 && freq < 0.04) {
                vlfPower += power;
            } else if (freq >= 0.04 && freq < 0.15) {
                lfPower += power;
            } else if (freq >= 0.15 && freq <= 0.40) {
                hfPower += power;
            }
        }

        double totalPower = vlfPower + lfPower + hfPower;
        double lfHfRatio = hfPower > 0 ? lfPower / hfPower : 0;

        return new Metrics(rmssd, sdnn, pnn50, lfPower, hfPower, totalPower, lfHfRatio, artifacts, artifactPct);
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) return;

        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - Integer.numberOfTrailingZeros(n));
            if (j > i) {
                double tempR = real[i];
                real[i] = real[j];
                real[j] = tempR;

                double tempI = imag[i];
                imag[i] = imag[j];
                imag[j] = tempI;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);

            for (int i = 0; i < n; i += len) {
                double wR = 1;
                double wI = 0;
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j;
                    int v = i + j + len / 2;

                    double vR = real[v] * wR - imag[v] * wI;
                    double vI = real[v] * wI + imag[v] * wR;

                    real[v] = real[u] - vR;
                    imag[v] = imag[u] - vI;

                    real[u] += vR;
                    imag[u] += vI;

                    double nextWR = wR * wlenR - wI * wlenI;
                    double nextWI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                    wI = nextWI;
                }
            }
        }
    }

    public static List<Integer> parseRrIntervals(byte[] data) {
        List<Integer> rrList = new ArrayList<>();
        if (data == null || data.length < 2) return rrList;

        byte flags = data[0];
        boolean is16BitHr = (flags & 0x01) != 0;
        boolean rrPresent = (flags & 0x10) != 0;

        if (!rrPresent) return rrList;

        int offset = is16BitHr ? 3 : 2;
        if ((flags & 0x08) != 0) offset += 2;

        while (offset + 1 < data.length) {
            int rr1024 = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            int rrMs = (int) Math.round((rr1024 / 1024.0) * 1000.0);
            rrList.add(rrMs);
            offset += 2;
        }
        return rrList;
    }
}
