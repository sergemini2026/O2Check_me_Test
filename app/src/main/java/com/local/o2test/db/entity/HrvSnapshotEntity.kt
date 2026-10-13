package com.local.o2test.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.local.o2test.HrvCalculator

@Entity(tableName = "hrv_snapshots")
data class HrvSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val rmssd: Double,
    val sdnn: Double,
    val pnn50: Double,
    val lfPower: Double,
    val hfPower: Double,
    val totalPower: Double,
    val lfHfRatio: Double,
    val sd1: Double,
    val sd2: Double,
    val sd1sd2Ratio: Double,
    val baevskySi: Double,
    val artifactsDetected: Int,
    val artifactPct: Double
) {
    // Вторичный конструктор для удобного маппинга из нашего Java-класса Metrics
    constructor(timestamp: Long, m: HrvCalculator.Metrics) : this(
        timestamp = timestamp,
        rmssd = m.rmssd,
        sdnn = m.sdnn,
        pnn50 = m.pnn50,
        lfPower = m.lfPower,
        hfPower = m.hfPower,
        totalPower = m.totalPower,
        lfHfRatio = m.lfHfRatio,
        sd1 = m.sd1,
        sd2 = m.sd2,
        sd1sd2Ratio = m.sd1sd2Ratio,
        baevskySi = m.baevskySi,
        artifactsDetected = m.artifactsDetected,
        artifactPct = m.artifactPct
    )
}
