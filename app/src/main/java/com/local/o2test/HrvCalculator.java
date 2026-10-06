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

        // ==========================================
        // 1. ФИЛЬТРАЦИЯ ВЫБРОСОВ (АРТЕФАКТОВ)
        // ==========================================
        List<Double> cleanRr = new ArrayList<>();
        int artifacts = 0;
        int consecutiveArtifacts = 0;

        for (int i = 0; i < rawRrList.size(); i++) {
            double rr = rawRrList.get(i);
            if (rr < 300 || rr > 2000) {
                artifacts++;
                continue;
            }
            if (!cleanRr.isEmpty()) {
                double prev = cleanRr.get(cleanRr.size() - 1);
                if (Math.abs(rr - prev) / prev > 0.25) {
                    artifacts++;
                    consecutiveArtifacts++;
                    // Если подряд идет 3 и более "выбросов", сбрасываем счетчик и принимаем новый ритм
                    if (consecutiveArtifacts < 3) {
                        continue;
                    }
                }
            }
            consecutiveArtifacts = 0;
            cleanRr.add(rr);
        }

        if (cleanRr.size() < 10) {
            return new Metrics(0, 0, 0, 0, 0, 0, 0, artifacts, 100.0);
        }

        int N = cleanRr.size();
        double artifactPct = ((double) artifacts / rawRrList.size()) * 100.0;

        // ==========================================
        // 2. ВРЕМЕННОЙ АНАЛИЗ (RMSSD, pNN50, SDNN) STRICTLY ON cleanRr
        // ==========================================
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

        // SDNN по физическому ряду RR
        double sumRr = 0.0;
        for (double rr : cleanRr) {
            sumRr += rr;
        }
        double meanRr = sumRr / N;

        double sumSdnnSq = 0.0;
        for (double rr : cleanRr) {
            double diff = rr - meanRr;
            sumSdnnSq += diff * diff;
        }
        double sdnn = Math.sqrt(sumSdnnSq / (N - 1));

        // ==========================================
        // 3. РЕСЕМПЛИРОВАНИЕ В 4 Гц (PCHIP / Monotone Hermite)
        // ==========================================
        double[] timeStamps = new double[N];
        double currentTime = 0;
        for (int i = 0; i < N; i++) {
            timeStamps[i] = currentTime;
            currentTime += cleanRr.get(i) / 1000.0; // секунды
        }

        double samplingFreq = 4.0;
        double dt = 1.0 / samplingFreq; // 0.25 сек
        int numSamples = (int) Math.floor(currentTime * samplingFreq);

        if (numSamples < 32) {
            return new Metrics(rmssd, sdnn, pnn50, 0, 0, 0, 0, artifacts, artifactPct);
        }

        double[] rrArray = new double[N];
        for (int i = 0; i < N; i++) rrArray[i] = cleanRr.get(i);

        double[] resampled = pchipInterpolate(timeStamps, rrArray, numSamples, dt);

        // ==========================================
        // 4. ДЕТРЕНДИНГ НА СЕТКЕ 4 Гц (Smoothness Priors, lambda = 500)
        // ==========================================
        // На 4 Гц сетке lambda = 500 соответствует cutoff frequency ~0.035 Hz (убирает VLF-дрейф)
        double[] zTrend = smoothnessPriorsDetrend(resampled, 500.0);
        double[] detrended = new double[numSamples];
        for (int i = 0; i < numSamples; i++) {
            detrended[i] = resampled[i] - zTrend[i];
        }

        // ==========================================
        // 5. СПЕКТРАЛЬНЫЙ АНАЛИЗ (WELCH'S PSD, 50% Overlap, Hann Window)
        // ==========================================
        int segmentLength = 128; // 32 сек
        if (numSamples < segmentLength) {
            segmentLength = numSamples;
        }
        int overlap = segmentLength / 2; // 50%
        int step = segmentLength - overlap;

        int fftSize = 256;

        double hannPowerSum = 0;
        double[] hannWindow = new double[segmentLength];
        for (int i = 0; i < segmentLength; i++) {
            hannWindow[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (segmentLength - 1)));
            hannPowerSum += hannWindow[i] * hannWindow[i];
        }

        double[] avgPsd = new double[fftSize / 2];
        int segmentCount = 0;

        for (int start = 0; start + segmentLength <= numSamples; start += step) {
            double[] real = new double[fftSize];
            double[] imag = new double[fftSize];

            for (int i = 0; i < segmentLength; i++) {
                real[i] = detrended[start + i] * hannWindow[i];
            }

            fft(real, imag);

            // Двусторонняя PSD нормализация
            double norm = 2.0 / (samplingFreq * hannPowerSum);

            for (int k = 0; k < fftSize / 2; k++) {
                double power = (real[k] * real[k] + imag[k] * imag[k]) * norm;
                avgPsd[k] += power;
            }
            segmentCount++;
        }

        if (segmentCount == 0) {
            segmentCount = 1;
        }

        double df = samplingFreq / fftSize;
        double vlfPower = 0;
        double lfPower = 0;
        double hfPower = 0;

        for (int k = 0; k < fftSize / 2; k++) {
            avgPsd[k] /= segmentCount;
            double freq = k * df;
            double bandPower = avgPsd[k] * df;

            if (freq >= 0.0033 && freq < 0.04) {
                vlfPower += bandPower;
            } else if (freq >= 0.04 && freq < 0.15) {
                lfPower += bandPower;
            } else if (freq >= 0.15 && freq <= 0.40) {
                hfPower += bandPower;
            }
        }

        double totalPower = vlfPower + lfPower + hfPower;
        double lfHfRatio = hfPower > 0 ? lfPower / hfPower : 0;

        return new Metrics(rmssd, sdnn, pnn50, lfPower, hfPower, totalPower, lfHfRatio, artifacts, artifactPct);
    }

    // --- Интерполяция PCHIP (Monotone Piecewise Cubic Hermite) ---
    private static double[] pchipInterpolate(double[] x, double[] y, int numSamples, double dt) {
        int n = x.length;
        double[] resampled = new double[numSamples];

        if (n < 3) {
            int idx = 0;
            for (int i = 0; i < numSamples; i++) {
                double t = i * dt;
                while (idx < n - 2 && x[idx + 1] < t) idx++;
                double t0 = x[idx], t1 = x[idx + 1];
                resampled[i] = y[idx] + (y[idx + 1] - y[idx]) * ((t - t0) / (t1 - t0));
            }
            return resampled;
        }

        double[] h = new double[n - 1];
        double[] delta = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
            delta[i] = (y[i + 1] - y[i]) / h[i];
        }

        double[] d = new double[n];

        // Конечные точки
        d[0] = pchipEndSlope(h[0], h[1], delta[0], delta[1]);
        d[n - 1] = pchipEndSlope(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);

        // Внутренние точки
        for (int i = 1; i < n - 1; i++) {
            if (delta[i - 1] * delta[i] <= 0) {
                d[i] = 0;
            } else {
                double w1 = 2 * h[i] + h[i - 1];
                double w2 = h[i] + 2 * h[i - 1];
                d[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
            }
        }

        int seg = 0;
        for (int i = 0; i < numSamples; i++) {
            double t = i * dt;
            while (seg < n - 2 && x[seg + 1] < t) {
                seg++;
            }
            double hSeg = h[seg];
            double s = (t - x[seg]) / hSeg;
            double s2 = s * s;
            double s3 = s2 * s;

            double h00 = 2 * s3 - 3 * s2 + 1;
            double h10 = s3 - 2 * s2 + s;
            double h01 = -2 * s3 + 3 * s2;
            double h11 = s3 - s2;

            resampled[i] = h00 * y[seg] + h10 * hSeg * d[seg] + h01 * y[seg + 1] + h11 * hSeg * d[seg + 1];
        }

        return resampled;
    }

    private static double pchipEndSlope(double h1, double h2, double del1, double del2) {
        double d = ((2 * h1 + h2) * del1 - h1 * del2) / (h1 + h2);
        if (Math.signum(d) != Math.signum(del1)) {
            d = 0;
        } else if ((Math.signum(del1) != Math.signum(del2)) && (Math.abs(d) > Math.abs(3 * del1))) {
            d = 3 * del1;
        }
        return d;
    }

    // --- Smoothness Priors Detrending ---
    private static double[] smoothnessPriorsDetrend(double[] y, double lambda) {
        int N = y.length;
        double[] zTrend = new double[N];
        if (N < 3) {
            System.arraycopy(y, 0, zTrend, 0, N);
            return zTrend;
        }

        double alpha = lambda * lambda;

        double[] d = new double[N];
        double[] e = new double[N - 1];
        double[] f = new double[N - 2];

        for (int i = 0; i < N; i++) {
            if (i == 0 || i == N - 1) {
                d[i] = 1.0 + alpha;
            } else if (i == 1 || i == N - 2) {
                d[i] = 1.0 + 5.0 * alpha;
            } else {
                d[i] = 1.0 + 6.0 * alpha;
            }
        }

        for (int i = 0; i < N - 1; i++) {
            if (i == 0 || i == N - 2) {
                e[i] = -2.0 * alpha;
            } else {
                e[i] = -4.0 * alpha;
            }
        }

        for (int i = 0; i < N - 2; i++) {
            f[i] = alpha;
        }

        double[] dL = new double[N];
        double[] l1 = new double[N - 1];
        double[] l2 = new double[N - 2];

        for (int i = 0; i < N; i++) {
            double sumD = d[i];
            if (i >= 1) sumD -= l1[i - 1] * l1[i - 1] * dL[i - 1];
            if (i >= 2) sumD -= l2[i - 2] * l2[i - 2] * dL[i - 2];
            dL[i] = sumD;

            if (i < N - 1) {
                double sumL1 = e[i];
                if (i >= 1) sumL1 -= l2[i - 1] * l1[i - 1] * dL[i - 1];
                l1[i] = sumL1 / dL[i];
            }

            if (i < N - 2) {
                l2[i] = f[i] / dL[i];
            }
        }

        double[] w = new double[N];
        for (int i = 0; i < N; i++) {
            double val = y[i];
            if (i >= 1) val -= l1[i - 1] * w[i - 1];
            if (i >= 2) val -= l2[i - 2] * w[i - 2];
            w[i] = val;
        }

        double[] v = new double[N];
        for (int i = 0; i < N; i++) {
            v[i] = w[i] / dL[i];
        }

        for (int i = N - 1; i >= 0; i--) {
            double val = v[i];
            if (i < N - 1) val -= l1[i] * zTrend[i + 1];
            if (i < N - 2) val -= l2[i] * zTrend[i + 2];
            zTrend[i] = val;
        }

        return zTrend;
    }

    // --- FFT ---
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

    // --- BLE Parser ---
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
