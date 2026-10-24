package com.local.o2test;

import java.util.ArrayList;
import java.util.List;

public class BreatheProgressManager {
    private final BreatheAnimationView pacerView;
    private final List<BreatheAnimationView.PhaseSegment> persistentPhaseStack = new ArrayList<>();
    
    private static final int TRAINING_PHASE_CYCLES = 90; // 90 циклов тренировки
    private static final int POSTI_WINDOW_CYCLES = 30;   // 30 циклов теста PSTI

    private int totalCyclesInCurrentPhase = 0;
    private boolean isCurrentPhaseWhite = false; // false = оранжевый, true = белый
    private float lastClockPhase = -1f;
    private boolean isActive = false;

    public BreatheProgressManager(BreatheAnimationView pacerView) {
        this.pacerView = pacerView;
        reset();
    }

    /**
     * Сброс состояния прогресс-бара в дефолт (полный оранжевый круг)
     */
    public void reset() {
        isActive = false;
        totalCyclesInCurrentPhase = 0;
        isCurrentPhaseWhite = false;
        lastClockPhase = -1f;
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    /**
     * Старт сеанса мониторинга
     */
    public void start() {
        isActive = true;
        totalCyclesInCurrentPhase = 0;
        isCurrentPhaseWhite = false;
        lastClockPhase = -1f;
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
    }

    /**
     * Отображение прогресса калибровки (убывание оранжевой дуги от 100% до 0%)
     */
    public void setCalibrationProgress(float percent) {
        if (pacerView != null) {
            List<BreatheAnimationView.PhaseSegment> calibStack = new ArrayList<>();
            calibStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f - (percent / 100.0f)));
            pacerView.setPhaseStack(calibStack);
        }
    }

    /**
     * Обработка такта (тика) MasterClock для обновления стека o-w-o-w
     */
    public void onClockTick(float phase) {
        if (!isActive || pacerView == null) return;

        pacerView.updatePhaseExternal(phase);

        if (lastClockPhase > 0.8f && phase < 0.2f) {
            totalCyclesInCurrentPhase++;
        }
        lastClockPhase = phase;

        int targetPhaseCycles = isCurrentPhaseWhite ? POSTI_WINDOW_CYCLES : TRAINING_PHASE_CYCLES;

        if (totalCyclesInCurrentPhase >= targetPhaseCycles) {
            // Переключение фазы (o -> w или w -> o) с сохранением стека подложек
            totalCyclesInCurrentPhase = 0;
            isCurrentPhaseWhite = !isCurrentPhaseWhite;

            persistentPhaseStack.clear();
            persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(isCurrentPhaseWhite, 1.0f));
        }

        float phaseProgress = (totalCyclesInCurrentPhase + phase) / (float) targetPhaseCycles;
        float currentSegmentProgress = 1.0f - Math.min(1.0f, phaseProgress);

        synchronized (persistentPhaseStack) {
            if (!persistentPhaseStack.isEmpty()) {
                persistentPhaseStack.get(persistentPhaseStack.size() - 1).progress = currentSegmentProgress;
            }
        }

        pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
    }

    /**
     * Запуск стека для фазы замера PSTI (белая активная линия поверх оранжевой подложки)
     */
    public void startPostiStack(float pstiProgress) {
        if (pacerView != null) {
            List<BreatheAnimationView.PhaseSegment> stack = new ArrayList<>();
            stack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f));
            stack.add(new BreatheAnimationView.PhaseSegment(true, pstiProgress));
            pacerView.setPhaseStack(stack);
        }
    }

    /**
     * Завершение фазы PSTI и возобновление тренировочного цикла
     */
    public void finishPostiStack() {
        totalCyclesInCurrentPhase = 0;
        isCurrentPhaseWhite = false;
        persistentPhaseStack.clear();
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(true, 1.0f));  // белая подложка
        persistentPhaseStack.add(new BreatheAnimationView.PhaseSegment(false, 1.0f)); // новая оранжевая фаза
        if (pacerView != null) {
            pacerView.setPhaseStack(new ArrayList<>(persistentPhaseStack));
        }
        lastClockPhase = -1f;
    }
}
