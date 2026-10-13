package com.local.o2test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint elementPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private boolean isMonitoringActive = false;
    private float breathProgress = 0f; // 0.0 (выдох/пятно) до 1.0 (вдох/фрактал)
    private ValueAnimator breathAnimator;

    private static final float[][] CYAN_CIRCLES = {
        {100, 80, 22}, {118, 88, 22}, {120, 110, 22},
        {100, 122, 22}, {80, 110, 22}, {82, 88, 22}
    };

    private static final float[][] PINK_CIRCLES = {
        {100, 54, 27}, {139, 69, 24}, {152, 108, 24}, {125, 144, 27},
        {87, 152, 24}, {56, 123, 24}, {53, 82, 27}, {77, 56, 24}
    };

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        startBreathingLoop();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (breathAnimator != null) {
            breathAnimator.cancel();
        }
        breathProgress = 0f; // возврат в компактное состояние
        invalidate();
    }

    public void updateRr(int rrMs) {
        if (!isMonitoringActive) return;
        // Точка расширения под будущую аппаратную коррекцию фазы по RR-интервалам
    }

    private void startBreathingLoop() {
        if (breathAnimator != null) breathAnimator.cancel();
        
        // Цикл дыхания (4с вдох -> 4с выдох)
        breathAnimator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator.setDuration(8000);
        breathAnimator.setInterpolator(new LinearInterpolator());
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;
            float val = (float) animation.getAnimatedValue();
            if (val <= 0.5f) {
                breathProgress = val * 2.0f; // Вдох (0 -> 1)
            } else {
                breathProgress = 1.0f - (val - 0.5f) * 2.0f; // Выдох (1 -> 0)
            }
            invalidate();
        });
        breathAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (breathAnimator != null) breathAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float scale = Math.min(width, height) / 200f;
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        bgRect.set(0, 0, 200, 200);
        canvas.drawRoundRect(bgRect, 16, 16, bgPaint);

        // Масштаб лепестков от компактного пятна (0.3) до полного фрактала (1.0)
        float currentScale = 0.3f + 0.7f * breathProgress;
        canvas.save();
        canvas.scale(currentScale, currentScale, 100, 100);

        // Плавный переход цвета: от розового (выдох) к голубому (вдох)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathProgress);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, breathProgress);

        for (float[] c : CYAN_CIRCLES) {
            float cx = c[0], cy = c[1], r = c[2];
            elementPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, elementPaint);
        }

        for (float[] c : PINK_CIRCLES) {
            float cx = c[0], cy = c[1], r = c[2];
            elementPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, elementPaint);
        }

        corePaint.setShader(new RadialGradient(100, 100, 14,
                new int[]{0xFFEEFFFF, 0x44FFFFFF}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, 14, corePaint);

        canvas.restore();
        canvas.restore();
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
