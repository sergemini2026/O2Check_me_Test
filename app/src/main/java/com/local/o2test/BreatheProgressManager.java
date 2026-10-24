package com.local.o2test;

import android.os.Handler;
import android.os.Looper;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    
    private boolean isActive = false;
    private float currentDegrees = 360.0f;
    private long lastFrameTime = 0;
    
    // Ровно 5 минут в секундах для каждого цикла (дамп или PSTI)
    private static final float DURATION_CYCLE_SEC = 5.0f * 60.0f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable animationRunnable;

    public BreatheProgressManager(BreatheAnimationView pacerView) {
        this.pacerView = pacerView;
        reset();
    }

    public void setModulationPeriod(float periodSec) {
        // Не используется
    }

    public void reset() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        currentDegrees = 360.0f;
        if (pacerView != null) {
            pacerView.setProgress(1.0f);
        }
    }

    /**
     * Запуск прогресс-бара по нажатию кнопки «Монитор»
     */
    public void start() {
        isActive = true;
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        
        if (pacerView != null) {
            pacerView.setProgress(1.0f);
        }

        startLoop();
    }

    /**
     * Перезапуск/синхронизация цикла при завершении дампа или старте PSTI
     */
    public void restart() {
        if (!isActive) {
            start();
            return;
        }
        currentDegrees = 360.0f;
        lastFrameTime = System.currentTimeMillis();
        if (pacerView != null) {
            pacerView.setProgress(1.0f);
        }
    }

    public void stop() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        reset();
    }

    public void startPostiStack() {
        // Синхронизируем прогресс-бар при начале замера PSTI
        restart();
    }

    public void finishPostiStack() {
        // Синхронизируем прогресс-бар при возврате к тренировке
        restart();
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

                // Угловая скорость для 5 минут (360 градусов / 300 секунд = 1.2 град/сек)
                float speedDegPerSec = 360.0f / DURATION_CYCLE_SEC;

                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 360.0f; // Зацикливание 5-минутного периода до конца сессии
                }

                float progress = currentDegrees / 360.0f;
                if (pacerView != null) {
                    pacerView.setProgress(progress);
                }

                if (isActive) {
                    handler.postDelayed(this, 30);
                }
            }
        };
        handler.post(animationRunnable);
    }
}
