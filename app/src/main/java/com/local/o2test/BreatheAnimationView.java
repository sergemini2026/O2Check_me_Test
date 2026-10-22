package com.local.o2test;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    /**
     * Плавная остановка пейсера с мягким сжатием в исходную точку (без рывков)
     */
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
        shrinkAnimator.setDuration(600); // Плавное сжатие за 600 мс
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

        // Создаем круглый контур для обрезки (клиппинга) холста
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) / 2f;

        clipPath.reset();
        clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW);

        // Обрезаем весь последующий вывод по кругу
        canvas.save();
        canvas.clipPath(clipPath);

        // Рисуем круглую фоновую заливку пейсера
        canvas.drawCircle(centerX, centerY, radius, bgPaint);

        float scale = Math.min(width, height) / 200f;
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        if (!isMonitoringActive && shrinkAnimator == null) {
            petalPaint.setShader(new RadialGradient(100, 100, 24f,
                    new int[]{0xFFFF2D55, 0xDDFF2D55}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(100, 100, 24f, petalPaint);
            canvas.restore();
            canvas.restore();
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

        canvas.restore();
        canvas.restore();
        canvas.restore(); // Восстанавливаем состояние холста после clipPath
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
