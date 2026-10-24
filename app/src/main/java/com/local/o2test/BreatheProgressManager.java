package com.local.o2test;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    private final List<BreatheAnimationView.PhaseSegment> persistentPhaseStack = new ArrayList<>();
    
    private boolean isActive = false;
    private boolean isTestPhase = false; // false = тренировка (оранжевый, 15 мин), true = тест PSTI (белый, 5 мин)
    
    private float currentDegrees = 360.0f;
    private long lastFrameTime = 0;
    
    // Длительности в секундах
    private static final float DURATION_TRAIN_SEC = 15.0f * 60.0f; // 15 минут
    private static final float DURATION_TEST_SEC = 5.0f * 60.0f;   // 5 минут

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable animationRunnable;

    public BreatheProgressManager(BreatheAnimationView pacerView) {
        this.pacerView = pacerView;
        reset();
    }

    public void setModulationPeriod(float periodSec) {
        // Период ползунка не влияет на скорость прогресс-бара согласно спецификации
    }

    public void reset() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        currentDegrees = 360.0f;
        isTestPhase = false;
        persistentPhaseStack.clear();
        // По умолчанию базовый стек
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    /**
     * Запуск прогресс-бара строго по нажатию кнопки «Монитор» (начало сессии)
     */
    public void start() {
        isActive = true;
        isTestPhase = false;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        // Инициализация стека из 3 объектов: оранжевый подложка, белый, оранжевый активный
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // 1. Оранжевый
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // 2. Белый
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // 3. Оранжевый (активный)

        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }

        startLoop();
    }

    public void stop() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        reset();
    }

    /**
     * Переход на фазу замера PSTI (тест 5 минут)
     */
    public void startPostiStack() {
        isTestPhase = true;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        synchronized (persistentPhaseStack) {
            // Поддержание структуры стека, смена активного верхнего слоя на белый
            persistentPhaseStack.clear();
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // Подложка
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // Средний
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // Верхний активный (белый)
        }

        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    /**
     * Возврат из теста PSTI в фазу тренировки (15 минут)
     */
    public void finishPostiStack() {
        isTestPhase = false;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        synchronized (persistentPhaseStack) {
            persistentPhaseStack.clear();
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // Подложка
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // Средний
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // Верхний активный (оранжевый)
        }

        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    private void startLoop() {
        handler.removeCallbacks(animationRunnable);
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActive) return;

                long now = System.currentTimeMillis();
                float dt = (now - lastFrameTime) / 1000.0f;
                lastFrameTime = now;

                if (dt > 0.2f) dt = 0.05f;

                // Скорость сокращения дуги: 15 минут для тренировки, 5 минут для теста
                float totalDurationSec = isTestPhase ? DURATION_TEST_SEC : DURATION_TRAIN_SEC;
                float speedDegPerSec = 360.0f / totalDurationSec;

                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 360.0f;
                    isTestPhase = !isTestPhase; // Автоматическое переключение фазы

                    synchronized (persistentPhaseStack) {
                        persistentPhaseStack.clear();
                        // Ротация стека до 3 объектов с чередованием приоритетов видимости
                        boolean nextColor = isTestPhase;
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(!nextColor, 1.0f));
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(nextColor, 1.0f));
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(nextColor, 1.0f));
                    }
                } else {
                    float progress = currentDegrees / 360.0f;
                    synchronized (persistentPhaseStack) {
                        if (!persistentPhaseStack.isEmpty()) {
                            persistentPhaseStack.get(persistentPhaseStack.size() - 1).progress = progress;
                        }
                    }
                }

                if (pacerView != null) {
                    pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
                }

                handler.postDelayed(this, 30);
            }
        };
        handler.post(animationRunnable);
    }
}
