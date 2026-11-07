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
    val sampleEntropy: Double = 0.0,
    val dfaAlpha1: Double = 1.0,
    val modulationSec: Float = 10.0f,
    val artifactsDetected: Int,
    val artifactPct: Double,
    val isPstiPhase: Boolean = false,
    val isDumpSnapshot: Boolean = false,
    val globalUniqueIndex: Int = 0,
    val trainingDumpIndex: Int = 0,
    val testDumpIndex: Int = 0,
    val blockNumber: Int = 1 // Порядковый номер блока внутри цикла тренировки
) {
    constructor(
        timestamp: Long,
        m: HrvCalculator.Metrics,
        modulationSec: Float,
        isPstiPhase: Boolean,
        isDumpSnapshot: Boolean,
        globalUniqueIndex: Int,
        trainingDumpIndex: Int,
        testDumpIndex: Int,
        blockNumber: Int
    ) : this(
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
        sampleEntropy = m.sampleEntropy,
        dfaAlpha1 = m.dfaAlpha1,
        modulationSec = modulationSec,
        artifactsDetected = m.artifactsDetected,
        artifactPct = m.artifactPct,
        isPstiPhase = isPstiPhase,
        isDumpSnapshot = isDumpSnapshot,
        globalUniqueIndex = globalUniqueIndex,
        trainingDumpIndex = trainingDumpIndex,
        testDumpIndex = testDumpIndex,
        blockNumber = blockNumber
    )
}
