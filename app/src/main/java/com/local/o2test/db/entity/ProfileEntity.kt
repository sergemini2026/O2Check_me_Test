package com.local.o2test.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val age: Int = 0,
    val weightKg: Float = 0f,
    val heightCm: Float = 0f,
    val baselineSystolicBp: Int = 120,
    val baselineDiastolicBp: Int = 80,
    val createdAt: Long = System.currentTimeMillis()
)
