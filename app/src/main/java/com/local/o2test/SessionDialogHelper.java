package com.local.o2test;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class SessionDialogHelper {

    public static void openSessionsDialog(Context context, ExecutorService dbExecutor, long profileId, String profileName, boolean isAuthorized) {
        if (!isAuthorized || profileId == -1L) {
            Toast.makeText(context, "Сначала выберите профиль!", Toast.LENGTH_SHORT).show();
            return;
        }

        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            // Получаем сессии и сортируем в хронологическом порядке (от старых к новым)
            List<SessionEntity> sessions = new ArrayList<>(db.sessionDao().getSessionsByProfileSync(profileId));
            sessions.sort((s1, s2) -> Long.compare(s1.getStartTime(), s2.getStartTime()));

            ((android.app.Activity) context).runOnUiThread(() -> {
                showDialog(context, dbExecutor, profileId, profileName, sessions);
            });
        });
    }

    private static void showDialog(Context context, ExecutorService dbExecutor, long profileId, String profileName, List<SessionEntity> sessions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(context);
        title.setText("Сессии: " + profileName);
        title.setTextSize(18);
        title.setPadding(10, 10, 10, 15);
        title.setTextColor(0xFFFFFFFF);
        rootLayout.addView(title);

        if (sessions == null || sessions.isEmpty()) {
            TextView emptyText = newTextView(context, "Сохраненных сессий не найдено.");
            rootLayout.addView(emptyText);

            builder.setView(rootLayout);
            builder.setNegativeButton("ЗАКРЫТЬ", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
            return;
        }

        ListView listView = new ListView(context);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        rootLayout.addView(listView);

        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);
        buttonLayout.setPadding(0, 15, 0, 0);

        Button btnDelete = new Button(context);
        btnDelete.setText("УДАЛИТЬ");
        btnDelete.setTextColor(0xFFFF5252);
        btnDelete.setBackgroundColor(Color.TRANSPARENT);

        Button btnClose = new Button(context);
        btnClose.setText("ЗАКРЫТЬ");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setBackgroundColor(Color.TRANSPARENT);

        buttonLayout.addView(btnDelete);
        buttonLayout.addView(btnClose);
        rootLayout.addView(buttonLayout);

        builder.setView(rootLayout);
        AlertDialog dialog = builder.create();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        Set<Integer> selectedPositions = new HashSet<>();

        // Формируем список для отображения: новые сверху (position = 0 -> самый новый), старые внизу
        List<String> sessionStrings = new ArrayList<>();
        for (int position = 0; position < sessions.size(); position++) {
            int dataIndex = sessions.size() - 1 - position;
            SessionEntity s = sessions.get(dataIndex);
            int sessionNumber = dataIndex + 1; // Самая первая созданная сессия всегда имеет номер #1
            
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

                if (selectedPositions.contains(position)) {
                    tv.setBackgroundColor(Color.parseColor("#334155"));
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                }
                return tv;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
            adapter.notifyDataSetChanged();
            return true;
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            int dataIndex = sessions.size() - 1 - position;
            SessionEntity session = sessions.get(dataIndex);
            File targetFile = null;

            if (!TextUtils.isEmpty(session.getPdfPath())) {
                File f = new File(session.getPdfPath());
                if (f.exists()) targetFile = f;
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
                Toast.makeText(context, "PDF-отчет для этой сессии не найден", Toast.LENGTH_SHORT).show();
            }
        });

        btnDelete.setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) {
                Toast.makeText(context, "Сначала выберите сессии долгим нажатием", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(context)
                    .setTitle("Удаление сессий")
                    .setMessage("Удалить выбранные сессии (" + selectedPositions.size() + ") и связанные с ними файлы (PDF и CSV)?")
                    .setPositiveButton("Удалить", (d, w) -> {
                        dbExecutor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(context);
                            List<SessionEntity> toDelete = new ArrayList<>();
                            for (int pos : selectedPositions) {
                                int dataIndex = sessions.size() - 1 - pos;
                                if (dataIndex >= 0 && dataIndex < sessions.size()) {
                                    toDelete.add(sessions.get(dataIndex));
                                }
                            }

                            for (SessionEntity session : toDelete) {
                                db.sessionDao().deleteSessionSync(session.getId());

                                if (!TextUtils.isEmpty(session.getPdfPath())) {
                                    try {
                                        File f = new File(session.getPdfPath());
                                        String fileName = f.getName();
                                        context.getContentResolver().delete(
                                            MediaStore.Files.getContentUri("external"),
                                            MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                                            new String[]{fileName}
                                        );
                                        if (f.exists()) f.delete();
                                    } catch (Exception ignored) {}
                                }

                                if (!TextUtils.isEmpty(session.getCsvPath())) {
                                    try {
                                        File f = new File(session.getCsvPath());
                                        String fileName = f.getName();
                                        context.getContentResolver().delete(
                                            MediaStore.Files.getContentUri("external"),
                                            MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                                            new String[]{fileName}
                                        );
                                        if (f.exists()) f.delete();
                                    } catch (Exception ignored) {}
                                }
                            }

                            List<SessionEntity> updatedSessions = new ArrayList<>(db.sessionDao().getSessionsByProfileSync(profileId));
                            updatedSessions.sort((s1, s2) -> Long.compare(s1.getStartTime(), s2.getStartTime()));

                            ((android.app.Activity) context).runOnUiThread(() -> {
                                dialog.dismiss();
                                Toast.makeText(context, "Сессии и файлы успешно удалены", Toast.LENGTH_SHORT).show();
                                showDialog(context, dbExecutor, profileId, profileName, updatedSessions);
                            });
                        });
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

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

            Intent chooser = Intent.createChooser(intent, "Открыть PDF с помощью:");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка открытия PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
