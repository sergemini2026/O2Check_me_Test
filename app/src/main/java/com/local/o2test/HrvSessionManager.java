package com.local.o2test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HrvSessionManager {

    private static final int RING_BUFFER_SIZE = 300;
    private final LinkedList<Integer> ringBuffer = new LinkedList<>();
    private final List<Integer> rrSessionList = new ArrayList<>();

    // Физиологические границы для RR-интервалов (мс): от 300 до 2000 (30 - 200 уд/мин)
    private static final int MIN_RR = 300;
    private static final int MAX_RR = 2000;

    /**
     * Сброс данных при старте нового сеанса
     */
    public synchronized void resetSession() {
        ringBuffer.clear();
        rrSessionList.clear();
    }

    /**
     * Добавление нового RR-интервала в диспетчер
     */
    public synchronized void addRrInterval(int rrMs) {
        // Первичный фильтр физиологической адекватности
        if (rrMs < MIN_RR || rrMs > MAX_RR) {
            return;
        }

        // 1. Уровень кольцевого буфера (фиксированное окно для реалтайм-визуализации)
        if (ringBuffer.size() >= RING_BUFFER_SIZE) {
            ringBuffer.poll();
        }
        ringBuffer.add(rrMs);

        // 2. Уровень накопительного массива всей сессии
        rrSessionList.add(rrMs);
    }

    /**
     * Получение среза данных для плавной отрисовки в реальном времени
     */
    public synchronized List<Integer> getRingBuffer() {
        return new ArrayList<>(ringBuffer);
    }

    /**
     * Получение сырого массива всей сессии
     */
    public synchronized List<Integer> getRawSessionData() {
        return new ArrayList<>(rrSessionList);
    }

    /**
     * Продвинутая фильтрация сессионного массива для устранения артефактов и выбросов 
     * перед расчетом глубоких метрик (Пуанкаре, Баевский, Спектр).
     */
    public synchronized List<Integer> getFilteredSessionData() {
        List<Integer> raw = new ArrayList<>(rrSessionList);
        if (raw.size() < 5) {
            return raw;
        }

        List<Integer> filtered = new ArrayList<>();
        
        // Алгоритм локальной фильтрации на основе отклонения от локальной медианы соседей
        for (int i = 0; i < raw.size(); i++) {
            int current = raw.get(i);
            boolean isArtifact = false;

            if (i > 0 && i < raw.size() - 1) {
                int prev = raw.get(i - 1);
                int next = raw.get(i + 1);
                float localMedian = (prev + next) / 2f;
                
                // Если текущее значение отклоняется от локальной медианы более чем на 25% — считаем артефактом
                if (Math.abs(current - localMedian) / localMedian > 0.25f) {
                    isArtifact = true;
                }
            }

            if (!isArtifact) {
                filtered.add(current);
            }
        }
        return filtered;
    }

    public synchronized int getSessionSize() {
        return rrSessionList.size();
    }
}
