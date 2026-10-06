package com.local.o2test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class UiBuilder {

    public static class Views {
        public LinearLayout mainLayout;
        public TextView tvLiveMetrics;
        public TrendChartView chartView;
        public TextView tvHrvMetrics;
        public TextView tvLog;
        public ScrollView logScrollView;
        public ImageButton btnHeart;
        public Button btnReconnect;
        public Button btnSave;
        public Button btnSettings;
        public Button btnExit;
    }

    public static Views buildUi(Context context) {
        Views v = new Views();

        v.mainLayout = new LinearLayout(context);
        v.mainLayout.setOrientation(LinearLayout.VERTICAL);
        v.mainLayout.setPadding(20, 20, 20, 20);

        v.tvLiveMetrics = new TextView(context);
        v.tvLiveMetrics.setTextSize(18);
        v.tvLiveMetrics.setGravity(Gravity.CENTER);
        v.mainLayout.addView(v.tvLiveMetrics);

        // --- ВЕРХНЯЯ ПАНЕЛЬ КНОПОК ---
        LinearLayout btnBar = new LinearLayout(context);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setPadding(0, 2, 0, 2);

        int btnBarHeightPx = (int) (52 * context.getResources().getDisplayMetrics().density);
        btnBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, btnBarHeightPx));

        v.btnReconnect = createButton(context, "Связь", android.R.drawable.ic_popup_sync);
        v.btnSave = createButton(context, "Сохранить", android.R.drawable.ic_menu_save);
        v.btnSettings = createButton(context, "Настройки", android.R.drawable.ic_menu_preferences);
        v.btnExit = createButton(context, "Выход", android.R.drawable.ic_menu_close_clear_cancel);

        btnBar.addView(v.btnReconnect);
        btnBar.addView(v.btnSave);
        btnBar.addView(v.btnSettings);
        btnBar.addView(v.btnExit);
        v.mainLayout.addView(btnBar);

        v.chartView = new TrendChartView(context);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f);
        v.chartView.setLayoutParams(chartParams);
        v.mainLayout.addView(v.chartView);

        // --- БЛОК ВСР ---
        v.tvHrvMetrics = new TextView(context);
        v.tvHrvMetrics.setTextSize(16);
        v.tvHrvMetrics.setTextColor(Color.WHITE);
        v.tvHrvMetrics.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams hrvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrvParams.setMargins(0, 8, 0, 4);
        v.tvHrvMetrics.setLayoutParams(hrvParams);
        v.tvHrvMetrics.setText("RMSSD: -- | pNN50: -- | LF/HF: -- | TP: --");
        v.mainLayout.addView(v.tvHrvMetrics);

        // --- БЛОК УПРАВЛЕНИЯ МОНИТОРИНГОМ ---
        LinearLayout heartContainer = new LinearLayout(context);
        heartContainer.setOrientation(LinearLayout.HORIZONTAL);
        heartContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams heartContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heartContainerParams.setMargins(0, 0, 12, 4);
        heartContainer.setLayoutParams(heartContainerParams);

        TextView tvMonitorLabel = new TextView(context);
        tvMonitorLabel.setText("Панель монитора");
        tvMonitorLabel.setTextSize(13);
        tvMonitorLabel.setTextColor(Color.WHITE);
        tvMonitorLabel.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 0, 10, 0);
        tvMonitorLabel.setLayoutParams(labelParams);

        v.btnHeart = new ImageButton(context);
        v.btnHeart.setBackgroundColor(Color.TRANSPARENT);
        v.btnHeart.setImageResource(R.drawable.ic_heart_pulse);
        v.btnHeart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnHeart.setAlpha(0.5f);

        int heartSizePx = (int) (60 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(heartSizePx, heartSizePx);
        v.btnHeart.setLayoutParams(heartParams);

        heartContainer.addView(tvMonitorLabel);
        heartContainer.addView(v.btnHeart);
        v.mainLayout.addView(heartContainer);

        v.tvLog = new TextView(context);
        v.tvLog.setTextSize(11);

        v.logScrollView = new ScrollView(context);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        logParams.topMargin = 4;
        v.logScrollView.setLayoutParams(logParams);
        v.logScrollView.addView(v.tvLog);
        v.mainLayout.addView(v.logScrollView);

        return v;
    }

    private static Button createButton(Context context, String text, int iconRes) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setSingleLine(true);

        int iconPaddingPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_MM, 3, context.getResources().getDisplayMetrics());

        if (iconRes != 0) {
            Drawable scaledIcon = getScaledDrawable(context, iconRes, 18);
            btn.setCompoundDrawablesWithIntrinsicBounds(scaledIcon, null, null, null);
            btn.setCompoundDrawablePadding(iconPaddingPx);
        }

        int paddingHorizPx = (int) (1 * context.getResources().getDisplayMetrics().density);
        int paddingVertPx = (int) (2 * context.getResources().getDisplayMetrics().density);
        btn.setPadding(paddingHorizPx, paddingVertPx, paddingHorizPx, paddingVertPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        params.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(params);

        if (iconRes != 0) {
            btn.post(() -> {
                boolean materialSuccess = false;
                try {
                    java.lang.reflect.Method setIconResourceMethod = btn.getClass().getMethod("setIconResource", int.class);
                    java.lang.reflect.Method setIconGravityMethod = btn.getClass().getMethod("setIconGravity", int.class);
                    java.lang.reflect.Method setIconPaddingMethod = btn.getClass().getMethod("setIconPadding", int.class);

                    setIconResourceMethod.invoke(btn, iconRes);
                    setIconGravityMethod.invoke(btn, 0x03); // ICON_GRAVITY_TEXT_START
                    setIconPaddingMethod.invoke(btn, iconPaddingPx);
                    materialSuccess = true;
                } catch (Exception ignored) {
                }

                if (!materialSuccess) {
                    int width = btn.getWidth();
                    if (width > 0) {
                        Drawable[] drawables = btn.getCompoundDrawables();
                        int iconWidth = (drawables[0] != null) ? drawables[0].getIntrinsicWidth() : 0;
                        float textWidth = btn.getPaint().measureText(text);

                        float contentWidth = iconWidth + iconPaddingPx + textWidth;
                        int pad = Math.max(0, (int) ((width - contentWidth) / 2f));
                        btn.setPadding(pad, paddingVertPx, pad, paddingVertPx);
                    }
                }
            });
        }

        return btn;
    }

    private static Drawable getScaledDrawable(Context context, int resId, int sizeDp) {
        Drawable d = context.getResources().getDrawable(resId, context.getTheme());
        int sizePx = (int) (sizeDp * context.getResources().getDisplayMetrics().density);

        if (d instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            return new BitmapDrawable(context.getResources(),
                    Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true));
        }

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static void updateStatusHeader(TextView tvLiveMetrics, int spo2, int hr, float pi, int battery) {
        updateStatusHeader(tvLiveMetrics, spo2, hr, pi, battery, 0);
    }

    public static void updateStatusHeader(TextView tvLiveMetrics, int spo2, int hr, float pi, int batteryO2, int batteryH10) {
        if (tvLiveMetrics == null) return;

        String partO2 = String.format(Locale.US, "SpO2: %d%%", spo2);
        String partHR = String.format(Locale.US, "  |  HR: %d bpm", hr);
        String partPI = String.format(Locale.US, "  |  PI: %.1f%%", pi);
        String partPower = String.format(Locale.US, "  |  Power O2 %d%% / H10 %d%%", batteryO2, batteryH10);

        SpannableStringBuilder builder = new SpannableStringBuilder();

        int start = 0;
        builder.append(partO2);
        builder.setSpan(new ForegroundColorSpan(Color.CYAN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partHR);
        builder.setSpan(new ForegroundColorSpan(Color.GREEN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partPI);
        builder.setSpan(new ForegroundColorSpan(Color.YELLOW), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = builder.length();
        builder.append(partPower);
        builder.setSpan(new ForegroundColorSpan(Color.RED), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvLiveMetrics.setText(builder);
    }
}
