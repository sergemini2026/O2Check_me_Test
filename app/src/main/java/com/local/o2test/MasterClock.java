package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;
    private float currentPhaseValue = 0f;

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

        clockAnimator = ValueAnimator.ofFloat(normalizedPhase, normalizedPhase + 1.0f);
        clockAnimator.setDuration(10000); // 10 секунд на полный дыхательный цикл (0.1 Гц)
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
     * Плавная динамическая коррекция фазы на лету (без рывков) при новом замере или автоподстройке
     */
    public void adjustPhase(float targetPhase) {
        if (!isRunning) return;
        
        float normTarget = targetPhase % 1.0f;
        if (normTarget < 0) normTarget += 1.0f;

        // Плавный сдвиг текущей фазы к целевой фазе Майера (устраняет рассинхрон)
        float diff = normTarget - currentPhaseValue;
        if (diff > 0.5f) diff -= 1.0f;
        if (diff < -0.5f) diff += 1.0f;

        // Корректируем аниматор с учетом разницы
        if (clockAnimator != null && clockAnimator.isRunning()) {
            long currentPlayTime = clockAnimator.getCurrentPlayTime();
            float animatedFraction = clockAnimator.getAnimatedFraction();
            
            // Перезапускаем аниматор с плавно смещенной базовой точкой
            float newStart = currentPhaseValue + (diff * 0.3f); // 30% коррекция за раз для мягкости
            clockAnimator.cancel();
            
            clockAnimator = ValueAnimator.ofFloat(newStart, newStart + 1.0f);
            clockAnimator.setDuration(10000);
            clockAnimator.setInterpolator(new LinearInterpolator());
            clockAnimator.setRepeatCount(ValueAnimator.INFINITE);
            clockAnimator.setCurrentPlayTime(currentPlayTime % 10000);
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
