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

    public static void saveSessionToCsv(Context context, List<DataPoint> sessionData, String customDirPath, String suffix, ExportCallback callback) {
        if (sessionData == null) {
            sessionData = new java.util.ArrayList<>();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileSuffix = (suffix != null) ? suffix : "";
        String fileName = "O2_Session_" + timeStamp + fileSuffix + ".csv";

        File docDir;
        if (customDirPath != null && !customDirPath.isEmpty()) {
            docDir = new File(customDirPath);
        } else {
            docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        }

        if (docDir != null && !docDir.exists()) {
            docDir.mkdirs();
        }
        File file = new File(docDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Timestamp,Elapsed_Sec,SpO2,HR,PI,RR_ms\n");
            
            for (DataPoint dp : sessionData) {
                writer.append(String.format(Locale.US, "%s,%d,%d,%d,%.2f,%d\n",
                        dp.timestamp, dp.elapsedSec, dp.spo2, dp.hr, dp.pi, dp.rrMs));
            }
            callback.onSuccess(file.getAbsolutePath(), fileName);
        } catch (IOException e) {
            callback.onError("Ошибка сохранения CSV: " + e.getMessage());
        }
    }
}
