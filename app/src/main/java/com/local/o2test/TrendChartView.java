package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TrendChartView extends View {

    private static final int MAX_POINTS = 120; // Храним последние 120 точек

    private final List<Integer> spo2List = new ArrayList<>();
    private final List<Integer> o2HrList = new ArrayList<>();
    private final List<Integer> polarHrList = new ArrayList<>();
    private final List<Float> rrList = new ArrayList<>();

    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint spo2Paint = new Paint();
    private final Paint o2HrPaint = new Paint();
    private final Paint polarHrPaint = new Paint();
    private final Paint rrPaint = new Paint();

    public TrendChartView(Context context) {
        super(context);
        init();
    }

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.LTGRAY);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        spo2Paint.setColor(Color.CYAN);
        spo2Paint.setStrokeWidth(3f);
        spo2Paint.setStyle(Paint.Style.STROKE);
        spo2Paint.setAntiAlias(true);

        o2HrPaint.setColor(Color.GREEN);
        o2HrPaint.setStrokeWidth(3f);
        o2HrPaint.setStyle(Paint.Style.STROKE);
        o2HrPaint.setAntiAlias(true);

        polarHrPaint.setColor(Color.MAGENTA);
        polarHrPaint.setStrokeWidth(3f);
        polarHrPaint.setStyle(Paint.Style.STROKE);
        polarHrPaint.setAntiAlias(true);

        rrPaint.setColor(Color.YELLOW);
        rrPaint.setStrokeWidth(3f);
        rrPaint.setStyle(Paint.Style.STROKE);
        rrPaint.setAntiAlias(true);

        setBackgroundColor(Color.BLACK);
    }

    public void addDataPoint(DataPoint dp) {
        if (dp != null) {
            spo2List.add(dp.spo2);
            o2HrList.add(dp.hr);
            if (spo2List.size() > MAX_POINTS) spo2List.remove(0);
            if (o2HrList.size() > MAX_POINTS) o2HrList.remove(0);
            postInvalidate();
        }
    }

    public void addPolarPoint(int hr, float rrMs) {
        polarHrList.add(hr);
        if (rrMs > 0) rrList.add(rrMs);
        if (polarHrList.size() > MAX_POINTS) polarHrList.remove(0);
        if (rrList.size() > MAX_POINTS) rrList.remove(0);
        postInvalidate();
    }

    public void clearData() {
        spo2List.clear();
        o2HrList.clear();
        polarHrList.clear();
        rrList.clear();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        // Отрисовка сетки
        for (int i = 1; i < 4; i++) {
            float y = (height / 4f) * i;
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // Тренд SpO2 (шкала 70 - 100 %)
        drawIntTrend(canvas, spo2List, 70, 100, spo2Paint, width, height);

        // Тренд O2 HR (шкала 40 - 180 bpm)
        drawIntTrend(canvas, o2HrList, 40, 180, o2HrPaint, width, height);

        // Тренд Polar HR (шкала 40 - 180 bpm)
        drawIntTrend(canvas, polarHrList, 40, 180, polarHrPaint, width, height);

        // Тренд RR (шкала 400 - 1400 ms)
        drawFloatTrend(canvas, rrList, 400f, 1400f, rrPaint, width, height);

        // Легенда
        canvas.drawText("Cyan: SpO2 | Green: O2 HR | Magenta: Polar HR | Yellow: RR (ms)", 20, 35, textPaint);
    }

    private void drawIntTrend(Canvas canvas, List<Integer> data, int minVal, int maxVal, Paint paint, int width, int height) {
        if (data == null || data.size() < 2) return;

        Path path = new Path();
        float xStep = (float) width / (MAX_POINTS - 1);

        for (int i = 0; i < data.size(); i++) {
            float x = i * xStep;
            float val = data.get(i);
            float normalized = (val - minVal) / (float) (maxVal - minVal);
            normalized = Math.max(0f, Math.min(1f, normalized));
            float y = height - (normalized * height);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }

    private void drawFloatTrend(Canvas canvas, List<Float> data, float minVal, float maxVal, Paint paint, int width, int height) {
        if (data == null || data.size() < 2) return;

        Path path = new Path();
        float xStep = (float) width / (MAX_POINTS - 1);

        for (int i = 0; i < data.size(); i++) {
            float x = i * xStep;
            float val = data.get(i);
            float normalized = (val - minVal) / (maxVal - minVal);
            normalized = Math.max(0f, Math.min(1f, normalized));
            float y = height - (normalized * height);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }
}
