package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SessionExportManager {

    private static final String PREF_NAME = "O2AppPrefs";

    public interface ExportCallback {
        void onLog(String message);
    }

    /**
     * 1) Проверка наличия папки O2Test (при отсутствии — создание),
     * очистка только старых папок профилей (директорий),
     * с сохранением файла бэкапа (o2test_backup.zip) для возможности импорта.
     */
    public static void cleanLegacyO2TestDirectory(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    return; // Нет системного разрешения "Все файлы"
                }
            }

            File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File o2TestDir = new File(baseDir, "O2Test");
            
            if (!o2TestDir.exists()) {
                o2TestDir.mkdirs();
                return;
            }

            if (o2TestDir.isDirectory()) {
                File[] children = o2TestDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        // Удаляем исключительно старые папки профилей, оставляя o2test_backup.zip нетронутым
                        if (child.isDirectory()) {
                            deleteDirRecursive(child);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            callback.onLog("Сохранение сессии в папку профиля " + activeProfileName + "...");
        }

        List<DataPoint> copyForExport;
        synchronized (sessionData) {
            copyForExport = new ArrayList<>(sessionData);
        }

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File o2TestDir = new File(baseDir, "O2Test");
        File profileDir = new File(o2TestDir, activeProfileName);
        if (!profileDir.exists()) {
            profileDir.mkdirs();
        }
        String customPath = profileDir.getAbsolutePath();

        CsvExporter.saveSessionToCsv(context, copyForExport, customPath, new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(String filePath, String fileName) {
                if (callback != null) {
                    callback.onLog("CSV успешно сохранен в папку профиля: " + fileName);
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

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File o2TestDir = new File(baseDir, "O2Test");
        File profileDir = new File(o2TestDir, activeProfileName);
        if (!profileDir.exists()) {
            profileDir.mkdirs();
        }
        String customPath = profileDir.getAbsolutePath();

        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                long endTime = System.currentTimeMillis();

                SessionEntity session = new SessionEntity(
                        0,
                        activeProfileId,
                        "Базовый мониторинг",
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

                List<SessionEntity> profileSessions = db.sessionDao().getSessionsByProfileSync(activeProfileId);
                List<Double> pstiHistory = new ArrayList<>();
                for (int i = profileSessions.size() - 1; i >= 0; i--) {
                    pstiHistory.add(profileSessions.get(i).getPstiScore());
                }
                if (pstiHistory.isEmpty() || pstiHistory.get(pstiHistory.size() - 1) != lastPstiScore) {
                    pstiHistory.add(lastPstiScore);
                }

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
                        pstiHistory,
                        new PdfExporter.PdfExportCallback() {
                            @Override
                            public void onSuccess(String pdfFilePath, String fileNamePdf) {
                                dbExecutor.execute(() -> {
                                    db.sessionDao().updatePathsSync(sessionId, pdfFilePath, csvPath);
                                });

                                if (callback != null) {
                                    callback.onLog("PDF успешно создан в папке профиля: " + fileNamePdf);
                                }
                                Toast.makeText(context, "Сессия и отчеты сохранены в папку профиля", Toast.LENGTH_LONG).show();
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
     * 3) При экспорте (создании бэкапа) сохраняется база, WAL, SHM и вся структура папки.
     */
    public static void exportDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        dbExecutor.execute(() -> {
            try {
                File dbFile = context.getDatabasePath("synergy_database");
                if (!dbFile.exists()) {
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> Toast.makeText(context, "Файл базы данных не найден!", Toast.LENGTH_SHORT).show());
                    return;
                }

                File exportDir = backupFile.getParentFile();
                if (exportDir != null && !exportDir.exists()) {
                    exportDir.mkdirs();
                }

                File tempZip = new File(exportDir, "temp_backup.zip");
                if (tempZip.exists()) tempZip.delete();

                try (FileOutputStream fos = new FileOutputStream(tempZip);
                     ZipOutputStream zos = new ZipOutputStream(fos)) {

                    addFileToZip(dbFile, "o2test_backup.db", zos);

                    File walFile = new File(dbFile.getParentFile(), "synergy_database-wal");
                    if (walFile.exists()) {
                        addFileToZip(walFile, "o2test_backup.db-wal", zos);
                    }

                    File shmFile = new File(dbFile.getParentFile(), "synergy_database-shm");
                    if (shmFile.exists()) {
                        addFileToZip(shmFile, "o2test_backup.db-shm", zos);
                    }

                    File[] children = exportDir.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            if (child.equals(backupFile) || child.equals(tempZip)) {
                                continue;
                            }
                            addRecursiveToZip(exportDir, child, zos);
                        }
                    }
                }

                if (backupFile.exists()) {
                    backupFile.delete();
                }
                if (!tempZip.renameTo(backupFile)) {
                    copyFile(tempZip, backupFile);
                    tempZip.delete();
                }

                File oldDb = new File(exportDir, "o2test_backup.db");
                if (oldDb.exists()) oldDb.delete();
                File oldWal = new File(exportDir, "o2test_backup.db-wal");
                if (oldWal.exists()) oldWal.delete();
                File oldShm = new File(exportDir, "o2test_backup.db-shm");
                if (oldShm.exists()) oldShm.delete();

                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLog("[БЭКАП] Полный архив успешно создан: " + backupFile.getAbsolutePath());
                    }
                    Toast.makeText(context, "Экспорт бэкапа выполнен успешно", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> Toast.makeText(context, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * 2) Импорт и восстановление базы через бэкап со сбросом синглтона Room и переносом WAL/SHM файлов.
     */
    public static void importDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle("Внимание!")
            .setMessage("Импорт бэкапа перезапишет текущие профили и сессии. Продолжить?")
            .setPositiveButton("Да", (d, w) -> {
                dbExecutor.execute(() -> {
                    try {
                        File exportDir = backupFile.getParentFile();
                        if (exportDir == null || !exportDir.exists()) {
                            return;
                        }

                        unzipFolder(backupFile, exportDir);

                        File extractedDb = new File(exportDir, "o2test_backup.db");
                        if (!extractedDb.exists()) {
                            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                            mainHandler.post(() -> Toast.makeText(context, "В архиве не найден файл базы данных!", Toast.LENGTH_LONG).show());
                            return;
                        }

                        // 1. Закрываем текущую БД и сбрасываем инстанс Room в памяти
                        try {
                            AppDatabase.getInstance(context.getApplicationContext()).close();
                        } catch (Exception ignored) {}

                        try {
                            java.lang.reflect.Field instanceField = AppDatabase.class.getDeclaredField("INSTANCE");
                            instanceField.setAccessible(true);
                            instanceField.set(null, null);
                        } catch (Exception ignored) {
                            try {
                                java.lang.reflect.Method resetMethod = AppDatabase.class.getMethod("resetInstance");
                                resetMethod.invoke(null);
                            } catch (Exception ignored2) {}
                        }

                        // 2. Восстанавливаем основной файл БД
                        File dbFile = context.getDatabasePath("synergy_database");
                        try (FileChannel src = new FileInputStream(extractedDb).getChannel();
                             FileChannel dst = new FileOutputStream(dbFile).getChannel()) {
                            dst.transferFrom(src, 0, src.size());
                        }

                        // 3. Восстанавливаем / очищаем файлы WAL и SHM
                        File extractedWal = new File(exportDir, "o2test_backup.db-wal");
                        File walFile = new File(dbFile.getParentFile(), "synergy_database-wal");
                        if (extractedWal.exists()) {
                            try (FileChannel src = new FileInputStream(extractedWal).getChannel();
                                 FileChannel dst = new FileOutputStream(walFile).getChannel()) {
                                dst.transferFrom(src, 0, src.size());
                            }
                            extractedWal.delete();
                        } else if (walFile.exists()) {
                            walFile.delete();
                        }

                        File extractedShm = new File(exportDir, "o2test_backup.db-shm");
                        File shmFile = new File(dbFile.getParentFile(), "synergy_database-shm");
                        if (extractedShm.exists()) {
                            try (FileChannel src = new FileInputStream(extractedShm).getChannel();
                                 FileChannel dst = new FileOutputStream(shmFile).getChannel()) {
                                dst.transferFrom(src, 0, src.size());
                            }
                            extractedShm.delete();
                        } else if (shmFile.exists()) {
                            shmFile.delete();
                        }

                        extractedDb.delete();

                        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onLog("[БЭКАП] База данных и отчеты сессий успешно восстановлены из архива.");
                            }
                            Toast.makeText(context, "Импорт выполнен успешно!", Toast.LENGTH_LONG).show();
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

    private static void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
        }
        zos.closeEntry();
    }

    private static void addRecursiveToZip(File rootFolder, File file, ZipOutputStream zos) throws IOException {
        String filePath = file.getAbsolutePath();
        String rootPath = rootFolder.getAbsolutePath();
        String zipEntryName = filePath.substring(rootPath.length() + 1);

        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(zipEntryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addRecursiveToZip(rootFolder, child, zos);
                }
            }
        } else {
            addFileToZip(file, zipEntryName, zos);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileChannel in = new FileInputStream(src).getChannel();
             FileChannel out = new FileOutputStream(dst).getChannel()) {
            out.transferFrom(in, 0, in.size());
        }
    }

    private static void unzipFolder(File zipFile, File targetFolder) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(targetFolder, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] bytes = new byte[1024];
                        int length;
                        while ((length = zis.read(bytes)) >= 0) {
                            fos.write(bytes, 0, length);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static boolean deleteDirRecursive(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirRecursive(child);
                }
            }
        }
        return dir != null && dir.delete();
    }
}
