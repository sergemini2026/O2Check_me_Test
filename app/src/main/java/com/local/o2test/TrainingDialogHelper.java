package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;

public class TrainingDialogHelper {

    private static final long CLICK_DELAY_MS = 500; // Задержка 0.5 сек для визуального отклика

    public interface TrainingStartCallback {
        void onTrainingSelected(String trainingType, String modeName, int targetX, int[] pattern);
        void onRequireProfileAuth();
    }

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    /**
     * Создает Selector с визуальной подсветкой выбранной строки при касании
     */
    private static StateListDrawable createRowSelector() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x4000F2FE));
        drawable.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(0x4000F2FE));
        drawable.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return drawable;
    }

    private interface OnItemClickCallback {
        void onItemClick(int position);
    }

    /**
     * Универсальный метод построения кастомного диалогового окна на базе View с шириной 75% и отступом 1 см
     */
    private static void showCustomMenuDialog(Context context, String titleText, CharSequence[] items, int selectedIndex, OnItemClickCallback callback, String negativeButtonText, Runnable onNegative) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        // Заголовок диалога
        TextView titleView = new TextView(context);
        titleView.setText(getCenteredTitle(titleText));
        titleView.setTextSize(18);
        titleView.setTextColor(Color.WHITE);
        int pad20 = (int) (20 * context.getResources().getDisplayMetrics().density);
        titleView.setPadding(pad20, pad20, pad20, (int) (12 * context.getResources().getDisplayMetrics().density));
        rootLayout.addView(titleView);

        // Контейнер элементов списка с прокруткой
        ScrollView scrollView = new ScrollView(context);
        LinearLayout itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        for (int i = 0; i < items.length; i++) {
            final int pos = i;
            TextView itemTv = new TextView(context);
            itemTv.setText(items[i]);
            itemTv.setTextSize(16);
            itemTv.setTextColor(Color.WHITE);

            // Точный перевод 1 см (10 мм) в пиксели для отступа слева
            int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 10, context.getResources().getDisplayMetrics());
            int vertPad = (int) (14 * context.getResources().getDisplayMetrics().density);
            itemTv.setPadding(indentPx, vertPad, vertPad, vertPad);

            if (pos == selectedIndex) {
                itemTv.setBackgroundColor(0x2500F2FE); // Подсветка выбранного пункта
            } else {
                itemTv.setBackground(createRowSelector());
            }

            itemTv.setOnClickListener(v -> {
                v.setPressed(true);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (dialogHolder[0] != null) {
                        dialogHolder[0].dismiss();
                    }
                    if (callback != null) {
                        callback.onItemClick(pos);
                    }
                }, CLICK_DELAY_MS);
            });

            itemsContainer.addView(itemTv);
        }

        scrollView.addView(itemsContainer);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        builder.setView(rootLayout);
        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText, (d, w) -> {
                if (onNegative != null) {
                    onNegative.run();
                } else {
                    d.dismiss();
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.75);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static void openTrainingDialog(Context context, ExecutorService dbExecutor, long activeProfileId, String activeProfileName, boolean isAuthorized, TrainingStartCallback callback) {
        if (!isAuthorized || activeProfileId == -1L) {
            Toast.makeText(context, "Сначала войдите в профиль!", Toast.LENGTH_SHORT).show();
            callback.onRequireProfileAuth();
            return;
        }

        CharSequence[] categories = {
            "🧬      ИГГТ (рандомная)",
            "🌬️      Пранаяма (тайминг)"
        };

        showCustomMenuDialog(context, "Выбор тренировки", categories, -1, position -> {
            if (position == 0) {
                showIgztSubMenu(context, callback);
            } else {
                showPranayamaSubMenu(context, callback);
            }
        }, "Отмена", null);
    }

    private static void showIgztSubMenu(Context context, TrainingStartCallback callback) {
        CharSequence[] igztModes = {
            "Гипоксически-гипероксическая",
            "Гипероксически-гипоксическая",
            "Длительная гипоксическая",
            "Длительная гипероксическая"
        };

        showCustomMenuDialog(context, "ИГГТ (рандомная)", igztModes, -1, position -> {
            Toast.makeText(context, "Модуль ИГГТ находится в разработке", Toast.LENGTH_SHORT).show();
        }, "Назад", () -> openTrainingDialog(context, null, -1L, "", true, callback));
    }

    private static void showPranayamaSubMenu(Context context, TrainingStartCallback callback) {
        CharSequence[] pranayamaModes = {
            "3-1 (Стандарт)",
            "2-1 (Краткий)",
            "1-1 (Экспресс)"
        };

        showCustomMenuDialog(context, "Пранаяма (тайминг)", pranayamaModes, -1, position -> {
            if (position == 0) {
                int[] pattern = {3, 3, 3};
                callback.onTrainingSelected("Пранаяма", "3-1 Стандарт", 3, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 3-1 (Стандарт)", Toast.LENGTH_SHORT).show();
            } else if (position == 1) {
                int[] pattern = {2, 2};
                callback.onTrainingSelected("Пранаяма", "2-1 Краткий", 2, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 2-1 (Краткий)", Toast.LENGTH_SHORT).show();
            } else if (position == 2) {
                int[] pattern = {1};
                callback.onTrainingSelected("Пранаяма", "1-1 Экспресс", 1, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 1-1 (Экспресс)", Toast.LENGTH_SHORT).show();
            }
        }, "Назад", () -> openTrainingDialog(context, null, -1L, "", true, callback));
    }
}
