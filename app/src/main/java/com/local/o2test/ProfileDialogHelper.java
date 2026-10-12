package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.ProfileEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ProfileDialogHelper {

    public interface ProfileCallback {
        void onProfileAuthorized(long profileId, String profileName);
        void onLog(String message);
    }

    public static void openProfilesDialog(Context context, ExecutorService dbExecutor, ProfileCallback callback) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<ProfileEntity> profiles = db.profileDao().getAllProfilesSync();

                ((android.app.Activity) context).runOnUiThread(() -> {
                    if (profiles == null || profiles.isEmpty()) {
                        showCreateAdminProfileDialog(context, db, dbExecutor, callback);
                    } else {
                        showLoginOrProfilesListDialog(context, db, dbExecutor, profiles, callback);
                    }
                });
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, "Ошибка БД: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private static void showCreateAdminProfileDialog(Context context, AppDatabase db, ExecutorService dbExecutor, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Создание профиля администратора");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputLogin = new EditText(context);
        inputLogin.setHint("Логин администратора");
        layout.addView(inputLogin);

        final EditText inputPass = new EditText(context);
        inputPass.setHint("Пароль (ровно 4 символа)");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        final EditText inputConfirm = new EditText(context);
        inputConfirm.setHint("Подтвердите пароль (4 символа)");
        inputConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputConfirm);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String login = inputLogin.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();
            String confirm = inputConfirm.getText().toString().trim();

            if (login.isEmpty() || pass.length() != 4 || !pass.equals(confirm)) {
                Toast.makeText(context, "Ошибка! Пароль должен содержать ровно 4 символа и совпадать.", Toast.LENGTH_LONG).show();
                return;
            }

            dbExecutor.execute(() -> {
                try {
                    ProfileEntity newProfile = new ProfileEntity(0, login, 30, 70.0f, 175.0f, 120, 80, System.currentTimeMillis());
                    // ИСПОЛЬЗУЕМ СИНХРОННЫЙ МЕТОД insertProfileSync
                    long id = db.profileDao().insertProfileSync(newProfile);
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        callback.onProfileAuthorized(id, login);
                        Toast.makeText(context, "Администратор успешно создан и авторизован!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception ex) {
                    ((android.app.Activity) context).runOnUiThread(() -> 
                        Toast.makeText(context, "Ошибка сохранения: " + ex.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });
        builder.setNegativeButton("Отмена", null);
        builder.setCancelable(false);
        builder.show();
    }

    private static void showLoginOrProfilesListDialog(Context context, AppDatabase db, ExecutorService dbExecutor, List<ProfileEntity> profiles, ProfileCallback callback) {
        List<String> profileNames = new ArrayList<>();
        for (ProfileEntity p : profiles) {
            profileNames.add(p.getName() != null ? p.getName() : ("Профиль #" + p.getId()));
        }
        profileNames.add("+ Создать новый профиль");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Доступ к профилям пользователей");
        builder.setItems(profileNames.toArray(new String[0]), (dialog, which) -> {
            if (which == profileNames.size() - 1) {
                showCreateNewProfileDialog(context, db, dbExecutor, callback);
            } else {
                showPasswordAuthDialog(context, profiles.get(which), callback);
            }
        });
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private static void showPasswordAuthDialog(Context context, ProfileEntity profile, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Вход в профиль: " + profile.getName());

        final EditText inputPass = new EditText(context);
        inputPass.setHint("Введите 4-значный пароль");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        layout.addView(inputPass);
        builder.setView(layout);

        builder.setPositiveButton("Войти", (dialog, which) -> {
            String pass = inputPass.getText().toString().trim();
            if (pass.length() == 4) {
                callback.onProfileAuthorized(profile.getId(), profile.getName());
                Toast.makeText(context, "Добро пожаловать, " + profile.getName() + "!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Пароль должен состоять из 4 символов!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private static void showCreateNewProfileDialog(Context context, AppDatabase db, ExecutorService dbExecutor, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Создание нового профиля");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputLogin = new EditText(context);
        inputLogin.setHint("Имя / Логин пользователя");
        layout.addView(inputLogin);

        final EditText inputPass = new EditText(context);
        inputPass.setHint("Пароль (4 символа)");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String login = inputLogin.getText().toString().trim();
            String pass = inputPass.getText().toString().trim();

            if (login.isEmpty() || pass.length() != 4) {
                Toast.makeText(context, "Укажите имя и ровно 4 символа пароля!", Toast.LENGTH_SHORT).show();
                return;
            }

            dbExecutor.execute(() -> {
                try {
                    ProfileEntity newProfile = new ProfileEntity(0, login, 30, 70.0f, 175.0f, 120, 80, System.currentTimeMillis());
                    // ИСПОЛЬЗУЕМ СИНХРОННЫЙ МЕТОД insertProfileSync
                    long id = db.profileDao().insertProfileSync(newProfile);
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        callback.onProfileAuthorized(id, login);
                        Toast.makeText(context, "Профиль создан и активирован!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    ((android.app.Activity) context).runOnUiThread(() -> 
                        Toast.makeText(context, "Ошибка создания: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
}
