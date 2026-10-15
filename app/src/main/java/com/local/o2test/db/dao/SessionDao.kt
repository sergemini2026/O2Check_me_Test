package com.local.o2test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.o2test.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionSync(session: SessionEntity): Long

    @Query("UPDATE sessions SET pdfPath = :path WHERE id = :sessionId")
    fun updatePdfPathSync(sessionId: Long, path: String)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getSessionsByProfile(profileId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getSessionsByProfileSync(profileId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    fun deleteSessionSync(sessionId: Long)
}
