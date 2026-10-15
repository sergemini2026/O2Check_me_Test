package com.local.o2test.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.local.o2test.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfileSync(profile: ProfileEntity): Long

    @Update
    fun updateProfileSync(profile: ProfileEntity)

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    fun getAllProfilesSync(): List<ProfileEntity>

    // Метод для поиска профилей по имени или фамилии (следующий шаг)
    @Query("SELECT * FROM profiles WHERE firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchProfilesSync(query: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    fun deleteProfileSync(id: Long)
}
