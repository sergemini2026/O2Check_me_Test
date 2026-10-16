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
    
    // Непрерывное время для идеальной плавной анимации MasterClock (10 секунд на цикл)
    private long lastFrameTime = 0;
    private float animationProgress = 0f; // [0.0 ... 1.0]
    private static final float CYCLE_DURATION_MS = 10000f; // 5с вдох, 5с выдох
    
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
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
        // Принудительно выключаем активность при создании/перезаходе
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
                if (dt > 500) dt = 16; // Защита от резких скачков времени при возврате из фона

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
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Если вьюха стала невидимой или свернулась — останавливаем тикер во избежание фоновой работы
        if (visibility != VISIBLE) {
            pauseAnimation();
        } else if (isMonitoringActive) {
            resumeAnimation();
        }
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

        // Если мониторинг неактивен — рисуем статичный сплошной розовый круг в центре (исходная точка выдоха)
        if (!isMonitoringActive) {
            corePaint.setShader(new RadialGradient(100, 100, 18f,
                    new int[]{0xFFFF4081, 0xCCFF4081}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(100, 100, 18f, corePaint);
            canvas.restore();
            return;
        }

        // Фаза дыхания: 0.0 -> 0.5 (Вдох: сжатый розовый -> раскрытый голубой калейдоскоп), 
        // 0.5 -> 1.0 (Выдох: голубой калейдоскоп -> сжатие обратно в чистый розовый круг)
        float breathPhase;
        boolean isInhale;
        if (animationProgress <= 0.5f) {
            breathPhase = animationProgress * 2.0f; // Вдох (0.0 -> 1.0)
            isInhale = true;
        } else {
            breathPhase = 2.0f - (animationProgress * 2.0f); // Выдох (1.0 -> 0.0)
            isInhale = false;
        }

        // Вращение всей структуры
        canvas.save();
        float activeRotation = isInhale ? rotationAngle : -rotationAngle;
        canvas.rotate(activeRotation, 100f, 100f);

        // Чистые цвета: Розовый (#FF4081) при выдохе (breathPhase = 0), Голубой (#00E5FF) при вдохе (breathPhase = 1)
        int startColor = interpolateColor(0xDDFF4081, 0xDD00E5FF, breathPhase);
        int endColor = interpolateColor(0x20FF4081, 0x2000E5FF, breathPhase);

        // Элегантный вариант EliteHRV: ровно 8 лепестков + легкое расширение кольца
        int petalCount = 8;
        float distance = 28f * breathPhase;
        float petalRadius = 14f + 8f * breathPhase;

        for (int i = 0; i < petalCount; i++) {
            double angle = i * (2.0 * Math.PI / petalCount);
            float cx = 100f + (float)(distance * Math.cos(angle));
            float cy = 100f + (float)(distance * Math.sin(angle));

            elementPaint.setShader(new RadialGradient(cx, cy, Math.max(1f, petalRadius),
                    new int[]{startColor, endColor}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, petalRadius, elementPaint);
        }

        canvas.restore();

        // Центральное ядро: при breathPhase -> 0 (выдох) превращается в чистый сплошной розовый круг
        float coreRadius = 12f + 10f * breathPhase;
        int coreColor1 = interpolateColor(0xFFFF4081, 0xFFEEFFFF, breathPhase);
        int coreColor2 = interpolateColor(0xEEFF4081, 0x4400E5FF, breathPhase);
        
        corePaint.setShader(new RadialGradient(100, 100, coreRadius,
                new int[]{coreColor1, coreColor2}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
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
