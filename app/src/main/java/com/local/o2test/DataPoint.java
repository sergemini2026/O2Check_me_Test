package com.local.o2test;

public class DataPoint {
    public String timestamp;
    public int elapsedSec;
    public int spo2;
    public int hr;
    public float pi;

    public DataPoint(String timestamp, int elapsedSec, int spo2, int hr, float pi) {
        this.timestamp = timestamp;
        this.elapsedSec = elapsedSec;
        this.spo2 = spo2;
        this.hr = hr;
        this.pi = pi;
    }

    public DataPoint(int elapsedSec, int spo2, int hr, float pi) {
        this("", elapsedSec, spo2, hr, pi);
    }
}
