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
    
    // Непрерывная анимация на базе реального времени с фиксированным циклом MasterClock (10 секунд)
    private long lastFrameTime = 0;
    private float animationProgress = 0f; // [0.0 ... 1.0] текущий непрерывный цикл
    private static final float CYCLE_DURATION_MS = 10000f; // 10 секунд (5с вдох, 5с выдох)
    
    private float rotationAngle = 0f;
    
    private ValueAnimator tickerAnimator;
    private ValueAnimator rotationAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public BreatheAnimationView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setRunning(boolean running) {
        if (running) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        animationProgress = 0f;
        lastFrameTime = System.currentTimeMillis();
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
                if (dt > 500) dt = 16; // Защита от фризов при сворачивании

                animationProgress += (dt / CYCLE_DURATION_MS);
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
        rotationAnimator.setDuration(30000);
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        if (width > 0) {
            setMeasuredDimension(width, width); // Строго квадратный канвас
        }
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

        // Плавная фаза дыхания: 0.0 -> 0.5 (Вдох / Расширение), 0.5 -> 1.0 (Выдох / Сжатие в круг)
        float breathPhase;
        boolean isInhale;
        if (animationProgress <= 0.5f) {
            breathPhase = animationProgress * 2.0f; // Вдох (0.0 -> 1.0)
            isInhale = true;
        } else {
            breathPhase = 2.0f - (animationProgress * 2.0f); // Выдох (1.0 -> 0.0)
            isInhale = false;
        }

        // Вращение всей структуры (на вдохе в одну сторону, на выдохе — в обратную)
        canvas.save();
        float activeRotation = isInhale ? rotationAngle : -rotationAngle;
        canvas.rotate(activeRotation, 100f, 100f);

        // Цветовые градиенты: Розовый (#FF4081) <-> Голубой (#00E5FF)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathPhase);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, breathPhase);

        // 1. Внутренний уровень калейдоскопа (8 элементов)
        int count1 = 8;
        float dist1 = 25f * breathPhase;
        float radius1 = 16f + 8f * breathPhase;
        drawPetalRing(canvas, count1, dist1, radius1, startColor, endColor);

        // 2. Средний уровень (16 элементов, плавно распускаются при вдохе)
        if (breathPhase > 0.15f) {
            float p2 = (breathPhase - 0.15f) / 0.85f;
            int count2 = 16;
            float dist2 = 45f * p2;
            float radius2 = 10f + 8f * p2;
            int alpha = (int)(255 * p2);
            drawPetalRing(canvas, count2, dist2, radius2, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        // 3. Внешний уровень (32 элемента калейдоскопа)
        if (breathPhase > 0.5f) {
            float p3 = (breathPhase - 0.5f) / 0.5f;
            int count3 = 32;
            float dist3 = 68f * p3;
            float radius3 = 6f + 6f * p3;
            int alpha = (int)(255 * p3);
            drawPetalRing(canvas, count3, dist3, radius3, applyAlpha(startColor, alpha), applyAlpha(endColor, alpha));
        }

        canvas.restore();

        // Центральное ядро: при выдохе (breathPhase -> 0) стягивается в чистый сплошной плотный круг
        float coreRadius = 8f + 16f * breathPhase;
        int coreColor1 = interpolateColor(0xFFEEFFFF, 0xFF00E5FF, breathPhase);
        int coreColor2 = interpolateColor(0x88FFFFFF, 0x3300E5FF, breathPhase);
        
        corePaint.setShader(new RadialGradient(100, 100, coreRadius,
                new int[]{coreColor1, coreColor2}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, coreRadius, corePaint);

        canvas.restore();
    }

    private void drawPetalRing(Canvas canvas, int count, float distance, float radius, int startColor, int endColor) {
        for (int i = 0; i < count; i++) {
            double angle = i * (2.0 * Math.PI / count);
            float cx = 100f + (float)(distance * Math.cos(angle));
            float cy = 100f + (float)(distance * Math.sin(angle));

            elementPaint.setShader(new RadialGradient(cx, cy, Math.max(1f, radius),
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
