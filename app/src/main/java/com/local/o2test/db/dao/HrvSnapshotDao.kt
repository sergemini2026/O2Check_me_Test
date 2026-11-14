package com.local.o2test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.o2test.db.entity.HrvSnapshotEntity

@Dao
interface HrvSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(snapshots: List<HrvSnapshotEntity>)

    @Query("SELECT * FROM hrv_snapshots ORDER BY id ASC")
    fun getAllSnapshots(): List<HrvSnapshotEntity>

    /**
     * Выборка полного массива микро-срезов конкретного макро-дампа по его dumpIndex
     */
    @Query("SELECT * FROM hrv_snapshots WHERE dumpIndex = :dumpIndex ORDER BY id ASC")
    fun getSnapshotsForMacroDump(dumpIndex: Int): List<HrvSnapshotEntity>

    /**
     * Выборка замыкающих срезов (вех с флагом isMacroDump = true) для отслеживания фаз и триггеров
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isMacroDump = 1 ORDER BY id ASC")
    fun getMacroDumps(): List<HrvSnapshotEntity>

    /**
     * Выборка данных по конкретной фазе сессии (false — Тренировка, true — Тест PSTI)
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isPstiPhase = :isPsti ORDER BY id ASC")
    fun getSnapshotsByPhase(isPsti: Boolean): List<HrvSnapshotEntity>

    /**
     * Упорядоченная выборка всех снимков сессии по сквозным индексам дампов и фазам (для отчета)
     */
    @Query("SELECT * FROM hrv_snapshots ORDER BY dumpIndex ASC, isPstiPhase ASC, blockNumber ASC, timestamp ASC")
    fun getOrderedSnapshotsForReport(): List<HrvSnapshotEntity>

    /**
     * Выборка данных тренировочной фазы, отсортированная по дампам и времени
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isPstiPhase = 0 ORDER BY dumpIndex ASC, timestamp ASC")
    fun getTrainingSnapshots(): List<HrvSnapshotEntity>

    /**
     * Выборка данных фазы теста PSTI, отсортированная по дампам и времени
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isPstiPhase = 1 ORDER BY dumpIndex ASC, timestamp ASC")
    fun getTestSnapshots(): List<HrvSnapshotEntity>

    /**
     * Выборка дампов-границ (isMacroDump = 1), отсортированная строго по dumpIndex
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isMacroDump = 1 ORDER BY dumpIndex ASC")
    fun getMacroDumpBoundaries(): List<HrvSnapshotEntity>

    @Query("DELETE FROM hrv_snapshots")
    fun deleteAll()
}
