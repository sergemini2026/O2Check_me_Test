package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrendChartView extends View {
    private final List<DataPoint> points = new ArrayList<>();
    private final Paint paintGrid = new Paint();
    private final Paint paintText = new Paint();
    private final Paint paintSubText = new Paint();
    private final Paint paintSpO2 = new Paint();
    private final Paint paintHR = new Paint();
    private final Paint paintPI = new Paint();
    private final Paint paintCursor = new Paint();
    private final Paint paintTooltipBg = new Paint();

    private Float touchX = null;

    public TrendChartView(Context context) {
        super(context);
        initPaints();
    }

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        paintGrid.setColor(Color.parseColor("#333333"));
        paintGrid.setStrokeWidth(1.5f);

        paintText.setTextSize(22f);
        paintText.setAntiAlias(true);

        paintSubText.setColor(Color.GRAY);
        paintSubText.setTextSize(16f);
        paintSubText.setAntiAlias(true);

        paintSpO2.setColor(Color.CYAN);
        paintSpO2.setStrokeWidth(4f);
        paintSpO2.setStyle(Paint.Style.STROKE);
        paintSpO2.setAntiAlias(true);

        paintHR.setColor(Color.GREEN);
        paintHR.setStrokeWidth(4f);
        paintHR.setStyle(Paint.Style.STROKE);
        paintHR.setAntiAlias(true);

        paintPI.setColor(Color.YELLOW);
        paintPI.setStrokeWidth(4f);
        paintPI.setStyle(Paint.Style.STROKE);
        paintPI.setAntiAlias(true);

        paintCursor.setColor(Color.WHITE);
        paintCursor.setStrokeWidth(2f);
        paintCursor.setAntiAlias(true);

        paintTooltipBg.setColor(Color.parseColor("#CC1E1E1E"));
        paintTooltipBg.setStyle(Paint.Style.FILL);
    }

    public void addDataPoint(DataPoint dp) {
        points.add(dp);
        invalidate();
    }

    public void clearData() {
        points.clear();
        touchX = null;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touchX = event.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchX = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#121212"));

        float w = getWidth();
        float h = getHeight();
        float leftPad = 80f;
        float rightPad = 100f;
        float topPad = 15f;
        float bottomPad = 32f;

        float availableH = h - topPad - bottomPad;
        float zoneH = availableH / 3f;
        float plotW = w - leftPad - rightPad;

        // СКОЛЬЗЯЩЕЕ ОКНО (Показываем только последние 5 минут = 300 секунд)
        float VIEW_WINDOW_SEC = 300f;
        float lastSec = points.isEmpty() ? 0 : points.get(points.size() - 1).elapsedSec;
        float startSec = Math.max(0, lastSec - VIEW_WINDOW_SEC);
        float endSec = Math.max(startSec + VIEW_WINDOW_SEC, 60f);
        float timeRange = endSec - startSec;

        // 1. Сетка SpO2
        int[] o2Ticks = {100, 95, 90, 85, 80};
        for (int val : o2Ticks) {
            float ratio = (val - 80f) / (100f - 80f);
            float y = (topPad + zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(val + "%", leftPad + 5f, y + 5f, paintSubText);
        }

        // 2. Расчет ДИНАМИЧЕСКОГО масштаба оси Y для ЧСС только для ВИДИМЫХ точек
        float minHR = Float.MAX_VALUE;
        float maxHR = Float.MIN_VALUE;

        for (DataPoint dp : points) {
            if (dp.elapsedSec >= startSec && dp.elapsedSec <= endSec) {
                if (dp.hr > 0) {
                    if (dp.hr < minHR) minHR = dp.hr;
                    if (dp.hr > maxHR) maxHR = dp.hr;
                }
            }
        }

        if (minHR == Float.MAX_VALUE) {
            minHR = 50f; maxHR = 100f;
        } else if (maxHR - minHR < 6f) {
            float mid = (minHR + maxHR) / 2f;
            minHR = mid - 4f; maxHR = mid + 4f;
        } else {
            minHR -= 2f; maxHR += 2f;
        }

        float hrStep = (maxHR - minHR) / 4f;
        for (int i = 0; i <= 4; i++) {
            float val = maxHR - i * hrStep;
            float ratio = 1.0f - (i / 4.0f);
            float y = (topPad + 2 * zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(String.format(Locale.US, "%.0f", val), leftPad + 5f, y + 5f, paintSubText);
        }

        // 3. Сетка PI
        int[] piTicks = {2, 1, 0};
        for (int val : piTicks) {
            float ratio = (val - 0f) / (2f - 0f);
            float y = (topPad + 3 * zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(val + "%", leftPad + 5f, y + 5f, paintSubText);
        }

        // 4. Временная шкала со скользящим окном
        float timeStepSec = 60f;
        float firstGrid = (float) Math.ceil(startSec / timeStepSec) * timeStepSec;

        for (float t = firstGrid; t <= endSec; t += timeStepSec) {
            float x = leftPad + ((t - startSec) / timeRange) * plotW;
            if (x > w - rightPad) break;
            canvas.drawLine(x, topPad, x, topPad + 3 * zoneH, paintGrid);

            int mins = (int) (t / 60);
            String label = mins + "m";
            canvas.drawText(label, x - 10f, h - 6f, paintSubText);
        }

        paintText.setColor(Color.CYAN); canvas.drawText("O2", 15f, topPad + zoneH * 0.55f, paintText);
        paintText.setColor(Color.GREEN); canvas.drawText("Pulse", 15f, topPad + zoneH * 1.55f, paintText);
        paintText.setColor(Color.YELLOW); canvas.drawText("PI", 15f, topPad + zoneH * 2.55f, paintText);

        if (points.isEmpty()) return;

        DataPoint last = points.get(points.size() - 1);
        paintText.setColor(Color.CYAN); canvas.drawText(last.spo2 + "%", w - rightPad + 15f, topPad + zoneH * 0.55f, paintText);
        paintText.setColor(Color.GREEN); canvas.drawText(last.hr + "", w - rightPad + 15f, topPad + zoneH * 1.55f, paintText);
        paintText.setColor(Color.YELLOW); canvas.drawText(String.format(Locale.US, "%.1f%%", last.pi), w - rightPad + 15f, topPad + zoneH * 2.55f, paintText);

        if (points.size() < 2) return;

        Path pathSpO2 = new Path();
        Path pathHR = new Path();
        Path pathPI = new Path();

        canvas.save();
        canvas.clipRect(leftPad, topPad, w - rightPad, h - bottomPad);

        int count = 0;
        for (DataPoint dp : points) {
            if (dp.elapsedSec >= startSec) count++;
        }

        if (count >= 2) {
            float[] xVec = new float[count];
            float[] ySpO2Vec = new float[count];
            float[] yHRVec = new float[count];
            float[] yPIVec = new float[count];

            int idx = 0;
            for (DataPoint dp : points) {
                if (dp.elapsedSec < startSec) continue;

                float x = leftPad + ((dp.elapsedSec - startSec) / timeRange) * plotW;

                float normSpO2 = (Math.max(80f, Math.min(100f, (float) dp.spo2)) - 80f) / 20f;
                float ySpO2 = (topPad + zoneH) - (normSpO2 * zoneH);

                float normHR = (Math.max(minHR, Math.min(maxHR, (float) dp.hr)) - minHR) / (maxHR - minHR);
                float yHR = (topPad + 2 * zoneH) - (normHR * zoneH);

                float normPI = (Math.max(0f, Math.min(2f, dp.pi)) - 0f) / 2f;
                float yPI = (topPad + 3 * zoneH) - (normPI * zoneH);

                xVec[idx] = x;
                ySpO2Vec[idx] = ySpO2;
                yHRVec[idx] = yHR;
                yPIVec[idx] = yPI;
                idx++;
            }

            buildCubicSplinePath(xVec, ySpO2Vec, pathSpO2);
            buildCubicSplinePath(xVec, yHRVec, pathHR);
            buildCubicSplinePath(xVec, yPIVec, pathPI);

            canvas.drawPath(pathSpO2, paintSpO2);
            canvas.drawPath(pathHR, paintHR);
            canvas.drawPath(pathPI, paintPI);
        } else if (count == 1) {
            for (DataPoint dp : points) {
                if (dp.elapsedSec < startSec) continue;
                float x = leftPad + ((dp.elapsedSec - startSec) / timeRange) * plotW;

                float normSpO2 = (Math.max(80f, Math.min(100f, (float) dp.spo2)) - 80f) / 20f;
                float ySpO2 = (topPad + zoneH) - (normSpO2 * zoneH);

                float normHR = (Math.max(minHR, Math.min(maxHR, (float) dp.hr)) - minHR) / (maxHR - minHR);
                float yHR = (topPad + 2 * zoneH) - (normHR * zoneH);

                float normPI = (Math.max(0f, Math.min(2f, dp.pi)) - 0f) / 2f;
                float yPI = (topPad + 3 * zoneH) - (normPI * zoneH);

                canvas.drawPoint(x, ySpO2, paintSpO2);
                canvas.drawPoint(x, yHR, paintHR);
                canvas.drawPoint(x, yPI, paintPI);
            }
        }

        canvas.restore();

        if (touchX != null && touchX >= leftPad && touchX <= w - rightPad) {
            canvas.drawLine(touchX, topPad, touchX, topPad + 3 * zoneH, paintCursor);

            float touchRatio = (touchX - leftPad) / plotW;
            float targetSec = startSec + (touchRatio * timeRange);

            DataPoint closest = null;
            float minDiff = Float.MAX_VALUE;
            for (DataPoint dp : points) {
                float diff = Math.abs(dp.elapsedSec - targetSec);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = dp;
                }
            }

            if (closest != null) {
                String info = String.format(Locale.US, "[%dm%ds] O2:%d%% | HR:%d | PI:%.1f%%",
                        (int) closest.elapsedSec / 60, (int) closest.elapsedSec % 60,
                        closest.spo2, closest.hr, closest.pi);

                float boxW = 390f;
                float boxH = 36f;
                float boxX = Math.min(Math.max(touchX - boxW / 2f, leftPad), w - rightPad - boxW);
                float boxY = topPad + 2f;

                canvas.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, paintTooltipBg);
                paintText.setColor(Color.WHITE);
                paintText.setTextSize(20f);
                canvas.drawText(info, boxX + 10f, boxY + 25f, paintText);
            }
        }
    }

    private void buildCubicSplinePath(float[] x, float[] y, Path path) {
        path.reset();
        int n = x.length;
        if (n == 0) return;

        if (n == 1) {
            path.moveTo(x[0], y[0]);
            return;
        }

        if (n == 2) {
            path.moveTo(x[0], y[0]);
            path.lineTo(x[1], y[1]);
            return;
        }

        path.moveTo(x[0], y[0]);

        for (int i = 0; i < n - 1; i++) {
            float p0x = (i == 0) ? (2 * x[0] - x[1]) : x[i - 1];
            float p0y = (i == 0) ? (2 * y[0] - y[1]) : y[i - 1];

            float p1x = x[i];
            float p1y = y[i];

            float p2x = x[i + 1];
            float p2y = y[i + 1];

            float p3x = (i + 2 < n) ? x[i + 2] : (2 * x[n - 1] - x[n - 2]);
            float p3y = (i + 2 < n) ? y[i + 2] : (2 * y[n - 1] - y[n - 2]);

            float ctrl1x = p1x + (p2x - p0x) / 6.0f;
            float ctrl1y = p1y + (p2y - p0y) / 6.0f;

            float ctrl2x = p2x - (p3x - p1x) / 6.0f;
            float ctrl2y = p2y - (p3y - p1y) / 6.0f;

            path.cubicTo(ctrl1x, ctrl1y, ctrl2x, ctrl2y, p2x, p2y);
        }
    }
}
