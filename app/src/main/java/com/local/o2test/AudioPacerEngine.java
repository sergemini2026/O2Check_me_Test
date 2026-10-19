package com.local.o2test;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;

public class AudioPacerEngine {
    private static final String TAG = "AudioPacerEngine";
    private static final int SAMPLE_RATE = 48000; // Повышенная частота для высокого качества

    private AudioTrack audioTrack;
    private volatile boolean isRunning = false;
    private volatile boolean isPrepared = false;
    private Thread audioThread;

    private volatile float currentPhase = 0f;
    private final Context context;
    private short[] decodedPcmSamples;

    // Параметры для плавного изменения громкости (Fade-in / Fade-out) исключающие акустический шок
    private volatile float currentVolumeGain = 0.0f;
    private static final float VOLUME_STEP = 0.002f; // Скорость изменения громкости за один цикл буфера

    public AudioPacerEngine(Context context) {
        this.context = context;
    }

    /**
     * Асинхронная предзагрузка и декодирование аудио в фоновом потоке
     */
    public void prepareAsync(Runnable onReadyCallback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Log.d(TAG, "Старт фоновой декодировки MP3...");
            short[] rawPcm = loadPcmFromRaw(context, R.raw.o2test_01);
            
            // Применяем кроссфейд для бесшовной кольцевой закольцовки (окна по 2048 семплов на стерео-канал)
            decodedPcmSamples = applyLoopCrossfade(rawPcm, 2048);
            
            isPrepared = true;
            Log.d(TAG, "Декодирование и кроссфейд завершены. Семплов в памяти: " + decodedPcmSamples.length);
            if (onReadyCallback != null) {
                onReadyCallback.run();
            }
        });
    }

    /**
     * Utility-метод для устранения щелчков: плавный кроссфейд на стыке начала и конца PCM-массива
     */
    private short[] applyLoopCrossfade(short[] input, int crossfadeSamples) {
        if (input == null || input.length < crossfadeSamples * 4) {
            return input; // Слишком короткий массив для кроссфейда
        }

        int totalSamples = input.length;
        // Работаем со стереопарами, поэтому кроссфейд должен быть кратен 2
        int cf = (crossfadeSamples / 2) * 2;
        short[] output = new short[totalSamples - cf]; // Слегка подрезаем хвост на длину зоны перекрытия

        // Копируем основную часть без изменений
        System.arraycopy(input, 0, output, 0, totalSamples - cf);

        // Накладываем кроссфейд: плавно смешиваем конец оригинального трека с его началом
        for (int i = 0; i < cf; i += 2) {
            float progress = (float) (i / 2) / (cf / 2); // от 0.0 до 1.0

            int tailIndex = totalSamples - cf + i;
            int headIndex = i;

            if (tailIndex + 1 < input.length && headIndex + 1 < output.length) {
                short tailL = input[tailIndex];
                short tailR = input[tailIndex + 1];

                short headL = input[headIndex];
                short headR = input[headIndex + 1];

                // Кросс-модуляция: конец затухает (1.0 -> 0.0), начало нарастает (0.0 -> 1.0)
                output[headIndex]     = (short) (tailL * (1.0f - progress) + headL * progress);
                output[headIndex + 1] = (short) (tailR * (1.0f - progress) + headR * progress);
            }
        }

        return output;
    }

    public void start() {
        if (isRunning) return;
        
        currentVolumeGain = 0.0f; // Всегда начинаем с нуля для реализации плавного Fade-in

        // Если предзагрузка не успела или не была вызвана, подгружаем синхронно с кроссфейдом
        if (decodedPcmSamples == null || decodedPcmSamples.length == 0) {
            short[] rawPcm = loadPcmFromRaw(context, R.raw.o2test_01);
            decodedPcmSamples = applyLoopCrossfade(rawPcm, 2048);
        }

        if (decodedPcmSamples.length == 0) {
            Log.e(TAG, "Ошибка: буфер PCM пуст!");
            return;
        }

        isRunning = true;

        int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

        final int finalBufferSize = bufferSize;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

        audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                finalBufferSize * 2,
                AudioTrack.MODE_STREAM,
                0
        );

        audioTrack.play();

        audioThread = new Thread(() -> {
            short[] outBuffer = new short[finalBufferSize / 2];
            int pcmIndex = 0;

            double baseFreq = 150.0;
            double phaseLeft = 0;
            double phaseRight = 0;

            // Цикл работает пока идет сессия ИЛИ пока не завершился плавный Fade-out звука
            while (isRunning || currentVolumeGain > 0.0f) {
                float phase = currentPhase;

                // Плавное регулирование коэффициента громкости (Fade-in / Fade-out)
                if (isRunning) {
                    if (currentVolumeGain < 1.0f) {
                        currentVolumeGain = Math.min(1.0f, currentVolumeGain + VOLUME_STEP);
                    }
                } else {
                    if (currentVolumeGain > 0.0f) {
                        currentVolumeGain = Math.max(0.0f, currentVolumeGain - VOLUME_STEP);
                    }
                }

                // Модуляция дельты бинаурального ритма
                double targetDelta = 8.0 + 4.0 * Math.sin(phase * 2.0 * Math.PI);
                double freqLeft = baseFreq;
                double freqRight = baseFreq + targetDelta;

                // Амплитудная огибающая с учетом текущего глобального гейна
                float volumeEnvelope = (float) (0.4f + 0.6f * Math.abs(Math.sin(phase * 2.0 * Math.PI))) * currentVolumeGain;

                for (int i = 0; i < outBuffer.length; i += 2) {
                    short ambientL = 0;
                    short ambientR = 0;

                    if (decodedPcmSamples.length > 0) {
                        ambientL = decodedPcmSamples[pcmIndex];
                        if (pcmIndex + 1 < decodedPcmSamples.length) {
                            ambientR = decodedPcmSamples[pcmIndex + 1];
                        }
                        
                        pcmIndex += 2;
                        if (pcmIndex >= decodedPcmSamples.length) {
                            pcmIndex = 0; // Абсолютно бесшовное кольцо без щелчков
                        }
                    }

                    short modAmbientL = (short) (ambientL * volumeEnvelope);
                    short modAmbientR = (short) (ambientR * volumeEnvelope);

                    phaseLeft += 2.0 * Math.PI * freqLeft / SAMPLE_RATE;
                    phaseRight += 2.0 * Math.PI * freqRight / SAMPLE_RATE;

                    if (phaseLeft > 2.0 * Math.PI) phaseLeft -= 2.0 * Math.PI;
                    if (phaseRight > 2.0 * Math.PI) phaseRight -= 2.0 * Math.PI;

                    short binauralL = (short) (Math.sin(phaseLeft) * 1500 * currentVolumeGain);
                    short binauralR = (short) (Math.sin(phaseRight) * 1500 * currentVolumeGain);

                    outBuffer[i]     = (short) clamp(modAmbientL + binauralL);
                    outBuffer[i + 1] = (short) clamp(modAmbientR + binauralR);
                }

                if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.write(outBuffer, 0, outBuffer.length);
                }
            }

            // Безопасное освобождение AudioTrack после полного затухания звука
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                    audioTrack.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                audioTrack = null;
            }
        });

        audioThread.start();
        Log.d(TAG, "Аудиодвижок запущен с кроссфейдом и плавной регулировкой громкости.");
    }

    private short[] loadPcmFromRaw(Context context, int resId) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MediaExtractor extractor = new MediaExtractor();
        try {
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            int audioTrackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            if (audioTrackIndex < 0) return new short[0];
            extractor.selectTrack(audioTrackIndex);

            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodec codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputBufIndex = codec.dequeueInputBuffer(10000);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufIndex);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                int res = codec.dequeueOutputBuffer(info, 10000);
                if (res >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(res);
                    if (outputBuffer != null && info.size > 0) {
                        byte[] chunk = new byte[info.size];
                        outputBuffer.get(chunk);
                        outputBuffer.clear();
                        outStream.write(chunk);
                    }
                    codec.releaseOutputBuffer(res, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            codec.stop();
            codec.release();
            extractor.release();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка декодирования аудио: " + e.getMessage());
        }

        byte[] pcmBytes = outStream.toByteArray();
        short[] pcmShorts = new short[pcmBytes.length / 2];
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShorts);
        return pcmShorts;
    }

    private int clamp(int value) {
        if (value > 32767) return 32767;
        if (value < -32768) return -32768;
        return value;
    }

    public void updatePhase(float progress) {
        this.currentPhase = progress;
    }

    public void stop() {
        isRunning = false; // Сигнализирует потоку о необходимости плавного затухания (Fade-out)
        
        // Предотвращаем блокировку главного потока UI через join, если вызов происходит изнутри аудиопотока
        if (audioThread != null && Thread.currentThread() != audioThread) {
            try {
                audioThread.join(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioThread = null;
        }
        Log.d(TAG, "Запрос плавной остановки аудиодвижка (Fade-out).");
    }
}
