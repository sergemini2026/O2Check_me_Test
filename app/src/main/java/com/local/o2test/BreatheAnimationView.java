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
    private float breathPhase = 0f; // от 0.0 (выдох) до 1.0 (вдох)
    private float rotationAngle = 0f;
    
    private ValueAnimator breathAnimator;
    private ValueAnimator rotationAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        breathPhase = 0f;
        rotationAngle = 0f;
        startDynamicLoop(70); // Старт с базового пульса 70 bpm
        startRotationLoop();
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (breathAnimator != null) breathAnimator.cancel();
        if (rotationAnimator != null) rotationAnimator.cancel();
        breathPhase = 0f;
        rotationAngle = 0f;
        invalidate();
    }

    /**
     * Вычисление биоритма на основе параметров пульса и оксигенации.
     */
    public void updateTelemetry(int hr, int rrMs, float pi) {
        if (!isMonitoringActive) return;

        // Физиологический расчет темпа дыхания по текущему пульсу (HR / 4)
        int effectiveHr = (hr > 30 && hr < 220) ? hr : 70;
        float breathsPerMinute = effectiveHr / 4.0f;
        long cycleDurationMs = (long) ((60.0f / breathsPerMinute) * 1000.0f);

        // Плавная адаптация скорости аниматора под изменение пульса
        if (breathAnimator == null || Math.abs(breathAnimator.getDuration() - cycleDurationMs) > 400) {
            updateAnimatorDuration(cycleDurationMs);
        }
    }

    private void startDynamicLoop(int initialHr) {
        if (breathAnimator != null) breathAnimator.cancel();

        float breathsPerMinute = initialHr / 4.0f;
        long cycleDurationMs = (long) ((60.0f / breathsPerMinute) * 1000.0f);

        breathAnimator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator.setDuration(cycleDurationMs);
        breathAnimator.setInterpolator(new LinearInterpolator());
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;
            float progress = (float) animation.getAnimatedValue();
            
            // Синусоидальный цикл: вдох (0 -> 1) и выдох (1 -> 0)
            if (progress <= 0.5f) {
                breathPhase = progress * 2.0f; 
            } else {
                breathPhase = 2.0f - (progress * 2.0f); 
            }
            invalidate();
        });
        breathAnimator.start();
    }

    private void updateAnimatorDuration(long newDuration) {
        if (breathAnimator == null) return;
        long currentPlayTime = breathAnimator.getCurrentPlayTime();
        breathAnimator.cancel();
        breathAnimator.setDuration(newDuration);
        if (currentPlayTime < newDuration) {
            breathAnimator.setCurrentPlayTime(currentPlayTime);
        }
        breathAnimator.start();
    }

    private void startRotationLoop() {
        if (rotationAnimator != null) rotationAnimator.cancel();

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(25000); // Медленное медитативное вращение за 25 секунд
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;
            rotationAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        rotationAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopMonitoring();
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

        // Карточка-фон
        bgRect.set(0, 0, 200, 200);
        canvas.drawRoundRect(bgRect, 16, 16, bgPaint);

        // Вращение всей структуры
        canvas.save();
        canvas.rotate(rotationAngle, 100f, 100f);

        // Цветовой переход: розовый (выдох) -> яркий циан (вдох)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathPhase);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, breathPhase);

        // =========================================================================
        // УСЛОЖНЕНИЕ ФРАКТАЛА БЕЗ МАСШТАБИРОВАНИЯ (8 -> 16 -> 32 лепестка)
        // =========================================================================

        // 1. Внутренний уровень (8 лепестков)
        int count1 = 8;
        float dist1 = 30f + 12f * breathPhase;
        float radius1 = 18f + 5f * breathPhase;
        drawPetalRing(canvas, count1, dist1, radius1, startColor, endColor);

        // 2. Средний уровень (16 лепестков при раскрытии > 20%)
        if (breathPhase > 0.2f) {
            float p2 = (breathPhase - 0.2f) / 0.8f;
            int count2 = 16;
            float dist2 = 50f + 18f * p2;
            float radius2 = 12f + 6f * p2;
            int alpha = (int)(255 * p2);
            drawPetalRing(canvas, count2, dist2, radius2, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        // 3. Внешний уровень (32 лепестка при раскрытии > 60%)
        if (breathPhase > 0.6f) {
            float p3 = (breathPhase - 0.6f) / 0.4f;
            int count3 = 32;
            float dist3 = 72f + 15f * p3;
            float radius3 = 6f + 5f * p3;
            int alpha = (int)(255 * p3);
            drawPetalRing(canvas, count3, dist3, radius3, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        canvas.restore(); // Сброс вращения

        // Центральное световое ядро
        float coreRadius = 12f + 5f * breathPhase;
        corePaint.setShader(new RadialGradient(100, 100, coreRadius,
                new int[]{0xFFEEFFFF, 0x44FFFFFF}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, coreRadius, corePaint);

        canvas.restore();
    }

    private void drawPetalRing(Canvas canvas, int count, float distance, float radius, int startColor, int endColor) {
        for (int i = 0; i < count; i++) {
            double angle = i * (2.0 * Math.PI / count);
            float cx = 100f + (float)(distance * Math.cos(angle));
            float cy = 100f + (float)(distance * Math.sin(angle));

            elementPaint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, elementPaint);
        }
    }

    private int applyAlpha(int color, int alphaMultiplier) {
        int a = (Color.alpha(color) * alphaMultiplier) / 255;
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
