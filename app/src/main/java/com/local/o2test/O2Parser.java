private void parseData(byte[] data) {
    if (data == null || data.length < 13) return;

    if ((data[0] & 0xFF) == 0x55) {
        // Смещаем индексы на +2 байта правее (было 7 и 8, стали 9 и 10 из-за нулей после 0D)
        int spo2 = data[9] & 0xFF;
        int hr = data[10] & 0xFF;
        
        // PI и батарея в хвосте пакета
        float pi = (data.length > 16 && data[16] != 0) ? (data[16] & 0xFF) / 10.0f : 0f;
        int battery = (data.length > 15) ? (data[15] & 0xFF) : 0;

        if (spo2 > 0 && spo2 <= 100 && hr > 0 && hr < 250) {
            long now = System.currentTimeMillis();

            runOnUiThread(() -> updateStatusHeader(spo2, hr, pi, battery));

            if (isRecording) {
                int elapsedSec = (int) ((now - sessionStartTime) / 1000);
                DataPoint dp = new DataPoint(now, elapsedSec, spo2, hr, pi);
                sessionData.add(dp);
                runOnUiThread(() -> chartView.addDataPoint(dp));
            }
        }
    }
}
