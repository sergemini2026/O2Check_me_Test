package com.local.o2test;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

public class MasterClock {
    private ValueAnimator clockAnimator;
    private boolean isRunning = false;
    private MasterClockListener listener;

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

        clockAnimator = ValueAnimator.ofFloat(normalizedPhase, normalizedPhase + 1.0f);
        clockAnimator.setDuration(10000); // 10 секунд на полный дыхательный цикл (0.1 Гц)
        clockAnimator.setInterpolator(new LinearInterpolator());
        clockAnimator.setRepeatCount(ValueAnimator.INFINITE);
        clockAnimator.addUpdateListener(animation -> {
            if (!isRunning) return;
            float val = (float) animation.getAnimatedValue();
            float currentPhase = val % 1.0f;
            if (listener != null) {
                listener.onTick(currentPhase);
            }
        });
        clockAnimator.start();
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
