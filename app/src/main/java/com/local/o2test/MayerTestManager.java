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

    private static final int REQUIRED_RR_COUNT = 10;
    private static final float STAGE1_LOW_SEC = 8.0f;
    private static final float STAGE1_MID_SEC = 10.0f;
    private static final float STAGE1_HIGH_SEC = 12.0f;
    private static final float STAGE2_STEP_SEC = 0.2f;

    private enum SearchPhase {
        IDLE,
        STAGE1_CHECK_LOW,
        STAGE1_CHECK_HIGH,
        STAGE1_CHECK_BASE_TEN,
        STAGE1_CHECK_MIDPOINT,
        STAGE2_FINE_SCAN
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

    private float intervalStartSec = 9.0f;
    private float intervalEndSec = 10.0f;
    private final List<Float> stage2Candidates = new ArrayList<>();
    private int stage2Index = 0;

    private float currentCandidatePeriodSec = 8.0f;
    private float bestPeriodSec = 10.0f;
    private double bestLfHfValue = -1.0;

    public interface MayerTestListener {
        void onTestModeChanged(boolean isActive);
        void onLog(String message);
        default void onTestPeriodChanged(float periodSec) {}
        default void onMayerTestCompleted(float calculatedFmHz) {}
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
        intervalStartSec = 9.0f;
        intervalEndSec = 10.0f;
        stage2Candidates.clear();
        stage2Index = 0;
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

        if (measuredLfHf > bestLfHfValue) {
            bestLfHfValue = measuredLfHf;
            bestPeriodSec = currentCandidatePeriodSec;
        }

        if (listener != null) {
            listener.onLog(String.format(
                    Locale.US,
                    "[МАЙЕР ТЕСТ] Точка T=%.1f с (%.2f Hz) | LF/HF=%.3f",
                    currentCandidatePeriodSec,
                    1.0f / currentCandidatePeriodSec,
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
                if (Math.abs(initialPeriodSec - STAGE1_MID_SEC) > 0.05f) {
                    searchPhase = SearchPhase.STAGE1_CHECK_BASE_TEN;
                    currentCandidatePeriodSec = STAGE1_MID_SEC;
                    notifyPeriodAndContinue();
                } else {
                    lfHfAt10 = -1.0f;
                    prepareMidpointFromBounds();
                }
                break;

            case STAGE1_CHECK_BASE_TEN:
                lfHfAt10 = (float) measuredLfHf;
                prepareMidpointFromBounds();
                break;

            case STAGE1_CHECK_MIDPOINT:
                lfHfAtMid = (float) measuredLfHf;
                selectOneSecondIntervalAndStartStage2();
                break;

            case STAGE2_FINE_SCAN:
                stage2Index++;
                if (stage2Index < stage2Candidates.size()) {
                    currentCandidatePeriodSec = stage2Candidates.get(stage2Index);
                    notifyPeriodAndContinue();
                } else {
                    completeTestWithBestPeriod();
                }
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void prepareMidpointFromBounds() {
        boolean pickHighRange;
        if (lfHfAt10 >= 0.0f) {
            float lowAnchor = Math.max(lfHfAt8, lfHfAt10);
            float highAnchor = Math.max(lfHfAt12, lfHfAt10);
            pickHighRange = highAnchor > lowAnchor;
        } else {
            pickHighRange = lfHfAt12 > lfHfAt8;
        }

        searchPhase = SearchPhase.STAGE1_CHECK_MIDPOINT;
        if (pickHighRange) {
            midPointSec = 11.0f;
        } else {
            midPointSec = 9.0f;
        }
        currentCandidatePeriodSec = midPointSec;
        notifyPeriodAndContinue();
    }

    private void selectOneSecondIntervalAndStartStage2() {
        if (midPointSec < 10.0f) {
            // Диапазон [8.0, 10.0], средняя точка 9.0: сравниваем [8.0..9.0] и [9.0..10.0]
            float leftScore = Math.max(lfHfAt8, lfHfAtMid);
            float rightScore = lfHfAt10 >= 0.0f ? Math.max(lfHfAtMid, lfHfAt10) : lfHfAtMid;
            if (rightScore > leftScore) {
                intervalStartSec = 9.0f;
                intervalEndSec = 10.0f;
            } else {
                intervalStartSec = 8.0f;
                intervalEndSec = 9.0f;
            }
        } else {
            // Диапазон [10.0, 12.0], средняя точка 11.0: сравниваем [10.0..11.0] и [11.0..12.0]
            float leftScore = lfHfAt10 >= 0.0f ? Math.max(lfHfAt10, lfHfAtMid) : lfHfAtMid;
            float rightScore = Math.max(lfHfAtMid, lfHfAt12);
            if (rightScore > leftScore) {
                intervalStartSec = 11.0f;
                intervalEndSec = 12.0f;
            } else {
                intervalStartSec = 10.0f;
                intervalEndSec = 11.0f;
            }
        }

        stage2Candidates.clear();
        int steps = Math.round((intervalEndSec - intervalStartSec) / STAGE2_STEP_SEC);
        for (int i = 0; i <= steps; i++) {
            float candidate = Math.round((intervalStartSec + i * STAGE2_STEP_SEC) * 10.0f) / 10.0f;
            if (Math.abs(candidate - currentCandidatePeriodSec) > 0.01f) {
                stage2Candidates.add(candidate);
            }
        }

        searchPhase = SearchPhase.STAGE2_FINE_SCAN;
        stage2Index = 0;

        if (!stage2Candidates.isEmpty()) {
            currentCandidatePeriodSec = stage2Candidates.get(0);
            notifyPeriodAndContinue();
        } else {
            completeTestWithBestPeriod();
        }
    }

    private void notifyPeriodAndContinue() {
        captureStepBaseline();
        if (listener != null) {
            listener.onTestPeriodChanged(currentCandidatePeriodSec);
        }
        evaluateNextValidRrWindow();
    }

    private void completeTestWithBestPeriod() {
        float finalPeriod = bestPeriodSec > 0f ? bestPeriodSec : initialPeriodSec;
        float calculatedFmHz = 1.0f / finalPeriod;

        isTestModeActive = false;
        resetSearchState();

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        if (listener != null) {
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
