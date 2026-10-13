package com.local.o2test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RectF;
import android.view.View;

public class BreatheAnimationView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    // Координаты лепестков (базовая сетка 200x200)
    private static final float[][] CYAN_CIRCLES = {
        {100, 80, 22}, {118, 88, 22}, {120, 110, 22},
        {100, 122, 22}, {80, 110, 22}, {82, 88, 22}
    };

    private static final float[][] PINK_CIRCLES = {
        {100, 54, 27}, {139, 69, 24}, {152, 108, 24}, {125, 144, 27},
        {87, 152, 24}, {56, 123, 24}, {53, 82, 27}, {77, 56, 24}
    };

    public BreatheAnimationView(Context context) {
        super(context);
        bgPaint.setColor(0xFF1E222A);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        
        // Масштаб по меньшей стороне, чтобы квадрат 200x200 вписался без искажений
        float scale = Math.min(width, height) / 200f;
        
        // Центрируем отрисовку внутри доступного пространства view
        float dx = (width - 200f * scale) / 2f;
        float dy = (height - 200f * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        // Карточка-фон с скругленными углами
        bgRect.set(0, 0, 200, 200);
        canvas.drawRoundRect(bgRect, 16, 16, bgPaint);

        // Отрисовка внутренних циановых кругов с радиальным градиентом
        for (float[] c : CYAN_CIRCLES) {
            float cx = c[0], cy = c[1], r = c[2];
            cyanPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{0xDD00E5FF, 0x1500E5FF}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, cyanPaint);
        }

        // Отрисовка внешних розовых кругов с радиальным градиентом
        for (float[] c : PINK_CIRCLES) {
            float cx = c[0], cy = c[1], r = c[2];
            pinkPaint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{0xDDFF4081, 0x15FF4081}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, pinkPaint);
        }

        // Центральное световое ядро
        corePaint.setShader(new RadialGradient(100, 100, 14,
                new int[]{0xFFEEFFFF, 0x44FFFFFF}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(100, 100, 14, corePaint);

        canvas.restore();
    }
}
