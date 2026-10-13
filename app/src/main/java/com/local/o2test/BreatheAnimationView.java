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
    
    // Плавная фаза дыхания (0.0 — выдох/сжатие, 1.0 — вдох/полное раскрытие)
    private float breathPhase = 0f;
    private float targetBreathPhase = 0f;
    private float rrSmooth = 800f;
    private ValueAnimator phaseAnimator;

    // Угол вращения структуры
    private float rotationAngle = 0f;
    private ValueAnimator rotationAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        breathPhase = 0f;
        targetBreathPhase = 0f;
        rrSmooth = 800f;
        rotationAngle = 0f;
        
        startRotationLoop();
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (phaseAnimator != null) phaseAnimator.cancel();
        if (rotationAnimator != null) rotationAnimator.cancel();
        breathPhase = 0f;
        rotationAngle = 0f;
        invalidate();
    }

    /**
     * Плавная привязка к реальным R-R интервалам (RSA).
     * Устраняет хаотичные скачки за счет экспоненциального сглаживания.
     */
    public void updateRr(int rrMs) {
        if (!isMonitoringActive || rrMs <= 0) return;

        // Сглаживаем сырой пульс, выделяя дыхательную волну
        rrSmooth = 0.85f * rrSmooth + 0.15f * rrMs;
        float diff = 800f - rrSmooth; // сдвиг относительно нормы
        
        // Целевая фаза дыхания [0.0, 1.0]
        targetBreathPhase = Math.max(0.0f, Math.min(1.0f, 0.5f + (diff / 120.0f)));

        animatePhaseToTarget();
    }

    private void animatePhaseToTarget() {
        if (phaseAnimator != null) phaseAnimator.cancel();

        phaseAnimator = ValueAnimator.ofFloat(breathPhase, targetBreathPhase);
        phaseAnimator.setDuration(1000); // Плавная инерция дыхательного цикла
        phaseAnimator.setInterpolator(new LinearInterpolator());
        phaseAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;
            breathPhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        phaseAnimator.start();
    }

    private void startRotationLoop() {
        if (rotationAnimator != null) rotationAnimator.cancel();

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(20000); // Полный оборот за 20 секунд
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

        // Применяем вращение всей структуры вокруг центра (100, 100)
        canvas.save();
        canvas.rotate(rotationAngle, 100f, 100f);

        // Цветовой переход: розовый (выдох) -> яркий циан (вдох)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathPhase);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, breathPhase);

        // =========================================================================
        // ЭФФЕКТ РАСКРЫВАЮЩЕГОСЯ ЦВЕТКА И УСЛОЖНЕНИЯ ФРАКТАЛА (Без изменения масштаба холста)
        // =========================================================================

        // 1. Внутренний контур (Базовые 8 лепестков, разворачиваются первыми)
        int count1 = 8;
        float dist1 = 30f + 15f * breathPhase;
        float radius1 = 18f + 6f * breathPhase;
        drawPetalRing(canvas, count1, dist1, radius1, startColor, endColor);

        // 2. Средний контур (16 лепестков, усложнение структуры при раскрытии)
        if (breathPhase > 0.2f) {
            float layerProgress = (breathPhase - 0.2f) / 0.8f;
            int count2 = 16;
            float dist2 = 50f + 20f * layerProgress;
            float radius2 = 12f + 8f * layerProgress;
            // Прозрачность слоев зависит от фазы раскрытия
            int alphaFactor = (int)(255 * layerProgress);
            drawPetalRing(canvas, count2, dist2, radius2, applyAlpha(startColor, alphaFactor), applyAlpha(endColor, alphaFactor));
        }

        // 3. Внешний контур (32 лепестка, максимальное фрактальное усложнение на вдохе)
        if (breathPhase > 0.6f) {
            float layerProgress = (breathPhase - 0.6f) / 0.4f;
            int count3 = 32;
            float dist3 = 75f + 15f * layerProgress;
            float radius3 = 6f + 6f * layerProgress;
            int alphaFactor = (int)(255 * layerProgress);
            drawPetalRing(canvas, count3, dist3, radius3, applyAlpha(startColor, alphaFactor), applyAlpha(endColor, alphaFactor));
        }

        canvas.restore(); // Сброс вращения для ядра

        // Центральное световое ядро (плавная пульсация)
        float coreRadius = 12f + 6f * breathPhase;
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
