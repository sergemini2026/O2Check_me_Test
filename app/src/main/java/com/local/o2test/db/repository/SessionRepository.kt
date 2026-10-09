package com.local.o2test.db.repository

import com.local.o2test.db.AppDatabase
import com.local.o2test.db.entity.ProfileEntity
import com.local.o2test.db.entity.RawMetricsEntity
import com.local.o2test.db.entity.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SessionRepository(private val db: AppDatabase) {

    // --- ПРОФИЛИ ---

    suspend fun insertProfile(profile: ProfileEntity): Long = withContext(Dispatchers.IO) {
        db.profileDao().insertProfile(profile)
    }

    fun getAllProfiles(): Flow<List<ProfileEntity>> {
        return db.profileDao().getAllProfiles()
    }

    suspend fun getProfileById(id: Long): ProfileEntity? = withContext(Dispatchers.IO) {
        db.profileDao().getProfileById(id)
    }

    // --- СЕССИИ И МЕТРИКИ ---

    /**
     * Атомарное сохранение завершенной тренировки вместе со всем массивом сырых данных.
     */
    suspend fun saveCompleteSession(
        session: SessionEntity,
        metricsList: List<RawMetricsEntity>
    ): Long = withContext(Dispatchers.IO) {
        val sessionId = db.sessionDao().insertSession(session)

        if (metricsList.isNotEmpty()) {
            val updatedMetrics = metricsList.map { metric ->
                metric.copy(sessionId = sessionId)
            }
            db.rawMetricsDao().insertMetrics(updatedMetrics)
        }

        sessionId
    }

    fun getSessionsForProfile(profileId: Long): Flow<List<SessionEntity>> {
        return db.sessionDao().getSessionsByProfile(profileId)
    }

    fun getMetricsForSession(sessionId: Long): Flow<List<RawMetricsEntity>> {
        return db.rawMetricsDao().getMetricsForSession(sessionId)
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        db.sessionDao().deleteSession(sessionId)
    }
}
