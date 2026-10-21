package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.ProfileEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ProfileDialogHelper {

    public interface ProfileCallback {
        void onProfileAuthorized(long profileId, String profileName);
        void onProfileDeleted(long deletedProfileId);
        void onLog(String message);
    }

    public static void openProfilesDialog(Context context, ExecutorService dbExecutor, ProfileCallback callback) {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            List<ProfileEntity> profiles = db.profileDao().getAllProfilesSync();

            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(() -> showProfilesListDialog(context, dbExecutor, profiles, callback));
        });
    }

    private static void showProfilesListDialog(Context context, ExecutorService dbExecutor, List<ProfileEntity> profiles, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Управление профилями");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        Button btnNewProfile = new Button(context);
        btnNewProfile.setText("+ Создать новый профиль");
        layout.addView(btnNewProfile);

        ListView listView = new ListView(context);
        List<String> profileNames = new ArrayList<>();
        for (ProfileEntity p : profiles) {
            String name = p.getFirstName() + (TextUtils.isEmpty(p.getLastName()) ? "" : " " + p.getLastName());
            profileNames.add(name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, profileNames);
        listView.setAdapter(adapter);
        layout.addView(listView);

        builder.setView(layout);
        builder.setNegativeButton("Закрыть", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        btnNewProfile.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateProfileDialog(context, dbExecutor, callback);
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ProfileEntity selected = profiles.get(position);
            dialog.dismiss();
            String fullName = selected.getFirstName() + (TextUtils.isEmpty(selected.getLastName()) ? "" : " " + selected.getLastName());
            if (callback != null) {
                callback.onProfileAuthorized(selected.getId(), fullName);
            }
        });

        dialog.show();
    }

    private static void showCreateProfileDialog(Context context, ExecutorService dbExecutor, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Новый профиль");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText etFirstName = new EditText(context);
        etFirstName.setHint("Имя");
        layout.addView(etFirstName);

        final EditText etLastName = new EditText(context);
        etLastName.setHint("Фамилия (необязательно)");
        layout.addView(etLastName);

        builder.setView(layout);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();

            if (TextUtils.isEmpty(firstName)) {
                Toast.makeText(context, "Имя не может быть пустым!", Toast.LENGTH_SHORT).show();
                return;
            }

            dbExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                ProfileEntity newProfile = new ProfileEntity(0, firstName, lastName, System.currentTimeMillis());
                long newId = db.profileDao().insertProfileSync(newProfile);

                String fullName = firstName + (TextUtils.isEmpty(lastName) ? "" : " " + lastName);
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLog("[ПРОФИЛЬ] Создан новый профиль: " + fullName);
                        callback.onProfileAuthorized(newId, fullName);
                    }
                    Toast.makeText(context, "Профиль создан и активирован", Toast.LENGTH_SHORT).show();
                });
            });
        });

        // Кнопка «Отмена» теперь возвращает пользователя в меню управления профилями
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
            openProfilesDialog(context, dbExecutor, callback);
        });

        builder.show();
    }
}
