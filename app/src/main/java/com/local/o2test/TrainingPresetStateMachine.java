package com.local.o2test;

import android.util.Log;

import java.util.Locale;

public class TrainingPresetStateMachine {
    private static final boolean DEBUG = true;
    private static final String TAG = "TrainingPresetStateMachine";

    private final int trainingDumpsCount;
    private int globalDumpIndex = 0;
    private int currentStepInCycle = 0; 

    /**
     * Контекст текущего кванта данных, выдаваемый автоматом состояний.
     */
    public static class QuantumContext {
        private final int dumpIndex;
        private final int blockNumber;
        private final boolean isPstiPhase;

        public QuantumContext(int dumpIndex, int blockNumber, boolean isPstiPhase) {
            this.dumpIndex = dumpIndex;
            this.blockNumber = blockNumber;
            this.isPstiPhase = isPstiPhase;
        }

        public int getDumpIndex() {
            return dumpIndex;
        }

        public int getBlockNumber() {
            return blockNumber;
        }

        public boolean isPstiPhase() {
            return isPstiPhase;
        }
    }

    public TrainingPresetStateMachine(int trainingDumpsCount) {
        this.trainingDumpsCount = Math.max(1, trainingDumpsCount);
        if (DEBUG) {
            Log.d(TAG, "[STATE_MACHINE] Инициализация автомата состояний. Тренировочных дампов в цикле: " + this.trainingDumpsCount);
        }
    }

    public TrainingPresetStateMachine(String presetName) {
        int count = 3; // Дефолт для 3-1
        if (presetName != null) {
            if (presetName.contains("3")) count = 3;
            else if (presetName.contains("2")) count = 2;
            else if (presetName.contains("1")) count = 1;
        }
        this.trainingDumpsCount = count;
        if (DEBUG) {
            Log.d(TAG, "[STATE_MACHINE] Инициализация автомата по пресету: " + presetName + " (дампов в цикле: " + count + ")");
        }
    }

    /**
     * Получение контекста для следующего кванта и сдвиг состояния автомата.
     * Потокобезопасный метод (synchronized) для исключения race conditions в сервисе.
     */
    public synchronized QuantumContext advanceAndGetNext() {
        // Если текущий шаг равен числу тренировочных дампов, значит это фаза PSTI
        boolean isPsti = (currentStepInCycle == trainingDumpsCount);
        int blockNumber = (currentStepInCycle % trainingDumpsCount) + 1;

        QuantumContext context = new QuantumContext(globalDumpIndex, blockNumber, isPsti);

        if (DEBUG) {
            Log.d(TAG, String.format(Locale.US, "[STATE_MACHINE] Выдан контекст кванта -> dumpIndex: %d | блок: %d | PSTI фаза: %b (шаг цикла: %d)",
                    globalDumpIndex, blockNumber, isPsti, currentStepInCycle));
        }

        // Инкремент для следующего вызова
        globalDumpIndex++;
        currentStepInCycle++;
        if (currentStepInCycle > trainingDumpsCount) {
            currentStepInCycle = 0; // Сброс цикла после завершения фазы PSTI
        }

        return context;
    }

    /**
     * Сброс автомата состояний при перезапуске или остановке сессии.
     */
    public synchronized void reset() {
        globalDumpIndex = 0;
        currentStepInCycle = 0;
        if (DEBUG) {
            Log.d(TAG, "[STATE_MACHINE] Сброс состояния автомата в исходную точку.");
        }
    }

    public synchronized int getGlobalDumpIndex() {
        return globalDumpIndex;
    }
}
