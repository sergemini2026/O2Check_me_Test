package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.HrvSnapshotEntity;
import com.local.o2test.db.entity.SessionEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class SessionExportManager {

    private static final String PREF_NAME = "O2AppPrefs";

    public interface ExportCallback {
        void onLog(String message);
    }

    /**
     * Автоматическое сохранение сессии: экспорт в CSV -> запись в БД -> генерация PDF
     */
    public static void saveSessionAutomatically(Context context, 
                                                SharedPreferences prefs,
                                                List<DataPoint> sessionData,
                                                long activeProfileId,
                                                String activeProfileName,
                                                long sessionStartTime,
                                                int currentPolarHr,
                                                int currentSpo2,
                                                double lastPstiScore,
                                                double lastPstiRmssdRatio,
                                                ExecutorService dbExecutor,
                                                HrvForegroundService hrvService,
                                                boolean isBound,
                                                ExportCallback callback) {
        if (callback != null) {
            callback.onLog("Сохранение сессии в CSV и PDF...");
        }

        List<DataPoint> copyForExport;
        synchronized (sessionData) {
            copyForExport = new ArrayList<>(sessionData);
        }

        String customPath = prefs.getString("custom_export_path", null);

        CsvExporter.saveSessionToCsv(context, copyForExport, customPath, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                if (callback != null) {
                    callback.onLog("CSV успешно сохранен: " + fileName);
                }
                saveSessionToDatabase(context, prefs, copyForExport, filePath, activeProfileId, 
                        activeProfileName, sessionStartTime, currentPolarHr, currentSpo2, 
                        lastPstiScore, lastPstiRmssdRatio, dbExecutor, hrvService, isBound, callback);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) {
                    callback.onLog("Ошибка сохранения CSV: " + errorMessage);
                }
                saveSessionToDatabase(context, prefs, copyForExport, "", activeProfileId, 
                        activeProfileName, sessionStartTime, currentPolarHr, currentSpo2, 
                        lastPstiScore, lastPstiRmssdRatio, dbExecutor, hrvService, isBound, callback);
            }
        });
    }

    /**
     * Внутренняя логика записи сессии и HRV-снимков в базу данных
     */
    private static void saveSessionToDatabase(Context context,
                                              SharedPreferences prefs,
                                              List<DataPoint> points,
                                              String csvPath,
                                              long activeProfileId,
                                              String activeProfileName,
                                              long sessionStartTime,
                                              int currentPolarHr,
                                              int currentSpo2,
                                              double lastPstiScore,
                                              double lastPstiRmssdRatio,
                                              ExecutorService dbExecutor,
                                              HrvForegroundService hrvService,
                                              boolean isBound,
                                              ExportCallback callback) {
        if (activeProfileId == -1L) return;

        final List<SessionSnapshot> capturedSnapshots;
        if (isBound && hrvService != null) {
            capturedSnapshots = hrvService.flushSnapshotBuffer();
        } else {
            capturedSnapshots = new ArrayList<>();
        }

        String customPath = prefs.getString("custom_export_path", null);

        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                long endTime = System.currentTimeMillis();

                SessionEntity session = new SessionEntity(
                        0,
                        activeProfileId,
                        "Базовый мониторинг", // или текущий режим тренировки
                        sessionStartTime,
                        endTime,
                        currentPolarHr > 0 ? currentPolarHr : 75,
                        currentSpo2 > 0 ? currentSpo2 : 98,
                        45.0,
                        50.0,
                        1.2,
                        35.0,
                        "",
                        csvPath,
                        lastPstiScore,
                        lastPstiRmssdRatio
                );
                long sessionId = db.sessionDao().insertSessionSync(session);

                List<HrvSnapshotEntity> entityList = new ArrayList<>();
                if (!capturedSnapshots.isEmpty()) {
                    for (SessionSnapshot snapshot : capturedSnapshots) {
                        HrvSnapshotEntity entity = new HrvSnapshotEntity(snapshot.getTimestamp(), snapshot.getMetrics());
                        if (entity.getBaevskySi() > 0 && entity.getBaevskySi() <= 300) {
                            entityList.add(entity);
                        }
                    }
                    if (!entityList.isEmpty()) {
                        db.hrvSnapshotDao().insertAll(entityList);
                    }
                }

                List<Integer> sessionRrList = new ArrayList<>();
                for (DataPoint dp : points) {
                    if (dp.rrMs > 0) {
                        sessionRrList.add(dp.rrMs);
                    }
                }

                if (callback != null) {
                    callback.onLog("Сессия #" + sessionId + " зарегистрирована в БД. Индекс PSTI: " + 
                            String.format(Locale.US, "%.1f%%", lastPstiScore));
                }

                // Генерация PDF на главном потоке (требуется для работы с графикой/контекстом UI если нужно)
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> PdfExporter.generateHrvPdfReport(
                        context,
                        activeProfileName,
                        "Базовый мониторинг",
                        sessionStartTime,
                        endTime,
                        entityList,
                        sessionRrList,
                        points,
                        customPath,
                        lastPstiScore,
                        lastPstiRmssdRatio,
                        new PdfExporter.PdfExportCallback() {
                            @Override
                            public void onSuccess(String pdfFilePath, String fileNamePdf) {
                                dbExecutor.execute(() -> {
                                    db.sessionDao().updatePathsSync(sessionId, pdfFilePath, csvPath);
                                });

                                if (callback != null) {
                                    callback.onLog("PDF успешно создан: " + fileNamePdf);
                                }
                                Toast.makeText(context, "Сессия и отчеты успешно сохранены", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (callback != null) {
                                    callback.onLog("Ошибка создания PDF: " + errorMessage);
                                }
                            }
                        }
                ));

            } catch (Exception e) {
                if (callback != null) {
                    callback.onLog("Ошибка записи сессии в БД: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Экспорт (создание бэкапа) базы данных
     */
    public static void exportDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        dbExecutor.execute(() -> {
            try {
                File dbFile = context.getDatabasePath("o2test_db");
                if (!dbFile.exists()) {
                    dbFile = context.getDatabasePath("app_database");
                }
                if (!dbFile.exists()) {
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> Toast.makeText(context, "Файл базы данных не найден!", Toast.LENGTH_SHORT).show());
                    return;
                }

                FileChannel src = new FileInputStream(dbFile).getChannel();
                FileChannel dst = new FileOutputStream(backupFile).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();

                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLog("[БЭКАП] База данных успешно экспортирована: " + backupFile.getAbsolutePath());
                    }
                    Toast.makeText(context, "Экспорт бэкапа выполнен успешно", Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> Toast.makeText(context, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Импорт (восстановление бэкапа) базы данных
     */
    public static void importDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle("Внимание!")
            .setMessage("Импорт базы данных перезапишет текущие локальные данные. Продолжить?")
            .setPositiveButton("Да", (d, w) -> {
                dbExecutor.execute(() -> {
                    try {
                        File dbFile = context.getDatabasePath("o2test_db");
                        if (!dbFile.exists()) {
                            dbFile = context.getDatabasePath("app_database");
                        }

                        AppDatabase.getInstance(context.getApplicationContext()).close();

                        FileChannel src = new FileInputStream(backupFile).getChannel();
                        FileChannel dst = new FileOutputStream(dbFile).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();

                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onLog("[БЭКАП] База данных успешно восстановлена из бэкапа.");
                            }
                            Toast.makeText(context, "Импорт выполнен. Перезапустите приложение.", Toast.LENGTH_LONG).show();
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> Toast.makeText(context, "Ошибка импорта: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
