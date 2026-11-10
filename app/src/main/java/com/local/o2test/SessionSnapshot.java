package com.local.o2test;

public class SessionSnapshot {

    private final long timestamp;
    private final HrvCalculator.Metrics metrics;
    private final float modulationSec;
    private final boolean isPstiPhase;
    private final boolean isDumpSnapshot;
    private final boolean isMayerTestMode;
    private final int globalUniqueIndex;
    private final int trainingDumpIndex;
    private final int testDumpIndex;
    private final int blockNumber;

    public SessionSnapshot(
            long timestamp,
            HrvCalculator.Metrics metrics,
            float modulationSec,
            boolean isPstiPhase,
            boolean isDumpSnapshot,
            boolean isMayerTestMode,
            int globalUniqueIndex,
            int trainingDumpIndex,
            int testDumpIndex,
            int blockNumber
    ) {
        this.timestamp = timestamp;
        this.metrics = metrics;
        this.modulationSec = modulationSec;
        this.isPstiPhase = isPstiPhase;
        this.isDumpSnapshot = isDumpSnapshot;
        this.isMayerTestMode = isMayerTestMode;
        this.globalUniqueIndex = globalUniqueIndex;
        this.trainingDumpIndex = trainingDumpIndex;
        this.testDumpIndex = testDumpIndex;
        this.blockNumber = blockNumber;
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
     * Признак итогового снимка по завершении 300-ударного дампа
     */
    public boolean isDumpSnapshot() {
        return isDumpSnapshot;
    }

    /**
     * Признак работы в режиме тестирования частоты Майера (открытый контур / автоподстройка отключена)
     */
    public boolean isMayerTestMode() {
        return isMayerTestMode;
    }

    /**
     * Сквозной глобальный уникальный номер дампа сессии
     */
    public int getGlobalUniqueIndex() {
        return globalUniqueIndex;
    }

    /**
     * Порядковый номер тренировочного макро-дампа
     */
    public int getTrainingDumpIndex() {
        return trainingDumpIndex;
    }

    /**
     * Порядковый номер макро-дампа теста PSTI
     */
    public int getTestDumpIndex() {
        return testDumpIndex;
    }

    /**
     * Порядковый номер блока внутри тренировочного цикла
     */
    public int getBlockNumber() {
        return blockNumber;
    }

    /**
     * Проверка, прошел ли данный снимок проверку Sanity Check
     */
    public boolean isValid() {
        return metrics != null && metrics.isValid;
    }
}
