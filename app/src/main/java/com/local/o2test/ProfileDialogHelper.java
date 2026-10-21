package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.ProfileEntity;

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

        ListView listView = new ListView(context);
        
        ArrayAdapter<ProfileEntity> adapter = new ArrayAdapter<ProfileEntity>(context, android.R.layout.simple_list_item_1, profiles) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout itemLayout = new LinearLayout(context);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(10, 15, 10, 15);

                ProfileEntity profile = getItem(position);
                String fullName = profile.getFirstName() + (TextUtils.isEmpty(profile.getLastName()) ? "" : " " + profile.getLastName());

                TextView tvName = new TextView(context);
                tvName.setText(fullName);
                tvName.setTextSize(16f);
                tvName.setTextColor(context.getResources().getColor(android.R.color.black));
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                tvName.setLayoutParams(nameParams);
                itemLayout.addView(tvName);

                Button btnEdit = new Button(context);
                btnEdit.setText("Изменить");
                btnEdit.setTextSize(12f);
                btnEdit.setOnClickListener(v -> {
                    showEditProfileDialog(context, dbExecutor, profile, callback);
                });
                itemLayout.addView(btnEdit);

                Button btnDelete = new Button(context);
                btnDelete.setText("Удалить");
                btnDelete.setTextSize(12f);
                btnDelete.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
                btnDelete.setOnClickListener(v -> {
                    new AlertDialog.Builder(context)
                        .setTitle("Удаление профиля")
                        .setMessage("Удалить профиль " + fullName + "?")
                        .setPositiveButton("Да", (dialogD, whichD) -> {
                            dbExecutor.execute(() -> {
                                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                                db.profileDao().deleteProfileSync(profile.getId());
                                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                                mainHandler.post(() -> {
                                    if (callback != null) {
                                        callback.onProfileDeleted(profile.getId());
                                        callback.onLog("[ПРОФИЛЬ] Удален профиль: " + fullName);
                                    }
                                    Toast.makeText(context, "Профиль удален", Toast.LENGTH_SHORT).show();
                                    openProfilesDialog(context, dbExecutor, callback);
                                });
                            });
                        })
                        .setNegativeButton("Нет", null)
                        .show();
                });
                itemLayout.addView(btnDelete);

                return itemLayout;
            }
        };

        listView.setAdapter(adapter);
        
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        Button btnNewProfile = new Button(context);
        btnNewProfile.setText("+ Создать новый профиль");
        layout.addView(btnNewProfile);

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
                ProfileEntity newProfile = new ProfileEntity(0, firstName, lastName, (int) (System.currentTimeMillis() / 1000));
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

        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
            openProfilesDialog(context, dbExecutor, callback);
        });

        builder.show();
    }

    private static void showEditProfileDialog(Context context, ExecutorService dbExecutor, ProfileEntity profile, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Редактировать профиль");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText etFirstName = new EditText(context);
        etFirstName.setText(profile.getFirstName());
        layout.addView(etFirstName);

        final EditText etLastName = new EditText(context);
        etLastName.setText(profile.getLastName() != null ? profile.getLastName() : "");
        layout.addView(etLastName);

        builder.setView(layout);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();

            if (TextUtils.isEmpty(firstName)) {
                Toast.makeText(context, "Имя не может быть пустым!", Toast.LENGTH_SHORT).show();
                return;
            }

            dbExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                ProfileEntity updatedProfile = new ProfileEntity(profile.getId(), firstName, lastName, (int) (profile.getCreatedAt() / 1000));
                db.profileDao().updateProfileSync(updatedProfile);

                String fullName = firstName + (TextUtils.isEmpty(lastName) ? "" : " " + lastName);
                android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLog("[ПРОФИЛЬ] Изменен профиль: " + fullName);
                    }
                    Toast.makeText(context, "Профиль обновлен", Toast.LENGTH_SHORT).show();
                    openProfilesDialog(context, dbExecutor, callback);
                });
            });
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> {
            dialog.dismiss();
            openProfilesDialog(context, dbExecutor, callback);
        });

        builder.show();
    }
}
