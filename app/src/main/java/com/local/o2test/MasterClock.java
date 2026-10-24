package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;
    private float currentPhaseValue = 0f;
    
    // Базовая длительность цикла в мс (динамическая, от 6000 до 14000 мс)
    private long baseDurationMs = 10000;
    private long targetDurationMs = 10000;

    public interface MasterClockListener {
        void onTick(float phase);
    }

    public MasterClock(MasterClockListener listener) {
        this.listener = listener;
    }

    public void setBaseDurationMs(long durationMs) {
        this.targetDurationMs = Math.max(6000, Math.min(14000, durationMs));
        // Если часы не запущены, сразу применяем длительность
        if (!isRunning || clockAnimator == null || !clockAnimator.isRunning()) {
            this.baseDurationMs = this.targetDurationMs;
        }
    }

    public long getBaseDurationMs() {
        return baseDurationMs;
    }

    public void start(float initialPhase) {
        if (isRunning) return;
        isRunning = true;

        float normalizedPhase = initialPhase % 1.0f;
        if (normalizedPhase < 0) normalizedPhase += 1.0f;
        currentPhaseValue = normalizedPhase;

        baseDurationMs = targetDurationMs;
        createAndStartAnimator(normalizedPhase, baseDurationMs);
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
            
            // Плавное приближение baseDurationMs к targetDurationMs на границах циклов
            if (baseDurationMs != targetDurationMs) {
                if (currentPhaseValue < 0.05f || currentPhaseValue > 0.95f) {
                    long diff = targetDurationMs - baseDurationMs;
                    baseDurationMs += Math.signum(diff) * Math.min(Math.abs(diff), 150);
                }
            }

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

        long adjustedDuration = (long) (baseDurationMs * (1.0f - (diff * 0.2f)));
        
        long minLimit = (long) (baseDurationMs * 0.85f);
        long maxLimit = (long) (baseDurationMs * 1.15f);
        if (adjustedDuration < minLimit) adjustedDuration = minLimit;
        if (adjustedDuration > maxLimit) adjustedDuration = maxLimit;

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
