@Override
public void onDataReceived(byte[] data) {
    if (data == null || data.length < 15) return;

    if ((data[0] & 0xFF) == 0x55) {
        int rawSpo2 = data[7] & 0xFF;
        int rawHr = data[8] & 0xFF;
        
        // Читаем батарею с индекса 14
        int battery = (data.length > 14) ? (data[14] & 0xFF) : 0;
        
        // PI берем с индекса 16 (или 10, если короткий пакет)
        int rawPi = (data.length > 16) ? (data[16] & 0xFF) : ((data.length > 10) ? (data[10] & 0xFF) : 0);
        float pi = rawPi / 10.0f;

        // Если палец не вставлен, прибор шлет 0xFF (255)
        boolean isFingerOn = (rawSpo2 > 0 && rawSpo2 <= 100) && (rawHr > 0 && rawHr < 250);

        int spo2 = isFingerOn ? rawSpo2 : 0;
        int hr = isFingerOn ? rawHr : 0;

        long now = System.currentTimeMillis();

        runOnUiThread(() -> updateStatusHeader(spo2, hr, pi, battery));

        if (isFingerOn && isRecording) {
            if (sessionStartTime == 0) sessionStartTime = now;
            int elapsedSec = (int) ((now - sessionStartTime) / 1000);
            
            // В вашей рабочей версии DataPoint принимал long timestamp в миллисекундах:
            DataPoint dp = new DataPoint(now, elapsedSec, spo2, hr, pi);
            sessionData.add(dp);
            runOnUiThread(() -> chartView.addDataPoint(dp));
        }
    }
}
