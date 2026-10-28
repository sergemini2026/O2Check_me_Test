package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;

public class TrainingDialogHelper {

    public interface TrainingStartCallback {
        void onTrainingSelected(String trainingType, String modeName, int[] pattern);
        void onRequireProfileAuth();
    }

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    public static void openTrainingDialog(Context context, ExecutorService dbExecutor, long activeProfileId, String activeProfileName, boolean isAuthorized, TrainingStartCallback callback) {
        if (!isAuthorized || activeProfileId == -1L) {
            Toast.makeText(context, "Сначала войдите в профиль!", Toast.LENGTH_SHORT).show();
            callback.onRequireProfileAuth();
            return;
        }

        String[] categories = {
            "ИГГТ (рандомная)",
            "Пранаяма (тайминг)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор тренировки"));
        builder.setItems(categories, (dialog, which) -> {
            if (which == 0) {
                showIgztSubMenu(context, callback);
            } else {
                showPranayamaSubMenu(context, callback);
            }
        });
        builder.setNegativeButton("Отмена", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            try {
                int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
                if (titleId > 0) {
                    android.widget.TextView title = dialog.findViewById(titleId);
                    if (title != null) title.setGravity(android.view.Gravity.CENTER);
                }
            } catch (Exception ignored) {}
        });
        dialog.show();
    }

    private static void showIgztSubMenu(Context context, TrainingStartCallback callback) {
        String[] igztModes = {
            "Гипоксически-гипероксическая",
            "Гипероксически-гипоксическая",
            "Длительная гипоксическая",
            "Длительная гипероксическая"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("ИГГТ (рандомная)"));
        builder.setItems(igztModes, (dialog, which) -> {
            Toast.makeText(context, "Модуль ИГГТ находится в разработке", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Назад", (d, w) -> openTrainingDialog(context, null, -1L, "", true, callback));
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            try {
                int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
                if (titleId > 0) {
                    android.widget.TextView title = dialog.findViewById(titleId);
                    if (title != null) title.setGravity(android.view.Gravity.CENTER);
                }
            } catch (Exception ignored) {}
        });
        dialog.show();
    }

    private static void showPranayamaSubMenu(Context context, TrainingStartCallback callback) {
        String[] pranayamaModes = {
            "3-1-3-1-3-1 (Стандартный)",
            "3-1-2-1-1-1 (Укороченный)",
            "1-1-1-1-1-1 (Экспресс)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Пранаяма (тайминг)"));
        builder.setItems(pranayamaModes, (dialog, which) -> {
            if (which == 0) {
                int[] pattern = {3, 3, 3};
                callback.onTrainingSelected("Пранаяма", "3-1-3-1-3-1", pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма (3-1-3-1-3-1)", Toast.LENGTH_SHORT).show();
            } else if (which == 1) {
                int[] pattern = {3, 2, 1};
                callback.onTrainingSelected("Пранаяма", "3-1-2-1-1-1", pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма (3-1-2-1-1-1)", Toast.LENGTH_SHORT).show();
            } else if (which == 2) {
                int[] pattern = {1, 1, 1};
                callback.onTrainingSelected("Пранаяма", "1-1-1-1-1-1", pattern);
                Toast.makeText(context, "Выбран режим: Пранаяма (1-1-1-1-1-1)", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Назад", (d, w) -> openTrainingDialog(context, null, -1L, "", true, callback));
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            try {
                int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
                if (titleId > 0) {
                    android.widget.TextView title = dialog.findViewById(titleId);
                    if (title != null) title.setGravity(android.view.Gravity.CENTER);
                }
            } catch (Exception ignored) {}
        });
        dialog.show();
    }
}
