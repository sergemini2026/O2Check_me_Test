package com.local.o2test.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val workoutMode: String,
    val startTime: Long,
    val endTime: Long,
    val avgHr: Int,
    val avgSpo2: Int,
    val rmssd: Double,
    val sdnn: Double,
    val lfHfRatio: Double,
    val stressIndex: Double
)
