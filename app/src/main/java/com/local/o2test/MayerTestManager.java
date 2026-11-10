package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.widget.Button;

public class MayerTestManager {

    private final Context context;
    private final Button btnTest;
    private final MayerTestListener listener;

    private boolean isAvailable = false;
    private boolean isTestModeActive = false;

    public interface MayerTestListener {
        void onTestModeChanged(boolean isActive);
        void onLog(String message);
    }

    public MayerTestManager(Context context, Button btnTest, MayerTestListener listener) {
        this.context = context;
        this.btnTest = btnTest;
        this.listener = listener;
        applyInitialState();
    }

    private void applyInitialState() {
        if (btnTest != null) {
            btnTest.setEnabled(false);
            btnTest.setAlpha(0.5f);
        }
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
        if (btnTest != null && !isTestModeActive) {
            btnTest.setEnabled(available);
            btnTest.setAlpha(available ? 1.0f : 0.5f);
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public boolean isMayerTestModeActive() {
        return isTestModeActive;
    }

    /**
     * Обработка нажатия на кнопку «Тест» с защитой от повторных срабатываний
     */
    public void handleTestButtonClick() {
        if (!isAvailable || isTestModeActive) return;

        isTestModeActive = true;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
            // Блокируем кнопку на время активного теста, чтобы исключить повторные клики
            btnTest.setEnabled(false);
            btnTest.setAlpha(1.0f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования активирован: автоподстройка фазы отключена до конца текущего дампа.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(true);
        }

        showTestAlert();
    }

    /**
     * Сброс режима и возврат кнопки в штатный вид
     */
    public void resetTestMode() {
        if (!isTestModeActive) return;

        isTestModeActive = false;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования завершен. Возврат к штатной автоподстройке фазы.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(false);
        }
    }

    private void showTestAlert() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"));
            builder.setMessage("\nРежим тестирования, автоподстройка фазы отключена до конца текущего дампа.");
            builder.setPositiveButton("ОК", null);
            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SpannableString getCenteredTitle(String title) {
        SpannableString s = new SpannableString(title);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), 0);
        return s;
    }
}
