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
    
    // Переменные для абсолютно непрерывной анимации на базе реального времени
    private long lastFrameTime = 0;
    private float animationProgress = 0f; // [0.0 ... 1.0] текущий непрерывный цикл
    private float currentCycleDurationMs = 4000f; // текущая длительность цикла дыхания
    private float targetCycleDurationMs = 4000f; // целевая длительность по данным пульса
    
    private float rotationAngle = 0f;
    
    private ValueAnimator tickerAnimator;
    private ValueAnimator rotationAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        animationProgress = 0f;
        lastFrameTime = System.currentTimeMillis();
        currentCycleDurationMs = 4000f;
        targetCycleDurationMs = 4000f;
        rotationAngle = 0f;
        
        startTicker();
        startRotationLoop();
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (tickerAnimator != null) tickerAnimator.cancel();
        if (rotationAnimator != null) rotationAnimator.cancel();
        animationProgress = 0f;
        rotationAngle = 0f;
        invalidate();
    }

    public void pauseAnimation() {
        if (tickerAnimator != null) tickerAnimator.cancel();
        if (rotationAnimator != null) rotationAnimator.cancel();
    }

    public void resumeAnimation() {
        if (isMonitoringActive) {
            lastFrameTime = System.currentTimeMillis();
            startTicker();
            startRotationLoop();
            invalidate();
        }
    }

    /**
     * Плавное обновление целевой длительности цикла на основе пульса (HR / 4).
     * Никогда не прерывает и не сбрасывает текущий прогресс, гарантируя 100% непрерывность.
     */
    public void updateTelemetry(int hr, int rrMs, float pi) {
        if (!isMonitoringActive) return;

        int effectiveHr = (hr > 30 && hr < 220) ? hr : 70;
        float breathsPerMinute = effectiveHr / 4.0f;
        targetCycleDurationMs = (60.0f / breathsPerMinute) * 1000.0f;
    }

    private void startTicker() {
        if (tickerAnimator != null) tickerAnimator.cancel();

        lastFrameTime = System.currentTimeMillis();
        tickerAnimator = ValueAnimator.ofFloat(0f, 1f);
        tickerAnimator.setDuration(1000);
        tickerAnimator.setInterpolator(new LinearInterpolator());
        tickerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        tickerAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;

            long now = System.currentTimeMillis();
            long dt = now - lastFrameTime;
            lastFrameTime = now;

            if (dt > 0) {
                // Защита от зависания при возврате из фона (срезаем гигантский dt)
                if (dt > 500) {
                    dt = 16; 
                }

                currentCycleDurationMs = currentCycleDurationMs * 0.96f + targetCycleDurationMs * 0.04f;

                animationProgress += (dt / currentCycleDurationMs);
                if (animationProgress >= 1.0f) {
                    animationProgress %= 1.0f;
                }
            }
            invalidate();
        });
        tickerAnimator.start();
    }

    private void startRotationLoop() {
        if (rotationAnimator != null) rotationAnimator.cancel();

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(25000);
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
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isMonitoringActive) {
            lastFrameTime = System.currentTimeMillis();
        }
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

        // Плавная фаза дыхания (0.0 — выдох, 1.0 — вдох) из непрерывного времени
        float breathPhase;
        if (animationProgress <= 0.5f) {
            breathPhase = animationProgress * 2.0f; // Вдох
        } else {
            breathPhase = 2.0f - (animationProgress * 2.0f); // Выдох
        }

        // Вращение всей структуры
        canvas.save();
        canvas.rotate(rotationAngle, 100f, 100f);

        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathPhase);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, breathPhase);

        // 1. Внутренний уровень (8 лепестков)
        int count1 = 8;
        float dist1 = 30f + 12f * breathPhase;
        float radius1 = 18f + 5f * breathPhase;
        drawPetalRing(canvas, count1, dist1, radius1, startColor, endColor);

        // 2. Средний уровень (16 лепестков)
        if (breathPhase > 0.2f) {
            float p2 = (breathPhase - 0.2f) / 0.8f;
            int count2 = 16;
            float dist2 = 50f + 18f * p2;
            float radius2 = 12f + 6f * p2;
            int alpha = (int)(255 * p2);
            drawPetalRing(canvas, count2, dist2, radius2, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        // 3. Внешний уровень (32 лепестка)
        if (breathPhase > 0.6f) {
            float p3 = (breathPhase - 0.6f) / 0.4f;
            int count3 = 32;
            float dist3 = 72f + 15f * p3;
            float radius3 = 6f + 5f * p3;
            int alpha = (int)(255 * p3);
            drawPetalRing(canvas, count3, dist3, radius3, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        canvas.restore();

        // Центральное ядро
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
