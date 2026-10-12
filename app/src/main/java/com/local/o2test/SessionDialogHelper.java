package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.SessionEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class SessionDialogHelper {

    public static void openSessionsDialog(Context context, ExecutorService dbExecutor, long activeProfileId, String activeProfileName, boolean isAuthorized) {
        if (!isAuthorized || activeProfileId == -1L) {
            Toast.makeText(context, "Сначала войдите в профиль!", Toast.LENGTH_SHORT).show();
            return;
        }

        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<SessionEntity> sessions = db.sessionDao().getSessionsByProfileSync(activeProfileId);

                ((android.app.Activity) context).runOnUiThread(() -> {
                    if (sessions == null || sessions.isEmpty()) {
                        new AlertDialog.Builder(context)
                                .setTitle("Сохраненные сессии")
                                .setMessage("У пользователя " + activeProfileName + " пока нет сохраненных сессий.")
                                .setPositiveButton("ОК", null)
                                .show();
                    } else {
                        String[] sessionItems = new String[sessions.size()];
                        for (int i = 0; i < sessions.size(); i++) {
                            SessionEntity s = sessions.get(i);
                            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(s.getStartTime()));
                            sessionItems[i] = "Сессия #" + s.getId() + " | " + dateStr + " | Режим: " + s.getWorkoutMode() + " | SpO2: " + s.getAvgSpo2() + "%";
                        }

                        new AlertDialog.Builder(context)
                                .setTitle("Сессии: " + activeProfileName)
                                .setItems(sessionItems, (dialog, which) -> {
                                    SessionEntity selected = sessions.get(which);
                                    Toast.makeText(context, "Выбрана сессия #" + selected.getId(), Toast.LENGTH_LONG).show();
                                })
                                .setPositiveButton("Закрыть", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, "Ошибка загрузки сессий: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}
