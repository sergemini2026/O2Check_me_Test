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
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private boolean isMonitoringActive = false;
    
    // Плавная анимация движения из первого рабочего варианта
    private float animationProgress = 0f; // [0.0 ... 1.0]
    private float rotationAngle = 0f;
    
    private ValueAnimator breathAnimator;
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
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
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
        isMonitoringActive = true;
        animationProgress = 0f;
        rotationAngle = 0f;
        
        startBreathAnimator();
        startRotationAnimator();
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
        animationProgress = 0f;
        rotationAngle = 0f;
        invalidate();
    }

    // Плавный зацикленный аниматор дыхания (10 секунд на полный цикл: 5с вдох, 5с выдох)
    private void startBreathAnimator() {
        if (breathAnimator != null) breathAnimator.cancel();

        breathAnimator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator.setDuration(10000);
        breathAnimator.setInterpolator(new LinearInterpolator());
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.addUpdateListener(animation -> {
            if (!isMonitoringActive) return;
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        breathAnimator.start();
    }

    // Плавное вращение структуры без рывков
    private void startRotationAnimator() {
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
        float scale = Math.min(width, height) / 200f;
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        // Карточка-фон
        bgRect.set(0, 0, 200, 200);
        canvas.drawRoundRect(bgRect, 16, 16, bgPaint);

        // Если сессия не активна — статичный сплошной розовый круг (точка выдоха)
        if (!isMonitoringActive) {
            petalPaint.setShader(new RadialGradient(100, 100, 24f,
                    new int[]{0xFFFF2D55, 0xDDFF2D55}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(100, 100, 24f, petalPaint);
            canvas.restore();
            return;
        }

        // Абсолютно плавная синусоидальная фаза дыхания из первого варианта
        float breathPhase;
        boolean isInhale;
        if (animationProgress <= 0.5f) {
            float t = animationProgress * 2.0f; // 0.0 -> 1.0 (Вдох)
            breathPhase = (float)(1.0 - Math.cos(t * Math.PI)) / 2f;
            isInhale = true;
        } else {
            float t = (animationProgress - 0.5f) * 2.0f; // 1.0 -> 0.0 (Выдох)
            breathPhase = (float)(1.0 + Math.cos(t * Math.PI)) / 2f;
            isInhale = false;
        }

        // Плавное вращение всей фигуры
        canvas.save();
        float activeRotation = isInhale ? rotationAngle : -rotationAngle;
        canvas.rotate(activeRotation, 100f, 100f);

        // Цветовые градиенты: при сжатии (breathPhase = 0) — чистый розовый, при раскрытии — голубоватый
        int innerColor = interpolateColor(0xFFFF2D55, 0xCCFF4081, breathPhase);
        int outerColor = interpolateColor(0xC8FF2D55, 0x40FF4081, breathPhase);

        // Геометрия образца: центральный круг + ровно 6 симметричных колец
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

        // Центральное кольцо
        petalPaint.setShader(new RadialGradient(100, 100, petalRadius,
                new int[]{innerColor, outerColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, petalRadius, petalPaint);

        canvas.restore();
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
