package com.local.o2test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        ringPaint.setStyle(Paint.Style.FILL);
        
        // Включаем аппаратное ускорение для отрисовки слоя через GPU
        setLayerType(LAYER_TYPE_HARDWARE, null);
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
        if (width <= 0 || height <= 0) return;

        float scale = Math.min(width, height) / 200f;
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        // Карточка-фон (без аллокаций)
        bgRect.set(0, 0, 200, 200);
        canvas.drawRoundRect(bgRect, 16, 16, bgPaint);

        // Плавная фаза дыхания (0.0 — выдох, 1.0 — вдох)
        float breathPhase;
        if (animationProgress <= 0.5f) {
            breathPhase = animationProgress * 2.0f; // Вдох
        } else {
            breathPhase = 2.0f - (animationProgress * 2.0f); // Выдох
        }

        // Вращение всей структуры
        canvas.save();
        canvas.rotate(rotationAngle, 100f, 100f);

        // Динамический цвет (розовый -> циан) без создания объектов градиентов в памяти
        int dynamicColor = interpolateColor(0xFF4081, 0x00E5FF, breathPhase);

        // Оптимизированный узор (нулевые аллокации в памяти во время кадров)
        float baseRadius = 28f + 12f * breathPhase;

        // Внутреннее кольцо (8 элементов)
        for (int i = 0; i < 8; i++) {
            double angle = i * (2.0 * Math.PI / 8.0);
            float cx = 100f + (float)(baseRadius * Math.cos(angle));
            float cy = 100f + (float)(baseRadius * Math.sin(angle));
            
            ringPaint.setColor(dynamicColor);
            ringPaint.setAlpha(130);
            canvas.drawCircle(cx, cy, 16f + 5f * breathPhase, ringPaint);
        }

        // Внешнее кольцо (16 элементов при раскрытии > 20%)
        if (breathPhase > 0.2f) {
            float p2 = (breathPhase - 0.2f) / 0.8f;
            float outerDist = baseRadius * 1.6f;
            for (int i = 0; i < 16; i++) {
                double angle = i * (2.0 * Math.PI / 16.0) + 0.1f;
                float cx = 100f + (float)(outerDist * Math.cos(angle));
                float cy = 100f + (float)(outerDist * Math.sin(angle));
                
                ringPaint.setColor(dynamicColor);
                ringPaint.setAlpha((int)(90 * p2));
                canvas.drawCircle(cx, cy, 10f + 4f * p2, ringPaint);
            }
        }

        canvas.restore(); // Сброс вращения

        // Центральное световое ядро
        float coreRadius = 14f + 5f * breathPhase;
        ringPaint.setColor(0xFFEEFFFF);
        ringPaint.setAlpha(230);
        canvas.drawCircle(100, 100, coreRadius, ringPaint);

        canvas.restore();
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int r = (int) (rA + fraction * (rB - rA));
        int g = (int) (gA + fraction * (gB - gA));
        int b = (int) (bA + fraction * (bB - bA));
        return Color.rgb(r, g, b);
    }
}
