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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SettingsDialogManager {

    private static final long CLICK_DELAY_MS = 500; // Задержка 0.5 сек для визуального отклика

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

    /**
     * Кастомный адаптер списков с подсветкой текущего активного пункта и отступом (~1 см слева)
     */
    private static class HighlightArrayAdapter extends ArrayAdapter<CharSequence> {
        private final int selectedIndex;

        public HighlightArrayAdapter(Context context, CharSequence[] items, int selectedIndex) {
            super(context, android.R.layout.select_dialog_item, items);
            this.selectedIndex = selectedIndex;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            
            if (position == selectedIndex) {
                view.setBackgroundColor(0x2500F2FE); // Легкая постоянная подсветка выбранного
            } else {
                view.setBackground(createRowSelector());
            }

            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                tv.setPadding(75, 28, 32, 28);
            }
            return view;
        }
    }

    /**
     * Главное меню настроек (без пункта сохранения сессий — всё строго по стандарту Documents/O2Test)
     */
    public static void showSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] menuItems = {
            "Аудио",
            "Видео",
            "Резервирование"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Настройки системы"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, menuItems, -1);
        AlertDialog dialog = builder.setAdapter(adapter, null).setNegativeButton("Закрыть", null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();
                if (position == 0) {
                    showAudioSettingsDialog(context, prefs, callback);
                } else if (position == 1) {
                    showVideoSettingsDialog(context, prefs, callback);
                } else if (position == 2) {
                    showBackupSettingsDialog(context, prefs, callback);
                }
            }, CLICK_DELAY_MS);
        });

        dialog.show();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор аудио сопровождения"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, audioOptions, currentAudioIndex);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

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
            }, CLICK_DELAY_MS);
        });

        dialog.show();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Режим визуализации"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, videoModes, currentVideoIndex);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

                String modeStr = (position == 0) ? "single" : "broadcast";
                prefs.edit().putString("video_render_mode", modeStr)
                            .putInt("pref_video_index", position)
                            .apply();
                String label = (position == 0) ? "Одиночный" : "Трансляция";

                if (callback != null) {
                    callback.onLog("[НАСТРОЙКИ] Режим видео изменен на: " + label);
                }
                Toast.makeText(context, "Режим видео: " + label, Toast.LENGTH_SHORT).show();
            }, CLICK_DELAY_MS);
        });

        dialog.show();
    }

    /**
     * Подменю В: Резервное копирование с проверкой папки Documents/O2Test и файла бэкапа
     */
    private static void showBackupSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        // Жестко фиксированный путь: Documents/O2Test
        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File exportDir = new File(baseDir, "O2Test");

        // Проверяем наличие папки, если нет — создаем
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File backupFile = new File(exportDir, "o2test_backup.db");
        boolean hasBackup = backupFile.exists();

        // Формируем пункты меню в зависимости от наличия бэкапа согласно логике
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

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Резервное копирование базы данных"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, backupOptions, -1);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

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
            }, CLICK_DELAY_MS);
        });

        dialog.show();
    }
}
