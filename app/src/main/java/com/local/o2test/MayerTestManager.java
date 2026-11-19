package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MayerTestManager {

    private static final int REQUIRED_RR_COUNT = 50;
    private static final float STAGE1_LOW_SEC = 8.0f;
    private static final float STAGE1_MID_SEC = 10.0f;
    private static final float STAGE1_HIGH_SEC = 12.0f;
    private static final float HALF_SECOND_STEP_SEC = 0.5f;

    private enum SearchPhase {
        IDLE,
        STAGE1_CHECK_LOW,
        STAGE1_CHECK_HIGH,
        STAGE1_CHECK_BASE_TEN,
        STAGE1_CHECK_MIDPOINT,
        STAGE2_CHECK_HALF_SEC
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

    private final Context context;
    private final UiBuilder.TestButtonContainer btnTest;
    private final MayerTestListener listener;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isAvailable = false;
    private boolean isTestModeActive = false;

    private long sessionStartTimeMs = 0L;
    private float initialPeriodSec = 10.0f;
    private SearchPhase searchPhase = SearchPhase.IDLE;
    private int stepStartValidCount = 0;

    private float lfHfAt8 = -1.0f;
    private float lfHfAt10 = -1.0f;
    private float lfHfAt12 = -1.0f;
    private float midPointSec = 9.0f;
    private float lfHfAtMid = -1.0f;
    private float halfSecPointSec = 9.5f;
    private float lfHfAtHalfSec = -1.0f;

    private final List<MeasuredPoint> measuredSeries = new ArrayList<>();

    private float currentCandidatePeriodSec = 8.0f;
    private float bestPeriodSec = 10.0f;
    private double bestLfHfValue = -1.0;

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
        this.initialPeriodSec = baselinePeriodSec > 0f ? baselinePeriodSec : 10.0f;
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

        isTestModeActive = true;
        resetSearchState();
        searchPhase = SearchPhase.STAGE1_CHECK_LOW;
        currentCandidatePeriodSec = STAGE1_LOW_SEC;
        bestPeriodSec = currentCandidatePeriodSec;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
            // Блокируем контейнер на время активного теста, чтобы исключить повторные клики
            btnTest.setEnabled(false);
            btnTest.setAlpha(1.0f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования активирован: автоподстройка фазы отключена до конца текущего дампа.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(true);
            listener.onTestPeriodChanged(currentCandidatePeriodSec);
        }

        captureStepBaseline();
        showTestAlert();
        evaluateNextValidRrWindow();
    }

    /**
     * Сброс режима и возврат кнопки в штатный вид
     */
    public void resetTestMode() {
        if (!isTestModeActive) return;

        isTestModeActive = false;
        resetSearchState();

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования завершен. Возврат к штатной автоподстройке фазы.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(false);
        }
    }

    private void resetSearchState() {
        searchPhase = SearchPhase.IDLE;
        stepStartValidCount = 0;
        lfHfAt8 = -1.0f;
        lfHfAt10 = -1.0f;
        lfHfAt12 = -1.0f;
        midPointSec = 9.0f;
        lfHfAtMid = -1.0f;
        halfSecPointSec = 9.5f;
        lfHfAtHalfSec = -1.0f;
        measuredSeries.clear();
        bestLfHfValue = -1.0;
    }

    private void captureStepBaseline() {
        List<Integer> currentValid = (listener != null) ? listener.getValidRrIntervalsForTest() : null;
        stepStartValidCount = (currentValid != null) ? currentValid.size() : 0;
    }

    private void evaluateNextValidRrWindow() {
        if (!isTestModeActive || searchPhase == SearchPhase.IDLE) return;

        queryExecutor.execute(() -> {
            if (!isTestModeActive) return;

            List<Integer> validRrList = (listener != null) ? listener.getValidRrIntervalsForTest() : null;
            if (validRrList == null) {
                mainHandler.postDelayed(this::evaluateNextValidRrWindow, 500L);
                return;
            }

            int totalValid = validRrList.size();
            int newValidCount = totalValid - stepStartValidCount;
            if (newValidCount < REQUIRED_RR_COUNT) {
                mainHandler.postDelayed(this::evaluateNextValidRrWindow, 500L);
                return;
            }

            int fromIdx = Math.max(0, totalValid - REQUIRED_RR_COUNT);
            List<Integer> rrWindow = new ArrayList<>(validRrList.subList(fromIdx, totalValid));

            double lfHf = HrvCalculator.calculateStandardLfHf(rrWindow);
            if (Double.isNaN(lfHf) || Double.isInfinite(lfHf) || lfHf < 0.0) {
                lfHf = 0.0;
            }

            final double measuredLfHf = lfHf;
            mainHandler.post(() -> advanceSearchWithMeasurement(measuredLfHf));
        });
    }

    private void advanceSearchWithMeasurement(double measuredLfHf) {
        if (!isTestModeActive) return;

        float freqHz = 1.0f / currentCandidatePeriodSec;
        measuredSeries.add(new MeasuredPoint(currentCandidatePeriodSec, freqHz, (float) measuredLfHf));

        if (measuredLfHf > bestLfHfValue) {
            bestLfHfValue = measuredLfHf;
            bestPeriodSec = currentCandidatePeriodSec;
        }

        if (listener != null) {
            listener.onLog(String.format(
                    Locale.US,
                    "[МАЙЕР ТЕСТ] Точка T=%.1f с (%.2f Hz) | LF/HF=%.3f",
                    currentCandidatePeriodSec,
                    freqHz,
                    measuredLfHf
            ));
        }

        switch (searchPhase) {
            case STAGE1_CHECK_LOW:
                lfHfAt8 = (float) measuredLfHf;
                searchPhase = SearchPhase.STAGE1_CHECK_HIGH;
                currentCandidatePeriodSec = STAGE1_HIGH_SEC;
                notifyPeriodAndContinue();
                break;

            case STAGE1_CHECK_HIGH:
                lfHfAt12 = (float) measuredLfHf;
                if (lfHfAt10 < 0.0f) {
                    searchPhase = SearchPhase.STAGE1_CHECK_BASE_TEN;
                    currentCandidatePeriodSec = STAGE1_MID_SEC;
                    notifyPeriodAndContinue();
                } else {
                    prepareMidpointFromBounds();
                }
                break;

            case STAGE1_CHECK_BASE_TEN:
                lfHfAt10 = (float) measuredLfHf;
                prepareMidpointFromBounds();
                break;

            case STAGE1_CHECK_MIDPOINT:
                lfHfAtMid = (float) measuredLfHf;
                selectHalfSecondPointAndStartStage2();
                break;

            case STAGE2_CHECK_HALF_SEC:
                lfHfAtHalfSec = (float) measuredLfHf;
                evaluateConvergenceAndComplete();
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void prepareMidpointFromBounds() {
        float lowRangeScore = Math.max(lfHfAt8, lfHfAt10);
        float highRangeScore = Math.max(lfHfAt12, lfHfAt10);
        boolean pickHighRange = highRangeScore > lowRangeScore;

        searchPhase = SearchPhase.STAGE1_CHECK_MIDPOINT;
        midPointSec = pickHighRange ? 11.0f : 9.0f;
        currentCandidatePeriodSec = midPointSec;
        notifyPeriodAndContinue();
    }

    private void selectHalfSecondPointAndStartStage2() {
        if (midPointSec < 10.0f) {
            // Выбран поддиапазон [8.0..10.0], измерена средняя 9.0: сравниваем [8.0..9.0] и [9.0..10.0]
            float leftScore = Math.max(lfHfAt8, lfHfAtMid);
            float rightScore = Math.max(lfHfAtMid, lfHfAt10);
            halfSecPointSec = (rightScore > leftScore) ? 9.5f : 8.5f;
        } else {
            // Выбран поддиапазон [10.0..12.0], измерена средняя 11.0: сравниваем [10.0..11.0] и [11.0..12.0]
            float leftScore = Math.max(lfHfAt10, lfHfAtMid);
            float rightScore = Math.max(lfHfAtMid, lfHfAt12);
            halfSecPointSec = (rightScore > leftScore) ? 11.5f : 10.5f;
        }

        // Гарантируем шаг 0.5 с в выбранном односекундном интервале
        halfSecPointSec = Math.round(halfSecPointSec / HALF_SECOND_STEP_SEC) * HALF_SECOND_STEP_SEC;

        searchPhase = SearchPhase.STAGE2_CHECK_HALF_SEC;
        currentCandidatePeriodSec = halfSecPointSec;
        notifyPeriodAndContinue();
    }

    private void notifyPeriodAndContinue() {
        captureStepBaseline();
        if (listener != null) {
            listener.onTestPeriodChanged(currentCandidatePeriodSec);
        }
        evaluateNextValidRrWindow();
    }

    private void evaluateConvergenceAndComplete() {
        MeasuredPoint maxPoint = null;
        double sumLfHf = 0.0;
        for (MeasuredPoint pt : measuredSeries) {
            sumLfHf += pt.lfHf;
            if (maxPoint == null || pt.lfHf > maxPoint.lfHf) {
                maxPoint = pt;
            }
        }

        double meanLfHf = !measuredSeries.isEmpty() ? (sumLfHf / measuredSeries.size()) : 0.0;
        float maxLfHf = maxPoint != null ? maxPoint.lfHf : 0.0f;

        boolean hasPositivePeak = maxPoint != null && maxLfHf > 0.0f;
        boolean hasProminence = hasPositivePeak && (meanLfHf <= 1e-6 || maxLfHf >= meanLfHf * 1.25);
        float refinedMax = Math.max(Math.max(0.0f, lfHfAtMid), Math.max(0.0f, lfHfAtHalfSec));
        boolean hasRefinedSupport = hasPositivePeak
                && (refinedMax >= meanLfHf * 1.05
                || (maxPoint != null && Math.abs(maxPoint.periodSec - halfSecPointSec) <= 1.0f));

        float selectedPeriod;
        String summary;

        if (hasPositivePeak && hasProminence && hasRefinedSupport) {
            selectedPeriod = maxPoint.periodSec;
            float selectedFreq = 1.0f / selectedPeriod;
            summary = String.format(
                    Locale.US,
                    "Сходимость подтверждена: оптимум T=%.1f с (%.2f Hz), max LF/HF=%.3f при среднем %.3f.",
                    selectedPeriod,
                    selectedFreq,
                    maxLfHf,
                    meanLfHf
            );
        } else {
            if (initialPeriodSec > 0f) {
                selectedPeriod = initialPeriodSec;
            } else if (hasPositivePeak) {
                selectedPeriod = maxPoint.periodSec;
            } else {
                selectedPeriod = STAGE1_MID_SEC;
            }
            float selectedFreq = 1.0f / selectedPeriod;
            summary = String.format(
                    Locale.US,
                    "Сходимость не подтверждена (ряд плоский/неоднородный, max LF/HF=%.3f при среднем %.3f): применен базовый период T=%.1f с (%.2f Hz).",
                    maxLfHf,
                    meanLfHf,
                    selectedPeriod,
                    selectedFreq
            );
        }

        float calculatedFmHz = 1.0f / selectedPeriod;

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
