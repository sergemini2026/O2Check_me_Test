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
data class SessionEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val workoutMode: String,
    val startTime: Long,
    val endTime: Long,
    val avgHr: Int,
    val avgSpo2: Int,
    val rmssd: Double,
    val sdnn: Double,
    val lfHfRatio: Double,
    val stressIndex: Double,
    val pdfPath: String = "",
    val csvPath: String = "",
    val testCsvPath: String = "",        // Путь к тестовому CSV-файлу (PSTI)
    val pstiScore: Double = 0.0,         // Интегральный балл следовой когерентности (0-100%)
    val pstiRmssdRatio: Double = 0.0,    // Коэффициент удержания RMSSD в свободном окне
    val baselineLfHf: Double = 0.0,      // Базовый вегетативный баланс по первым 100 интервалам
    val cycleLfHfValues: String = ""     // Массив значений LF/HF по тестовым окнам (через запятую)
)
