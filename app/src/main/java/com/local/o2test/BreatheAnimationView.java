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
    private static final float DEFAULT_POINTER_VALUE_SEC = 12.0f;
    private static final float MIN_POINTER_VALUE_SEC = 6.0f;
    private static final float MAX_POINTER_VALUE_SEC = 18.0f;
    private static final float POINTER_STEP_SEC = 0.5f;
    private static final int TOTAL_SCALE_STEPS = 24;
    private static final int CENTER_STEP_INDEX = 12; // 12.0 с в верхней точке дуги
    private static final float SCALE_ARC_SWEEP_DEG = 300.0f;
    private static final float STEP_ANGLE_DEG = SCALE_ARC_SWEEP_DEG / TOTAL_SCALE_STEPS; // 300.0f / 24 = 12.5f

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orangeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerOrangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private boolean isMonitoringActive = false;
    private float animationProgress = 0f; // [0.0 ... 1.0] для лепестков пейсера
    private float progress = 1.0f;         // [0.0 ... 1.0] для внешнего кольца прогресса

    // Шаг Б.3: текущее соотношение фаз дыхательного цикла ("1/1" — 50/50 или "1/2" — 1/3 вдох / 2/3 выдох)
    private String breathRatio = "1/1";

    private float pointerValue = DEFAULT_POINTER_VALUE_SEC; // Текущее значение указателя [6.0 ... 18.0], шаг 0.5

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

        whiteRingPaint.setColor(0xFFFFFFFF);
        whiteRingPaint.setStyle(Paint.Style.STROKE);
        whiteRingPaint.setStrokeWidth(5.0f);
        whiteRingPaint.setAntiAlias(true);

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

    /**
     * Шаг Б.3: установка соотношения фаз ("1/1" или "1/2") для визуального пейсера.
     */
    public void setBreathRatio(String ratio) {
        String normalized = ("1/2".equals(ratio != null ? ratio.trim() : "")) ? "1/2" : "1/1";
        if (!normalized.equals(this.breathRatio)) {
            this.breathRatio = normalized;
            invalidate();
        }
    }

    public String getBreathRatio() {
        return breathRatio;
    }

    /**
     * Шаг Б.1 / Б.3: маппинг сырой фазы [0.0..1.0) в отображаемую фазу [0.0..1.0)
     * с учётом выбранного соотношения вдох/выдох ("1/1" — 50/50, "1/2" — 1/3 вдох, 2/3 выдох).
     */
    private static float mapPhaseForRatio(float rawPhase, String ratio) {
        float p = rawPhase % 1.0f;
        if (p < 0f) {
            p += 1.0f;
        }
        if ("1/2".equals(ratio)) {
            float inhaleSplit = 1.0f / 3.0f;
            if (p < inhaleSplit) {
                return 0.5f * (p / inhaleSplit);
            } else {
                return 0.5f + 0.5f * ((p - inhaleSplit) / (1.0f - inhaleSplit));
            }
        }
        return p;
    }

    public float getPointerValue() {
        return pointerValue;
    }

    public void setPointerValue(float value) {
        this.pointerValue = Math.max(MIN_POINTER_VALUE_SEC, Math.min(MAX_POINTER_VALUE_SEC, value));
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
                int totalSteps = TOTAL_SCALE_STEPS;
                float radiusReduction = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_MM, 5f, getResources().getDisplayMetrics()
                );
                float padding = 35f * (width / 300f);
                float radius = (Math.min(width, height) / 2f) - padding - radiusReduction;
                float scaleRadius = radius + 8f;

                int bestIndex = 0;
                float minSqDist = Float.MAX_VALUE;

                for (int i = 0; i <= totalSteps; i++) {
                    double angle = -Math.PI / 2 + (i - CENTER_STEP_INDEX) * Math.toRadians(STEP_ANGLE_DEG);
                    float tx = centerX + scaleRadius * (float) Math.cos(angle);
                    float ty = centerY + scaleRadius * (float) Math.sin(angle);

                    float sqDist = (x - tx) * (x - tx) + (y - ty) * (y - ty);
                    if (sqDist < minSqDist) {
                        minSqDist = sqDist;
                        bestIndex = i;
                    }
                }

                float newVal = MIN_POINTER_VALUE_SEC + (bestIndex * POINTER_STEP_SEC);
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
            // Шаг Б.3: применяем маппинг фазы ("1/1" -> 0.5 разворот, "1/2" -> 1/3 вдох / 2/3 выдох)
            float mappedProgress = mapPhaseForRatio(animationProgress, breathRatio);

            float breathPhase;
            if (mappedProgress <= 0.5f) {
                float t = mappedProgress * 2.0f;
                breathPhase = (float) (1.0 - Math.cos(t * Math.PI)) / 2f;
            } else {
                float t = (mappedProgress - 0.5f) * 2.0f;
                breathPhase = (float) (1.0 + Math.cos(t * Math.PI)) / 2f;
            }

            float activeRotation = (float) (Math.sin(mappedProgress * 2.0 * Math.PI) * 45.0f);

            canvas.save();
            canvas.rotate(activeRotation, 100f, 100f);

            int innerColor = interpolateColor(0xFFFF2D55, 0xCCFF4081, breathPhase);
            int outerColor = interpolateColor(0xC8FF2D55, 0x40FF4081, breathPhase);

            int petalCount = 6;
            float orbitDistance = 28f * breathPhase;
            float petalRadius = 26f;

            for (int i = 0; i < petalCount; i++) {
                double angle = i * (2.0 * Math.PI / petalCount);
                float cx = 100f + (float) (orbitDistance * Math.cos(angle));
                float cy = 100f + (float) (orbitDistance * Math.sin(angle));

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

        // 2. Отрисовка шкалы, делений, цифр и внешних колец прогресса
        int totalSteps = TOTAL_SCALE_STEPS;
        float scaleRadius = radius + 8f;
        float textRadius = radius + 26f;

        float ringRadius = textRadius + 32f;
        RectF ringRect = new RectF(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius);

        // Сначала рисуем фоновое белое кольцо (меньший приоритет видимости / подложка)
        canvas.drawCircle(centerX, centerY, ringRadius, whiteRingPaint);

        // Поверх белого рисуем сокращающееся оранжевое кольцо (больший приоритет видимости)
        if (progress >= 1.0f) {
            canvas.drawCircle(centerX, centerY, ringRadius, orangeRingPaint);
        } else if (progress > 0.0f) {
            float sweepAngle = -360.0f * progress;
            canvas.drawArc(ringRect, -90f, sweepAngle, false, orangeRingPaint);
        }

        for (int i = 0; i <= totalSteps; i++) {
            float val = MIN_POINTER_VALUE_SEC + (i * POINTER_STEP_SEC);

            double angle = -Math.PI / 2 + (i - CENTER_STEP_INDEX) * Math.toRadians(STEP_ANGLE_DEG);

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
        float stepIndex = (pointerValue - MIN_POINTER_VALUE_SEC) / POINTER_STEP_SEC;
        double pointerAngle = -Math.PI / 2 + (stepIndex - CENTER_STEP_INDEX) * Math.toRadians(STEP_ANGLE_DEG);
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
