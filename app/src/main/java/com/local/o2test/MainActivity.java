package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private static final long DEFAULT_DURATION_MS = 12000L;
    private static final long MIN_DURATION_MS = 6000L;
    private static final long MAX_DURATION_MS = 14000L;
    private static final float MIN_PHASE_LEAD_OFFSET = -0.35f;
    private static final float MAX_PHASE_LEAD_OFFSET = 0.35f;

    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;
    private float currentPhaseValue = 0f;

    // Коэффициент упреждения фазы при автокоррекции (по умолчанию +25% цикла)
    private float phaseLeadOffset = 0.25f;

    // Базовая длительность цикла в мс (динамическая, от 6000 до 14000 мс; по умолчанию 12000 мс / 0.0833 Гц)
    private long baseDurationMs = DEFAULT_DURATION_MS;
    private long targetDurationMs = DEFAULT_DURATION_MS;

    public interface MasterClockListener {
        void onTick(float phase);
    }

    public MasterClock(MasterClockListener listener) {
        this.listener = listener;
    }

    public void setPhaseLeadOffset(float offset) {
        if (Float.isNaN(offset) || Float.isInfinite(offset)) {
            return;
        }
        this.phaseLeadOffset = Math.max(MIN_PHASE_LEAD_OFFSET, Math.min(MAX_PHASE_LEAD_OFFSET, offset));
    }

    public float getPhaseLeadOffset() {
        return phaseLeadOffset;
    }

    public void setBaseDurationMs(long durationMs) {
        this.targetDurationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));
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
     * Сверхмягкая фазовая автокоррекция с учетом упреждения фазы без разрывов анимации.
     */
    public void adjustPhase(float targetPhase) {
        if (!isRunning || clockAnimator == null || !clockAnimator.isRunning()) return;

        float normTarget = targetPhase % 1.0f;
        if (normTarget < 0) normTarget += 1.0f;

        normTarget = (normTarget + phaseLeadOffset) % 1.0f;
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
