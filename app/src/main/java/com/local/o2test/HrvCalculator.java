package com.local.o2test;

import java.util.ArrayList;
import java.util.List;

public class HrvCalculator {

    // Коэффициент экспоненциального сглаживания (0.15 — плавный отклик без шума)
    private static final float EMA_ALPHA = 0.15f;

    private static float smoothedLf = 0f;
    private static float smoothedHf = 0f;
    private static float smoothedTp = 0f;
    private static float smoothedRatio = 0f;

    public static class Metrics {
        public final float rmssd;
        public final float pnn50;
        public final float lfPower;      // ms²
        public final float hfPower;      // ms²
        public final float totalPower;   // ms²
        public final float lfHfRatio;

        public Metrics(float rmssd, float pnn50, float lfPower, float hfPower, float totalPower, float lfHfRatio) {
            this.rmssd = rmssd;
            this.pnn50 = pnn50;
            this.lfPower = lfPower;
            this.hfPower = hfPower;
            this.totalPower = totalPower;
            this.lfHfRatio = lfHfRatio;
        }
    }

    public static void resetSmoothing() {
        smoothedLf = 0f;
        smoothedHf = 0f;
        smoothedTp = 0f;
        smoothedRatio = 0f;
    }

    public static List<Integer> parseRrIntervals(byte[] data) {
        List<Integer> rrList = new ArrayList<>();
        if (data == null || data.length < 2) return rrList;

        byte flags = data[0];
        boolean hr16Bit = (flags & 0x01) != 0;
        boolean eePresent = (flags & 0x08) != 0;
        boolean rrPresent = (flags & 0x10) != 0;

        if (!rrPresent) return rrList;

        int offset = 1;
        offset += hr16Bit ? 2 : 1;
        if (eePresent) offset += 2;

        while (offset + 1 < data.length) {
            int rawRr = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
            int rrMs = (int) Math.round((rawRr / 1024.0) * 1000.0);
            if (rrMs >= 300 && rrMs <= 2000) { // Фильтрация артефактов
                rrList.add(rrMs);
            }
            offset += 2;
        }
        return rrList;
    }

