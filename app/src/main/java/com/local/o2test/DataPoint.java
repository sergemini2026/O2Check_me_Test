package com.local.o2test;

public class DataPoint {
    public long timestamp;
    public int elapsedSec;
    public int spo2;
    public int hr;
    public float pi;

    public DataPoint(long timestamp, int elapsedSec, int spo2, int hr, float pi) {
        this.timestamp = timestamp;
        this.elapsedSec = elapsedSec;
        this.spo2 = spo2;
        this.hr = hr;
        this.pi = pi;
    }
}
