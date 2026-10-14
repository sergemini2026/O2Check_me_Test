package com.local.o2test.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.local.o2test.HrvCalculator

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val age: Int = 30,
    val weightKg: Float = 70.0f,
    val heightCm: Float = 175.0f,
    val baselineSystolicBp: Int = 120,
    val baselineDiastolicBp: Int = 80,
    val fitnessLevel: String = "Средний", // Низкий, Средний, Высокий, Максимальный
    val gender: String = "Мужской", // Новое поле: Пол
    val createdAt: Long = System.currentTimeMillis()
)
