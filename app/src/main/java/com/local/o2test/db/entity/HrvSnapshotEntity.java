package com.local.o2test.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.local.o2test.HrvCalculator;

@Entity(tableName = "hrv_snapshots")
public class HrvSnapshotEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    public double rmssd;
    public double sdnn;
    public double pnn50;
    public double lfPower;
    public double hfPower;
    public double totalPower;
    public double lfHfRatio;
    public double sd1;
    public double sd2;
    public double sd1sd2Ratio;
    public double baevskySi;
    
    public int artifactsDetected;
    public double artifactPct;

    public HrvSnapshotEntity(long timestamp, HrvCalculator.Metrics m) {
        this.timestamp = timestamp;
        this.rmssd = m.rmssd;
        this.sdnn = m.sdnn;
        this.pnn50 = m.pnn50;
        this.lfPower = m.lfPower;
        this.hfPower = m.hfPower;
        this.totalPower = m.totalPower;
        this.lfHfRatio = m.lfHfRatio;
        this.sd1 = m.sd1;
        this.sd2 = m.sd2;
        this.sd1sd2Ratio = m.sd1sd2Ratio;
        this.baevskySi = m.baevskySi;
        this.artifactsDetected = m.artifactsDetected;
        this.artifactPct = m.artifactPct;
    }
}
