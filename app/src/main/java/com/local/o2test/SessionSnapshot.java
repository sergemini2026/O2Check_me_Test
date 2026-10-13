package com.local.o2test;

public class SessionSnapshot {

    private final long timestamp;
    private final HrvCalculator.Metrics metrics;

    public SessionSnapshot(long timestamp, HrvCalculator.Metrics metrics) {
        this.timestamp = timestamp;
        this.metrics = metrics;
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
     * Проверка, прошел ли данный снимок проверку Sanity Check
     */
    public boolean isValid() {
        return metrics != null && metrics.isValid;
    }
}
