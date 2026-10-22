package com.local.o2test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private boolean isMonitoringActive = false;
    private float animationProgress = 0f; // [0.0 ... 1.0]
    
    private ValueAnimator breathAnimator;
    private ValueAnimator shrinkAnimator;

    public BreatheAnimationView(Context context) {
        super(context);
        init();
    }

    public BreatheAnimationView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);

        // Настройка оранжевого цвета шкалы и делений
        scalePaint.setColor(0xFFFF6D00);
        scalePaint.setStrokeWidth(2f);
        scalePaint.setStyle(Paint.Style.STROKE);

        // Настройка оранжевого цвета для цифр шкалы
        textPaint.setColor(0xFFFF6D00);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        isMonitoringActive = false;
    }

    public void setRunning(boolean running) {
        if (running) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    public void startMonitoring() {
        startMonitoringWithPhase(0.0f);
    }

    public void startMonitoringWithPhase(float initialPhase) {
        if (shrinkAnimator != null) {
            shrinkAnimator.cancel();
            shrinkAnimator = null;
        }
        isMonitoringActive = true;
        float phase = initialPhase % 1.0f;
        if (phase < 0) phase += 1.0f;

        animationProgress = phase;
        invalidate();
    }

    public void updatePhaseExternal(float phase) {
        if (!isMonitoringActive) {
            isMonitoringActive = true;
        }
        float normalized = phase % 1.0f;
        if (normalized < 0) normalized += 1.0f;
        this.animationProgress = normalized;
        invalidate();
    }

    public void stopMonitoring() {
        if (!isMonitoringActive && shrinkAnimator == null) return;

        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }

        if (shrinkAnimator != null) {
            shrinkAnimator.cancel();
        }

        shrinkAnimator = ValueAnimator.ofFloat(animationProgress, 0.0f);
        shrinkAnimator.setDuration(600);
        shrinkAnimator.setInterpolator(new DecelerateInterpolator());
        shrinkAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        shrinkAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isMonitoringActive = false;
                animationProgress = 0f;
                shrinkAnimator = null;
                invalidate();
            }
        });
        shrinkAnimator.start();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopMonitoring();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopMonitoring();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        if (width > 0) {
            setMeasuredDimension(width, width);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f;
        
        // Уменьшение физического диаметра канваса на 1 см (уменьшение радиуса на 5 мм)
        float radiusReduction = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_MM, 5f, getResources().getDisplayMetrics()
        );
        
        float padding = 35f * (width / 300f);
        float radius = (Math.min(width, height) / 2f) - padding - radiusReduction;

        // 1. Отрисовка разметочной шкалы циферблата (от 6 до 14 с половинными делениями)
        // Всего 16 шагов по 0.5 (диапазон от 6 до 14 = 8 единиц), шаг угла = 18.75 градусов (300 градусов общая дуга)
        int totalSteps = 16; 
        float scaleRadius = radius + 8f; 
        float textRadius = radius + 26f;

        for (int i = 0; i <= totalSteps; i++) {
            float val = 6f + (i * 0.5f);
            
            // Центр шкалы (значение 10, i = 8) находится строго вверху (-Math.PI / 2)
            double angle = -Math.PI / 2 + (i - 8) * Math.toRadians(18.75);

            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            boolean isInteger = (val % 1.0f == 0);
            float tickLen = isInteger ? 10f : 5f;
            
            scalePaint.setStrokeWidth(isInteger ? 2.5f : 1.5f);
            float startX = centerX + scaleRadius * cosA;
            float startY = centerY + scaleRadius * sinA;
            float endX = centerX + (scaleRadius + tickLen) * cosA;
            float endY = centerY + (scaleRadius + tickLen) * sinA;
            canvas.drawLine(startX, startY, endX, endY, scalePaint);

            if (isInteger) {
                float txtX = centerX + (textRadius + 4f) * cosA;
                float txtY = centerY + (textRadius + 4f) * sinA + 8f; 
                canvas.drawText(String.format(Locale.US, "%d", (int) val), txtX, txtY, textPaint);
            }
        }

        // 2. Обрезка внутреннего холста пейсера строго по кругу внутри шкалы (Save 1)
        clipPath.reset();
        clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);

        // Рисуем фоновую заливку пейсера
        canvas.drawCircle(centerX, centerY, radius, bgPaint);

        float scale = radius / 100f;
        float dx = centerX - 100f * scale;
        float dy = centerY - 100f * scale;

        // Сохранение для масштабирования/трансляции (Save 2)
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        if (!isMonitoringActive && shrinkAnimator == null) {
            petalPaint.setShader(new RadialGradient(100, 100, 24f,
                    new int[]{0xFFFF2D55, 0xDDFF2D55}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(100, 100, 24f, petalPaint);
            canvas.restore(); // Восстановление Save 2
            canvas.restore(); // Восстановление Save 1
            return;
        }

        float breathPhase;
        if (animationProgress <= 0.5f) {
            float t = animationProgress * 2.0f;
            breathPhase = (float)(1.0 - Math.cos(t * Math.PI)) / 2f;
        } else {
            float t = (animationProgress - 0.5f) * 2.0f;
            breathPhase = (float)(1.0 + Math.cos(t * Math.PI)) / 2f;
        }

        float activeRotation = (float) (Math.sin(animationProgress * 2.0 * Math.PI) * 45.0f);

        // Сохранение для анимации вращения (Save 3)
        canvas.save();
        canvas.rotate(activeRotation, 100f, 100f);

        int innerColor = interpolateColor(0xFFFF2D55, 0xCCFF4081, breathPhase);
        int outerColor = interpolateColor(0xC8FF2D55, 0x40FF4081, breathPhase);

        int petalCount = 6;
        float orbitDistance = 28f * breathPhase; 
        float petalRadius = 26f;

        for (int i = 0; i < petalCount; i++) {
            double angle = i * (2.0 * Math.PI / petalCount);
            float cx = 100f + (float)(orbitDistance * Math.cos(angle));
            float cy = 100f + (float)(orbitDistance * Math.sin(angle));

            petalPaint.setShader(new RadialGradient(cx, cy, petalRadius,
                    new int[]{innerColor, outerColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, petalRadius, petalPaint);
        }

        petalPaint.setShader(new RadialGradient(100, 100, petalRadius,
                new int[]{innerColor, outerColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, petalRadius, petalPaint);

        canvas.restore(); // Восстановление Save 3 (вращение)
        canvas.restore(); // Восстановление Save 2 (трансляция/масштаб)
        canvas.restore(); // Восстановление Save 1 (clipPath)
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
