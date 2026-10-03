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

        // 1. Сетка и метки SpO2 (Фиксировано 80% - 100%)
        int[] o2Ticks = {100, 95, 90, 85, 80};
        for (int val : o2Ticks) {
            float ratio = (val - 80f) / (100f - 80f);
            float y = (topPad + zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(val + "%", leftPad + 5f, y + 5f, paintSubText);
        }

        // 2. Расчет ДИНАМИЧЕСКОГО масштаба оси Y для ЧСС (Pulse)
        float minHR = Float.MAX_VALUE;
        float maxHR = Float.MIN_VALUE;

        for (DataPoint dp : points) {
            if (dp.hr > 0) {
                if (dp.hr < minHR) minHR = dp.hr;
                if (dp.hr > maxHR) maxHR = dp.hr;
            }
        }

        if (minHR == Float.MAX_VALUE) {
            minHR = 50f;
            maxHR = 100f;
        } else if (maxHR - minHR < 6f) {
            float mid = (minHR + maxHR) / 2f;
            minHR = mid - 4f;
            maxHR = mid + 4f;
        } else {
            minHR -= 2f;
            maxHR += 2f;
        }

        // Динамическая сетка оси Y для пульса (5 меток)
        float hrStep = (maxHR - minHR) / 4f;
        for (int i = 0; i <= 4; i++) {
            float val = maxHR - i * hrStep;
            float ratio = 1.0f - (i / 4.0f);
            float y = (topPad + 2 * zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(String.format(Locale.US, "%.0f", val), leftPad + 5f, y + 5f, paintSubText);
        }

        // 3. Сетка PI (0 - 2%)
        int[] piTicks = {2, 1, 0};
        for (int val : piTicks) {
            float ratio = (val - 0f) / (2f - 0f);
            float y = (topPad + 3 * zoneH) - ratio * zoneH;
            canvas.drawLine(leftPad, y, w - rightPad, y, paintGrid);
            canvas.drawText(val + "%", leftPad + 5f, y + 5f, paintSubText);
        }

        // 4. Временная шкала
        // Приведение типов для корректной работы с float/int временем
        float lastSec = points.isEmpty() ? 0 : points.get(points.size() - 1).elapsedSec;
        float maxTime = Math.max(60, lastSec);
        float timeStepSec = (maxTime > 1800) ? 900f : ((maxTime > 300) ? 300f : 60f);

        for (float t = 0; t <= maxTime; t += timeStepSec) {
            float x = leftPad + (t / maxTime) * plotW;
            canvas.drawLine(x, topPad, x, topPad + 3 * zoneH, paintGrid);

            int mins = (int) (t / 60);
            String label = mins + "m";
            canvas.drawText(label, x - 10f, h - 6f, paintSubText);
        }

        paintText.setColor(Color.CYAN);
        canvas.drawText("O2", 15f, topPad + zoneH * 0.55f, paintText);

        paintText.setColor(Color.GREEN);
        canvas.drawText("Pulse", 15f, topPad + zoneH * 1.55f, paintText);

        paintText.setColor(Color.YELLOW);
        canvas.drawText("PI", 15f, topPad + zoneH * 2.55f, paintText);

        if (points.isEmpty()) return;

        DataPoint last = points.get(points.size() - 1);
        paintText.setColor(Color.CYAN);
        canvas.drawText(last.spo2 + "%", w - rightPad + 15f, topPad + zoneH * 0.55f, paintText);

        paintText.setColor(Color.GREEN);
        // Приведение для ЧСС, чтобы предотвратить вывод дробей при целочисленных значениях датчика
        canvas.drawText((int)last.hr + "", w - rightPad + 15f, topPad + zoneH * 1.55f, paintText);

        paintText.setColor(Color.YELLOW);
        canvas.drawText(String.format(Locale.US, "%.1f%%", last.pi), w - rightPad + 15f, topPad + zoneH * 2.55f, paintText);

        if (points.size() < 2) return;

        Path pathSpO2 = new Path();
        Path pathHR = new Path();
        Path pathPI = new Path();

        float prevX = 0, prevYSpO2 = 0, prevYPI = 0;
        // Переменная prevYHR больше не нужна для расчета середины Безье, так как используем прямые линии

        for (int i = 0; i < points.size(); i++) {
            DataPoint dp = points.get(i);
            
            // Если elapsedSec переведен во float, график по оси X будет абсолютно плавным
            float x = leftPad + (dp.elapsedSec / maxTime) * plotW; 

            float minSpO2 = 80f, maxSpO2 = 100f;
            float normSpO2 = (Math.max(minSpO2, Math.min(maxSpO2, (float) dp.spo2)) - minSpO2) / (maxSpO2 - minSpO2);
            float ySpO2 = (topPad + zoneH) - (normSpO2 * zoneH);

            // Динамическая шкала для ЧСС отработана отлично
            float normHR = (Math.max(minHR, Math.min(maxHR, (float) dp.hr)) - minHR) / (maxHR - minHR);
            float yHR = (topPad + 2 * zoneH) - (normHR * zoneH);

            float minPI = 0f, maxPI = 2f;
            float normPI = (Math.max(minPI, Math.min(maxPI, dp.pi)) - minPI) / (maxPI - minPI);
            float yPI = (topPad + 3 * zoneH) - (normPI * zoneH);

            if (i == 0) {
                pathSpO2.moveTo(x, ySpO2);
                pathHR.moveTo(x, yHR);
                pathPI.moveTo(x, yPI);
            } else {
                // Для SpO2 и PI оставляем сглаживание (они меняются плавно)
                float midX = (prevX + x) / 2f;
                float midYSpO2 = (prevYSpO2 + ySpO2) / 2f;
                float midYPI = (prevYPI + yPI) / 2f;

                pathSpO2.quadTo(prevX, prevYSpO2, midX, midYSpO2);
                pathPI.quadTo(prevX, prevYPI, midX, midYPI);

                // КРИТИЧНО ДЛЯ POLAR: Строим прямую линию точно в фактическую координату (x, yHR),
                // чтобы не срезать микро-колебания ЧСС (вариабельность).
                pathHR.lineTo(x, yHR);
            }
            prevX = x;
            prevYSpO2 = ySpO2;
            prevYPI = yPI;
        }
        
        // Доводим сглаженные графики до последней фактической точки (для HR этого делать не нужно, он уже там)
        pathSpO2.lineTo(prevX, prevYSpO2);
        pathPI.lineTo(prevX, prevYPI);

        canvas.drawPath(pathSpO2, paintSpO2);
        canvas.drawPath(pathHR, paintHR);
        canvas.drawPath(pathPI, paintPI);

        if (touchX != null && touchX >= leftPad && touchX <= w - rightPad) {
            canvas.drawLine(touchX, topPad, touchX, topPad + 3 * zoneH, paintCursor);

            float touchRatio = (touchX - leftPad) / plotW;
            float targetSec = touchRatio * maxTime;

            DataPoint closest = points.get(0);
            float minDiff = Math.abs(closest.elapsedSec - targetSec);
            for (DataPoint dp : points) {
                float diff = Math.abs(dp.elapsedSec - targetSec);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = dp;
                }
            }

            // Добавлено приведение к (int) для elapsedSec и hr, чтобы предотвратить креш String.format,
            // если в DataPoint эти переменные были переведены во float
            String info = String.format(Locale.US, "[%dm%ds] O2:%d%% | HR:%d | PI:%.1f%%",
                    (int)closest.elapsedSec / 60, (int)closest.elapsedSec % 60,
                    (int)closest.spo2, (int)closest.hr, closest.pi);

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
