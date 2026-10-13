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

    @Query("SELECT * FROM hrv_snapshots ORDER BY timestamp ASC")
    fun getAllSnapshots(): List<HrvSnapshotEntity>

    @Query("DELETE FROM hrv_snapshots")
    fun deleteAll()
}
