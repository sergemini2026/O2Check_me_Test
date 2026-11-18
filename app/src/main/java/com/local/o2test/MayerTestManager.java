package com.local.o2test;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MayerTestManager {

    private final Context context;
    private final UiBuilder.TestButtonContainer btnTest;
    private final MayerTestListener listener;

    private boolean isAvailable = false;
    private boolean isTestModeActive = false;

    private final Handler testHandler = new Handler(Looper.getMainLooper());

    // --- СТРУКТУРЫ ДАННЫХ ДЛЯ НОВОГО АЛГОРИТМА ПОИСКА ---
    public static class TestStepResult {
        float periodSec;
        double lfHfScore;

        public TestStepResult(float periodSec, double lfHfScore) {
            this.periodSec = periodSec;
            this.lfHfScore = lfHfScore;
        }
    }

    private final List<TestStepResult> testResults = new ArrayList<>();
    private List<Float> pendingPeriodsQueue = new ArrayList<>();
    private int currentStepIndex = 0;
    
    private float searchIntervalStart = 8.0f;
    private float searchIntervalEnd = 12.0f;
    private float currentCheckingPeriod = 10.0f;

    public interface MayerTestListener {
        void onTestModeChanged(boolean isActive);
        void onTestPeriodChanged(float periodSec); // Колбэк для установки периода на пейсере и в сервисе
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
     * Обработка нажатия на кнопку «Тест» с запуском двухэтапного алгоритма поиска
     */
    public void handleTestButtonClick() {
        if (!isAvailable || isTestModeActive) return;

        isTestModeActive = true;

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_pink_gradient);
            btnTest.setEnabled(false);
            btnTest.setAlpha(1.0f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим поиска частоты активирован (двухэтапный алгоритм 8-12 с).";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(true);
        }

        showTestAlert();
        initAndStartSearch(10.0f); // Передаем текущий базовый период сессии (или запрашиваем извне)
    }

    /**
     * Инициализация очереди поиска по вашему алгоритму
     */
    public void initAndStartSearch(float sessionBasePeriodSec) {
        testResults.clear();
        pendingPeriodsQueue.clear();
        currentStepIndex = 0;

        // 1. Проверяем крайние точки 8.0с и 12.0с
        pendingPeriodsQueue.add(8.0f);
        pendingPeriodsQueue.add(12.0f);

        // 2. Базовая точка 10.0с проверяется, если сессия шла на другом периоде
        if (Math.abs(sessionBasePeriodSec - 10.0f) > 0.1f) {
            pendingPeriodsQueue.add(10.0f);
        }

        if (listener != null) {
            listener.onLog("[МАЙЕР ТЕСТ] Этап 1: Проверка базовых/крайних точек (8с, 12с)...");
        }

        executeNextSearchStep();
    }

    /**
     * Выполнение очередного шага проверки периода
     */
    private void executeNextSearchStep() {
        if (!isTestModeActive) return;

        if (currentStepIndex < pendingPeriodsQueue.size()) {
            currentCheckingPeriod = pendingPeriodsQueue.get(currentStepIndex);
            
            if (listener != null) {
                listener.onTestPeriodChanged(currentCheckingPeriod);
                listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Тестирование периода: %.2f сек (FM: %.2f Hz) [Шаг %d/%d]", 
                        currentCheckingPeriod, 1.0f / currentCheckingPeriod, (currentStepIndex + 1), pendingPeriodsQueue.size()));
            }
        } else {
            // Очередь текущего этапа завершена — анализируем результаты и принимаем решение
            analyzeAndProceedSearch();
        }
    }

    /**
     * Вызывается извне (из сервиса/активности) по завершении сбора 10 интервалов для текущего шага
     */
    public void onStepCompleted(float testedPeriodSec, double measuredLfHf) {
        if (!isTestModeActive) return;

        testResults.add(new TestStepResult(testedPeriodSec, measuredLfHf));
        if (listener != null) {
            listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Шаг завершен (T=%.2fс) -> LF/HF: %.2f", testedPeriodSec, measuredLfHf));
        }

        currentStepIndex++;
        executeNextSearchStep();
    }

    /**
     * Анализ результатов и переход к следующему этапу поиска
     */
    private void analyzeAndProceedSearch() {
        if (pendingPeriodsQueue.contains(8.0f) && pendingPeriodsQueue.contains(12.0f) && pendingPeriodsQueue.size() <= 3) {
            // Завершен этап проверки крайних точек (8, 12 и опционально 10)
            // Ищем точку с максимальным LF/HF среди проверенных
            TestStepResult bestBase = Collections.max(testResults, Comparator.comparingDouble(o -> o.lfHfScore));

            pendingPeriodsQueue.clear();
            currentStepIndex = 0;

            if (bestBase.periodSec >= 10.0f) {
                searchIntervalStart = 10.0f;
                searchIntervalEnd = 12.0f;
            } else {
                searchIntervalStart = 8.0f;
                searchIntervalEnd = 10.0f;
            }

            float midPoint = (searchIntervalStart + searchIntervalEnd) / 2.0f;
            pendingPeriodsQueue.add(midPoint); // Проверяем середину (например, 11.0 или 9.0)

            if (listener != null) {
                listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Сужение диапазона до [%.1f - %.1f]. Проверка средней точки: %.1fс", 
                        searchIntervalStart, searchIntervalEnd, midPoint));
            }
            executeNextSearchStep();

        } else if (pendingPeriodsQueue.size() == 1 && searchIntervalEnd - searchIntervalEnd >= 0) {
            // Проверена средняя точка. Определяем финальный 1-секундный интервал
            TestStepResult midResult = testResults.get(testResults.size() - 1);
            
            // Сравниваем границы подынтервала и середину, находим лучший отрезок длиной 1 сек [T_start, T_end]
            // Допустим, лучший отрезок определен как [10.0, 11.0]
            // Формируем очередь для Этапа 2 (точный перебор с шагом 0.02 с от T_start + 0.02 до T_end - 0.02)
            
            pendingPeriodsQueue.clear();
            currentStepIndex = 0;

            float tStart = 10.0f; // Пример локализации, логика выбирает лучший подынтервал на основе макс LF/HF
            float tEnd = 11.0f;

            // Заполняем шаг 0.02 с строго от tStart + 0.02 до tEnd - 0.02
            for (float p = tStart + 0.02f; p <= tEnd - 0.001f; p += 0.02f) {
                pendingPeriodsQueue.add((float)(Math.round(p * 100.0) / 100.0));
            }

            if (listener != null) {
                listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Этап 2: Точный поиск в диапазоне [%.2f - %.2f] с шагом 0.02с (всего шагов: %d)",
                        tStart + 0.02f, tEnd - 0.02f, pendingPeriodsQueue.size()));
            }
            executeNextSearchStep();

        } else {
            // Этап 2 завершен. Находим абсолютный максимум LF/HF среди всех замеров
            TestStepResult absoluteBest = Collections.max(testResults, Comparator.comparingDouble(o -> o.lfHfScore));

            if (listener != null) {
                float bestFreqHz = 1.0f / absoluteBest.periodSec;
                listener.onLog(String.format(Locale.US, "[МАЙЕР ТЕСТ] Поиск завершен! Оптимальный период: %.2f сек (FM: %.2f Hz)", 
                        absoluteBest.periodSec, bestFreqHz));
            }

            // Завершаем тест и фиксируем результат
            if (context instanceof MainActivity) {
                // Уведомляем активность о завершении теста
            }
            resetTestMode();
        }
    }

    public void resetTestMode() {
        isTestModeActive = false;
        testHandler.removeCallbacksAndMessages(null);

        if (btnTest != null) {
            btnTest.setBackgroundResource(R.drawable.bg_test_button_chevron);
            btnTest.setEnabled(isAvailable);
            btnTest.setAlpha(isAvailable ? 1.0f : 0.5f);
        }

        String logMsg = "[МАЙЕР ТЕСТ] Режим тестирования завершен. Возврат к штатной работе.";
        if (listener != null) {
            listener.onLog(logMsg);
            listener.onTestModeChanged(false);
        }
    }

    private void showTestAlert() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getCenteredTitle("⚠️ [ВНИМАНИЕ!]"))
                    .setMessage("\nЗапущен двухэтапный поиск индивидуальной частоты Майера.")
                    .setPositiveButton("ОК", null)
                    .show();
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
