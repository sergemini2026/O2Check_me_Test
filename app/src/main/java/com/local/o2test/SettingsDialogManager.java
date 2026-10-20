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

    private static final String PREF_NAME = "O2AppPrefs";

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
        // Цвет при нажатии / фокусе (полупрозрачный неоновый бирюзовый)
        drawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x4000F2FE));
        drawable.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(0x4000F2FE));
        // Обычное состояние (прозрачное)
        drawable.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return drawable;
    }

    /**
     * Кастомный адаптер списков с интерактивным откликом при клике
     */
    private static class HighlightArrayAdapter extends ArrayAdapter<CharSequence> {
        public HighlightArrayAdapter(Context context, CharSequence[] items) {
            super(context, android.R.layout.select_dialog_item, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            view.setBackground(createRowSelector());
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(Color.WHITE);
                ((TextView) view).setTextSize(15);
                view.setPadding(32, 24, 32, 24);
            }
            return view;
        }
    }

    /**
     * Главное меню настроек
     */
    public static void showSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] menuItems = {
            "Сохранение сессий",
            "Аудио",
            "Видео",
            "Резервирование"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Настройки системы"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, menuItems);
        AlertDialog dialog = builder.setAdapter(adapter, null).setNegativeButton("Закрыть", null).create();

        // Кастомная обработка клика с задержкой для визуального отклика
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();
                if (position == 0) {
                    showFolderSettingsDialog(context, prefs, callback);
                } else if (position == 1) {
                    showAudioSettingsDialog(context, prefs, callback);
                } else if (position == 2) {
                    showVideoSettingsDialog(context, prefs, callback);
                } else if (position == 3) {
                    showBackupSettingsDialog(context, prefs, callback);
                }
            }, 120);
        });

        dialog.show();
    }

    /**
     * Подменю А: Настройки сохранения сессий
     */
    private static void showFolderSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] paths = {"Папка Документы (Documents)", "Папка Загрузки (Downloads)"};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор корневой папки отчетов"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, paths);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

                if (callback != null && !callback.isProfileAuthorized()) {
                    Toast.makeText(context, "Сначала выберите профиль для создания папки!", Toast.LENGTH_SHORT).show();
                    callback.openProfilesManager();
                    return;
                }

                String profileName = (callback != null) ? callback.getActiveProfileName() : "DefaultProfile";

                File baseDir = (position == 0) 
                        ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

                File o2TestDir = new File(baseDir, "O2Test");
                if (!o2TestDir.exists()) {
                    o2TestDir.mkdirs();
                }

                File profileDir = new File(o2TestDir, profileName);
                if (!profileDir.exists()) {
                    profileDir.mkdirs();
                }

                String finalPath = profileDir.getAbsolutePath();
                prefs.edit().putString("custom_export_path", finalPath).apply();
                
                if (callback != null) {
                    callback.onLog("[НАСТРОЙКИ] Путь сохранения сессий установлен: " + finalPath);
                }
                Toast.makeText(context, "Папка создана: " + profileName, Toast.LENGTH_SHORT).show();
            }, 120);
        });

        dialog.show();
    }

    /**
     * Подменю Б: Выбор аудиодорожки
     */
    private static void showAudioSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] audioOptions = {
            "Спокойствие",
            "Безмятежность",
            "Мечтательность",
            "Удовлетворение"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор аудио сопровождения"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, audioOptions);
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
                            .apply();

                if (callback != null) {
                    callback.onAudioResourceChanged(selectedResId, trackName);
                    callback.onLog("[НАСТРОЙКИ] Выбрана аудиодорожка: " + trackName);
                }
                Toast.makeText(context, "Аудио: " + trackName, Toast.LENGTH_SHORT).show();
            }, 120);
        });

        dialog.show();
    }

    /**
     * Подменю В: Режимы видео
     */
    private static void showVideoSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] videoModes = {
            "Одиночный",
            "Трансляция"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Режим визуализации видео"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, videoModes);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

                String modeStr = (position == 0) ? "single" : "broadcast";
                prefs.edit().putString("video_render_mode", modeStr).apply();
                String label = (position == 0) ? "Одиночный" : "Трансляция";

                if (callback != null) {
                    callback.onLog("[НАСТРОЙКИ] Режим видео изменен на: " + label);
                }
                Toast.makeText(context, "Режим видео: " + label, Toast.LENGTH_SHORT).show();
            }, 120);
        });

        dialog.show();
    }

    /**
     * Подменю Г: Резервное копирование
     */
    private static void showBackupSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        String customPath = prefs.getString("custom_export_path", null);

        if (customPath == null || customPath.isEmpty()) {
            Toast.makeText(context, "Сначала задайте путь сохранения в пункте 'Сохранение сессий'!", Toast.LENGTH_LONG).show();
            return;
        }

        File exportDir = new File(customPath);
        File backupFile = new File(exportDir, "o2test_backup.db");
        boolean hasBackup = backupFile.exists();

        CharSequence[] backupOptions = {
            "Экспорт базы данных (Создать бэкап)",
            hasBackup ? "Импорт базы данных (Восстановить бэкап)" : "Импорт базы данных (Недоступно: нет файла)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Резервное копирование базы данных"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, backupOptions);
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
                        Toast.makeText(context, "Файл бэкапа не найден в папке сессий!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (callback != null) {
                        callback.onImportBackupRequested(backupFile);
                    }
                }
            }, 120);
        });

        dialog.show();
    }
}
