package com.local.o2test;

public class SessionSnapshot {

    private final long timestamp;
    private final HrvCalculator.Metrics metrics;
    private final float modulationSec;

    public SessionSnapshot(long timestamp, HrvCalculator.Metrics metrics) {
        this.timestamp = timestamp;
        this.metrics = metrics;
        this.modulationSec = 10.0f; // Значение по умолчанию
    }

    public SessionSnapshot(long timestamp, HrvCalculator.Metrics metrics, float modulationSec) {
        this.timestamp = timestamp;
        this.metrics = metrics;
        this.modulationSec = modulationSec;
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
     * Проверка, прошел ли данный снимок проверку Sanity Check
     */
    public boolean isValid() {
        return metrics != null && metrics.isValid;
    }
}
