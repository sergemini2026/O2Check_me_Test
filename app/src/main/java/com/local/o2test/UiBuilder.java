package com.local.o2test;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.local.o2test.db.entity.ProfileEntity;
import com.local.o2test.db.entity.SessionEntity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UiBuilder {

    public interface ProfileCallback {
        void onProfileSelected(ProfileEntity profile);
    }

    public static class Views {
        public LinearLayout mainLayout;
        public TextView tvLiveMetrics;
        public TrendChartView chartView;
        public TextView tvHrvMetrics;
        public TextView tvStatusValue;
        public TextView tvAttractorIndex; // Индекс Аттрактора (IA) над пейсером
        public TextView tvLog;
        public ScrollView logScrollView;
        public ImageButton btnHeart;
        public Button btnReconnect;
        public Button btnSave;
        public Button btnSettings;
        public Button btnExit;

        // Кнопки функциональной панели
        public ImageButton btnWorkout;
        public ImageButton btnSessions;
        public ImageButton btnProfiles;

        // Правая половина нижней части экрана с фрактальным пейсером
        public FrameLayout rightPanel;
        public BreatheAnimationView pacerView;
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

        // --- БЛОК ВСР (с поддержкой LF/HF) ---
        v.tvHrvMetrics = new TextView(context);
        v.tvHrvMetrics.setTextSize(16);
        v.tvHrvMetrics.setTextColor(Color.WHITE);
        v.tvHrvMetrics.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams hrvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrvParams.setMargins(0, 8, 0, 2);
        v.tvHrvMetrics.setLayoutParams(hrvParams);
        v.tvHrvMetrics.setText("Профиль: Не выбран  |  RMSSD: --  |  pNN50: --  |  LF/HF: --  |  TP: --");
        v.mainLayout.addView(v.tvHrvMetrics);

        // --- СТРОКА СТАТУСА ---
        v.tvStatusValue = new TextView(context);
        v.tvStatusValue.setText("");
        v.tvStatusValue.setTextSize(13);
        v.tvStatusValue.setTextColor(0xFFFF6D00);
        v.tvStatusValue.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, 4);
        v.tvStatusValue.setLayoutParams(statusParams);
        v.mainLayout.addView(v.tvStatusValue);

        // --- БЛОК УПРАВЛЕНИЯ МОНИТОРИНГОМ И ТРЕНИРОВКАМИ (4 КНОПКИ) ---
        LinearLayout actionPanel = new LinearLayout(context);
        actionPanel.setOrientation(LinearLayout.HORIZONTAL);
        actionPanel.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionPanelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionPanelParams.setMargins(0, 2, 0, 4);
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

        // 4. Кнопка "Монитор" (Сердце)
        v.btnHeart = new ImageButton(context);
        v.btnHeart.setBackgroundColor(Color.TRANSPARENT);
        v.btnHeart.setImageResource(R.drawable.ic_heart_pulse);
        v.btnHeart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.btnHeart.setAlpha(0.5f);
        v.btnHeart.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));
        actionPanel.addView(createActionItem(context, v.btnHeart, "Монитор"));

        v.mainLayout.addView(actionPanel);

        // --- НИЖНЯЯ ПАНЕЛЬ: 60% (ЛОГ) И 40% (ПРАВАЯ ПАНЕЛЬ С ФРАКТАЛЬНЫМ ПЕЙСЕРОМ) ---
        LinearLayout bottomSplitLayout = new LinearLayout(context);
        bottomSplitLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        splitParams.topMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
        bottomSplitLayout.setLayoutParams(splitParams);

        // Левая половина: Лог
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

        // Правая половина: вертикальный контейнер для Индекса Аттрактора и пейсера
        LinearLayout rightVerticalLayout = new LinearLayout(context);
        rightVerticalLayout.setOrientation(LinearLayout.VERTICAL);
        rightVerticalLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f);
        rightParams.leftMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
        rightVerticalLayout.setLayoutParams(rightParams);

        // Индекс Аттрактора (IA) сверху над модулем визуализации
        v.tvAttractorIndex = new TextView(context);
        v.tvAttractorIndex.setText("IA: [--%]");
        v.tvAttractorIndex.setTextSize(15);
        v.tvAttractorIndex.setTextColor(0xFFFF6D00); // Оранжевый цвет
        v.tvAttractorIndex.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iaParams.topMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
        iaParams.bottomMargin = (int) (4 * context.getResources().getDisplayMetrics().density);
        v.tvAttractorIndex.setLayoutParams(iaParams);
        rightVerticalLayout.addView(v.tvAttractorIndex);

        // Правая половина (обертка для центрирования пейсера)
        v.rightPanel = new FrameLayout(context);
        LinearLayout.LayoutParams rightPanelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        v.rightPanel.setLayoutParams(rightPanelParams);

        // Создаем BreatheAnimationView
        v.pacerView = new BreatheAnimationView(context);
        FrameLayout.LayoutParams pacerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        pacerParams.gravity = Gravity.CENTER;
        v.pacerView.setLayoutParams(pacerParams);
        v.rightPanel.addView(v.pacerView);

        rightVerticalLayout.addView(v.rightPanel);

        bottomSplitLayout.addView(v.logScrollView);
        bottomSplitLayout.addView(rightVerticalLayout);

        v.mainLayout.addView(bottomSplitLayout);

        return v;
    }

    /**
     * Универсальный метод обновления строки параметров HRV с поддержкой LF/HF
     */
    public static void updateHrvDisplay(TextView tvHrvMetrics, String profileName, String rmssd, String pnn50, String lfHfStr, String tp) {
        if (tvHrvMetrics != null) {
            String displayStr = String.format(Locale.US,
                    "Профиль: %s  |  RMSSD: %s  |  pNN50: %s  |  LF/HF: %s  |  TP: %s",
                    profileName, rmssd, pnn50, lfHfStr, tp);
            tvHrvMetrics.setText(displayStr);
        }
    }

    public static void showSessionsDialog(Context context, ProfileEntity profile, List<SessionEntity> sessions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        
        TextView title = new TextView(context);
        String fullName = profile.getFirstName() + (TextUtils.isEmpty(profile.getLastName()) ? "" : " " + profile.getLastName());
        title.setText("Сессии: " + fullName);
        title.setTextSize(18);
        title.setPadding(30, 30, 30, 10);
        title.setTextColor(0xFFFFFFFF);
        builder.setCustomTitle(title);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);

        if (sessions == null || sessions.isEmpty()) {
            TextView emptyText = new TextView(context);
            emptyText.setText("Сохраненных сессий не найдено.");
            emptyText.setTextColor(0xFFA0A0A0);
            emptyText.setPadding(10, 20, 10, 20);
            layout.addView(emptyText);
        } else {
            List<SessionEntity> sortedSessions = new ArrayList<>(sessions);
            sortedSessions.sort((s1, s2) -> Long.compare(s1.getStartTime(), s2.getStartTime()));

            ListView listView = new ListView(context);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

            List<String> sessionStrings = new ArrayList<>();
            for (int position = 0; position < sortedSessions.size(); position++) {
                int dataIndex = sortedSessions.size() - 1 - position;
                SessionEntity s = sortedSessions.get(dataIndex);
                int sessionNumber = dataIndex + 1;
                
                sessionStrings.add("Сессия #" + sessionNumber + " | " + sdf.format(new Date(s.getStartTime())) + 
                                  " | " + s.getWorkoutMode() + " | SpO2: " + s.getAvgSpo2() + "%");
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, sessionStrings);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                int dataIndex = sortedSessions.size() - 1 - position;
                SessionEntity session = sortedSessions.get(dataIndex);
                if (!TextUtils.isEmpty(session.getPdfPath())) {
                    File file = new File(session.getPdfPath());
                    if (file.exists()) {
                        try {
                            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(uri, "application/pdf");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, "Ошибка открытия PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Файл отчета не найден: " + session.getPdfPath(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "Для данной сессии нет сохраненного PDF", Toast.LENGTH_SHORT).show();
                }
            });

            layout.addView(listView);
        }

        builder.setView(layout);
        builder.setNegativeButton("ЗАКРЫТЬ", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.85);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.70);
            dialog.getWindow().setLayout(width, height);
        }
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
                    setIconGravityMethod.invoke(btn, 0x03);
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
