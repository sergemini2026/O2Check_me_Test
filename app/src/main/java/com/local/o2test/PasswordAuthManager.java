package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PasswordAuthManager {

    private static final String KEY_MASTER_PASS = "master_password";

    public interface AuthCallback {
        void onPasswordAuthorized();
        void onExitRequested();
        void onLog(String message);
    }

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    /**
     * Проверяет наличие сохраненного мастер-пароля и запускает соответствующий диалог
     */
    public static void checkMasterPasswordOnStart(Context context, SharedPreferences prefs, AuthCallback callback) {
        String savedPass = prefs.getString(KEY_MASTER_PASS, null);

        if (savedPass == null) {
            showCreateMasterPasswordDialog(context, prefs, callback);
        } else {
            showEnterMasterPasswordDialog(context, savedPass, callback);
        }
    }

    /**
     * Диалог первичного создания 4-значного мастер-пароля
     */
    private static void showCreateMasterPasswordDialog(Context context, SharedPreferences prefs, AuthCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Создание мастер-пароля системы"));
        builder.setMessage("Введите 4-значный пароль администратора для защиты доступа к данным.");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputPass = new EditText(context);
        inputPass.setHint("Пароль (4 символа)");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        final EditText inputConfirm = new EditText(context);
        inputConfirm.setHint("Подтвердите пароль (4 символа)");
        inputConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputConfirm);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String pass = inputPass.getText().toString().trim();
            String confirm = inputConfirm.getText().toString().trim();

            if (pass.length() != 4 || !pass.equals(confirm)) {
                Toast.makeText(context, "Ошибка! Пароль должен содержать 4 символа и совпадать.", Toast.LENGTH_LONG).show();
                showCreateMasterPasswordDialog(context, prefs, callback);
                return;
            }

            prefs.edit().putString(KEY_MASTER_PASS, pass).apply();
            Toast.makeText(context, "Мастер-пароль успешно создан!", Toast.LENGTH_SHORT).show();
            
            if (callback != null) {
                callback.onLog("Система запущена. Выберите профиль для начала работы.");
                callback.onPasswordAuthorized();
            }
        });
        builder.setNegativeButton("Выход", (dialog, which) -> {
            if (callback != null) {
                callback.onExitRequested();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Диалог авторизации администратора при последующих запусках
     */
    private static void showEnterMasterPasswordDialog(Context context, String savedPass, AuthCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Авторизация администратора"));
        builder.setMessage("Введите 4-значный мастер-пароль для доступа к системе.");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputPass = new EditText(context);
        inputPass.setHint("4-значный пароль");
        inputPass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);
        builder.setPositiveButton("Войти", (dialog, which) -> {
            String pass = inputPass.getText().toString().trim();
            if (pass.equals(savedPass)) {
                Toast.makeText(context, "Доступ разрешен!", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onLog("Система запущена. Выберите профиль для начала работы.");
                    callback.onPasswordAuthorized();
                }
            } else {
                Toast.makeText(context, "Неверный пароль!", Toast.LENGTH_SHORT).show();
                showEnterMasterPasswordDialog(context, savedPass, callback);
            }
        });
        builder.setNegativeButton("Выход", (dialog, which) -> {
            if (callback != null) {
                callback.onExitRequested();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }
}
