package com.local.o2test.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.local.o2test.db.entity.HrvSnapshotEntity;

import java.util.List;

@Dao
public interface HrvSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HrvSnapshotEntity> snapshots);

    @Query("SELECT * FROM hrv_snapshots ORDER BY timestamp ASC")
    List<HrvSnapshotEntity> getAllSnapshots();

    @Query("DELETE FROM hrv_snapshots")
    void deleteAll();
}
