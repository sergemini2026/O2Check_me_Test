package com.local.o2test;

import android.os.Handler;
import android.os.Looper;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    
    private boolean isActive = false;
    private float currentDegrees = 360.0f;
    private long lastFrameTime = 0;
    
    // Ровно 15 минут в секундах
    private static final float DURATION_TRAIN_SEC = 15.0f * 60.0f;

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

    public void stop() {
        isActive = false;
        handler.removeCallbacks(animationRunnable);
        reset();
    }

    public void startPostiStack() {
        // Заглушка, чтобы не ломать вызовы из MainActivity
    }

    public void finishPostiStack() {
        // Заглушка, чтобы не ломать вызовы из MainActivity
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

                // Угловая скорость для 15 минут (360 градусов / 900 секунд = 0.4 град/сек)
                float speedDegPerSec = 360.0f / DURATION_TRAIN_SEC;

                currentDegrees -= speedDegPerSec * dt;

                if (currentDegrees <= 0.0f) {
                    currentDegrees = 0.0f;
                    isActive = false; // Дошли до 0 и остановились
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
