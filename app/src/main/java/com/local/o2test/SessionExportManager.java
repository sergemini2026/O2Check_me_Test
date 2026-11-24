package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.HrvSnapshotEntity;
import com.local.o2test.db.entity.ProfileEntity;
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

    private static final boolean DEBUG = true;
    private static final String TAG = "SessionExportManager";
    private static final String PREF_NAME = "O2AppPrefs";

    public interface ExportCallback {
        void onLog(String message);
    }

    /**
     * Запись об одном запуске теста Майера с временными рамками и резюме сходимости ряда.
     */
    public static class MayerTestRunEntry {
        private final long startTimeMs;
        private final long endTimeMs;
        private final String convergenceSummary;

        public MayerTestRunEntry(long startTimeMs, long endTimeMs, String convergenceSummary) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = Math.max(startTimeMs, endTimeMs);
            this.convergenceSummary = (convergenceSummary != null) ? convergenceSummary.trim() : "";
        }

        public long getStartTimeMs() {
            return startTimeMs;
        }

        public long getEndTimeMs() {
            return endTimeMs;
        }

        public String getConvergenceSummary() {
            return convergenceSummary;
        }
    }

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    public static void cleanLegacyO2TestDirectory(Context context) {
        try {
            File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File o2TestDir = new File(baseDir, "O2Test");
            if (!o2TestDir.exists()) {
                o2TestDir.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveSessionAutomatically(final Context context,
                                                final SharedPreferences prefs,
                                                final List<DataPoint> sessionData,
                                                final long activeProfileId,
                                                final String activeProfileName,
                                                final String workoutMode,
                                                final int trainingTargetX,
                                                final long sessionStartTime,
                                                final int currentPolarHr,
                                                final int currentSpo2,
                                                final double lastPstiScore,
                                                final double lastPstiRmssdRatio,
                                                final double baselineLfHf,
                                                final String cycleLfHfValues,
                                                final ExecutorService dbExecutor,
                                                final HrvForegroundService hrvService,
                                                final boolean isBound,
                                                final ExportCallback callback) {
        saveSessionAutomatically(
                context, prefs, sessionData, activeProfileId, activeProfileName,
                workoutMode, trainingTargetX, sessionStartTime, currentPolarHr, currentSpo2,
                lastPstiScore, lastPstiRmssdRatio, baselineLfHf, cycleLfHfValues,
                "", new ArrayList<MayerTestRunEntry>(), dbExecutor, hrvService, isBound, callback
        );
    }

    public static void saveSessionAutomatically(final Context context,
                                                final SharedPreferences prefs,
                                                final List<DataPoint> sessionData,
                                                final long activeProfileId,
                                                final String activeProfileName,
                                                final String workoutMode,
                                                final int trainingTargetX,
                                                final long sessionStartTime,
                                                final int currentPolarHr,
                                                final int currentSpo2,
                                                final double lastPstiScore,
                                                final double lastPstiRmssdRatio,
                                                final double baselineLfHf,
                                                final String cycleLfHfValues,
                                                final String mayerConvergenceSummary,
                                                final ExecutorService dbExecutor,
                                                final HrvForegroundService hrvService,
                                                final boolean isBound,
                                                final ExportCallback callback) {
        saveSessionAutomatically(
                context, prefs, sessionData, activeProfileId, activeProfileName,
                workoutMode, trainingTargetX, sessionStartTime, currentPolarHr, currentSpo2,
                lastPstiScore, lastPstiRmssdRatio, baselineLfHf, cycleLfHfValues,
                mayerConvergenceSummary, new ArrayList<MayerTestRunEntry>(),
                dbExecutor, hrvService, isBound, callback
        );
    }

    public static void saveSessionAutomatically(final Context context,
                                                final SharedPreferences prefs,
                                                final List<DataPoint> sessionData,
                                                final long activeProfileId,
                                                final String activeProfileName,
                                                final String workoutMode,
                                                final int trainingTargetX,
                                                final long sessionStartTime,
                                                final int currentPolarHr,
                                                final int currentSpo2,
                                                final double lastPstiScore,
                                                final double lastPstiRmssdRatio,
                                                final double baselineLfHf,
                                                final String cycleLfHfValues,
                                                final String mayerConvergenceSummary,
                                                final List<MayerTestRunEntry> mayerTestRuns,
                                                final ExecutorService dbExecutor,
                                                final HrvForegroundService hrvService,
                                                final boolean isBound,
                                                final ExportCallback callback) {
        if (callback != null) {
            callback.onLog("Сохранение сессии в папку профиля " + activeProfileName + "...");
        }

        List<DataPoint> allPoints = new ArrayList<>();
        List<DataPoint> trainingPoints = null;
        List<DataPoint> testPoints = null;
        if (isBound && hrvService != null) {
            trainingPoints = hrvService.getTrainingDataPoints();
            testPoints = hrvService.getTestDataPoints();
            if (trainingPoints != null) allPoints.addAll(trainingPoints);
            if (testPoints != null) allPoints.addAll(testPoints);
        } else {
            if (sessionData != null) allPoints.addAll(sessionData);
            trainingPoints = (sessionData != null) ? new ArrayList<>(sessionData) : new ArrayList<>();
            testPoints = new ArrayList<>();
        }
        if (trainingPoints == null) trainingPoints = new ArrayList<>();
        if (testPoints == null) testPoints = new ArrayList<>();

        final List<DataPoint> finalAllPoints = allPoints;
        final List<DataPoint> finalTrainingPoints = trainingPoints;
        final List<DataPoint> finalTestPoints = testPoints;
        final String finalWorkoutMode = (workoutMode != null && !workoutMode.isEmpty()) ? workoutMode : "Базовый мониторинг";
        final String finalConvergenceSummary = (mayerConvergenceSummary != null) ? mayerConvergenceSummary : "";
        final List<MayerTestRunEntry> finalMayerTestRuns = (mayerTestRuns != null)
                ? new ArrayList<>(mayerTestRuns)
                : new ArrayList<MayerTestRunEntry>();

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File o2TestDir = new File(baseDir, "O2Test");
        File profileDir = new File(o2TestDir, activeProfileName);
        if (!profileDir.exists()) {
            profileDir.mkdirs();
        }
        final String customPath = profileDir.getAbsolutePath();

        CsvExporter.saveSessionToCsv(context, finalAllPoints, customPath, "", new CsvExporter.ExportCallback() {
            @Override
            public void onSuccess(final String csvFilePath, String csvFileName) {
                if (callback != null) {
                    callback.onLog("Единый CSV-файл сессии успешно сохранен: " + csvFileName);
                }
                proceedWithDatabaseAndPdf(context, prefs, finalTrainingPoints, finalTestPoints, csvFilePath,
                        activeProfileId, activeProfileName, finalWorkoutMode, trainingTargetX, sessionStartTime, currentPolarHr, currentSpo2,
                        lastPstiScore, lastPstiRmssdRatio, baselineLfHf, cycleLfHfValues, finalConvergenceSummary, finalMayerTestRuns,
                        dbExecutor, hrvService, isBound, callback);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) {
                    callback.onLog("Ошибка сохранения CSV: " + errorMessage);
                }
                proceedWithDatabaseAndPdf(context, prefs, finalTrainingPoints, finalTestPoints, "",
                        activeProfileId, activeProfileName, finalWorkoutMode, trainingTargetX, sessionStartTime, currentPolarHr, currentSpo2,
                        lastPstiScore, lastPstiRmssdRatio, baselineLfHf, cycleLfHfValues, finalConvergenceSummary, finalMayerTestRuns,
                        dbExecutor, hrvService, isBound, callback);
            }
        });
    }

    private static void proceedWithDatabaseAndPdf(final Context context,
                                                  final SharedPreferences prefs,
                                                  final List<DataPoint> trainingPoints,
                                                  final List<DataPoint> testPoints,
                                                  final String csvPath,
                                                  final long activeProfileId,
                                                  final String activeProfileName,
                                                  final String workoutMode,
                                                  final int trainingTargetX,
                                                  final long sessionStartTime,
                                                  final int currentPolarHr,
                                                  final int currentSpo2,
                                                  final double lastPstiScore,
                                                  final double lastPstiRmssdRatio,
                                                  final double baselineLfHf,
                                                  final String cycleLfHfValues,
                                                  final ExecutorService dbExecutor,
                                                  final HrvForegroundService hrvService,
                                                  final boolean isBound,
                                                  final ExportCallback callback) {
        proceedWithDatabaseAndPdf(
                context, prefs, trainingPoints, testPoints, csvPath,
                activeProfileId, activeProfileName, workoutMode, trainingTargetX, sessionStartTime,
                currentPolarHr, currentSpo2, lastPstiScore, lastPstiRmssdRatio,
                baselineLfHf, cycleLfHfValues, "", new ArrayList<MayerTestRunEntry>(),
                dbExecutor, hrvService, isBound, callback
        );
    }

    private static void proceedWithDatabaseAndPdf(final Context context,
                                                  final SharedPreferences prefs,
                                                  final List<DataPoint> trainingPoints,
                                                  final List<DataPoint> testPoints,
                                                  final String csvPath,
                                                  final long activeProfileId,
                                                  final String activeProfileName,
                                                  final String workoutMode,
                                                  final int trainingTargetX,
                                                  final long sessionStartTime,
                                                  final int currentPolarHr,
                                                  final int currentSpo2,
                                                  final double lastPstiScore,
                                                  final double lastPstiRmssdRatio,
                                                  final double baselineLfHf,
                                                  final String cycleLfHfValues,
                                                  final String mayerConvergenceSummary,
                                                  final ExecutorService dbExecutor,
                                                  final HrvForegroundService hrvService,
                                                  final boolean isBound,
                                                  final ExportCallback callback) {
        proceedWithDatabaseAndPdf(
                context, prefs, trainingPoints, testPoints, csvPath,
                activeProfileId, activeProfileName, workoutMode, trainingTargetX, sessionStartTime,
                currentPolarHr, currentSpo2, lastPstiScore, lastPstiRmssdRatio,
                baselineLfHf, cycleLfHfValues, mayerConvergenceSummary, new ArrayList<MayerTestRunEntry>(),
                dbExecutor, hrvService, isBound, callback
        );
    }

    private static void proceedWithDatabaseAndPdf(final Context context,
                                                  final SharedPreferences prefs,
                                                  final List<DataPoint> trainingPoints,
                                                  final List<DataPoint> testPoints,
                                                  final String csvPath,
                                                  final long activeProfileId,
                                                  final String activeProfileName,
                                                  final String workoutMode,
                                                  final int trainingTargetX,
                                                  final long sessionStartTime,
                                                  final int currentPolarHr,
                                                  final int currentSpo2,
                                                  final double lastPstiScore,
                                                  final double lastPstiRmssdRatio,
                                                  final double baselineLfHf,
                                                  final String cycleLfHfValues,
                                                  final String mayerConvergenceSummary,
                                                  final List<MayerTestRunEntry> mayerTestRuns,
                                                  final ExecutorService dbExecutor,
                                                  final HrvForegroundService hrvService,
                                                  final boolean isBound,
                                                  final ExportCallback callback) {
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
        final String customPath = profileDir.getAbsolutePath();
        final String finalConvergenceSummary = (mayerConvergenceSummary != null) ? mayerConvergenceSummary : "";
        final List<MayerTestRunEntry> finalMayerTestRuns = (mayerTestRuns != null)
                ? new ArrayList<>(mayerTestRuns)
                : new ArrayList<MayerTestRunEntry>();

        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                long endTime = System.currentTimeMillis();

                // Получаем актуальный профиль из базы для извлечения сохраненного значения FM
                ProfileEntity profile = db.profileDao().getProfileSync(activeProfileId);
                final float profileFm = (profile != null && profile.getFm() > 0f) ? profile.getFm() : 0.1f;

                SessionEntity session = new SessionEntity(
                        0,
                        activeProfileId,
                        workoutMode,
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
                        lastPstiRmssdRatio,
                        baselineLfHf,
                        cycleLfHfValues
                );
                long sessionId = db.sessionDao().insertSessionSync(session);

                List<HrvSnapshotEntity> entityList = new ArrayList<>();
                int dumpSnapshotsCount = 0;

                if (!capturedSnapshots.isEmpty()) {
                    for (SessionSnapshot snapshot : capturedSnapshots) {
                        HrvSnapshotEntity entity = new HrvSnapshotEntity(
                                snapshot.getTimestamp(),
                                snapshot.getMetrics(),
                                snapshot.getModulationSec(),
                                snapshot.isPstiPhase(),
                                snapshot.isMacroDump(),
                                snapshot.isMayerTestMode(),
                                snapshot.getSliceIndex(),
                                snapshot.getDumpIndex(),
                                snapshot.getBlockNumber(),
                                snapshot.getPhim()
                        );
                        if (entity.getBaevskySi() > 0 && entity.getBaevskySi() <= 300) {
                            entityList.add(entity);
                            if (snapshot.isMacroDump()) {
                                dumpSnapshotsCount++;
                            }
                        }
                    }

                    if (DEBUG) {
                        Log.d(TAG, String.format(Locale.US, "[EXPORT_MANAGER] Буфер выгружен: всего снимков = %d, валидных в БД = %d, из них макро-дампов (isMacroDump=true) = %d",
                                capturedSnapshots.size(), entityList.size(), dumpSnapshotsCount));
                    }

                    if (!entityList.isEmpty()) {
                        db.hrvSnapshotDao().insertAll(entityList);
                        if (DEBUG) {
                            Log.d(TAG, "[EXPORT_MANAGER] Пачка размеченных снимков успешно записана в базу данных.");
                        }
                    }
                }

                List<HrvSnapshotEntity> reportSnapshots = db.hrvSnapshotDao().getOrderedSnapshotsForReport(sessionStartTime, endTime);
                if (reportSnapshots == null || reportSnapshots.isEmpty()) {
                    reportSnapshots = entityList;
                }

                // Сопоставляем каждый снимок отчёта с актуальным значением FM из capturedSnapshots
                final List<Float> snapshotFmValues = resolveSnapshotFmValues(reportSnapshots, capturedSnapshots, profileFm);

                // Определяем первичную частоту теста по снимкам тестового режима (при наличии) для сопоставления по хронологии
                final float primaryTestFrequencyHz = resolvePrimaryTestFrequencyHz(
                        reportSnapshots,
                        snapshotFmValues,
                        finalMayerTestRuns,
                        finalConvergenceSummary,
                        profileFm
                );

                // Переоценка частоты по всей хронологии сессии (LF/HF + HR + амплитуда волны пульса)
                SessionFrequencyReevaluator reevaluator = new SessionFrequencyReevaluator();
                final SessionFrequencyReevaluator.ReevaluationResult reevaluationResult = reevaluator.evaluate(
                        reportSnapshots,
                        trainingPoints,
                        testPoints,
                        primaryTestFrequencyHz,
                        profileFm,
                        finalConvergenceSummary
                );

                if (callback != null && reevaluationResult != null && !reevaluationResult.shortLogMessage.isEmpty()) {
                    callback.onLog(reevaluationResult.shortLogMessage);
                }

                List<Integer> sessionRrList = new ArrayList<>();
                for (DataPoint dp : trainingPoints) {
                    if (dp.rrMs > 0) {
                        sessionRrList.add(dp.rrMs);
                    }
                }
                for (DataPoint dp : testPoints) {
                    if (dp.rrMs > 0) {
                        sessionRrList.add(dp.rrMs);
                    }
                }

                List<SessionEntity> profileSessions = db.sessionDao().getSessionsByProfileSync(activeProfileId);
                int calculatedSessionNumber = profileSessions.size();

                if (callback != null) {
                    callback.onLog("Сессия #" + calculatedSessionNumber + " зарегистрирована в БД. Индекс PSTI: " +
                            String.format(Locale.US, "%.1f%%", lastPstiScore) + String.format(Locale.US, " [Дампов в БД: %d]", dumpSnapshotsCount));
                }

                List<Double> pstiHistory = new ArrayList<>();
                for (int i = profileSessions.size() - 1; i >= 0; i--) {
                    pstiHistory.add(profileSessions.get(i).getPstiScore());
                }
                if (pstiHistory.isEmpty() || pstiHistory.get(pstiHistory.size() - 1) != lastPstiScore) {
                    pstiHistory.add(lastPstiScore);
                }

                final List<HrvSnapshotEntity> finalReportSnapshots = reportSnapshots;
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());

                if (DEBUG && callback != null && (!finalConvergenceSummary.isEmpty() || !finalMayerTestRuns.isEmpty())) {
                    callback.onLog("[EXPORT_MANAGER] Передача истории сходимости ряда в PDF-отчет (запусков=" + finalMayerTestRuns.size() + ", длина сводки=" + finalConvergenceSummary.length() + ")");
                }

                mainHandler.post(() -> PdfExporter.generateHrvPdfReport(
                        context,
                        activeProfileName,
                        workoutMode,
                        trainingTargetX,
                        sessionStartTime,
                        endTime,
                        finalReportSnapshots,
                        sessionRrList,
                        trainingPoints,
                        testPoints,
                        customPath,
                        lastPstiScore,
                        lastPstiRmssdRatio,
                        pstiHistory,
                        profileFm,
                        snapshotFmValues,
                        finalConvergenceSummary,
                        finalMayerTestRuns,
                        reevaluationResult,
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
     * Извлекает первичную частоту теста Майера (в Гц), если в сессии выполнялся тест частоты.
     * Возвращает 0f, если тест в сессии не запускался.
     */
    private static float resolvePrimaryTestFrequencyHz(
            List<HrvSnapshotEntity> reportSnapshots,
            List<Float> snapshotFmValues,
            List<MayerTestRunEntry> mayerTestRuns,
            String convergenceSummary,
            float profileFm
    ) {
        boolean hasTestRun = (mayerTestRuns != null && !mayerTestRuns.isEmpty())
                || (convergenceSummary != null && !convergenceSummary.trim().isEmpty());

        float lastTestSnapshotFm = 0f;
        if (reportSnapshots != null) {
            for (int i = 0; i < reportSnapshots.size(); i++) {
                HrvSnapshotEntity s = reportSnapshots.get(i);
                if (s != null && s.isMayerTestMode()) {
                    hasTestRun = true;
                    if (snapshotFmValues != null && i < snapshotFmValues.size() && snapshotFmValues.get(i) > 0f) {
                        lastTestSnapshotFm = snapshotFmValues.get(i);
                    } else if (s.getModulationSec() > 0f) {
                        lastTestSnapshotFm = 1.0f / s.getModulationSec();
                    }
                }
            }
        }

        if (!hasTestRun) {
            return 0f;
        }
        if (profileFm > 0f) {
            return profileFm;
        }
        return lastTestSnapshotFm;
    }

    /**
     * Подбирает для каждого снимка отчёта актуальное значение FM (в Гц) из выгруженных SessionSnapshot.
     * При отсутствии совпадений использует опорное значение профиля (profileFm).
     */
    private static List<Float> resolveSnapshotFmValues(List<HrvSnapshotEntity> reportSnapshots,
                                                         List<SessionSnapshot> capturedSnapshots,
                                                         float fallbackFm) {
        float defaultFm = (fallbackFm > 0f) ? fallbackFm : 0.1f;
        List<Float> result = new ArrayList<>();
        if (reportSnapshots == null || reportSnapshots.isEmpty()) {
            return result;
        }
        if (capturedSnapshots == null || capturedSnapshots.isEmpty()) {
            for (int i = 0; i < reportSnapshots.size(); i++) {
                result.add(defaultFm);
            }
            return result;
        }

        boolean[] used = new boolean[capturedSnapshots.size()];
        for (HrvSnapshotEntity entity : reportSnapshots) {
            int bestIdx = -1;
            long bestDiff = Long.MAX_VALUE;

            for (int i = 0; i < capturedSnapshots.size(); i++) {
                if (used[i]) continue;
                SessionSnapshot snap = capturedSnapshots.get(i);
                if (snap.isMacroDump() != entity.isMacroDump()) continue;
                if (snap.getDumpIndex() != entity.getDumpIndex()) continue;

                long diff = Math.abs(snap.getTimestamp() - entity.getTimestamp());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestIdx = i;
                }
            }

            if (bestIdx == -1) {
                for (int i = 0; i < capturedSnapshots.size(); i++) {
                    if (used[i]) continue;
                    SessionSnapshot snap = capturedSnapshots.get(i);
                    long diff = Math.abs(snap.getTimestamp() - entity.getTimestamp());
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestIdx = i;
                    }
                }
            }

            if (bestIdx != -1 && bestDiff <= 5000L) {
                used[bestIdx] = true;
                float snapFm = capturedSnapshots.get(bestIdx).getFmHz();
                result.add(snapFm > 0f ? snapFm : defaultFm);
            } else {
                result.add(defaultFm);
            }
        }
        return result;
    }

    public static void exportDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        Runnable exportRunnable = () -> {
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
        };

        if (backupFile.exists()) {
            new AlertDialog.Builder(context)
                .setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"))
                .setMessage("Файл бэкапа уже существует. Перезаписать ранее созданную версию?")
                .setPositiveButton("Да", (d, w) -> exportRunnable.run())
                .setNegativeButton("Отмена", null)
                .show();
        } else {
            exportRunnable.run();
        }
    }

    public static void importDatabaseBackup(Context context, File backupFile, ExecutorService dbExecutor, ExportCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"))
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

                        File dbFile = context.getDatabasePath("synergy_database");
                        try (FileChannel src = new FileInputStream(extractedDb).getChannel();
                             FileChannel dst = new FileOutputStream(dbFile).getChannel()) {
                            dst.transferFrom(src, 0, src.size());
                        }

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

    @SuppressWarnings("ResultOfMethodCallIgnored")
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
}
