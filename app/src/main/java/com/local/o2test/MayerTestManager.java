package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

public class MayerTestManager {

    private final Context context;
    private final UiBuilder.TestButtonContainer btnTest;
    private final MayerTestListener listener;

    private boolean isAvailable = false;
    private boolean isTestModeActive = false;

    // Параметры для автоматического сканирования частоты Майера в тестах
    private final Handler testHandler = new Handler(Looper.getMainLooper());
    private float currentTestPeriodSec = 9.0f; // Начальный период теста (сек)
    private static final float MIN_TEST_PERIOD_SEC = 6.0f;
    private static final float MAX_TEST_PERIOD_SEC = 14.0f;
    private static final float TEST_STEP_SEC = 0.2f;

    private Runnable testStepRunnable;

    public interface MayerTestListener {
        void onTestModeChanged(boolean isActive);
        void onTestPeriodChanged(float periodSec); // Новый колбэк для визуализации на пейсере
        void onLog(String message);
    }

    public MayerTestManager(Context context, UiBuilder.TestButtonContainer btnTest, MayerTestListener listener) {
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
     * Обработка нажатия на кнопку «Тест» с запуском шагов изменения периода
     */
    public void handleTestButtonClick() {
        if (!isAvailable || isTestModeActive) return;

        isTestModeActive = true;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
            btnTest.setEnabled(false);
            btnTest.setAlpha(1.0f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования активирован: автоподстройка фазы отключена, запущен поиск частоты.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(true);
        }

        showTestAlert();
        startTestPeriodScanning();
    }

    /**
     * Циклическое изменение тестового периода для визуальной и логической индикации
     */
    private void startTestPeriodScanning() {
        currentTestPeriodSec = 9.0f; // Стартуем с 9 сек
        testStepRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTestModeActive) return;

                currentTestPeriodSec += TEST_STEP_SEC;
                if (currentTestPeriodSec > MAX_TEST_PERIOD_SEC) {
                    currentTestPeriodSec = MIN_TEST_PERIOD_SEC; // Цикл сканирования
                }

                if (listener != null) {
                    listener.onTestPeriodChanged(currentTestPeriodSec);
                    listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Проверка периода: %.1f сек (FM: %.2f Hz)", 
                            currentTestPeriodSec, 1.0f / currentTestPeriodSec));
                }

                // Шаг сканирования каждые 5 секунд (или по логике дампа)
                testHandler.postDelayed(this, 5000);
            }
        };
        // Первый шаг через 5 секунд после старта теста
        testHandler.postDelayed(testStepRunnable, 5000);
    }

    /**
     * Сброс режима и возврат кнопки в штатный вид
     */
    public void resetTestMode() {
        if (!isTestModeActive && !isAvailable) {
            // Безопасный сброс если задачи висели
        }
        
        isTestModeActive = false;
        testHandler.removeCallbacksAndMessages(null);

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
