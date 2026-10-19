package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;
    private float currentPhaseValue = 0f;
    
    // Базовая длительность цикла (10 секунд = 0.1 Гц)
    private static final long BASE_DURATION_MS = 10000;

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
     * Вместо сброса аниматора мы чуть-чуть меняем скорость (длительность) текущего цикла,
     * позволяя пейсеру плавно «подтянуться» к биологическому ритму пользователя.
     */
    public void adjustPhase(float targetPhase) {
        if (!isRunning || clockAnimator == null || !clockAnimator.isRunning()) return;
        
        float normTarget = targetPhase % 1.0f;
        if (normTarget < 0) normTarget += 1.0f;

        // Вычисляем кратчайшую ошибку фазы (-0.5 до 0.5)
        float diff = normTarget - currentPhaseValue;
        if (diff > 0.5f) diff -= 1.0f;
        if (diff < -0.5f) diff += 1.0f;

        // Если ошибка микроскопическая (< 2%), ничего не делаем, чтобы не дергать систему
        if (Math.abs(diff) < 0.02f) return;

        // Ограничиваем максимальное влияние коррекции, чтобы изменение длительности было незаметным (±10%)
        // Если опережаем — замедляем цикл (увеличиваем время), если отстаем — ускоряем (уменьшаем время)
        long adjustedDuration = (long) (BASE_DURATION_MS * (1.0f - (diff * 0.2f)));
        
        // Жесткие лимиты безопасности для длительности цикла: от 8.5 до 11.5 секунд
        if (adjustedDuration < 8500) adjustedDuration = 8500;
        if (adjustedDuration > 11500) adjustedDuration = 11500;

        // Безопасно переводим аниматор на новую длительность с текущей точки без скачка фазы
        long currentPlayTime = clockAnimator.getCurrentPlayTime() % clockAnimator.getDuration();
        
        float currentFraction = (float) currentPlayTime / clockAnimator.getDuration();
        float currentAnimatedValue = (float) clockAnimator.getAnimatedValue();
        
        // Перезапускаем с текущего значения, но с чуть скорректированной скоростью (длительностью)
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
