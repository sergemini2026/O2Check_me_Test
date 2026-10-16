package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class BreatheAnimationView extends View {
    private final Paint paintBg = new Paint();
    private final Paint paintBorder = new Paint();
    private final Paint paintPetal = new Paint();
    private final Paint paintText = new Paint();

    private boolean isRunning = false;
    private long sessionStartTime = 0;
    private static final float CYCLE_DURATION_MS = 10000f; // 10 секунд (5с вдох, 5с выдох)

    public BreatheAnimationView(Context context) {
        super(context);
        init();
    }

    public BreatheAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintBg.setColor(Color.parseColor("#121212"));
        paintBg.setStyle(Paint.Style.FILL);

        paintBorder.setColor(Color.parseColor("#333333"));
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(2f);
        paintBorder.setAntiAlias(true);

        paintText.setColor(Color.parseColor("#A0A0A0"));
        paintText.setTextSize(22f);
        paintText.setAntiAlias(true);
        paintText.setTextAlign(Paint.Align.CENTER);
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
        if (running) {
            sessionStartTime = System.currentTimeMillis();
        }
        invalidate();
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // Делаем канвас строго квадратным (ширина равна высоте)
        int width = getMeasuredWidth();
        if (width > 0) {
            setMeasuredDimension(width, width);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // Рисуем фон и рамку квадрата
        canvas.drawRect(0, 0, w, h, paintBg);
        canvas.drawRect(0, 0, w, h, paintBorder);

        float progress = 0f; // от 0 до 1 внутри цикла
        boolean isInhale = true;

        if (isRunning) {
            long elapsed = System.currentTimeMillis() - sessionStartTime;
            float cycleTime = (elapsed % CYCLE_DURATION_MS);
            progress = cycleTime / CYCLE_DURATION_MS; // 0.0 -> 1.0 за 10 сек
            isInhale = (cycleTime <= 5000f); // Первые 5 сек — вдох, вторые 5 сек — выдох
        }

        // Фазовая интерполяция дыхания (синусоидальная плавность)
        // На вдохе (0 -> 0.5) фаза идет от 0 до 1. На выдохе (0.5 -> 1.0) от 1 до 0.
        float breathPhase;
        if (isInhale) {
            float t = (progress * 2f); // 0 -> 1 за 5с
            breathPhase = (float) (1.0 - Math.cos(t * Math.PI)) / 2f; // плавный синус
        } else {
            float t = ((progress - 0.5f) * 2f); // 0 -> 1 за 5с
            breathPhase = (float) (1.0 + Math.cos(t * Math.PI)) / 2f; // синус обратно к 0
        }

        // Интерполяция цветов градиента: Розовый (#E91E63) <-> Голубой (#00BCD4)
        int pinkColor = Color.argb(140, 233, 30, 99);
        int cyanColor = Color.argb(140, 0, 188, 212);
        
        int currentCoreColor = interpolateColor(pinkColor, cyanColor, breathPhase);
        int currentOuterColor = interpolateColor(
                Color.argb(40, 233, 30, 99),
                Color.argb(40, 0, 188, 212),
                breathPhase
        );

        // Кинематика вращения и радиусов
        // Вдох: вращение в одну сторону, выдох: в противоположную
        float baseRotation = isRunning ? (System.currentTimeMillis() - sessionStartTime) / 40f : 0f;
        float rotationAngle = isInhale ? baseRotation : -baseRotation;

        float maxRadius = w * 0.22f;
        float minRadius = w * 0.08f;
        float currentRadius = minRadius + (maxRadius - minRadius) * breathPhase;

        // Орбитальное смещение лепестков фрактала
        float maxOrbit = w * 0.15f;
        float currentOrbit = maxOrbit * breathPhase;

        canvas.save();
        canvas.rotate(rotationAngle, cx, cy);

        int petalCount = 6;
        for (int i = 0; i < petalCount; i++) {
            double angle = (i * 2.0 * Math.PI) / petalCount;
            float px = cx + (float) (Math.cos(angle) * currentOrbit);
            float py = cy + (float) (Math.sin(angle) * currentOrbit);

            // Радиальный градиент для каждого лепестка для передачи полупрозрачности и объема
            RadialGradient gradient = new RadialGradient(
                    px, py, currentRadius,
                    new int[]{currentCoreColor, currentOuterColor, Color.TRANSPARENT},
                    new float[]{0.0f, 0.6f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            paintPetal.setShader(gradient);
            canvas.drawCircle(px, py, currentRadius, paintPetal);
        }

        // Центральный ядровой лепесток
        RadialGradient centerGradient = new RadialGradient(
                cx, cy, currentRadius * 1.1f,
                new int[]{currentCoreColor, Color.argb(80, Color.red(currentCoreColor), Color.green(currentCoreColor), Color.blue(currentCoreColor)), Color.TRANSPARENT},
                new float[]{0.0f, 0.7f, 1.0f},
                Shader.TileMode.CLAMP
        );
        paintPetal.setShader(centerGradient);
        canvas.drawCircle(cx, cy, currentRadius * 1.1f, paintPetal);

        canvas.restore();

        // Текст статуса дыхания под объектом
        String statusText = !isRunning ? "Ожидание сессии" : (isInhale ? "Breathe in" : "Breathe out");
        canvas.drawText(statusText, cx, h - 20f, paintText);

        if (isRunning) {
            postInvalidateOnAnimation();
        }
    }

    private int interpolateColor(int colorA, int colorB, float ratio) {
        int a = (int) (Color.alpha(colorA) + ratio * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + ratio * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + ratio * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + ratio * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
