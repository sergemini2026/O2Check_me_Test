package com.local.o2test.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val firstName: String,
    val lastName: String = "",
    val age: Int = 30,
    val weightKg: Float = 70.0f,
    val heightCm: Float = 175.0f,
    val baselineSystolicBp: Int = 120,
    val baselineDiastolicBp: Int = 80,
    val fitnessLevel: String = "Средний",
    val gender: String = "Мужской",
    val createdAt: Long = System.currentTimeMillis()
)
