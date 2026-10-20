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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class SettingsDialogManager {

    private static final String PREF_NAME = "O2AppPrefs";
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
            
            // Если это сохраненный/выбранный пункт, подсвечиваем его при открытии
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

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, menuItems, -1);
        AlertDialog dialog = builder.setAdapter(adapter, null).setNegativeButton("Закрыть", null).create();

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
            }, CLICK_DELAY_MS);
        });

        dialog.show();
    }

    /**
     * Подменю А: Настройки сохранения сессий с миграцией файлов и бэкапов
     */
    private static void showFolderSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] paths = {"Папка Документы (Documents)", "Папка Загрузки (Downloads)"};
        
        // Читаем последний выбор (по умолчанию 0 - Documents)
        int currentFolderIndex = prefs.getInt("pref_folder_index", 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор корневой папки отчетов"));

        HighlightArrayAdapter adapter = new HighlightArrayAdapter(context, paths, currentFolderIndex);
        AlertDialog dialog = builder.setAdapter(adapter, null).create();

        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.setPressed(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();

                if (callback != null && !callback.isProfileAuthorized()) {
                    Toast.makeText(context, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
                    callback.openProfilesManager();
                    return;
                }

                String profileName = (callback != null) ? callback.getActiveProfileName() : "DefaultProfile";

                // Определяем старую и новую директории
                File oldBaseDir = (currentFolderIndex == 0) 
                        ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File oldO2Dir = new File(oldBaseDir, "O2Test");
                File oldProfileDir = new File(oldO2Dir, profileName);

                File newBaseDir = (position == 0) 
                        ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File newO2Dir = new File(newBaseDir, "O2Test");
                if (!newO2Dir.exists()) newO2Dir.mkdirs();

                File newProfileDir = new File(newO2Dir, profileName);
                if (!newProfileDir.exists()) newProfileDir.mkdirs();

                // Миграция файлов (сессии, PDF, бэкапы) со старого места на новое
                if (oldProfileDir.exists() && oldProfileDir.isDirectory() && !oldProfileDir.equals(newProfileDir)) {
                    File[] files = oldProfileDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                File destFile = new File(newProfileDir, file.getName());
                                copyAndRmvFile(file, destFile);
                            }
                        }
                    }
                    // Удаляем старую пустую папку профиля
                    deleteDirRecursively(oldProfileDir);
                }

                String finalPath = newProfileDir.getAbsolutePath();
                prefs.edit().putString("custom_export_path", finalPath)
                            .putInt("pref_folder_index", position)
                            .apply();

                if (callback != null) {
                    callback.onLog("[НАСТРОЙКИ] Путь сохранения изменен: " + finalPath);
                }
                Toast.makeText(context, "Папка установлена: " + (position == 0 ? "Documents" : "Downloads"), Toast.LENGTH_SHORT).show();
            }, CLICK_DELAY_MS);
        });

        dialog.show();
    }

    /**
     * Подменю Б: Выбор аудиодорожки с запоминанием выбора
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
     * Подменю В: Режимы видео с запоминанием выбора
     */
    private static void showVideoSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        CharSequence[] videoModes = {
            "Одиночный",
            "Трансляция"
        };

        int currentVideoIndex = prefs.getInt("pref_video_index", 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Режим визуализации видео"));

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
     * Подменю Г: Резервное копирование (автоматически создает папку O2Test в Documents по умолчанию)
     */
    private static void showBackupSettingsDialog(Context context, SharedPreferences prefs, SettingsCallback callback) {
        String customPath = prefs.getString("custom_export_path", null);

        File exportDir;
        if (customPath == null || customPath.isEmpty()) {
            // Если путь не задан, берем дефолтную папку Documents и подпапку O2Test
            File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            exportDir = new File(baseDir, "O2Test");
        } else {
            // Если в customPath прописан путь до конкретного профиля, поднимаемся на уровень выше к корневой O2Test
            File profileDir = new File(customPath);
            if (profileDir.getParentFile() != null && profileDir.getParentFile().getName().equals("O2Test")) {
                exportDir = profileDir.getParentFile();
            } else {
                exportDir = profileDir;
            }
        }

        // Гарантированно создаем корневую папку O2Test, если ее еще нет
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File backupFile = new File(exportDir, "o2test_backup.db");
        boolean hasBackup = backupFile.exists();

        CharSequence[] backupOptions = {
            "Экспорт базы данных (Создать бэкап)",
            hasBackup ? "Импорт базы данных (Восстановить бэкап)" : "Импорт базы данных (Недоступно: нет файла)"
        };

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

    // Вспомогательный метод миграции файлов
    private static void copyAndRmvFile(File src, File dst) {
        try {
            FileChannel in = new FileInputStream(src).getChannel();
            FileChannel out = new FileOutputStream(dst).getChannel();
            out.transferFrom(in, 0, in.size());
            in.close();
            out.close();
            src.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Вспомогательный метод удаления старой папки
    private static boolean deleteDirRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirRecursively(child);
                }
            }
        }
        return dir.delete();
    }
}
