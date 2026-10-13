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

        // Новые графические кнопки функциональной панели
        public ImageButton btnWorkout;
        public ImageButton btnSessions;
        public ImageButton btnProfiles;

        // Панель для правой половины нижней части экрана
        public LinearLayout rightPanel;
        public TextView tvStatusLabel;
        public TextView tvStatusValue;

        // Активные поля для датчиков кислорода (интегрированы с OxygenSensorManager)
        public TextView tvPo2Mix;
        public TextView tvPo2Hipo;
        public TextView tvPo2Hiper;
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

        // --- БЛОК УПРАВЛЕНИЯ МОНИТОРИНГom И ТРЕНИРОВКАМИ (4 ВЕКТОРНЫЕ КНОПКИ) ---
        LinearLayout actionPanel = new LinearLayout(context);
        actionPanel.setOrientation(LinearLayout.HORIZONTAL);
        actionPanel.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionPanelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionPanelParams.setMargins(0, 4, 0, 4);
        actionPanel.setLayoutParams(actionPanelParams);

        int iconSizePx = (int) (48 * context.getResources().getDisplayMetrics().density);

        // 1. Кнопка "Тренировки"
        v.btnWorkout = new ImageButton(context);
        v.btnWorkout.setBackgroundColor(Color.TRANSPARENT);
        v.btnWorkout.setImageResource(R.drawable.ic_workout);
        v.btnWorkout.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnWorkout.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        actionPanel.addView(createActionItem(context, v.btnWorkout, "Тренировки"));

        // 2. Кнопка "Сессии"
        v.btnSessions = new ImageButton(context);
        v.btnSessions.setBackgroundColor(Color.TRANSPARENT);
        v.btnSessions.setImageResource(R.drawable.ic_sessions);
        v.btnSessions.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnSessions.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        actionPanel.addView(createActionItem(context, v.btnSessions, "Сессии"));

        // 3. Кнопка "Профили"
        v.btnProfiles = new ImageButton(context);
        v.btnProfiles.setBackgroundColor(Color.TRANSPARENT);
        v.btnProfiles.setImageResource(R.drawable.ic_profile);
        v.btnProfiles.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnProfiles.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        actionPanel.addView(createActionItem(context, v.btnProfiles, "Профили"));

        // 4. Кнопка "Панель монитора" (Сердце)
        v.btnHeart = new ImageButton(context);
        v.btnHeart.setBackgroundColor(Color.TRANSPARENT);
        v.btnHeart.setImageResource(R.drawable.ic_heart_pulse);
        v.btnHeart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnHeart.setAlpha(0.5f);
        v.btnHeart.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        actionPanel.addView(createActionItem(context, v.btnHeart, "Панель монитора"));

        v.mainLayout.addView(actionPanel);

        // --- НИЖНЯЯ ПАНЕЛЬ: РАЗДЕЛЕНИЕ НА 60% (ЛОГ) И 40% (СТАТУС И ДАТЧИКИ) ---
        LinearLayout bottomSplitLayout = new LinearLayout(context);
        bottomSplitLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        splitParams.topMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
        bottomSplitLayout.setLayoutParams(splitParams);

        // Левая половина: Лог (60% ширины, вес 3.0)
        v.tvLog = new TextView(context);
        v.tvLog.setTextSize(11);

        v.logScrollView = new ScrollView(context);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 3.0f);
        v.logScrollView.setLayoutParams(leftParams);

        ScrollView.LayoutParams tvLogParams = new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        );
        v.tvLog.setLayoutParams(tvLogParams);
        v.logScrollView.addView(v.tvLog);

        // Правая половина: Панель статуса и режимов (40% ширины, вес 2.0)
        v.rightPanel = new LinearLayout(context);
        v.rightPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f);
        rightParams.leftMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
        v.rightPanel.setLayoutParams(rightParams);

        // --- ТРИ ПОЛЯ КИСЛОРОДНЫХ ДАТЧИКОВ (ВЫШЕ СТАТУСА) ---
        LinearLayout sensorsLayout = new LinearLayout(context);
        sensorsLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sensorsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sensorsParams.bottomMargin = (int) (6 * context.getResources().getDisplayMetrics().density);
        sensorsLayout.setLayoutParams(sensorsParams);
        sensorsLayout.setGravity(Gravity.CENTER);

        v.tvPo2Mix = createSensorField(context, "PO2Mix []");
        v.tvPo2Hipo = createSensorField(context, "PO2Hipo []");
        v.tvPo2Hiper = createSensorField(context, "PO2Hiper []");

        sensorsLayout.addView(v.tvPo2Mix);
        sensorsLayout.addView(v.tvPo2Hipo);
        sensorsLayout.addView(v.tvPo2Hiper);
        v.rightPanel.addView(sensorsLayout);

        // Верхняя строка правой панели: "Статус:" (левое выравнивание)
        v.tvStatusLabel = new TextView(context);
        v.tvStatusLabel.setText("Статус:");
        v.tvStatusLabel.setTextSize(12);
        v.tvStatusLabel.setTextColor(Color.LTGRAY);
        v.tvStatusLabel.setGravity(Gravity.START);
        v.rightPanel.addView(v.tvStatusLabel);

        // Следующая строка правой панели: значение статуса/режима (центрирование)
        v.tvStatusValue = new TextView(context);
        v.tvStatusValue.setText("");
        v.tvStatusValue.setTextSize(13);
        v.tvStatusValue.setTextColor(Color.WHITE);
        v.tvStatusValue.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
        v.tvStatusValue.setLayoutParams(valueParams);
        v.rightPanel.addView(v.tvStatusValue);

        // Добавляем обе половины в общий горизонтальный контейнер нижней части
        bottomSplitLayout.addView(v.logScrollView);
        bottomSplitLayout.addView(v.rightPanel);

        // Добавляем весь нижний блок в главный экран
        v.mainLayout.addView(bottomSplitLayout);

        return v;
    }

    private static TextView createSensorField(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTextColor(Color.YELLOW);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tv.setLayoutParams(params);
        return tv;
    }

    private static LinearLayout createActionItem(Context context, ImageButton btn, String labelText) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        itemLayout.setLayoutParams(itemParams);

        itemLayout.addView(btn);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(labelText);
        tvLabel.setTextSize(11);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 2, 0, 0);
        tvLabel.setLayoutParams(labelParams);

        itemLayout.addView(tvLabel);

        return itemLayout;
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
                    int w = btn.getWidth();
                    if (w > 0) {
                        Drawable[] drawables = btn.getCompoundDrawables();
                        int iconWidth = (drawables[0] != null) ? drawables[0].getIntrinsicWidth() : 0;
                        float textWidth = btn.getPaint().measureText(text);

                        float contentWidth = iconWidth + iconPaddingPx + textWidth;
                        int pad = Math.max(0, (int) ((w - contentWidth) / 2f));
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
