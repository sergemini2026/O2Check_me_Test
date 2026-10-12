package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.SessionEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class TrainingDialogHelper {

    public interface TrainingStartCallback {
        void onStartTraining(String trainingType, String modeName);
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

        String[] trainingTypes = {"Рандомная (Anti-Adaptive AI)", "Тайминг (AI-рекомендации)"};

        AlertDialog.Builder typeBuilder = new AlertDialog.Builder(context);
        typeBuilder.setTitle(getCenteredTitle("Выбор типа тренировки"));
        typeBuilder.setItems(trainingTypes, (dialogType, typeChoice) -> {
            boolean isRandom = (typeChoice == 0);
            showTrainingModeSelectionDialog(context, dbExecutor, activeProfileId, activeProfileName, isRandom, callback);
        });
        typeBuilder.setNegativeButton("Отмена", null);
        typeBuilder.show();
    }

    private static void showTrainingModeSelectionDialog(Context context, ExecutorService dbExecutor, long activeProfileId, String activeProfileName, boolean isRandom, TrainingStartCallback callback) {
        String[] modes = {
            "1) Гипоксически - Гипероксическая",
            "2) Гипероксически - Гипоксическая",
            "3) Длительная Гипоксическая",
            "4) Длительная Гипероксическая"
        };

        AlertDialog.Builder modeBuilder = new AlertDialog.Builder(context);
        modeBuilder.setTitle(getCenteredTitle(isRandom ? "Режим: Рандомная (AI)" : "Режим: Тайминг"));
        modeBuilder.setItems(modes, (dialogMode, modeChoice) -> {
            String selectedModeName = modes[modeChoice];

            if (isRandom) {
                confirmAndStart(context, "Рандомная", selectedModeName, "AI автономно определяет интервалы и частоту смены сред.", callback);
            } else {
                checkHistoryAndShowTimingOptions(context, dbExecutor, activeProfileId, selectedModeName, callback);
            }
        });
        modeBuilder.setNegativeButton("Назад", (d, w) -> openTrainingDialog(context, dbExecutor, activeProfileId, activeProfileName, true, callback));
        modeBuilder.show();
    }

    private static void checkHistoryAndShowTimingOptions(Context context, ExecutorService dbExecutor, long activeProfileId, String modeName, TrainingStartCallback callback) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<SessionEntity> sessions = db.sessionDao().getSessionsByProfileSync(activeProfileId);

                ((android.app.Activity) context).runOnUiThread(() -> {
                    if (sessions == null || sessions.isEmpty()) {
                        new AlertDialog.Builder(context)
                                .setTitle(getCenteredTitle("История сессий не найдена"))
                                .setMessage("У данного профиля отсутствуют сохраненные сессии.\n\nДля работы тайминга выберите режим 'Рандомная', чтобы AI мог накопить необходимый массив данных в ходе тестирования.")
                                .setPositiveButton("Выбрать Рандомный режим", (d, w) -> {
                                    confirmAndStart(context, "Рандомная", modeName, "AI запуск для накопления базы данных.", callback);
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                    } else {
                        showAiTimingOptionsDialog(context, modeName, sessions.size(), callback);
                    }
                });
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> showAiTimingOptionsDialog(context, modeName, 3, callback));
            }
        });
    }

    private static void showAiTimingOptionsDialog(Context context, String modeName, int sessionCount, TrainingStartCallback callback) {
        String[] aiOptions = {
            "Вариант 1: Гипоксия 60с / Гипероксия 30с (по " + sessionCount + " сессиям)",
            "Вариант 2: Гипоксия 90с / Гипероксия 45с (углубленный)",
            "Вариант 3: Гипоксия 45с / Гипероксия 45с (базовый)",
            "Ручная настройка интервалов"
        };

        AlertDialog.Builder optBuilder = new AlertDialog.Builder(context);
        optBuilder.setTitle(getCenteredTitle("AI Тайминг: " + modeName));
        optBuilder.setItems(aiOptions, (dialogOpt, optChoice) -> {
            if (optChoice == 3) {
                showCustomTimingConfigDialog(context, modeName, callback);
            } else {
                confirmAndStart(context, "Тайминг (" + aiOptions[optChoice] + ")", modeName, "Параметры предложены AI-автопилотом.", callback);
            }
        });
        optBuilder.setNegativeButton("Назад", null);
        optBuilder.show();
    }

    private static void showCustomTimingConfigDialog(Context context, String modeName, TrainingStartCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Ручная корректировка тайминга"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputHypo = new EditText(context);
        inputHypo.setHint("Гипоксия (сек)");
        inputHypo.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputHypo.setText("60");
        layout.addView(inputHypo);

        final EditText inputHyper = new EditText(context);
        inputHyper.setHint("Гипероксия (сек)");
        inputHyper.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputHyper.setText("30");
        layout.addView(inputHyper);

        builder.setView(layout);
        builder.setPositiveButton("Запуск", (dialog, which) -> {
            String hypo = inputHypo.getText().toString().trim();
            String hyper = inputHyper.getText().toString().trim();
            confirmAndStart(context, "Тайминг (Ручной: " + hypo + "с / " + hyper + "с)", modeName, "Установлены пользовательские интервалы.", callback);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private static void confirmAndStart(Context context, String trainingType, String modeName, String description, TrainingStartCallback callback) {
        new AlertDialog.Builder(context)
                .setTitle(getCenteredTitle("Подтверждение сеанса"))
                .setMessage("Тип: " + trainingType + "\nРежим: " + modeName + "\n\n" + description)
                .setPositiveButton("Начать тренировку", (dialog, which) -> callback.onStartTraining(trainingType, modeName))
                .setNegativeButton("Отмена", null)
                .show();
    }
}
