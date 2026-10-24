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
        // Период ползунка не влияет на длительность прогресс-бара
    }

    public void reset() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        currentDegrees = 360.0f;
        isTestPhase = false;
        persistentPhaseStack.clear();
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
        // Всегда ровно 3 объекта в стеке:
        // 1. Оранжевая подложка (полная, progress = 1.0)
        // 2. Белая прослойка (полная, progress = 1.0)
        // 3. Оранжевый активный слой (начинает убывать от 1.0)
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));

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
            persistentPhaseStack.clear();
            // Для теста: подложки заполнены, верхний активный слой — белый
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
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
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
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

                // Скорость: 15 минут (900 сек) для тренировки (~0.4°/сек), 5 минут (300 сек) для теста (1.2°/сек)
                float totalDurationSec = isTestPhase ? DURATION_TEST_SEC : DURATION_TRAIN_SEC;
                float speedDegPerSec = 360.0f / totalDurationSec;

                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 360.0f;
                    isTestPhase = !isTestPhase; // Смена фазы

                    synchronized (persistentPhaseStack) {
                        persistentPhaseStack.clear();
                        // Сдвиг стека и поддержание ровно 3 объектов с нужными цветами
                        boolean nextColor = isTestPhase;
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(!nextColor, 1.0f));
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(nextColor, 1.0f));
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(nextColor, 1.0f));
                    }
                } else {
                    float progress = currentDegrees / 360.0f;
                    synchronized (persistentPhaseStack) {
                        if (!persistentPhaseStack.isEmpty()) {
                            // Уменьшаем прогресс только у самого верхнего активного элемента
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
