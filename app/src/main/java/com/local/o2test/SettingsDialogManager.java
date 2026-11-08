package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SettingsDialogManager {

    private static final long CLICK_DELAY_MS = 500; // Задержка 0.5 сек для визуального отклика
    private static final int DIALOG_WIDTH_DP = 300;
    private static final int ITEM_LEFT_INDENT_DP = 36;

    public interface SettingsCallback {
        void onLog(String message);
        void onAudioResourceChanged(int resId, String trackName);
        void onExportBackupRequested(File backupFile);
        void onImportBackupRequested(File backupFile);
        boolean isProfileAuthorized();
        String getActiveProfileName();
        void openProfilesManager();
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
     * Универсальный метод построения кастомного диалогового окна на базе View с шириной 300 dp и отступом 36 dp
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

            // Фиксированный отступ слева 36 dp
            int indentPx = (int) (ITEM_LEFT_INDENT_DP * context.getResources().getDisplayMetrics().density);
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
            int width = (int) (DIALOG_WIDTH_DP * context.getResources().getDisplayMetrics().density);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Главное меню настроек
     */
    public static void showSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] menuItems = {
            "🎧    Аудио",
            "📹    Видео",
            "💾    Резервирование"
        };

        showCustomMenuDialog(context, "Настройки системы", menuItems, -1, position -> {
            if (position == 0) {
                showAudioSettingsDialog(context, prefs, callback);
            } else if (position == 1) {
                showVideoSettingsDialog(context, prefs, callback);
            } else if (position == 2) {
                showBackupSettingsDialog(context, prefs, callback);
            }
        }, "Закрыть", null);
    }

    /**
     * Подменю А: Выбор аудиодорожки с запоминанием выбора
     */
    private static void showAudioSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] audioOptions = {
            "Спокойствие",
            "Безмятежность",
            "Мечтательность",
            "Удовлетворение"
        };

        int currentAudioIndex = prefs.getInt("pref_audio_index", 0);

        showCustomMenuDialog(context, "Выбор аудио сопровождения", audioOptions, currentAudioIndex, position -> {
            int selectedResId = R.raw.o2test_01;
            String trackName = "Спокойствие";

            switch (position) {
                case 0:
                    selectedResId = R.raw.o2test_01;
                    trackName = "Спокойствие";
                    break;
                case 1:
                    selectedResId = R.raw.o2test_02;
                    trackName = "Безмятежность";
                    break;
                case 2:
                    selectedResId = R.raw.o2test_03;
                    trackName = "Мечтательность";
                    break;
                case 3:
                    selectedResId = R.raw.o2test_04;
                    trackName = "Удовлетворение";
                    break;
            }

            prefs.edit().putInt("selected_audio_res", selectedResId)
                        .putString("selected_audio_name", trackName)
                        .putInt("pref_audio_index", position)
                        .apply();

            if (callback != null) {
                callback.onAudioResourceChanged(selectedResId, trackName);
                callback.onLog("[НАСТРОЙКИ] Выбрана аудиодорожка: " + trackName);
            }
            Toast.makeText(context, "Аудио: " + trackName, Toast.LENGTH_SHORT).show();
        }, "Назад", () -> showSettingsDialog(context, prefs, callback));
    }

    /**
     * Подменю Б: Режимы видео с запоминанием выбора
     */
    private static void showVideoSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] videoModes = {
            "Одиночный",
            "Трансляция"
        };

        int currentVideoIndex = prefs.getInt("pref_video_index", 0);

        showCustomMenuDialog(context, "Режим визуализации", videoModes, currentVideoIndex, position -> {
            String modeStr = (position == 0) ? "single" : "broadcast";
            prefs.edit().putString("video_render_mode", modeStr)
                        .putInt("pref_video_index", position)
                        .apply();
            String label = (position == 0) ? "Одиночный" : "Трансляция";

            if (callback != null) {
                callback.onLog("[НАСТРОЙКИ] Режим видео изменен на: " + label);
            }
            Toast.makeText(context, "Режим видео: " + label, Toast.LENGTH_SHORT).show();
        }, "Назад", () -> showSettingsDialog(context, prefs, callback));
    }

    /**
     * Подменю В: Резервное копирование с проверкой папки Documents/O2Test
     */
    private static void showBackupSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File exportDir = new File(baseDir, "O2Test");

        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File backupFile = new File(exportDir, "o2test_backup.zip");
        boolean hasBackup = backupFile.exists();

        CharSequence[] backupOptions;
        if (hasBackup) {
            backupOptions = new CharSequence[]{
                "Экспорт базы данных (Создать бэкап)",
                "Импорт базы данных (Восстановить бэкап)"
            };
        } else {
            backupOptions = new CharSequence[]{
                "Экспорт базы данных (Создать бэкап)",
                "Импорт базы данных (Недоступно: нет файла)"
            };
        }

        showCustomMenuDialog(context, "Резервное копирование базы данных", backupOptions, -1, position -> {
            if (position == 0) {
                if (callback != null) {
                    callback.onExportBackupRequested(backupFile);
                }
            } else if (position == 1) {
                if (!hasBackup) {
                    Toast.makeText(context, "Файл бэкапа не найден в папке O2Test!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (callback != null) {
                    callback.onImportBackupRequested(backupFile);
                }
            }
        }, "Назад", () -> showSettingsDialog(context, prefs, callback));
    }
}
