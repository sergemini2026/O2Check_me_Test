package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orangeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerOrangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private boolean isMonitoringActive = false;
    private float animationProgress = 0f; // [0.0 ... 1.0] для лепестков пейсера
    private float progress = 1.0f;         // [0.0 ... 1.0] для внешнего кольца прогресса
    
    private float pointerValue = 10.0f;    // Текущее значение указателя [6.0 ... 14.0], шаг 0.5

    private OnPointerChangeListener pointerChangeListener;

    public interface OnPointerChangeListener {
        void onPointerValueChanged(float value);
    }

    public void setOnPointerChangeListener(OnPointerChangeListener listener) {
        this.pointerChangeListener = listener;
    }

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

        scalePaint.setColor(0xFFFF6D00);
        scalePaint.setStrokeWidth(2f);
        scalePaint.setStyle(Paint.Style.STROKE);

        orangeRingPaint.setColor(0xFFFF6D00);
        orangeRingPaint.setStyle(Paint.Style.STROKE);
        orangeRingPaint.setStrokeWidth(5.0f);
        orangeRingPaint.setAntiAlias(true);

        textPaint.setColor(0xFFFF6D00);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        pointerOrangePaint.setColor(0xFFFF6D00);
        pointerOrangePaint.setStyle(Paint.Style.STROKE);
        pointerOrangePaint.setStrokeWidth(5.0f);
        pointerOrangePaint.setAntiAlias(true);

        pointerWhitePaint.setColor(0xFFFFFFFF);
        pointerWhitePaint.setStyle(Paint.Style.STROKE);
        pointerWhitePaint.setStrokeWidth(5.0f);
        pointerWhitePaint.setAntiAlias(true);

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        isMonitoringActive = false;
        progress = 1.0f;
    }

    public float getPointerValue() {
        return pointerValue;
    }

    public void setPointerValue(float value) {
        this.pointerValue = Math.max(6.0f, Math.min(14.0f, value));
        invalidate();
    }

    /**
     * Чистая установка прогресса внешнего кольца (управляется BreatheProgressManager)
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        invalidate();
    }

    public void startMonitoring() {
        isMonitoringActive = true;
        invalidate();
    }

    public void stopMonitoring() {
        isMonitoringActive = false;
        animationProgress = 0f;
        progress = 1.0f;
        invalidate();
    }

    /**
     * Обновление фазы исключительно для анимации внутренних лепестков пейсера
     */
    public void updatePhaseExternal(float phase) {
        if (!isMonitoringActive) {
            isMonitoringActive = true;
        }
        float normalized = phase % 1.0f;
        if (normalized < 0) normalized += 1.0f;
        this.animationProgress = normalized;
        invalidate();
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
    public boolean onTouchEvent(MotionEvent event) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return super.onTouchEvent(event);

        float centerX = width / 2f;
        float centerY = height / 2f;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                int totalSteps = 16;
                float radiusReduction = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_MM, 5f, getResources().getDisplayMetrics()
                );
                float padding = 35f * (width / 300f);
                float radius = (Math.min(width, height) / 2f) - padding - radiusReduction;
                float scaleRadius = radius + 8f;

                int bestIndex = 0;
                float minSqDist = Float.MAX_VALUE;

                for (int i = 0; i <= totalSteps; i++) {
                    double angle = -Math.PI / 2 + (i - 8) * Math.toRadians(18.75);
                    float tx = centerX + scaleRadius * (float) Math.cos(angle);
                    float ty = centerY + scaleRadius * (float) Math.sin(angle);

                    float sqDist = (x - tx) * (x - tx) + (y - ty) * (y - ty);
                    if (sqDist < minSqDist) {
                        minSqDist = sqDist;
                        bestIndex = i;
                    }
                }

                float newVal = 6f + (bestIndex * 0.5f);
                if (pointerValue != newVal) {
                    pointerValue = newVal;
                    invalidate();
                    if (pointerChangeListener != null) {
                        pointerChangeListener.onPointerValueChanged(pointerValue);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2f;
        float centerY = height / 2f;
        
        float radiusReduction = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_MM, 5f, getResources().getDisplayMetrics()
        );
        
        float padding = 35f * (width / 300f);
        float radius = (Math.min(width, height) / 2f) - padding - radiusReduction;

        // 1. Анимированный фрактальный пейсер внутри круга
        clipPath.reset();
        clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);

        canvas.drawCircle(centerX, centerY, radius, bgPaint);

        float scale = radius / 100f;
        float dx = centerX - 100f * scale;
        float dy = centerY - 100f * scale;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        if (!isMonitoringActive) {
            petalPaint.setShader(new RadialGradient(100, 100, 24f,
                    new int[]{0xFFFF2D55, 0xDDFF2D55}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(100, 100, 24f, petalPaint);
            canvas.restore(); 
            canvas.restore(); 
        } else {
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
            canvas.restore(); 
        }

        // 2. Отрисовка шкалы, делений, цифр и внешнего кольца прогресса
        int totalSteps = 16; 
        float scaleRadius = radius + 8f; 
        float textRadius = radius + 26f;

        float ringRadius = textRadius + 32f;
        RectF ringRect = new RectF(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius);

        // Рендеринг внешнего кольца прогресса
        if (progress >= 1.0f) {
            canvas.drawCircle(centerX, centerY, ringRadius, orangeRingPaint);
        } else if (progress > 0.0f) {
            float sweepAngle = -360.0f * progress;
            canvas.drawArc(ringRect, -90f, sweepAngle, false, orangeRingPaint);
        }

        for (int i = 0; i <= totalSteps; i++) {
            float val = 6f + (i * 0.5f);
            
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

        // Указатель поверх шкалы
        float stepIndex = (pointerValue - 6.0f) / 0.5f;
        double pointerAngle = -Math.PI / 2 + (stepIndex - 8) * Math.toRadians(18.75);
        float cosP = (float) Math.cos(pointerAngle);
        float sinP = (float) Math.sin(pointerAngle);

        float arcRadius1 = radius - 5f;
        float arcRadius2 = radius - 11f;
        float angleDeg = (float) Math.toDegrees(pointerAngle);
        float sweepDeg = 10f;
        float startDeg = angleDeg - sweepDeg / 2f;

        RectF rect1 = new RectF(centerX - arcRadius1, centerY - arcRadius1, centerX + arcRadius1, centerY + arcRadius1);
        RectF rect2 = new RectF(centerX - arcRadius2, centerY - arcRadius2, centerX + arcRadius2, centerY + arcRadius2);

        canvas.drawArc(rect1, startDeg, sweepDeg, false, pointerOrangePaint);
        canvas.drawArc(rect2, startDeg, sweepDeg, false, pointerOrangePaint);

        float whiteStartRadius = radius + 3f;
        float whiteEndRadius = radius + 12f;
        float pStartX = centerX + whiteStartRadius * cosP;
        float pStartY = centerY + whiteStartRadius * sinP;
        float pEndX = centerX + whiteEndRadius * cosP;
        float pEndY = centerY + whiteEndRadius * sinP;

        canvas.drawLine(pStartX, pStartY, pEndX, pEndY, pointerWhitePaint);
    }

    private int interpolateColor(int colorA, int colorB, float fraction) {
        int a = (int) (Color.alpha(colorA) + fraction * (Color.alpha(colorB) - Color.alpha(colorA)));
        int r = (int) (Color.red(colorA) + fraction * (Color.red(colorB) - Color.red(colorA)));
        int g = (int) (Color.green(colorA) + fraction * (Color.green(colorB) - Color.green(colorA)));
        int b = (int) (Color.blue(colorA) + fraction * (Color.blue(colorB) - Color.blue(colorA)));
        return Color.argb(a, r, g, b);
    }
}
