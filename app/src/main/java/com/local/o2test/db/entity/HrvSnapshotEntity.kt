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
    val modulationSec: Float = 10.0f, // Сохранение периода модуляции в секундах
    val artifactsDetected: Int,
    val artifactPct: Double,
    val isPstiPhase: Boolean = false,  // Флаг принадлежности к фазе теста PSTI[span_1](start_span)[span_1](end_span)
    val isDumpSnapshot: Boolean = false, // Флаг итогового снимка по завершении 300-ударного дампа[span_2](start_span)[span_2](end_span)
    val dumpIndex: Int = 0 // Сквозной порядковый номер дампа сессии[span_3](start_span)[span_3](end_span)
) {
    constructor(timestamp: Long, m: HrvCalculator.Metrics, modulationSec: Float, isPstiPhase: Boolean, isDumpSnapshot: Boolean, dumpIndex: Int) : this(
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
        dumpIndex = dumpIndex
    )[span_4](start_span)[span_4](end_span)

    constructor(timestamp: Long, m: HrvCalculator.Metrics, modulationSec: Float, isPstiPhase: Boolean, isDumpSnapshot: Boolean) : this(
        timestamp, m, modulationSec, isPstiPhase, isDumpSnapshot, 0
    )[span_5](start_span)[span_5](end_span)

    constructor(timestamp: Long, m: HrvCalculator.Metrics, modulationSec: Float, isPstiPhase: Boolean) : this(
        timestamp, m, modulationSec, isPstiPhase, false, 0
    )[span_6](start_span)[span_6](end_span)

    constructor(timestamp: Long, m: HrvCalculator.Metrics, modulationSec: Float) : this(
        timestamp, m, modulationSec, false, false, 0
    )[span_7](start_span)[span_7](end_span)

    constructor(timestamp: Long, m: HrvCalculator.Metrics) : this(timestamp, m, 10.0f, false, false, 0)[span_8](start_span)[span_8](end_span)
}
