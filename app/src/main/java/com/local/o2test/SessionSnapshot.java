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

    // Строгий конструктор без устаревшего globalUniqueIndex
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
     * Проверка, прошел ли данный снимок проверку Sanity Check
     */
    public boolean isValid() {
        return metrics != null && metrics.isValid;
    }
}
