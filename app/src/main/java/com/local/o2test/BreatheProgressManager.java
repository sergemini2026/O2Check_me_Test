package com.local.o2test;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    private final List<BreatheAnimationView.PhaseSegment> persistentPhaseStack = new ArrayList<>();
    
    private boolean isActive = false;
    private boolean isTestPhase = false; // false = тренировка (15 минут), true = тест (5 минут)
    
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

    // Метод оставлен для совместимости, но период ползунка здесь больше не влияет на скорость круга
    public void setModulationPeriod(float periodSec) {
        // Игнорируем, так как длительности жесткие: 15 и 5 минут
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

    public void stop() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        reset();
    }

    public void startPostiStack() {
        isTestPhase = true; // Переход на 5-минутный цикл
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    public void finishPostiStack() {
        isTestPhase = false; // Возврат на 15-минутный цикл
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
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

                // Вычисляем скорость в градусах в секунду исходя из жесткого времени круга
                float totalDurationSec = isTestPhase ? DURATION_TEST_SEC : DURATION_TRAIN_SEC;
                float speedDegPerSec = 360.0f / totalDurationSec;

                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 360.0f;
                    isTestPhase = !isTestPhase; // Автоматическое чередование

                    synchronized (persistentPhaseStack) {
                        persistentPhaseStack.clear();
                        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(isTestPhase, 1.0f));
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
