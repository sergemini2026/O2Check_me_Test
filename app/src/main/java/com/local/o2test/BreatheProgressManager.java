package com.local.o2test;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    private final List<BreatheAnimationView.PhaseSegment> persistentPhaseStack = new ArrayList<>();
    
    private boolean isActive = false;
    private boolean isTestPhase = false; // false = тренировка, true = тест PSTI
    
    private float currentPeriodSec = 10.0f;
    private float currentDegrees = 360.0f;
    private long lastFrameTime = 0;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable animationRunnable;

    public BreatheProgressManager(BreatheAnimationView pacerView) {
        this.pacerView = pacerView;
        reset();
    }

    /**
     * Вызывается при изменении ползунка MasterClock.
     * Скорость мгновенно пересчитывается в следующем кадре без сброса накопленного прогресса.
     */
    public void setModulationPeriod(float periodSec) {
        this.currentPeriodSec = Math.max(6.0f, Math.min(14.0f, periodSec));
    }

    /**
     * Полный сброс прогресс-бара
     */
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
     * Старт прогресс-бара в момент нажатия кнопки «Монитор» (начало сессии)
     */
    public void start() {
        isActive = true;
        isTestPhase = false;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }

        startLoop();
    }

    /**
     * Остановка прогресс-бара при повторном нажатии кнопки «Монитор»
     */
    public void stop() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        reset();
    }

    /**
     * Переключение на фазу замера PSTI (тест)
     */
    public void startPostiStack() {
        isTestPhase = true;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // оранжевая подложка
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // белый активный слой
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    /**
     * Завершение PSTI и возврат к фазе тренировки
     */
    public void finishPostiStack() {
        isTestPhase = false;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // белая подложка
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // новая оранжевая фаза
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

                if (dt > 0.2f) dt = 0.05f; // Защита от фризов UI

                // Получаем угловую скорость из ProgressSpeedLookup в зависимости от фазы и периода T[span_2](start_span)[span_2](end_span)
                float speedDegPerSec = ProgressSpeedLookup.getDegPerSec(currentPeriodSec, isTestPhase);

                // Уменьшаем оставшиеся градусы дуги
                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 0.0f;
                }

                float progress = currentDegrees / 360.0f;
                synchronized (persistentPhaseStack) {
                    if (!persistentPhaseStack.isEmpty()) {
                        persistentPhaseStack.get(persistentPhaseStack.size() - 1).progress = progress;
                    }
                }

                if (pacerView != null) {
                    pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
                }

                handler.postDelayed(this, 30); // ~33 FPS для плавной анимации
            }
        };
        handler.post(animationRunnable);
    }
}
