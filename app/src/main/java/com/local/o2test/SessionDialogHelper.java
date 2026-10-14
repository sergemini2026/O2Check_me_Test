package com.local.o2test;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.SessionEntity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class SessionDialogHelper {

    public static void openSessionsDialog(Context context, ExecutorService dbExecutor, long profileId, String profileName, boolean isAuthorized) {
        if (!isAuthorized || profileId == -1L) {
            Toast.makeText(context, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
            return;
        }

        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<SessionEntity> sessions = db.sessionDao().getSessionsByProfileSync(profileId);

            ((android.app.Activity) context).runOnUiThread(() -> {
                showDialog(context, profileName, sessions);
            });
        });
    }

    private static void showDialog(Context context, String profileName, List<SessionEntity> sessions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        TextView title = new TextView(context);
        title.setText("Сессии: " + profileName);
        title.setTextSize(18);
        title.setPadding(30, 30, 30, 10);
        title.setTextColor(0xFFFFFFFF);
        builder.setCustomTitle(title);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 10, 20, 10);

        if (sessions == null || sessions.isEmpty()) {
            TextView emptyText = newTextView(context, "Сохраненных сессий не найдено.");
            layout.addView(emptyText);
        } else {
            ListView listView = new ListView(context);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

            List<String> sessionStrings = new ArrayList<>();
            for (int i = 0; i < sessions.size(); i++) {
                SessionEntity s = sessions.get(i);
                int sessionNumber = sessions.size() - i;
                // Строго без "Сес", только номер и параметры
                sessionStrings.add(String.format(Locale.US, "#%d | %s | %s | SpO2: %d%%",
                        sessionNumber, sdf.format(new Date(s.getStartTime())), s.getWorkoutMode(), s.getAvgSpo2()));
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, sessionStrings) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                    TextView tv = (TextView) super.getView(position, convertView, parent);
                    tv.setTextSize(13f);
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    return tv;
                }
            };
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                SessionEntity session = sessions.get(position);
                File targetFile = null;

                if (!TextUtils.isEmpty(session.getPdfPath())) {
                    File f = new File(session.getPdfPath());
                    if (f.exists()) {
                        targetFile = f;
                    }
                }

                if (targetFile == null || !targetFile.exists()) {
                    File docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    File[] pdfs = docDir.listFiles((dir, name) -> name.endsWith(".pdf"));
                    if (pdfs != null && pdfs.length > 0) {
                        targetFile = pdfs[0];
                        for (File p : pdfs) {
                            if (p.lastModified() > targetFile.lastModified()) {
                                targetFile = p;
                            }
                        }
                    }
                }

                if (targetFile != null && targetFile.exists()) {
                    openPdfFile(context, targetFile);
                } else {
                    Toast.makeText(context, "PDF-отчет для этой сессии не найден в папке Documents", Toast.LENGTH_SHORT).show();
                }
            });

            layout.addView(listView);
        }

        builder.setView(layout);
        builder.setNegativeButton("ЗАКРЫТЬ", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Компактные пропорции: ширина 85%, высота по содержимому (WRAP_CONTENT)
        if (dialog.getWindow() != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView newTextView(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFA0A0A0);
        tv.setPadding(10, 20, 10, 20);
        return tv;
    }

    private static void openPdfFile(Context context, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Принудительный вызов системного окна выбора приложения (Chooser)
            Intent chooser = Intent.createChooser(intent, "Открыть PDF с помощью:");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка открытия PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
