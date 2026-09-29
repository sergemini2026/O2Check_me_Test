package com.local.o2test;

public class DataPoint {
    public int elapsedSec;
    public int spo2;
    public int hr;
    public float pi;

    public DataPoint(int elapsedSec, int spo2, int hr, float pi) {
        this.elapsedSec = elapsedSec;
        this.spo2 = spo2;
        this.hr = hr;
        this.pi = pi;
    }
}
