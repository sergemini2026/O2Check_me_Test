package com.local.o2test;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvExporter {

    public interface ExportCallback {
        void onSuccess(String filePath, String fileName);
        void onError(String errorMessage);
    }

    public static void saveSessionToCsv(Context context, List<DataPoint> sessionData, ExportCallback callback) {
        if (sessionData == null || sessionData.isEmpty()) {
            callback.onError("Ошибка: Нет данных для сохранения.");
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "O2_Session_" + timeStamp + ".csv";

        File docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docDir != null && !docDir.exists()) {
            docDir.mkdirs();
        }
        File file = new File(docDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Timestamp,Elapsed_Sec,SpO2,HR,PI\n");
            for (DataPoint dp : sessionData) {
                writer.append(String.format(Locale.US, "%s,%d,%d,%d,%.2f\n",
        dp.timestamp, dp.elapsedSec, dp.spo2, dp.hr, dp.pi));

            }
            callback.onSuccess(file.getAbsolutePath(), fileName);
        } catch (IOException e) {
            callback.onError("Ошибка сохранения CSV: " + e.getMessage());
        }
    }
}
