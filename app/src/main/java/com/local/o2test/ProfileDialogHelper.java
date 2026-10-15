package com.local.o2test;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.local.o2test.db.AppDatabase;
import com.local.o2test.db.entity.ProfileEntity;
import com.local.o2test.db.entity.SessionEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                        showCreateProfileDialog(context, db, dbExecutor, callback, null);
                    } else {
                        showProfilesListDialog(context, db, dbExecutor, profiles, callback);
                    }
                });
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, "Ошибка БД: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private static void showProfilesListDialog(Context context, AppDatabase db, ExecutorService dbExecutor, List<ProfileEntity> profiles, ProfileCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(context);
        title.setText("Выбор профиля пользователя");
        title.setTextSize(18);
        title.setPadding(10, 10, 10, 15);
        title.setTextColor(0xFFFFFFFF);
        rootLayout.addView(title);

        ListView listView = new ListView(context);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        rootLayout.addView(listView);

        // Панель кнопок внизу: Создать, Редактировать, Удалить, Закрыть
        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);
        buttonLayout.setPadding(0, 15, 0, 0);

        Button btnCreate = new Button(context);
        btnCreate.setText("+ НОВЫЙ");
        btnCreate.setTextColor(0xFF10B981);
        btnCreate.setBackgroundColor(Color.TRANSPARENT);

        Button btnEdit = new Button(context);
        btnEdit.setText("РЕДАКТИРОВАТЬ");
        btnEdit.setTextColor(0xFF38BDF8);
        btnEdit.setBackgroundColor(Color.TRANSPARENT); // Исправлена опечатка

        Button btnDelete = new Button(context);
        btnDelete.setText("УДАЛИТЬ");
        btnDelete.setTextColor(0xFFFF5252);
        btnDelete.setBackgroundColor(Color.TRANSPARENT);

        Button btnClose = new Button(context);
        btnClose.setText("ЗАКРЫТЬ");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setBackgroundColor(Color.TRANSPARENT);

        buttonLayout.addView(btnCreate);
        buttonLayout.addView(btnEdit);
        buttonLayout.addView(btnDelete);
        buttonLayout.addView(btnClose);
        rootLayout.addView(buttonLayout);

        builder.setView(rootLayout);
        AlertDialog dialog = builder.create();

        Set<Integer> selectedPositions = new HashSet<>();

        List<String> profileNames = new ArrayList<>();
        for (ProfileEntity p : profiles) {
            profileNames.add(String.format("ID: %d | %s | Возраст: %d | Вес: %.1fкг",
                    p.getId(), p.getName(), p.getAge(), p.getWeightKg()));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, profileNames) {
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

        // Долгое нажатие для выделения профиля
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
            adapter.notifyDataSetChanged();
            return true;
        });

        // Обычное нажатие — авторизация/выбор профиля
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ProfileEntity selected = profiles.get(position);
            dialog.dismiss();
            callback.onProfileAuthorized(selected.getId(), selected.getName());
            Toast.makeText(context, "Активный профиль: " + selected.getName(), Toast.LENGTH_SHORT).show();
        });

        // Кнопка создания нового профиля
        btnCreate.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateProfileDialog(context, db, dbExecutor, callback, null);
        });

        // Кнопка редактирования выбранного профиля (доступна, если выбран ровно 1 профиль)
        btnEdit.setOnClickListener(v -> {
            if (selectedPositions.size() != 1) {
                Toast.makeText(context, "Выберите ровно один профиль для редактирования", Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = selectedPositions.iterator().next();
            ProfileEntity profileToEdit = profiles.get(pos);
            dialog.dismiss();
            showCreateProfileDialog(context, db, dbExecutor, callback, profileToEdit);
        });

        // Кнопка удаления выбранных профилей со всеми связанными сессиями и PDF
        btnDelete.setOnClickListener(v -> {
            if (selectedPositions.isEmpty()) {
                Toast.makeText(context, "Сначала выберите профиль(и) долгим нажатием", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(context)
                    .setTitle("Удаление профилей")
                    .setMessage("Удалить выбранные профили (" + selectedPositions.size() + "), а также все их сессии и PDF-отчеты?")
                    .setPositiveButton("Удалить", (d, w) -> {
                        dbExecutor.execute(() -> {
                            List<ProfileEntity> toDelete = new ArrayList<>();
                            for (int pos : selectedPositions) {
                                if (pos >= 0 && pos < profiles.size()) {
                                    toDelete.add(profiles.get(pos));
                                }
                            }

                            for (ProfileEntity profile : toDelete) {
                                // 1. Получаем все сессии профиля для удаления связанных PDF
                                List<SessionEntity> sessions = db.sessionDao().getSessionsByProfileSync(profile.getId());
                                for (SessionEntity session : sessions) {
                                    if (!TextUtils.isEmpty(session.getPdfPath())) {
                                        deleteFileWithMediaStore(context, new File(session.getPdfPath()));
                                    }
                                }
                                // 2. Удаляем сам профиль (сессии удалятся каскадно через БД ForeignKey)
                                db.profileDao().deleteProfileSync(profile.getId());
                            }

                            List<ProfileEntity> updatedProfiles = db.profileDao().getAllProfilesSync();
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                dialog.dismiss();
                                Toast.makeText(context, "Профили и связанные данные успешно удалены", Toast.LENGTH_SHORT).show();
                                if (!updatedProfiles.isEmpty()) {
                                    showProfilesListDialog(context, db, dbExecutor, updatedProfiles, callback);
                                } else {
                                    showCreateProfileDialog(context, db, dbExecutor, callback, null);
                                }
                            });
                        });
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.75);
            dialog.getWindow().setLayout(width, height);
        }
    }

    private static void showCreateProfileDialog(Context context, AppDatabase db, ExecutorService dbExecutor, ProfileCallback callback, ProfileEntity editingProfile) {
        boolean isEditMode = (editingProfile != null);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getCenteredTitle(isEditMode ? "Редактирование профиля" : "Создание профиля пользователя"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText inputName = new EditText(context);
        inputName.setHint("Имя / Логин");
        if (isEditMode) inputName.setText(editingProfile.getName());
        layout.addView(inputName);

        final EditText inputAge = new EditText(context);
        inputAge.setHint("Возраст (лет)");
        inputAge.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (isEditMode) inputAge.setText(String.valueOf(editingProfile.getAge()));
        layout.addView(inputAge);

        final EditText inputHeight = new EditText(context);
        inputHeight.setHint("Рост (см)");
        inputHeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (isEditMode) inputHeight.setText(String.valueOf(editingProfile.getHeightCm()));
        layout.addView(inputHeight);

        final EditText inputWeight = new EditText(context);
        inputWeight.setHint("Вес (кг)");
        inputWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (isEditMode) inputWeight.setText(String.valueOf(editingProfile.getWeightKg()));
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
        if (isEditMode && "Женский".equals(editingProfile.getGender())) {
            spinnerGender.setSelection(1);
        } else {
            spinnerGender.setSelection(0);
        }

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
        inputSysBp.setText(isEditMode ? String.valueOf(editingProfile.getBaselineSystolicBp()) : "120");
        layout.addView(inputSysBp);

        final EditText inputDiaBp = new EditText(context);
        inputDiaBp.setHint("Диастолическое АД (мм рт. ст.)");
        inputDiaBp.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputDiaBp.setText(isEditMode ? String.valueOf(editingProfile.getBaselineDiastolicBp()) : "80");
        layout.addView(inputDiaBp);

        // Поле выбора фитнес уровня
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
        
        if (isEditMode && editingProfile.getFitnessLevel() != null) {
            for (int i = 0; i < fitnessLevels.length; i++) {
                if (fitnessLevels[i].equals(editingProfile.getFitnessLevel())) {
                    spinnerFitness.setSelection(i);
                    break;
                }
            }
        } else {
            spinnerFitness.setSelection(1);
        }

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
        builder.setPositiveButton(isEditMode ? "Сохранить" : "Создать", (dialog, which) -> {
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
            long profileId = isEditMode ? editingProfile.getId() : 0;
            long createdAt = isEditMode ? editingProfile.getCreatedAt() : System.currentTimeMillis();

            dbExecutor.execute(() -> {
                try {
                    ProfileEntity profile = new ProfileEntity(profileId, name, age, weight, height, sysBp, diaBp, fitness, gender, createdAt);
                    long id = db.profileDao().insertProfileSync(profile);
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        callback.onProfileAuthorized(id, name);
                        Toast.makeText(context, isEditMode ? "Профиль успешно обновлен!" : "Профиль успешно создан и активирован!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    ((android.app.Activity) context).runOnUiThread(() -> 
                        Toast.makeText(context, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> openProfilesDialog(context, dbExecutor, callback));
        builder.show();
    }

    private static boolean deleteFileWithMediaStore(Context context, File file) {
        if (file == null) return true;
        try {
            if (file.exists() && file.delete()) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            String fileName = file.getName();
            ContentResolver resolver = context.getContentResolver();
            android.net.Uri collection = MediaStore.Files.getContentUri("external");
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{fileName};
            resolver.delete(collection, selection, selectionArgs);
        } catch (Exception ignored) {}

        return !file.exists();
    }

    private static int parseOrDefault(String val, int def) {
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }

    private static float parseFloatOrDefault(String val, float def) {
        try { return Float.parseFloat(val); } catch (Exception e) { return def; }
    }
}
