package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
        void onLog(String message);
    }

    private static SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }

    public static void openProfilesDialog(Context context, ExecutorService dbExecutor, ProfileCallback callback) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<ProfileEntity> profiles = db.profileDao().getAllProfilesSync();

                ((android.app.Activity) context).runOnUiThread(() -> {
                    if (profiles == null || profiles.isEmpty()) {
                        showCreateProfileDialog(context, db, dbExecutor, callback);
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

    private static void showLoginOrProfilesListDialog(Context context, AppDatabase db, ExecutorService dbExecutor, List<ProfileEntity> profiles, ProfileCallback callback) {
        List<String> profileNames = new ArrayList<>();
        for (ProfileEntity p : profiles) {
            profileNames.add(p.getName() != null ? p.getName() : ("Профиль #" + p.getId()));
        }
        profileNames.add("+ Создать новый профиль");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Выбор профиля пользователя"));
        builder.setItems(profileNames.toArray(new String[0]), (dialog, which) -> {
            if (which == profileNames.size() - 1) {
                showCreateProfileDialog(context, db, dbExecutor, callback);
            } else {
                ProfileEntity selected = profiles.get(which);
                callback.onProfileAuthorized(selected.getId(), selected.getName());
                Toast.makeText(context, "Активный профиль: " + selected.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private static void showCreateProfileDialog(Context context, AppDatabase db, ExecutorService dbExecutor, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle("Создание профиля пользователя"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputName = new EditText(context);
        inputName.setHint("Имя / Логин");
        layout.addView(inputName);

        final EditText inputAge = new EditText(context);
        inputAge.setHint("Возраст (лет)");
        inputAge.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputAge);

        final EditText inputHeight = new EditText(context);
        inputHeight.setHint("Рост (см)");
        inputHeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputHeight);

        final EditText inputWeight = new EditText(context);
        inputWeight.setHint("Вес (кг)");
        inputWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputWeight);

        // Поле выбора пола
        LinearLayout genderLayout = new LinearLayout(context);
        genderLayout.setOrientation(LinearLayout.HORIZONTAL);
        genderLayout.setPadding(0, 15, 0, 10);

        TextView tvGenderLabel = new TextView(context);
        tvGenderLabel.setText("Пол: ");
        tvGenderLabel.setTextSize(16);
        LinearLayout.LayoutParams genderLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        genderLabelParams.gravity = Gravity.CENTER_VERTICAL;
        tvGenderLabel.setLayoutParams(genderLabelParams);

        final Spinner spinnerGender = new Spinner(context);
        String[] genders = {"Мужской", "Женский"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);
        spinnerGender.setSelection(0); // По умолчанию "Мужской"

        LinearLayout.LayoutParams genderSpinnerParams = new LinearLayout.LayoutParams(
                0, 
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                1.0f
        );
        spinnerGender.setLayoutParams(genderSpinnerParams);

        genderLayout.addView(tvGenderLabel);
        genderLayout.addView(spinnerGender);
        layout.addView(genderLayout);

        final EditText inputSysBp = new EditText(context);
        inputSysBp.setHint("Систолическое АД (мм рт. ст.)");
        inputSysBp.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputSysBp.setText("120");
        layout.addView(inputSysBp);

        final EditText inputDiaBp = new EditText(context);
        inputDiaBp.setHint("Диастолическое АД (мм рт. ст.)");
        inputDiaBp.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputDiaBp.setText("80");
        layout.addView(inputDiaBp);

        // Строка с поясняющей меткой и Spinner для выбора уровня физической формы
        LinearLayout fitnessLayout = new LinearLayout(context);
        fitnessLayout.setOrientation(LinearLayout.HORIZONTAL);
        fitnessLayout.setPadding(0, 15, 0, 10);

        TextView tvFitnessLabel = new TextView(context);
        tvFitnessLabel.setText("Физический уровень: ");
        tvFitnessLabel.setTextSize(16);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.gravity = Gravity.CENTER_VERTICAL;
        tvFitnessLabel.setLayoutParams(labelParams);

        final Spinner spinnerFitness = new Spinner(context);
        String[] fitnessLevels = {"Низкий", "Средний", "Высокий", "Максимальный"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, fitnessLevels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFitness.setAdapter(spinnerAdapter);
        spinnerFitness.setSelection(1); // По умолчанию "Средний"

        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                0, 
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                1.0f
        );
        spinnerFitness.setLayoutParams(spinnerParams);

        fitnessLayout.addView(tvFitnessLabel);
        fitnessLayout.addView(spinnerFitness);
        layout.addView(fitnessLayout);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(context, "Укажите имя профиля!", Toast.LENGTH_SHORT).show();
                return;
            }

            int age = parseOrDefault(inputAge.getText().toString().trim(), 30);
            float height = parseFloatOrDefault(inputHeight.getText().toString().trim(), 175.0f);
            float weight = parseFloatOrDefault(inputWeight.getText().toString().trim(), 70.0f);
            String gender = spinnerGender.getSelectedItem().toString();
            int sysBp = parseOrDefault(inputSysBp.getText().toString().trim(), 120);
            int diaBp = parseOrDefault(inputDiaBp.getText().toString().trim(), 80);
            String fitness = spinnerFitness.getSelectedItem().toString();

            dbExecutor.execute(() -> {
                try {
                    ProfileEntity newProfile = new ProfileEntity(0, name, age, weight, height, sysBp, diaBp, fitness, gender, System.currentTimeMillis());
                    long id = db.profileDao().insertProfileSync(newProfile);
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        callback.onProfileAuthorized(id, name);
                        Toast.makeText(context, "Профиль успешно создан и активирован!", Toast.LENGTH_SHORT).show();
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

    private static int parseOrDefault(String val, int def) {
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }

    private static float parseFloatOrDefault(String val, float def) {
        try { return Float.parseFloat(val); } catch (Exception e) { return def; }
    }
}
