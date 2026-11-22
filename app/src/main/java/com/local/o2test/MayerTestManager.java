package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MayerTestManager {

    private static final int WINDOW_RR_COUNT = 100;
    private static final int TRANSIENT_SKIP_COUNT = 20;
    private static final int EDGE_HR_COUNT = 15;
    private static final int HR_SMOOTH_WINDOW = 4;

    private static final float PERIOD_10_SEC = 10.0f;
    private static final float PERIOD_12_SEC = 12.0f;

    private static final double MIN_AMPLITUDE_GATE_BPM = 3.0;
    private static final double MAX_HR_GATE_12_SEC_BPM = 60.0;

    private static final double LF_HF_FLOOR = 1.5;
    private static final double LF_HF_CEIL = 15.0;

    private static final double AMP_FLOOR_BPM = 2.0;
    private static final double AMP_CEIL_BPM = 10.0;

    private static final double HR_DROP_OFFSET_BPM = 2.0;
    private static final double HR_DROP_SPAN_BPM = 8.0;
    private static final double HR_TARGET_WINDOW_BPM = 15.0;
    private static final double HR_TARGET_10_SEC_BPM = 60.0;
    private static final double HR_TARGET_12_SEC_BPM = 50.0;

    private static final double WEIGHT_LF_HF = 0.40;
    private static final double WEIGHT_AMP = 0.35;
    private static final double WEIGHT_HR = 0.25;

    private static final double MIN_RESONANCE_SCORE = 0.35;
    private static final double MARGIN_12_SEC_OVER_10_SEC = 0.05;
    private static final double FALLBACK_HR_12_SEC_THRESHOLD_BPM = 55.0;

    private enum SearchPhase {
        IDLE,
        MEASURE_10_SEC,
        MEASURE_12_SEC
    }

    public static class MeasuredPoint {
        public final float periodSec;
        public final float freqHz;
        public final float lfHf;

        public MeasuredPoint(float periodSec, float freqHz, float lfHf) {
            this.periodSec = periodSec;
            this.freqHz = freqHz;
            this.lfHf = lfHf;
        }
    }

    private static class PointEvaluation {
        final float periodSec;
        final float freqHz;
        final double lfHf;
        final double pulseAmplitudeBpm;
        final double hrMeanBpm;
        final double hrDropBpm;
        final boolean passedAmplitudeGate;
        final boolean passedHrGate;
        final double sLfHf;
        final double sAmp;
        final double sHr;
        final double score;

        PointEvaluation(
                float periodSec,
                float freqHz,
                double lfHf,
                double pulseAmplitudeBpm,
                double hrMeanBpm,
                double hrDropBpm,
                boolean passedAmplitudeGate,
                boolean passedHrGate,
                double sLfHf,
                double sAmp,
                double sHr,
                double score
        ) {
            this.periodSec = periodSec;
            this.freqHz = freqHz;
            this.lfHf = lfHf;
            this.pulseAmplitudeBpm = pulseAmplitudeBpm;
            this.hrMeanBpm = hrMeanBpm;
            this.hrDropBpm = hrDropBpm;
            this.passedAmplitudeGate = passedAmplitudeGate;
            this.passedHrGate = passedHrGate;
            this.sLfHf = sLfHf;
            this.sAmp = sAmp;
            this.sHr = sHr;
            this.score = score;
        }

        boolean passedAllGates() {
            return passedAmplitudeGate && passedHrGate;
        }
    }

    private final Context context;
    private final UiBuilder.TestButtonContainer btnTest;
    private final MayerTestListener listener;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isAvailable = false;
    private boolean isTestModeActive = false;
    private int testRunToken = 0;

    private long sessionStartTimeMs = 0L;
    private float initialPeriodSec = PERIOD_10_SEC;
    private SearchPhase searchPhase = SearchPhase.IDLE;
    private int stepStartValidCount = 0;

    private PointEvaluation eval10Sec = null;
    private PointEvaluation eval12Sec = null;
    private final List<MeasuredPoint> measuredSeries = new ArrayList<>();

    private float currentCandidatePeriodSec = PERIOD_10_SEC;

    public interface MayerTestListener {
        void onTestModeChanged(boolean isActive);
        void onLog(String message);
        default void onTestPeriodChanged(float periodSec) {}
        default void onMayerTestCompleted(float calculatedFmHz) {}
        default void onMayerTestConvergenceSummary(String summary) {}
        default List<Integer> getValidRrIntervalsForTest() {
            return null;
        }
    }

    public MayerTestManager(Context context, UiBuilder.TestButtonContainer btnTest, MayerTestListener listener) {
        this.context = context;
        this.btnTest = btnTest;
        this.listener = listener;
        applyInitialState();
    }

    public void configureSessionContext(long sessionStartTimeMs, float baselinePeriodSec) {
        this.sessionStartTimeMs = Math.max(0L, sessionStartTimeMs);
        this.initialPeriodSec = baselinePeriodSec > 0f ? baselinePeriodSec : PERIOD_10_SEC;
    }

    private void applyInitialState() {
        if (btnTest != null) {
            btnTest.setEnabled(false);
            btnTest.setAlpha(0.5f);
        }
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
        if (btnTest != null && !isTestModeActive) {
            btnTest.setEnabled(available);
            btnTest.setAlpha(available ? 1.0f : 0.5f);
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public boolean isMayerTestModeActive() {
        return isTestModeActive;
    }

    /**
     * Обработка нажатия на кнопку «Тест» с защитой от повторных срабатываний
     */
    public void handleTestButtonClick() {
        if (!isAvailable || isTestModeActive) return;

        testRunToken++;
        isTestModeActive = true;
        resetSearchState();
        searchPhase = SearchPhase.MEASURE_10_SEC;
        currentCandidatePeriodSec = PERIOD_10_SEC;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
            btnTest.setEnabled(false);
            btnTest.setAlpha(1.0f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования активирован (точки 10с и 12с по 100 RR): автоподстройка фазы отключена до конца текущего дампа.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(true);
            listener.onTestPeriodChanged(currentCandidatePeriodSec);
        }

        captureStepBaseline();
        showTestAlert();
        evaluateNextValidRrWindow(testRunToken);
    }

    /**
     * Сброс режима и возврат кнопки в штатный вид
     */
    public void resetTestMode() {
        if (!isTestModeActive) return;

        testRunToken++;

        String interruptedSummary = null;
        if (searchPhase != SearchPhase.IDLE && !measuredSeries.isEmpty()) {
            interruptedSummary = String.format(
                    Locale.US,
                    "Тест прерван на фазе %s (измерено точек: %d) | %s",
                    searchPhase.name(),
                    measuredSeries.size(),
                    buildPartialSummary()
            );
        }

        isTestModeActive = false;
        resetSearchState();

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования завершен. Возврат к штатной автоподстройке фазы.";
        if (listener != null) {
            if (interruptedSummary != null) {
                listener.onMayerTestConvergenceSummary(interruptedSummary);
            }
            listener.onLog(logMsg);
            listener.onTestModeChanged(false);
        }
    }

    private void resetSearchState() {
        searchPhase = SearchPhase.IDLE;
        stepStartValidCount = 0;
        eval10Sec = null;
        eval12Sec = null;
        measuredSeries.clear();
    }

    private void captureStepBaseline() {
        List<Integer> currentValid = (listener != null) ? listener.getValidRrIntervalsForTest() : null;
        stepStartValidCount = (currentValid != null) ? currentValid.size() : 0;
    }

    private void evaluateNextValidRrWindow(final int activeToken) {
        if (!isTestModeActive || activeToken != testRunToken || searchPhase == SearchPhase.IDLE) return;

        final float targetPeriodSec = currentCandidatePeriodSec;
        final SearchPhase targetPhase = searchPhase;

        queryExecutor.execute(() -> {
            if (!isTestModeActive || activeToken != testRunToken) return;

            List<Integer> validRrList = (listener != null) ? listener.getValidRrIntervalsForTest() : null;
            if (validRrList == null) {
                mainHandler.postDelayed(() -> evaluateNextValidRrWindow(activeToken), 500L);
                return;
            }

            int totalValid = validRrList.size();
            int newValidCount = totalValid - stepStartValidCount;
            if (newValidCount < WINDOW_RR_COUNT) {
                mainHandler.postDelayed(() -> evaluateNextValidRrWindow(activeToken), 500L);
                return;
            }

            int fromIdx = Math.max(0, totalValid - WINDOW_RR_COUNT);
            List<Integer> rrWindow100 = new ArrayList<>(validRrList.subList(fromIdx, totalValid));

            final PointEvaluation evaluation = evaluateWindowForPeriod(rrWindow100, targetPeriodSec);
            mainHandler.post(() -> {
                if (!isTestModeActive || activeToken != testRunToken || searchPhase != targetPhase) return;
                advanceSearchWithEvaluation(evaluation);
            });
        });
    }

    private PointEvaluation evaluateWindowForPeriod(List<Integer> window100, float periodSec) {
        // 1. Полный массив (100 интервалов): штатный спектральный расчет LF/HF
        double lfHf = HrvCalculator.calculateStandardLfHf(window100);
        if (Double.isNaN(lfHf) || Double.isInfinite(lfHf) || lfHf < 0.0) {
            lfHf = 0.0;
        }

        int n = window100.size();
        double[] instHrAll = new double[n];
        for (int i = 0; i < n; i++) {
            int rr = Math.max(250, Math.min(3000, window100.get(i)));
            instHrAll[i] = 60000.0 / rr;
        }

        // Пульсовой тренд ΔHR: среднее первых 15 интервалов минус среднее последних 15 интервалов
        int edgeCount = Math.min(EDGE_HR_COUNT, n / 2);
        double hrStartMean = meanSlice(instHrAll, 0, edgeCount);
        double hrEndMean = meanSlice(instHrAll, n - edgeCount, n);
        double hrDropBpm = hrStartMean - hrEndMean;

        // 2. Установившаяся часть (например, интервалы с 21-го по 100-й -> индексы 20..99, последние 80 интервалов)
        int settledStart = Math.min(TRANSIENT_SKIP_COUNT, Math.max(0, n - 1));
        List<Integer> settledRrRaw = new ArrayList<>(window100.subList(settledStart, n));
        List<Integer> settledRrClean = cleanLocalArtifacts(settledRrRaw);

        double[] settledHr = new double[settledRrClean.size()];
        double sumSettledHr = 0.0;
        for (int i = 0; i < settledRrClean.size(); i++) {
            int rr = Math.max(250, Math.min(3000, settledRrClean.get(i)));
            double hr = 60000.0 / rr;
            settledHr[i] = hr;
            sumSettledHr += hr;
        }
        double hrMeanBpm = settledHr.length > 0 ? (sumSettledHr / settledHr.length) : 0.0;

        // Сглаживание мгновенного пульса скользящим окном по 3-5 ударам (4 удара)
        double[] smoothedHr = smoothMovingAverage(settledHr, HR_SMOOTH_WINDOW);

        // Поцикловый размах ΔHR_k = HR_max_k - HR_min_k на отрезках длительностью T (мс), медиана по циклам
        long cycleDurationMs = Math.max(1000L, Math.round(periodSec * 1000.0));
        double pulseAmplitudeBpm = computeCycleAmplitudeMedianBpm(smoothedHr, settledRrClean, cycleDurationMs);

        // 3. Жесткие фильтры допустимости (гейты) до взвешивания
        boolean passedAmplitudeGate = pulseAmplitudeBpm >= MIN_AMPLITUDE_GATE_BPM;
        boolean isTwelveSec = Math.abs(periodSec - PERIOD_12_SEC) < 0.01f;
        boolean passedHrGate = !isTwelveSec || (hrMeanBpm <= MAX_HR_GATE_12_SEC_BPM);

        // 4. Нормировка в [0.0 ... 1.0] и распределение весов
        double sLfHf = clamp01((lfHf - LF_HF_FLOOR) / (LF_HF_CEIL - LF_HF_FLOOR));
        double sAmp = clamp01((pulseAmplitudeBpm - AMP_FLOOR_BPM) / (AMP_CEIL_BPM - AMP_FLOOR_BPM));

        double sDrop = clamp01((hrDropBpm + HR_DROP_OFFSET_BPM) / HR_DROP_SPAN_BPM);
        double hrTarget = isTwelveSec ? HR_TARGET_12_SEC_BPM : HR_TARGET_10_SEC_BPM;
        double sTarget = clamp01(1.0 - (Math.abs(hrMeanBpm - hrTarget) / HR_TARGET_WINDOW_BPM));
        double sHr = 0.5 * sDrop + 0.5 * sTarget;

        double score;
        if (!passedAmplitudeGate || !passedHrGate) {
            score = 0.0;
        } else {
            score = WEIGHT_LF_HF * sLfHf + WEIGHT_AMP * sAmp + WEIGHT_HR * sHr;
        }

        float freqHz = 1.0f / periodSec;
        return new PointEvaluation(
                periodSec,
                freqHz,
                lfHf,
                pulseAmplitudeBpm,
                hrMeanBpm,
                hrDropBpm,
                passedAmplitudeGate,
                passedHrGate,
                sLfHf,
                sAmp,
                sHr,
                score
        );
    }

    /**
     * Локальная очистка единичных выбросов (>25% от локального среднего) без изменения основного потока данных
     */
    private List<Integer> cleanLocalArtifacts(List<Integer> rawList) {
        int size = (rawList != null) ? rawList.size() : 0;
        List<Integer> cleaned = new ArrayList<>(size);
        if (size == 0) return cleaned;

        for (int i = 0; i < size; i++) {
            int val = rawList.get(i);
            double sumNeighbors = 0.0;
            int countNeighbors = 0;

            int from = Math.max(0, i - 2);
            int to = Math.min(size - 1, i + 2);
            for (int j = from; j <= to; j++) {
                if (j == i) continue;
                int neighbor = rawList.get(j);
                if (neighbor >= 300 && neighbor <= 2500) {
                    sumNeighbors += neighbor;
                    countNeighbors++;
                }
            }

            if (countNeighbors > 0) {
                double localMean = sumNeighbors / countNeighbors;
                double relDiff = Math.abs(val - localMean) / localMean;
                if (relDiff > 0.25) {
                    cleaned.add((int) Math.round(localMean));
                    continue;
                }
            }
            cleaned.add(val);
        }
        return cleaned;
    }

    private double[] smoothMovingAverage(double[] values, int windowSize) {
        if (values == null || values.length == 0) return new double[0];
        int win = Math.max(1, windowSize);
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - win + 1);
            double sum = 0.0;
            int count = 0;
            for (int j = start; j <= i; j++) {
                sum += values[j];
                count++;
            }
            result[i] = count > 0 ? (sum / count) : values[i];
        }
        return result;
    }

    private double computeCycleAmplitudeMedianBpm(double[] hrSeries, List<Integer> rrList, long cycleDurationMs) {
        if (hrSeries == null || rrList == null || hrSeries.length == 0 || hrSeries.length != rrList.size()) {
            return 0.0;
        }

        List<Double> cycleRanges = new ArrayList<>();
        long accMs = 0L;
        double cycleMin = Double.POSITIVE_INFINITY;
        double cycleMax = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < hrSeries.length; i++) {
            double hr = hrSeries[i];
            long rr = Math.max(250L, Math.min(3000L, rrList.get(i)));

            if (hr < cycleMin) cycleMin = hr;
            if (hr > cycleMax) cycleMax = hr;
            accMs += rr;

            if (accMs >= cycleDurationMs) {
                if (cycleMax >= cycleMin && !Double.isInfinite(cycleMin) && !Double.isInfinite(cycleMax)) {
                    cycleRanges.add(cycleMax - cycleMin);
                }
                accMs = 0L;
                cycleMin = Double.POSITIVE_INFINITY;
                cycleMax = Double.NEGATIVE_INFINITY;
            }
        }

        if (cycleRanges.isEmpty()) {
            double minAll = Double.POSITIVE_INFINITY;
            double maxAll = Double.NEGATIVE_INFINITY;
            for (double hr : hrSeries) {
                if (hr < minAll) minAll = hr;
                if (hr > maxAll) maxAll = hr;
            }
            if (minAll < Double.POSITIVE_INFINITY && maxAll > Double.NEGATIVE_INFINITY) {
                return Math.max(0.0, maxAll - minAll);
            }
            return 0.0;
        }

        Collections.sort(cycleRanges);
        int mid = cycleRanges.size() / 2;
        if (cycleRanges.size() % 2 == 1) {
            return cycleRanges.get(mid);
        }
        return 0.5 * (cycleRanges.get(mid - 1) + cycleRanges.get(mid));
    }

    private double meanSlice(double[] arr, int fromInclusive, int toExclusive) {
        if (arr == null || arr.length == 0) return 0.0;
        int start = Math.max(0, fromInclusive);
        int end = Math.min(arr.length, toExclusive);
        if (end <= start) return 0.0;
        double sum = 0.0;
        for (int i = start; i < end; i++) {
            sum += arr[i];
        }
        return sum / (end - start);
    }

    private double clamp01(double val) {
        if (Double.isNaN(val) || val <= 0.0) return 0.0;
        if (val >= 1.0) return 1.0;
        return val;
    }

    private void advanceSearchWithEvaluation(PointEvaluation evaluation) {
        if (!isTestModeActive || evaluation == null) return;

        measuredSeries.add(new MeasuredPoint(evaluation.periodSec, evaluation.freqHz, (float) evaluation.lfHf));

        if (listener != null) {
            listener.onLog(String.format(
                    Locale.US,
                    "[МАЙЕР ТЕСТ] Точка T=%.1f с (%.2f Hz) | LF/HF=%.2f | A_pulse=%.2f уд/мин | HR=%.1f уд/мин (ΔHR=%+.1f) | Гейты[A:%s,HR:%s] | Q=%.3f",
                    evaluation.periodSec,
                    evaluation.freqHz,
                    evaluation.lfHf,
                    evaluation.pulseAmplitudeBpm,
                    evaluation.hrMeanBpm,
                    evaluation.hrDropBpm,
                    evaluation.passedAmplitudeGate ? "OK" : "0",
                    evaluation.passedHrGate ? "OK" : "0",
                    evaluation.score
            ));
        }

        if (searchPhase == SearchPhase.MEASURE_10_SEC) {
            eval10Sec = evaluation;
            searchPhase = SearchPhase.MEASURE_12_SEC;
            currentCandidatePeriodSec = PERIOD_12_SEC;
            captureStepBaseline();
            if (listener != null) {
                listener.onTestPeriodChanged(currentCandidatePeriodSec);
            }
            evaluateNextValidRrWindow(testRunToken);
        } else if (searchPhase == SearchPhase.MEASURE_12_SEC) {
            eval12Sec = evaluation;
            evaluateTwoPointDecisionAndComplete();
        }
    }

    private void evaluateTwoPointDecisionAndComplete() {
        double q10 = (eval10Sec != null) ? eval10Sec.score : 0.0;
        double q12 = (eval12Sec != null) ? eval12Sec.score : 0.0;
        double maxQ = Math.max(q10, q12);

        double referenceHr = (eval12Sec != null) ? eval12Sec.hrMeanBpm : ((eval10Sec != null) ? eval10Sec.hrMeanBpm : 60.0);

        float selectedPeriod;
        String decisionReason;

        if (maxQ < MIN_RESONANCE_SCORE) {
            if (referenceHr <= FALLBACK_HR_12_SEC_THRESHOLD_BPM) {
                selectedPeriod = PERIOD_12_SEC;
                decisionReason = String.format(Locale.US, "max Q=%.3f < %.2f, базовый выбор 12.0с (HR=%.1f <= %.0f)",
                        maxQ, MIN_RESONANCE_SCORE, referenceHr, FALLBACK_HR_12_SEC_THRESHOLD_BPM);
            } else {
                selectedPeriod = PERIOD_10_SEC;
                decisionReason = String.format(Locale.US, "max Q=%.3f < %.2f, базовый выбор 10.0с (HR=%.1f > %.0f)",
                        maxQ, MIN_RESONANCE_SCORE, referenceHr, FALLBACK_HR_12_SEC_THRESHOLD_BPM);
            }
        } else if (eval12Sec != null && eval12Sec.passedAllGates() && (q12 > q10 + MARGIN_12_SEC_OVER_10_SEC)) {
            selectedPeriod = PERIOD_12_SEC;
            decisionReason = String.format(Locale.US, "победа 12.0с: Q(12)=%.3f > Q(10)=%.3f + %.2f",
                    q12, q10, MARGIN_12_SEC_OVER_10_SEC);
        } else {
            selectedPeriod = PERIOD_10_SEC;
            decisionReason = String.format(Locale.US, "выбран приоритетный узел 10.0с (Q(10)=%.3f, Q(12)=%.3f)", q10, q12);
        }

        float selectedFreq = 1.0f / selectedPeriod;
        String summary = String.format(
                Locale.US,
                "Итог T=%.1f с (%.2f Hz) | %s | [10с: %s] | [12с: %s].",
                selectedPeriod,
                selectedFreq,
                decisionReason,
                formatEvaluationShort(eval10Sec),
                formatEvaluationShort(eval12Sec)
        );

        float calculatedFmHz = 1.0f / selectedPeriod;

        testRunToken++;
        isTestModeActive = false;
        resetSearchState();

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        if (listener != null) {
            listener.onLog("[МАЙЕР ТЕСТ] " + summary);
            listener.onMayerTestConvergenceSummary(summary);
            listener.onTestModeChanged(false);
            listener.onMayerTestCompleted(calculatedFmHz);
        }
    }

    private String formatEvaluationShort(PointEvaluation eval) {
        if (eval == null) return "нет данных";
        return String.format(
                Locale.US,
                "LF/HF=%.2f, A=%.2f, HR=%.1f(Δ%+.1f), Q=%.3f",
                eval.lfHf,
                eval.pulseAmplitudeBpm,
                eval.hrMeanBpm,
                eval.hrDropBpm,
                eval.score
        );
    }

    private String buildPartialSummary() {
        if (eval10Sec == null) {
            return "окно 10с еще не завершено";
        }
        return "10с: " + formatEvaluationShort(eval10Sec);
    }

    private void showTestAlert() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"));
            builder.setMessage("\nРежим тестирования, автоподстройка фазы отключена до конца текущего дампа.");
            builder.setPositiveButton("ОК", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }
}
