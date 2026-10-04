package com.local.o2test;

public class DataPoint {
    public String timestamp;
    public int elapsedSec;
    public int spo2;
    public int hr;
    public float pi;
    public int rrMs; // Поле для миллисекундных RR-интервалов

    public DataPoint(String timestamp, int elapsedSec, int spo2, int hr, float pi, int rrMs) {
        this.timestamp = timestamp;
        this.elapsedSec = elapsedSec;
        this.spo2 = spo2;
        this.hr = hr;
        this.pi = pi;
        this.rrMs = rrMs;
    }

    public DataPoint(int elapsedSec, int spo2, int hr, float pi, int rrMs) {
        this("", elapsedSec, spo2, hr, pi, rrMs);
    }
}
