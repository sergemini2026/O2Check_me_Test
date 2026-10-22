package com.local.o2test.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.local.o2test.db.dao.HrvSnapshotDao
import com.local.o2test.db.dao.ProfileDao
import com.local.o2test.db.dao.RawMetricsDao
import com.local.o2test.db.dao.SessionDao
import com.local.o2test.db.entity.HrvSnapshotEntity
import com.local.o2test.db.entity.ProfileEntity
import com.local.o2test.db.entity.RawMetricsEntity
import com.local.o2test.db.entity.SessionEntity

@Database(
    entities = [
        ProfileEntity::class,
        SessionEntity::class,
        RawMetricsEntity::class,
        HrvSnapshotEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun rawMetricsDao(): RawMetricsDao
    abstract fun hrvSnapshotDao(): HrvSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "synergy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
