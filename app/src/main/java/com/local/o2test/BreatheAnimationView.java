package com.local.o2test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isMonitoringActive = false;
    
    // Переменные для абсолютно непрерывной анимации на базе реального времени
    private long lastFrameTime = 0;
    private float animationProgress = 0f; // [0.0 ... 1.0] непрерывный цикл дыхания
    private float currentCycleDurationMs = 10000f; // 10 секунд по умолчанию (субгармоника 0.6 Гц: 5с вдох, 5с выдох)
    private float targetCycleDurationMs = 10000f;
    
    private float rotationAngle = 0f;
    
    private ValueAnimator tickerAnimator;
    private ValueAnimator rotationAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        init();
    }

    public BreatheAnimationView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#121212"));
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.parseColor("#333333"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);

        petalFillPaint.setStyle(Paint.Style.FILL);
        petalStrokePaint.setStyle(Paint.Style.STROKE);
        petalStrokePaint.setStrokeWidth(2f);

        corePaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.parseColor("#A0A0A0"));
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
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

    /**
     * Плавная подстройка частоты дыхания на основе телеметрии (ВСР / пульс),
     * сохраняя 100% непрерывность фазы.
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
                if (dt > 500) dt = 16; // Защита от фризов

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

        // Фон и рамка квадрата
        canvas.drawRect(0, 0, width, height, bgPaint);
        canvas.drawRect(0, 0, width, height, borderPaint);

        // Масштабирование под виртуальное поле 200x200 для точного позиционирования
        float scale = Math.min(width, height) / 200f;
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        // Фаза дыхания: 0.0 -> 0.5 (Вдох), 0.5 -> 1.0 (Выдох)
        float breathPhase;
        boolean isInhale;
        if (animationProgress <= 0.5f) {
            breathPhase = animationProgress * 2.0f; // Вдох (0 -> 1)
            isInhale = true;
        } else {
            breathPhase = 2.0f - (animationProgress * 2.0f); // Выдох (1 -> 0)
            isInhale = false;
        }

        // Цветовой переход: Розовый (#E91E63) <-> Голубой (#00BCD4)
        int pinkFill = Color.argb(100, 233, 30, 99);
        int cyanFill = Color.argb(100, 0, 188, 212);
        int currentFill = interpolateColor(pinkFill, cyanFill, breathPhase);
        int currentStroke = interpolateColor(Color.argb(220, 233, 30, 99), Color.argb(220, 0, 188, 212), breathPhase);

        petalFillPaint.setColor(currentFill);
        petalStrokePaint.setColor(currentStroke);

        // Вращение структуры (вдох — в одну сторону, выдох — в другую)
        float activeRotation = isInhale ? rotationAngle : -rotationAngle;

        canvas.save();
        canvas.rotate(activeRotation, 100f, 100f);

        // 1. Внутренний уровень (6 лепестков)
        float dist1 = 18f + 10f * breathPhase;
        float radius1 = 16f + 4f * breathPhase;
        drawPetalRing(canvas, 6, dist1, radius1);

        // 2. Средний уровень (12 лепестков, проявляются при раскрытии)
        if (breathPhase > 0.25f) {
            float p2 = (breathPhase - 0.25f) / 0.75f;
            float dist2 = 34f + 14f * p2;
            float radius2 = 10f + 5f * p2;
            int alphaMult = (int)(255 * p2);
            drawPetalRingWithAlpha(canvas, 12, dist2, radius2, alphaMult);
        }

        // 3. Внешний уровень (24 лепестка)
        if (breathPhase > 0.6f) {
            float p3 = (breathPhase - 0.6f) / 0.4f;
            float dist3 = 54f + 12f * p3;
            float radius3 = 6f + 4f * p3;
            int alphaMult = (int)(255 * p3);
            drawPetalRingWithAlpha(canvas, 24, dist3, radius3, alphaMult);
        }

        canvas.restore();

        // Четкое центральное ядро
        float coreRadius = 10f + 4f * breathPhase;
        corePaint.setColor(currentStroke);
        canvas.drawCircle(100f, 100f, coreRadius, corePaint);

        canvas.restore();

        // Текст статуса внизу
        String statusText = !isMonitoringActive ? "Ожидание сессии" : (isInhale ? "Breathe in" : "Breathe out");
        canvas.drawText(statusText, width / 2f, height - 16f, textPaint);
    }

    private void drawPetalRing(Canvas canvas, int count, float distance, float radius) {
        for (int i = 0; i < count; i++) {
            double angle = i * (2.0 * Math.PI / count);
            float cx = 100f + (float)(distance * Math.cos(angle));
            float cy = 100f + (float)(distance * Math.sin(angle));

            canvas.drawCircle(cx, cy, radius, petalFillPaint);
            canvas.drawCircle(cx, cy, radius, petalStrokePaint);
        }
    }

    private void drawPetalRingWithAlpha(Canvas canvas, int count, float distance, float radius, int alphaMultiplier) {
        int oldFillAlpha = petalFillPaint.getAlpha();
        int oldStrokeAlpha = petalStrokePaint.getAlpha();

        petalFillPaint.setAlpha((oldFillAlpha * alphaMultiplier) / 255);
        petalStrokePaint.setAlpha((oldStrokeAlpha * alphaMultiplier) / 255);

        drawPetalRing(canvas, count, distance, radius);

        petalFillPaint.setAlpha(oldFillAlpha);
        petalStrokePaint.setAlpha(oldStrokeAlpha);
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
