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

    @Query("SELECT * FROM hrv_snapshots ORDER BY globalUniqueIndex ASC")
    fun getAllSnapshots(): List<HrvSnapshotEntity>

    /**
     * Выборка только макро-дампов (каждые 300 RR) для отчетов и графиков
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isMacroDump = 1 ORDER BY globalUniqueIndex ASC")
    fun getMacroDumps(): List<HrvSnapshotEntity>

    /**
     * Выборка данных по конкретной фазе сессии (false — Тренировка, true — Тест PSTI)
     */
    @Query("SELECT * FROM hrv_snapshots WHERE isPstiPhase = :isPsti ORDER BY globalUniqueIndex ASC")
    fun getSnapshotsByPhase(isPsti: Boolean): List<HrvSnapshotEntity>

    @Query("DELETE FROM hrv_snapshots")
    fun deleteAll()
}
