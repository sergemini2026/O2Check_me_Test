package com.local.o2test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.o2test.db.entity.RawMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<RawMetricsEntity>)

    @Query("SELECT * FROM raw_metrics WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMetricsForSession(sessionId: Long): Flow<List<RawMetricsEntity>>

    @Query("DELETE FROM raw_metrics WHERE sessionId = :sessionId")
    suspend fun deleteMetricsForSession(sessionId: Long)
}
