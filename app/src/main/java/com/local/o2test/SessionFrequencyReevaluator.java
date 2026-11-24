package com.local.o2test;

import com.local.o2test.db.entity.HrvSnapshotEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SessionFrequencyReevaluator {

    private static final double LOW_LF_HF_THRESHOLD = 6.0;
    private static final double HIGH_LF_HF_THRESHOLD = 12.0;
    private static final double HR_THRESHOLD_FOR_12S = 70.0;
    private static final double INERTIA_DROP_RATIO = 0.75;
    private static final double INERTIA_HIGH_RATIO = 0.80;

    public enum SegmentRole {
        INERTIA_TAIL,
        STEADY_SLICE
    }

    public static class AnnotatedSlice {
        public final HrvSnapshotEntity snapshot;
        public final int index;
        public final int segmentIndex;
        public final float periodSec;
        public final double lfHf;
        public final double avgHr;
        public final double pulseAmplitudeMs;
        public final SegmentRole role;

        public AnnotatedSlice(
                HrvSnapshotEntity snapshot,
                int index,
                int segmentIndex,
                float periodSec,
                double lfHf,
                double avgHr,
                double pulseAmplitudeMs,
                SegmentRole role
        ) {
            this.snapshot = snapshot;
            this.index = index;
            this.segmentIndex = segmentIndex;
            this.periodSec = periodSec;
            this.lfHf = lfHf;
            this.avgHr = avgHr;
            this.pulseAmplitudeMs = pulseAmplitudeMs;
            this.role = role;
        }
    }

    public static class PeriodSegmentSummary {
        public final int segmentIndex;
        public final float periodSec;
        public final int totalSlices;
        public final int inertiaTailCount;
        public final int steadySliceCount;
        public final double steadyAvgLfHf;
        public final double steadyMedianLfHf;
        public final double intraTrendDelta;
        public final double lowLfHfSharePct;
        public final double highLfHfSharePct;
        public final double avgHr;
        public final double pulseAmplitudeMs;
        public final float prevPeriodSec;
        public final float nextPeriodSec;
        public final double transitionContrastDelta;

        public PeriodSegmentSummary(
                int segmentIndex,
                float periodSec,
                int totalSlices,
                int inertiaTailCount,
                int steadySliceCount,
                double steadyAvgLfHf,
                double steadyMedianLfHf,
                double intraTrendDelta,
                double lowLfHfSharePct,
                double highLfHfSharePct,
                double avgHr,
                double pulseAmplitudeMs,
                float prevPeriodSec,
                float nextPeriodSec,
                double transitionContrastDelta
        ) {
            this.segmentIndex = segmentIndex;
            this.periodSec = periodSec;
            this.totalSlices = totalSlices;
            this.inertiaTailCount = inertiaTailCount;
            this.steadySliceCount = steadySliceCount;
            this.steadyAvgLfHf = steadyAvgLfHf;
            this.steadyMedianLfHf = steadyMedianLfHf;
            this.intraTrendDelta = intraTrendDelta;
            this.lowLfHfSharePct = lowLfHfSharePct;
            this.highLfHfSharePct = highLfHfSharePct;
            this.avgHr = avgHr;
            this.pulseAmplitudeMs = pulseAmplitudeMs;
            this.prevPeriodSec = prevPeriodSec;
            this.nextPeriodSec = nextPeriodSec;
            this.transitionContrastDelta = transitionContrastDelta;
        }
    }

    public static class PeriodAggregateSummary {
        public final float periodSec;
        public final float frequencyHz;
        public final int sliceCount;
        public final int steadySliceCount;
        public final double steadyLfHf;
        public final double netTrend;
        public final double lowSharePct;
        public final double highSharePct;
        public final double avgHr;
        public final double avgPulseAmplitudeMs;
        public final double switchContrastScore;
        public final double totalScore;

        public PeriodAggregateSummary(
                float periodSec,
                float frequencyHz,
                int sliceCount,
                int steadySliceCount,
                double steadyLfHf,
                double netTrend,
                double lowSharePct,
                double highSharePct,
                double avgHr,
                double avgPulseAmplitudeMs,
                double switchContrastScore,
                double totalScore
        ) {
            this.periodSec = periodSec;
            this.frequencyHz = frequencyHz;
            this.sliceCount = sliceCount;
            this.steadySliceCount = steadySliceCount;
            this.steadyLfHf = steadyLfHf;
            this.netTrend = netTrend;
            this.lowSharePct = lowSharePct;
            this.highSharePct = highSharePct;
            this.avgHr = avgHr;
            this.avgPulseAmplitudeMs = avgPulseAmplitudeMs;
            this.switchContrastScore = switchContrastScore;
            this.totalScore = totalScore;
        }
    }

    public static class ReevaluationResult {
        public final float initialTestFrequencyHz;
        public final float initialTestPeriodSec;
        public final float finalFrequencyHz;
        public final float finalPeriodSec;
        public final boolean reevaluatedFromTest;
        public final boolean hasTestBaseline;
        public final String comparisonLabel;
        public final String reasoningSummary;
        public final String shortLogMessage;
        public final List<AnnotatedSlice> annotatedSlices;
        public final List<PeriodSegmentSummary> segments;
        public final Map<Float, PeriodAggregateSummary> periodAggregates;

        public ReevaluationResult(
                float initialTestFrequencyHz,
                float initialTestPeriodSec,
                float finalFrequencyHz,
                float finalPeriodSec,
                boolean reevaluatedFromTest,
                boolean hasTestBaseline,
                String comparisonLabel,
                String reasoningSummary,
                String shortLogMessage,
                List<AnnotatedSlice> annotatedSlices,
                List<PeriodSegmentSummary> segments,
                Map<Float, PeriodAggregateSummary> periodAggregates
        ) {
            this.initialTestFrequencyHz = initialTestFrequencyHz;
            this.initialTestPeriodSec = initialTestPeriodSec;
            this.finalFrequencyHz = finalFrequencyHz;
            this.finalPeriodSec = finalPeriodSec;
            this.reevaluatedFromTest = reevaluatedFromTest;
            this.hasTestBaseline = hasTestBaseline;
            this.comparisonLabel = comparisonLabel;
            this.reasoningSummary = reasoningSummary;
            this.shortLogMessage = shortLogMessage;
            this.annotatedSlices = annotatedSlices;
            this.segments = segments;
            this.periodAggregates = periodAggregates;
        }
    }

    private static class RawSegmentGroup {
        final int segmentIndex;
        final float periodSec;
        final List<HrvSnapshotEntity> items = new ArrayList<>();

        RawSegmentGroup(int segmentIndex, float periodSec) {
            this.segmentIndex = segmentIndex;
            this.periodSec = periodSec;
        }
    }

    public ReevaluationResult evaluate(
            List<HrvSnapshotEntity> snapshots,
            List<DataPoint> trainingPoints,
            List<DataPoint> testPoints,
            float primaryTestFrequencyHz,
            float fallbackFrequencyHz,
            String mayerConvergenceSummary
    ) {
        List<HrvSnapshotEntity> ordered = new ArrayList<>();
        if (snapshots != null) {
            for (HrvSnapshotEntity s : snapshots) {
                if (s != null) {
                    ordered.add(s);
                }
            }
        }
        Collections.sort(ordered, Comparator.comparingLong(HrvSnapshotEntity::getTimestamp));

        List<DataPoint> allPoints = new ArrayList<>();
        if (trainingPoints != null) {
            for (DataPoint dp : trainingPoints) {
                if (dp != null) allPoints.add(dp);
            }
        }
        if (testPoints != null) {
            for (DataPoint dp : testPoints) {
                if (dp != null) allPoints.add(dp);
            }
        }
        Collections.sort(allPoints, Comparator.comparingLong(DataPoint::getTimestamp));

        float safeFallbackFreq = (fallbackFrequencyHz > 0f) ? fallbackFrequencyHz : 0.1f;
        float safeFallbackPeriod = 1.0f / safeFallbackFreq;
        boolean hasTestBaseline = primaryTestFrequencyHz > 0f;
        float testFreqHz = hasTestBaseline ? primaryTestFrequencyHz : safeFallbackFreq;
        float testPeriodSec = 1.0f / testFreqHz;

        if (ordered.isEmpty()) {
            String emptyReason = "Недостаточно срезов хронологии для переоценки; сохранено опорное значение.";
            String emptyLog = String.format(Locale.US,
                    "[ПЕРЕОЦЕНКА ЧАСТОТЫ] Нет данных хронологии | Итог: %.2f Гц (%.1f с)",
                    testFreqHz, testPeriodSec);
            return new ReevaluationResult(
                    testFreqHz,
                    testPeriodSec,
                    testFreqHz,
                    testPeriodSec,
                    false,
                    hasTestBaseline,
                    hasTestBaseline
                            ? String.format(Locale.US, "тест: %.2f Гц (%.1f с) -> по хронологии: %.2f Гц (%.1f с)",
                            testFreqHz, testPeriodSec, testFreqHz, testPeriodSec)
                            : String.format(Locale.US, "по хронологии: %.2f Гц (%.1f с)", testFreqHz, testPeriodSec),
                    emptyReason,
                    emptyLog,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    new LinkedHashMap<>()
            );
        }

        // Шаг 2. Сегментация хронологии на непрерывные участки одного периода
        List<RawSegmentGroup> rawSegments = new ArrayList<>();
        RawSegmentGroup currentGroup = null;
        for (HrvSnapshotEntity snap : ordered) {
            float period = normalizePeriodSec(snap.getModulationSec(), safeFallbackPeriod);
            if (currentGroup == null || Math.abs(currentGroup.periodSec - period) >= 0.25f) {
                currentGroup = new RawSegmentGroup(rawSegments.size(), period);
                rawSegments.add(currentGroup);
            }
            currentGroup.items.add(snap);
        }

        // Шаг 3 и 4. Разметка инерционного следа (INERTIA_TAIL), STEADY_SLICE и расчёт характеристик сегментов
        List<AnnotatedSlice> annotatedSlices = new ArrayList<>(ordered.size());
        List<PeriodSegmentSummary> segmentSummaries = new ArrayList<>(rawSegments.size());
        int inertiaTailTotalCount = 0;

        for (int gIdx = 0; gIdx < rawSegments.size(); gIdx++) {
            RawSegmentGroup group = rawSegments.get(gIdx);
            float prevPeriod = (gIdx > 0) ? rawSegments.get(gIdx - 1).periodSec : -1f;
            float nextPeriod = (gIdx + 1 < rawSegments.size()) ? rawSegments.get(gIdx + 1).periodSec : -1f;

            double prevLastLfHf = 0.0;
            int prevGroupSize = 0;
            if (gIdx > 0) {
                RawSegmentGroup prevGroup = rawSegments.get(gIdx - 1);
                prevGroupSize = prevGroup.items.size();
                HrvSnapshotEntity lastPrevSnap = prevGroup.items.get(prevGroup.items.size() - 1);
                prevLastLfHf = extractLfHf(lastPrevSnap);
            }

            boolean switchedPeriod = (gIdx > 0 && Math.abs(group.periodSec - prevPeriod) >= 0.25f);
            boolean afterLongDump = (prevGroupSize >= 3);

            int n = group.items.size();
            double[] lfHfArr = new double[n];
            double[] hrArr = new double[n];
            double[] ampArr = new double[n];

            for (int i = 0; i < n; i++) {
                HrvSnapshotEntity s = group.items.get(i);
                lfHfArr[i] = extractLfHf(s);

                long windowStart = (i == 0)
                        ? ((gIdx > 0) ? rawSegments.get(gIdx - 1).items.get(prevGroupSize - 1).getTimestamp() : s.getTimestamp() - 15000L)
                        : group.items.get(i - 1).getTimestamp();
                long windowEnd = s.getTimestamp();
                if (windowEnd <= windowStart) {
                    windowStart = windowEnd - 15000L;
                }

                hrArr[i] = extractWindowHr(allPoints, windowStart, windowEnd, s);
                ampArr[i] = extractWindowPulseAmplitudeMs(allPoints, windowStart, windowEnd, s);
            }

            // Определяем число срезов инерционного хвоста в начале сегмента
            int tailCount = 0;
            if (switchedPeriod && n > 1 && prevLastLfHf > LOW_LF_HF_THRESHOLD) {
                double firstLfHf = lfHfArr[0];
                boolean startsHigh = firstLfHf >= prevLastLfHf * INERTIA_HIGH_RATIO;
                if (startsHigh || afterLongDump) {
                    int maxTail = afterLongDump ? Math.min(n - 1, 3) : Math.min(n - 1, 2);
                    for (int i = 0; i < maxTail; i++) {
                        boolean dropsNext = (i + 1 < n) && (lfHfArr[i + 1] <= lfHfArr[i] * INERTIA_DROP_RATIO || lfHfArr[i + 1] < prevLastLfHf * 0.75);
                        if (i == 0 || dropsNext) {
                            tailCount++;
                        } else {
                            break;
                        }
                    }
                }
            }

            List<Double> steadyLfHfList = new ArrayList<>();
            List<Double> steadyHrList = new ArrayList<>();
            List<Double> steadyAmpList = new ArrayList<>();
            int lowCount = 0;
            int highCount = 0;

            for (int i = 0; i < n; i++) {
                SegmentRole role = (i < tailCount) ? SegmentRole.INERTIA_TAIL : SegmentRole.STEADY_SLICE;
                if (role == SegmentRole.INERTIA_TAIL) {
                    inertiaTailTotalCount++;
                }
                annotatedSlices.add(new AnnotatedSlice(
                        group.items.get(i),
                        annotatedSlices.size(),
                        group.segmentIndex,
                        group.periodSec,
                        lfHfArr[i],
                        hrArr[i],
                        ampArr[i],
                        role
                ));

                if (role == SegmentRole.STEADY_SLICE) {
                    steadyLfHfList.add(lfHfArr[i]);
                    steadyHrList.add(hrArr[i]);
                    steadyAmpList.add(ampArr[i]);
                    if (lfHfArr[i] < LOW_LF_HF_THRESHOLD) lowCount++;
                    if (lfHfArr[i] >= HIGH_LF_HF_THRESHOLD) highCount++;
                }
            }

            if (steadyLfHfList.isEmpty()) {
                for (int i = 0; i < n; i++) {
                    steadyLfHfList.add(lfHfArr[i]);
                    steadyHrList.add(hrArr[i]);
                    steadyAmpList.add(ampArr[i]);
                    if (lfHfArr[i] < LOW_LF_HF_THRESHOLD) lowCount++;
                    if (lfHfArr[i] >= HIGH_LF_HF_THRESHOLD) highCount++;
                }
            }

            double steadyAvgLfHf = average(steadyLfHfList);
            double steadyMedianLfHf = median(steadyLfHfList);
            double intraTrendDelta = steadyLfHfList.get(steadyLfHfList.size() - 1) - steadyLfHfList.get(0);
            int denom = Math.max(1, steadyLfHfList.size());
            double lowSharePct = (lowCount * 100.0) / denom;
            double highSharePct = (highCount * 100.0) / denom;
            double segAvgHr = average(steadyHrList);
            double segAvgAmp = average(steadyAmpList);

            // Шаг 5. Контраст при переключениях (10.0 с <-> 12.0 с)
            double switchDelta = 0.0;
            if (switchedPeriod) {
                double firstSteadyLfHf = steadyLfHfList.get(0);
                double lastSteadyLfHf = steadyLfHfList.get(steadyLfHfList.size() - 1);
                if (lastSteadyLfHf > prevLastLfHf && intraTrendDelta >= 0.0) {
                    switchDelta += 2.5;
                } else if (firstSteadyLfHf > prevLastLfHf * 0.90) {
                    switchDelta += 1.2;
                }
                if (intraTrendDelta < -1.0 && lastSteadyLfHf < prevLastLfHf * 0.85) {
                    switchDelta -= 2.5;
                }
            }
            if (nextPeriod > 0f && Math.abs(nextPeriod - group.periodSec) >= 0.25f && gIdx + 1 < rawSegments.size()) {
                double nextFirstLfHf = extractLfHf(rawSegments.get(gIdx + 1).items.get(0));
                if (nextFirstLfHf < steadyAvgLfHf * 0.75) {
                    switchDelta += 0.8;
                } else if (nextFirstLfHf > steadyAvgLfHf * 1.15) {
                    switchDelta -= 0.8;
                }
            }

            segmentSummaries.add(new PeriodSegmentSummary(
                    group.segmentIndex,
                    group.periodSec,
                    n,
                    tailCount,
                    steadyLfHfList.size(),
                    steadyAvgLfHf,
                    steadyMedianLfHf,
                    intraTrendDelta,
                    lowSharePct,
                    highSharePct,
                    segAvgHr,
                    segAvgAmp,
                    prevPeriod,
                    nextPeriod,
                    switchDelta
            ));
        }

        // Агрегация по периодам (включая кандидаты 10.0 с и 12.0 с)
        Map<Float, List<PeriodSegmentSummary>> byPeriod = new LinkedHashMap<>();
        for (PeriodSegmentSummary seg : segmentSummaries) {
            float key = canonicalPeriodKey(seg.periodSec);
            byPeriod.computeIfAbsent(key, k -> new ArrayList<>()).add(seg);
        }
        byPeriod.putIfAbsent(10.0f, new ArrayList<>());
        byPeriod.putIfAbsent(12.0f, new ArrayList<>());

        Map<Float, PeriodAggregateSummary> periodAggregates = new LinkedHashMap<>();
        float bestPeriodSec = canonicalPeriodKey(testPeriodSec);
        double bestScore = -Double.MAX_VALUE;

        for (Map.Entry<Float, List<PeriodSegmentSummary>> entry : byPeriod.entrySet()) {
            float pSec = entry.getKey();
            List<PeriodSegmentSummary> pSegs = entry.getValue();
            float freqHz = 1.0f / pSec;

            if (pSegs.isEmpty()) {
                periodAggregates.put(pSec, new PeriodAggregateSummary(
                        pSec, freqHz, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -999.0
                ));
                continue;
            }

            int totalSliceCount = 0;
            int totalSteadyCount = 0;
            double weightedLfHfSum = 0.0;
            double weightedTrendSum = 0.0;
            double weightedLowSum = 0.0;
            double weightedHighSum = 0.0;
            double weightedHrSum = 0.0;
            double weightedAmpSum = 0.0;
            double contrastSum = 0.0;

            for (PeriodSegmentSummary seg : pSegs) {
                int w = Math.max(1, seg.steadySliceCount);
                totalSliceCount += seg.totalSlices;
                totalSteadyCount += seg.steadySliceCount;
                weightedLfHfSum += seg.steadyAvgLfHf * w;
                weightedTrendSum += seg.intraTrendDelta * w;
                weightedLowSum += seg.lowLfHfSharePct * w;
                weightedHighSum += seg.highLfHfSharePct * w;
                weightedHrSum += seg.avgHr * w;
                weightedAmpSum += seg.pulseAmplitudeMs * w;
                contrastSum += seg.transitionContrastDelta;
            }

            double denom = Math.max(1, totalSteadyCount);
            double aggLfHf = weightedLfHfSum / denom;
            double aggTrend = weightedTrendSum / denom;
            double aggLowPct = weightedLowSum / denom;
            double aggHighPct = weightedHighSum / denom;
            double aggHr = weightedHrSum / denom;
            double aggAmpMs = weightedAmpSum / denom;

            // Нормированный вклад амплитуды волны пульса (A_pulse в мс) и HR (порог <= 70 уд/мин для 12.0 с)
            double ampContribution = Math.min(6.0, aggAmpMs / 25.0);
            double hrBonus = 0.0;
            if (Math.abs(pSec - 12.0f) < 0.25f && aggHr > 0.0) {
                hrBonus = (aggHr <= HR_THRESHOLD_FOR_12S) ? 2.0 : -1.5;
            } else if (Math.abs(pSec - 10.0f) < 0.25f && aggHr > 85.0) {
                hrBonus = -0.8;
            }

            double lowPenalty = aggLowPct * 0.08;
            double highBonus = aggHighPct * 0.05;
            double score = aggLfHf + (aggTrend * 1.4) + highBonus - lowPenalty + contrastSum + ampContribution + hrBonus;

            PeriodAggregateSummary agg = new PeriodAggregateSummary(
                    pSec,
                    freqHz,
                    totalSliceCount,
                    totalSteadyCount,
                    aggLfHf,
                    aggTrend,
                    aggLowPct,
                    aggHighPct,
                    aggHr,
                    aggAmpMs,
                    contrastSum,
                    score
            );
            periodAggregates.put(pSec, agg);

            if (score > bestScore) {
                bestScore = score;
                bestPeriodSec = pSec;
            }
        }

        PeriodAggregateSummary bestAgg = periodAggregates.get(bestPeriodSec);
        float finalPeriodSec = (bestAgg != null && bestAgg.sliceCount > 0) ? bestPeriodSec : canonicalPeriodKey(testPeriodSec);
        float finalFrequencyHz = 1.0f / finalPeriodSec;

        boolean reevaluatedFromTest = hasTestBaseline && Math.abs(finalFrequencyHz - testFreqHz) >= 0.01f;
        String comparisonLabel = hasTestBaseline
                ? String.format(Locale.US, "тест: %.2f Гц (%.1f с) -> по хронологии: %.2f Гц (%.1f с)",
                testFreqHz, testPeriodSec, finalFrequencyHz, finalPeriodSec)
                : String.format(Locale.US, "по хронологии: %.2f Гц (%.1f с)", finalFrequencyHz, finalPeriodSec);

        String reasoningSummary = buildReasoningSummary(
                reevaluatedFromTest,
                hasTestBaseline,
                testFreqHz,
                testPeriodSec,
                finalFrequencyHz,
                finalPeriodSec,
                inertiaTailTotalCount,
                segmentSummaries,
                periodAggregates,
                mayerConvergenceSummary
        );

        String shortLogMessage = reevaluatedFromTest
                ? String.format(Locale.US,
                "[ПЕРЕОЦЕНКА ЧАСТОТЫ] Скорректировано: %.2f Гц (%.1f с) вместо %.2f Гц (%.1f с)",
                finalFrequencyHz, finalPeriodSec, testFreqHz, testPeriodSec)
                : String.format(Locale.US,
                "[ПЕРЕОЦЕНКА ЧАСТОТЫ] Подтверждено по хронологии: %.2f Гц (%.1f с)",
                finalFrequencyHz, finalPeriodSec);

        return new ReevaluationResult(
                testFreqHz,
                testPeriodSec,
                finalFrequencyHz,
                finalPeriodSec,
                reevaluatedFromTest,
                hasTestBaseline,
                comparisonLabel,
                reasoningSummary,
                shortLogMessage,
                annotatedSlices,
                segmentSummaries,
                periodAggregates
        );
    }

    private String buildReasoningSummary(
            boolean reevaluatedFromTest,
            boolean hasTestBaseline,
            float testFreqHz,
            float testPeriodSec,
            float finalFreqHz,
            float finalPeriodSec,
            int inertiaTailCount,
            List<PeriodSegmentSummary> segments,
            Map<Float, PeriodAggregateSummary> aggregates,
            String mayerConvergenceSummary
    ) {
        PeriodAggregateSummary chosen = aggregates.get(canonicalPeriodKey(finalPeriodSec));
        float otherKey = (Math.abs(canonicalPeriodKey(finalPeriodSec) - 12.0f) < 0.25f) ? 10.0f : 12.0f;
        PeriodAggregateSummary other = aggregates.get(otherKey);

        StringBuilder sb = new StringBuilder();
        if (reevaluatedFromTest) {
            sb.append(String.format(Locale.US,
                    "Частота переоценена с %.2f Гц (%.1f с) на %.2f Гц (%.1f с) по устойчивой динамике всей хронологии. ",
                    testFreqHz, testPeriodSec, finalFreqHz, finalPeriodSec));
        } else if (hasTestBaseline) {
            sb.append(String.format(Locale.US,
                    "Частота %.2f Гц (%.1f с) подтверждена анализом всей хронологии сессии. ",
                    finalFreqHz, finalPeriodSec));
        } else {
            sb.append(String.format(Locale.US,
                    "Опорная частота по хронологии сессии определена как %.2f Гц (%.1f с). ",
                    finalFreqHz, finalPeriodSec));
        }

        if (chosen != null && chosen.sliceCount > 0) {
            sb.append(String.format(Locale.US,
                    "Для %.1f с (STEADY): ср. LF/HF=%.2f, тренд=%.2f, просадок <6.0=%.0f%%, ср. HR=%.0f уд/мин, A_pulse=%.1f мс. ",
                    chosen.periodSec, chosen.steadyLfHf, chosen.netTrend, chosen.lowSharePct, chosen.avgHr, chosen.avgPulseAmplitudeMs));
        }

        if (other != null && other.sliceCount > 0) {
            sb.append(String.format(Locale.US,
                    "Для %.1f с: ср. LF/HF=%.2f, тренд=%.2f, просадок <6.0=%.0f%%. ",
                    other.periodSec, other.steadyLfHf, other.netTrend, other.lowSharePct));
        }

        if (inertiaTailCount > 0 || segments.size() > 1) {
            sb.append(String.format(Locale.US,
                    "Учтены %d сегмент(ов), переключения периодов и %d срез(ов) инерционного следа (INERTIA_TAIL). ",
                    segments.size(), inertiaTailCount));
        }

        if (mayerConvergenceSummary != null && !mayerConvergenceSummary.trim().isEmpty()) {
            sb.append("Сходимость теста: ").append(mayerConvergenceSummary.trim()).append(".");
        }

        return sb.toString().trim();
    }

    private static float normalizePeriodSec(float modSec, float fallbackPeriodSec) {
        if (modSec > 2.0f && modSec < 60.0f) {
            return modSec;
        }
        return fallbackPeriodSec;
    }

    private static float canonicalPeriodKey(float periodSec) {
        if (Math.abs(periodSec - 12.0f) <= Math.abs(periodSec - 10.0f)) {
            return 12.0f;
        }
        return 10.0f;
    }

    private static double extractLfHf(HrvSnapshotEntity s) {
        if (s == null) return 0.0;
        double lf = s.getLfPower();
        double hf = s.getHfPower();
        if (lf + hf > 0.0) {
            return (2.0 * lf) / (lf + hf);
        }
        return Math.max(0.0, s.getLfHfRatio());
    }

    private static double extractWindowHr(List<DataPoint> sortedPoints, long startMs, long endMs, HrvSnapshotEntity snap) {
        List<Double> hrs = new ArrayList<>();
        for (DataPoint dp : sortedPoints) {
            if (dp.getTimestamp() >= startMs && dp.getTimestamp() <= endMs) {
                if (dp.hr > 25 && dp.hr < 240) {
                    hrs.add((double) dp.hr);
                } else if (dp.rrMs >= 300 && dp.rrMs <= 2500) {
                    hrs.add(60000.0 / dp.rrMs);
                }
            }
        }
        if (!hrs.isEmpty()) {
            return average(hrs);
        }
        if (snap != null && !sortedPoints.isEmpty()) {
            long targetTs = snap.getTimestamp();
            long bestDiff = Long.MAX_VALUE;
            double nearestHr = 0.0;
            for (DataPoint dp : sortedPoints) {
                long diff = Math.abs(dp.getTimestamp() - targetTs);
                if (diff < bestDiff && diff <= 30000L) {
                    if (dp.hr > 25 && dp.hr < 240) {
                        bestDiff = diff;
                        nearestHr = dp.hr;
                    } else if (dp.rrMs >= 300 && dp.rrMs <= 2500) {
                        bestDiff = diff;
                        nearestHr = 60000.0 / dp.rrMs;
                    }
                }
            }
            if (nearestHr > 0.0) {
                return nearestHr;
            }
        }
        return 0.0;
    }

    /**
     * Оценка амплитуды волны пульса (A_pulse) по размаху/СКО rrMs в окне с fallback на hr.
     */
    private static double extractWindowPulseAmplitudeMs(List<DataPoint> sortedPoints, long startMs, long endMs, HrvSnapshotEntity snap) {
        List<Double> rrs = new ArrayList<>();
        List<Double> hrs = new ArrayList<>();
        for (DataPoint dp : sortedPoints) {
            if (dp.getTimestamp() >= startMs && dp.getTimestamp() <= endMs) {
                if (dp.rrMs >= 300 && dp.rrMs <= 2500) {
                    rrs.add((double) dp.rrMs);
                } else if (dp.hr > 25 && dp.hr < 240) {
                    hrs.add((double) dp.hr);
                }
            }
        }
        if (rrs.size() >= 2) {
            double minRr = Collections.min(rrs);
            double maxRr = Collections.max(rrs);
            return maxRr - minRr;
        }
        if (snap != null && snap.getSdnn() > 0.0) {
            return snap.getSdnn() * 2.0;
        }
        if (hrs.size() >= 2) {
            double minHr = Collections.min(hrs);
            double maxHr = Collections.max(hrs);
            return (maxHr - minHr) * 10.0;
        }
        return 0.0;
    }

    private static double average(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int mid = copy.size() / 2;
        if (copy.size() % 2 == 0) {
            return (copy.get(mid - 1) + copy.get(mid)) / 2.0;
        }
        return copy.get(mid);
    }
}
