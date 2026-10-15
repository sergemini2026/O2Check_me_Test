package com.local.o2test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.o2test.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    // Синхронный метод для вставки/обновления профиля из фонового потока
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfileSync(profile: ProfileEntity): Long

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    // Синхронный метод получения списка профилей для диалога
    @Query("SELECT * FROM profiles ORDER BY id DESC")
    fun getAllProfilesSync(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    // Синхронный метод удаления профиля из фонового потока
    @Query("DELETE FROM profiles WHERE id = :id")
    fun deleteProfileSync(id: Long)
}
