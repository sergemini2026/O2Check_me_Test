package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PacerView extends View {
    private final Paint paintBg = new Paint();
    private final Paint paintBorder = new Paint();
    private final Paint paintText = new Paint();

    public PacerView(Context context) {
        super(context);
        init();
    }

    public PacerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintBg.setColor(Color.parseColor("#1E1E1E"));
        paintBg.setStyle(Paint.Style.FILL);

        paintBorder.setColor(Color.parseColor("#444444"));
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(2f);
        paintBorder.setAntiAlias(true);

        paintText.setColor(Color.LTGRAY);
        paintText.setTextSize(24f);
        paintText.setAntiAlias(true);
        paintText.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // Рисуем фон канваса пейсера
        canvas.drawRect(0, 0, w, h, paintBg);
        canvas.drawRect(0, 0, w, h, paintBorder);

        // Временная заглушка текста для проверки отображения
        float x = w / 2f;
        float y = (h / 2f) - ((paintText.descent() + paintText.ascent()) / 2f);
        canvas.drawText("Пейсер [0.1 Hz]", x, y, paintText);
    }
}
