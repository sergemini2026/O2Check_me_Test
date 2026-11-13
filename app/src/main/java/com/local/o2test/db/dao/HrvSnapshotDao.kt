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

    @Query("DELETE FROM hrv_snapshots")
    fun deleteAll()
}
