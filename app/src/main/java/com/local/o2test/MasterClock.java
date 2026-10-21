package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;
    private float currentPhaseValue = 0f;
    
    // Базовая длительность цикла: гармоника частоты Шумана (7.83 секунды = 7830 мс)
    public static final long BASE_DURATION_MS = 10000;

    public interface MasterClockListener {
        void onTick(float phase);
    }

    public MasterClock(MasterClockListener listener) {
        this.listener = listener;
    }

    public void start(float initialPhase) {
        if (isRunning) return;
        isRunning = true;

        float normalizedPhase = initialPhase % 1.0f;
        if (normalizedPhase < 0) normalizedPhase += 1.0f;
        currentPhaseValue = normalizedPhase;

        createAndStartAnimator(normalizedPhase, BASE_DURATION_MS);
    }

    private void createAndStartAnimator(float startPhase, long durationMs) {
        if (clockAnimator != null) {
            clockAnimator.cancel();
        }

        clockAnimator = ValueAnimator.ofFloat(startPhase, startPhase + 1.0f);
        clockAnimator.setDuration(durationMs);
        clockAnimator.setInterpolator(new LinearInterpolator());
        clockAnimator.setRepeatCount(ValueAnimator.INFINITE);
        clockAnimator.addUpdateListener(animation -> {
            if (!isRunning) return;
            float val = (float) animation.getAnimatedValue();
            currentPhaseValue = val % 1.0f;
            if (listener != null) {
                listener.onTick(currentPhaseValue);
            }
        });
        clockAnimator.start();
    }

    /**
     * Сверхмягкая фазовая автокоррекция без разрывов анимации.
     */
    public void adjustPhase(float targetPhase) {
        if (!isRunning || clockAnimator == null || !clockAnimator.isRunning()) return;
        
        float normTarget = targetPhase % 1.0f;
        if (normTarget < 0) normTarget += 1.0f;

        float diff = normTarget - currentPhaseValue;
        if (diff > 0.5f) diff -= 1.0f;
        if (diff < -0.5f) diff += 1.0f;

        if (Math.abs(diff) < 0.02f) return;

        long adjustedDuration = (long) (BASE_DURATION_MS * (1.0f - (diff * 0.2f)));
        
        // Лимиты безопасности для периода 7.83с (от 6500 до 9200 мс)
        if (adjustedDuration < 6500) adjustedDuration = 6500;
        if (adjustedDuration > 9200) adjustedDuration = 9200;

        long currentPlayTime = clockAnimator.getCurrentPlayTime() % clockAnimator.getDuration();
        float currentAnimatedValue = (float) clockAnimator.getAnimatedValue();
        
        createAndStartAnimator(currentAnimatedValue, adjustedDuration);
    }

    public void stop() {
        isRunning = false;
        if (clockAnimator != null) {
            clockAnimator.cancel();
            clockAnimator = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
