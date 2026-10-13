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
import android.view.animation.DecelerateInterpolator;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint elementPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private boolean isMonitoringActive = false;
    private float currentProgress = 0f; // 0.0 (сжато/выдох) до 1.0 (раскрыто/вдох)
    private float targetProgress = 0f;
    
    // Скользящее среднее для базовой линии R-R интервалов
    private float rrBaseline = 800f; 
    private ValueAnimator progressAnimator;

    // Параметры симметричных колец (центр в точке 100, 100)
    private static final int CYAN_COUNT = 6;
    private static final float CYAN_DISTANCE = 26f;
    private static final float CYAN_RADIUS = 20f;

    private static final int PINK_COUNT = 8;
    private static final float PINK_DISTANCE = 56f;
    private static final float PINK_RADIUS = 22f;

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        currentProgress = 0f;
        targetProgress = 0f;
        rrBaseline = 800f;
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        currentProgress = 0f;
        targetProgress = 0f;
        invalidate();
    }

    /**
     * Реальная привязка к дыханию через R-R интервалы (Дыхательная синусовая аритмия).
     * Вдох -> пульс частеет -> RR укорачивается -> объект раскрывается (циан).
     * Выдох -> пульс реже -> RR удлиняется -> объект сжимается (розовый).
     */
    public void updateRr(int rrMs) {
        if (!isMonitoringActive || rrMs <= 0) return;

        // Обновляем адаптивную базовую линию RR (экспоненциальное сглаживание)
        rrBaseline = 0.9f * rrBaseline + 0.1f * rrMs;

        // Разница между базой и текущим RR:
        // Если rrMs < rrBaseline (вдох), diff > 0 -> стремимся к 1.0
        // Если rrMs > rrBaseline (выдох), diff < 0 -> стремимся к 0.0
        float diff = rrBaseline - rrMs;
        
        // Масштабируем отклонение в целевой прогресс [0.0, 1.0]
        targetProgress = Math.max(0.0f, Math.min(1.0f, 0.5f + (diff / 150.0f)));

        animateToTarget();
    }

    private void animateToTarget() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }

        progressAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress);
        progressAnimator.setDuration(800); // Плавный переход между ударами сердца
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            currentProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
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

        // Цветовой градиент: от розового (выдох, прогресс 0) к яркому циану (вдох, прогресс 1)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, currentProgress);
        int endColor = interpolateColor(0x15FF4081, 0x1500E5FF, currentProgress);

        // 1. Отрисовка внутреннего симметричного кольца (Циан / Лепестки)
        for (int i = 0; i < CYAN_COUNT; i++) {
            double angle = i * (2.0 * Math.PI / CYAN_COUNT);
            // Эффект "распускания": лепестки выдвигаются из центра при росте прогресса
            float currentDist = CYAN_DISTANCE * (0.25f + 0.75f * currentProgress);
            float cx = 100f + (float)(currentDist * Math.cos(angle));
            float cy = 100f + (float)(currentDist * Math.sin(angle));
            float r = CYAN_RADIUS * (0.4f + 0.6f * currentProgress);

            elementPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, elementPaint);
        }

        // 2. Отрисовка внешнего симметричного кольца (Розово-циановые внешние лепестки)
        for (int i = 0; i < PINK_COUNT; i++) {
            double angle = i * (2.0 * Math.PI / PINK_COUNT) + (Math.PI / PINK_COUNT); // Смещение для гармоничной сетки
            float currentDist = PINK_DISTANCE * (0.2f + 0.8f * currentProgress);
            float cx = 100f + (float)(currentDist * Math.cos(angle));
            float cy = 100f + (float)(currentDist * Math.sin(angle));
            float r = PINK_RADIUS * (0.3f + 0.7f * currentProgress);

            elementPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, elementPaint);
        }

        // 3. Центральное световое ядро (пульсирует в такт)
        float coreRadius = 10f + 6f * currentProgress;
        corePaint.setShader(new RadialGradient(100, 100, coreRadius,
                new int[]{0xFFEEFFFF, 0x44FFFFFF}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, coreRadius, corePaint);

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