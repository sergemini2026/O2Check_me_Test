package com.local.o2test.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.local.o2test.db.entity.ProfileEntity;
import kotlinx.coroutines.flow.Flow;
import java.util.List;

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertProfileSync(ProfileEntity profile); // Синхронный метод для вставки/обновления

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    Flow<List<ProfileEntity>> getAllProfiles();

    @Query("SELECT * FROM profiles ORDER BY id DESC")
    List<ProfileEntity> getAllProfilesSync(); // Синхронный метод получения списка

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?;

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long);

    @Query("DELETE FROM profiles WHERE id = :id")
    void deleteProfileSync(long id); // Синхронный метод удаления
}
