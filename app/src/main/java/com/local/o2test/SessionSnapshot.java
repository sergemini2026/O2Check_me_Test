package com.local.o2test;

public class SessionSnapshot {

    private final long timestamp;
    private final HrvCalculator.Metrics metrics;
    private final float modulationSec;
    private final boolean isPstiPhase;
    private final boolean isMacroDump;
    private final boolean isMayerTestMode;
    private final int sliceIndex;
    private final int dumpIndex;
    private final int blockNumber;
    private final float phim;
    private final float fmHz;
    private final String breathRatio;

    // Строгий конструктор без устаревшего globalUniqueIndex (опорное значение FM по умолчанию 0.1 Гц, режим "1/1")
    public SessionSnapshot(
            long timestamp,
            HrvCalculator.Metrics metrics,
            float modulationSec,
            boolean isPstiPhase,
            boolean isMacroDump,
            boolean isMayerTestMode,
            int sliceIndex,
            int dumpIndex,
            int blockNumber,
            float phim
    ) {
        this(
                timestamp,
                metrics,
                modulationSec,
                isPstiPhase,
                isMacroDump,
                isMayerTestMode,
                sliceIndex,
                dumpIndex,
                blockNumber,
                phim,
                0.1f,
                "1/1"
        );
    }

    // Расширенный конструктор с фиксацией актуального действующего значения частоты Майера (FM, Гц)
    public SessionSnapshot(
            long timestamp,
            HrvCalculator.Metrics metrics,
            float modulationSec,
            boolean isPstiPhase,
            boolean isMacroDump,
            boolean isMayerTestMode,
            int sliceIndex,
            int dumpIndex,
            int blockNumber,
            float phim,
            float fmHz
    ) {
        this(
                timestamp,
                metrics,
                modulationSec,
                isPstiPhase,
                isMacroDump,
                isMayerTestMode,
                sliceIndex,
                dumpIndex,
                blockNumber,
                phim,
                fmHz,
                "1/1"
        );
    }

    // Полный конструктор с фиксацией соотношения фаз дыхательного цикла ("1/1", "1/2" или "--" для PSTI)
    public SessionSnapshot(
            long timestamp,
            HrvCalculator.Metrics metrics,
            float modulationSec,
            boolean isPstiPhase,
            boolean isMacroDump,
            boolean isMayerTestMode,
            int sliceIndex,
            int dumpIndex,
            int blockNumber,
            float phim,
            float fmHz,
            String breathRatio
    ) {
        this.timestamp = timestamp;
        this.metrics = metrics;
        this.modulationSec = modulationSec;
        this.isPstiPhase = isPstiPhase;
        this.isMacroDump = isMacroDump;
        this.isMayerTestMode = isMayerTestMode;
        this.sliceIndex = sliceIndex;
        this.dumpIndex = dumpIndex;
        this.blockNumber = blockNumber;
        this.phim = phim;
        this.fmHz = fmHz;
        this.breathRatio = normalizeBreathRatio(breathRatio, isPstiPhase, isMayerTestMode);
    }

    private static String normalizeBreathRatio(
            String rawRatio,
            boolean isPstiPhase,
            boolean isMayerTestMode
    ) {
        if (isPstiPhase) {
            return "--";
        }
        if (isMayerTestMode) {
            return "1/1";
        }
        if (rawRatio == null) {
            return "1/1";
        }
        String trimmed = rawRatio.trim();
        return "1/2".equals(trimmed) ? "1/2" : "1/1";
    }

    /**
     * Время создания снимка (миллисекунды от начала эпохи)
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Набор рассчитанных и валидированных HRV метрик
     */
    public HrvCalculator.Metrics getMetrics() {
        return metrics;
    }

    /**
     * Получение текущего периода модуляции в секундах для данного снимка
     */
    public float getModulationSec() {
        return modulationSec;
    }

    /**
     * Признак того, что снимок относится к фазе теста PSTI
     */
    public boolean isPstiPhase() {
        return isPstiPhase;
    }

    /**
     * Признак макро-дампа (false — микро-срез, true — макро-дамп)
     */
    public boolean isMacroDump() {
        return isMacroDump;
    }

    /**
     * Признак работы в режиме тестирования частоты Майера (открытый контур / автоподстройка отключена)
     */
    public boolean isMayerTestMode() {
        return isMayerTestMode;
    }

    /**
     * Сквозной порядковый номер микро-среза
     */
    public int getSliceIndex() {
        return sliceIndex;
    }

    /**
     * Унифицированный порядковый номер макро-дампа
     */
    public int getDumpIndex() {
        return dumpIndex;
    }

    /**
     * Порядковый номер блока внутри тренировочного цикла
     */
    public int getBlockNumber() {
        return blockNumber;
    }

    /**
     * Мгновенное значение фазы Майера (PhM)
     */
    public float getPhim() {
        return phim;
    }

    /**
     * Актуальное действующее/вычисленное значение частоты Майера (FM, Гц) на момент создания снимка
     */
    public float getFmHz() {
        return fmHz;
    }

    /**
     * Соотношение длительностей фаз дыхательного цикла ("1/1", "1/2" или "--" для фазы PSTI)
     */
    public String getBreathRatio() {
        return breathRatio;
    }

    /**
     * Проверка, прошел ли данный снимок проверку Sanity Check
     */
    public boolean isValid() {
        return metrics != null && metrics.isValid;
    }
}
