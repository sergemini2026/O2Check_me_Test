package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;

public class TrainingDialogHelper {

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
     * Установка ширины диалогового окна равной 75% от ширины экрана (уменьшение на 1/4)
     */
    private static void setDialogWidth75(AlertDialog dialog, Context context) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.75);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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

    /**
     * Кастомный адаптер списков с подсветкой и отступом ровно 1 см слева
     */
    private static class HighlightArrayAdapter extends ArrayAdapter<CharSequence> {
        private final Context context;

        public HighlightArrayAdapter(Context context, CharSequence[] items) {
            super(context, android.R.layout.select_dialog_item, items);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            view.setBackground(createRowSelector());

            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                // Точный перевод 1 см (10 мм) в пиксели для отступа слева
                int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 10, context.getResources().getDisplayMetrics());
                tv.setPadding(indentPx, 28, 32, 28);
            }
            return view;
        }
    }

    public static void openTrainingDialog(Context context, ExecutorService dbExecutor, long activeProfileId, String activeProfileName, boolean isAuthorized, TrainingStartCallback callback) {
        if (!isAuthorized || activeProfileId == -1L) {
            Toast.makeText(context, "Сначала войдите в профиль!", Toast.LENGTH_SHORT).show();
            callback.onRequireProfileAuth();
            return;
        }

        CharSequence[] categories = {
            "🧬 ИГГТ (рандомная)",
            "🌬️ Пранаяма (тайминг)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор тренировки"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, categories);
        AlertDialog dialog = builder.setAdapter(adapter, (d, which) -> {
            if (which == 0) {
                showIgztSubMenu(context, callback);
            } else {
                showPranayamaSubMenu(context, callback);
            }
        }).setNegativeButton("Отмена", null).create();

        setDialogWidth75(dialog, context);
    }

    private static void showIgztSubMenu(Context context, TrainingStartCallback callback) {
        CharSequence[] igztModes = {
            "Гипоксически-гипероксическая",
            "Гипероксически-гипоксическая",
            "Длительная гипоксическая",
            "Длительная гипероксическая"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("ИГГТ (рандомная)"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, igztModes);
        AlertDialog dialog = builder.setAdapter(adapter, (d, which) -> {
            Toast.makeText(context, "Модуль ИГГТ находится в разработке", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Назад", (d, w) -> openTrainingDialog(context, null, -1L, "", true, callback)).create();

        setDialogWidth75(dialog, context);
    }

    private static void showPranayamaSubMenu(Context context, TrainingStartCallback callback) {
        CharSequence[] pranayamaModes = {
            "3-1 (Стандарт)",
            "2-1 (Краткий)",
            "1-1 (Экспресс)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Пранаяма (тайминг)"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, pranayamaModes);
        AlertDialog dialog = builder.setAdapter(adapter, (d, which) -> {
            if (which == 0) {
                int[] pattern = {3, 3, 3};
                callback.onTrainingSelected("Пранаяма", "3-1 Стандарт", 3, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 3-1 (Стандарт)", Toast.LENGTH_SHORT).show();
            } else if (which == 1) {
                int[] pattern = {2, 2};
                callback.onTrainingSelected("Пранаяма", "2-1 Краткий", 2, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 2-1 (Краткий)", Toast.LENGTH_SHORT).show();
            } else if (which == 2) {
                int[] pattern = {1};
                callback.onTrainingSelected("Пранаяма", "1-1 Экспресс", 1, pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма 1-1 (Экспресс)", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Назад", (d, w) -> openTrainingDialog(context, null, -1L, "", true, callback)).create();

        setDialogWidth75(dialog, context);
    }
}