    public static Metrics calculate(List<Integer> rrBuffer) {
        if (rrBuffer == null || rrBuffer.size() < 10) {
            resetSmoothing();
            return new Metrics(0, 0, 0, 0, 0, 0);
        }

        int n = rrBuffer.size();

        // 1. Расчет RMSSD и pNN50 (Временная область)
        double sumSqDiff = 0.0;
        int nn50Count = 0;

        for (int i = 0; i < n - 1; i++) {
            int diff = Math.abs(rrBuffer.get(i + 1) - rrBuffer.get(i));
            sumSqDiff += diff * diff;
            if (diff > 50) {
                nn50Count++;
            }
        }

        float rmssd = (float) Math.sqrt(sumSqDiff / (n - 1));
        float pnn50 = ((float) nn50Count / (n - 1)) * 100f;

        if (n < 30) {
            return new Metrics(rmssd, pnn50, 0, 0, 0, 0);
        }

        // 2. Выделение фиксированного окна БПФ: 256 точек (4 Гц, последние 64 секунды)
        int fftSize = 256;
        double fs = 4.0;
        double windowDurationSec = fftSize / fs;

        List<Double> timeStamps = new ArrayList<>();
        List<Integer> recentRr = new ArrayList<>();
        double totalTime = 0;

        for (int rr : rrBuffer) {
            totalTime += rr / 1000.0;
        }

        double startTimeThreshold = Math.max(0, totalTime - windowDurationSec);
        double currentTime = 0;

        for (int rr : rrBuffer) {
            currentTime += rr / 1000.0;
            if (currentTime >= startTimeThreshold) {
                timeStamps.add(currentTime - startTimeThreshold);
                recentRr.add(rr);
            }
        }

        if (recentRr.size() < 10) {
            return new Metrics(rmssd, pnn50, smoothedLf, smoothedHf, smoothedTp, smoothedRatio);
        }

        // Равномерная интерполяция
        double[] resampled = new double[fftSize];
        int rrIndex = 0;

        for (int i = 0; i < fftSize; i++) {
            double t = i / fs;
            while (rrIndex < timeStamps.size() - 2 && timeStamps.get(rrIndex + 1) < t) {
                rrIndex++;
            }
            double t0 = timeStamps.get(rrIndex);
            double t1 = timeStamps.get(rrIndex + 1);
            double y0 = recentRr.get(rrIndex);
            double y1 = recentRr.get(rrIndex + 1);

            if (t1 != t0) {
                resampled[i] = y0 + (y1 - y0) * (t - t0) / (t1 - t0);
            } else {
                resampled[i] = y0;
            }
        }

        // Центрирование (удаление постоянной составляющей)
        double mean = 0;
        for (int i = 0; i < fftSize; i++) mean += resampled[i];
        mean /= fftSize;
        for (int i = 0; i < fftSize; i++) resampled[i] -= mean;

        // Окно Ханна по всей фиксированной длине 256 точек
        double[] windowedReal = new double[fftSize];
        double[] windowedImag = new double[fftSize];
        double windowSumSq = 0;

        for (int i = 0; i < fftSize; i++) {
            double hanning = 0.5 * (1 - Math.cos(2 * Math.PI * i / (fftSize - 1)));
            windowedReal[i] = resampled[i] * hanning;
            windowSumSq += hanning * hanning;
        }

        // БПФ
        fft(windowedReal, windowedImag);

        // 3. Расчет спектральных мощностей
        double df = fs / fftSize;
        float rawLf = 0;
        float rawHf = 0;
        float rawVlf = 0;

        double normFactor = (windowSumSq > 0) ? (2.0 / (fftSize * windowSumSq)) : 0;

        for (int i = 1; i < fftSize / 2; i++) {
            double freq = i * df;
            double powerBin = (windowedReal[i] * windowedReal[i] + windowedImag[i] * windowedImag[i]) * normFactor;

            if (freq >= 0.0033 && freq < 0.04) {
                rawVlf += powerBin;
            } else if (freq >= 0.04 && freq < 0.15) {
                rawLf += powerBin;
            } else if (freq >= 0.15 && freq <= 0.40) {
                rawHf += powerBin;
            }
        }

        float rawTp = rawVlf + rawLf + rawHf;
        float rawRatio = (rawHf > 0) ? (rawLf / rawHf) : 0f;

        // 4. Экспоненциальное сглаживание (EMA)
        if (smoothedTp == 0f) {
            smoothedLf = rawLf;
            smoothedHf = rawHf;
            smoothedTp = rawTp;
            smoothedRatio = rawRatio;
        } else {
            smoothedLf = smoothedLf + EMA_ALPHA * (rawLf - smoothedLf);
            smoothedHf = smoothedHf + EMA_ALPHA * (rawHf - smoothedHf);
            smoothedTp = smoothedTp + EMA_ALPHA * (rawTp - smoothedTp);
            smoothedRatio = smoothedRatio + EMA_ALPHA * (rawRatio - smoothedRatio);
        }

        return new Metrics(rmssd, pnn50, smoothedLf, smoothedHf, smoothedTp, smoothedRatio);
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) return;

        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                double tempR = real[i]; real[i] = real[j]; real[j] = tempR;
                double tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI;
            }
            int m = n >> 1;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wlenR = Math.cos(angle);
            double wlenI = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k;
                    int v = i + k + len / 2;
                    double uR = real[u], uI = imag[u];
                    double vR = real[v] * wR - imag[v] * wI;
                    double vI = real[v] * wI + imag[v] * wR;

                    real[u] = uR + vR;
                    imag[u] = uI + vI;
                    real[v] = uR - vR;
                    imag[v] = uI - vI;

                    double nextWR = wR * wlenR - wI * wlenI;
                    double nextWI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                    wI = nextWI;
                }
            }
        }
    }
                        }
