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

public class AudioPacerEngine {
    private static final String TAG = "AudioPacerEngine";
    private static final int SAMPLE_RATE = 44100;

    private AudioTrack audioTrack;
    private volatile boolean isRunning = false;
    private Thread audioThread;

    // Текущий прогресс фазы дыхания (от 0.0 до 1.0), обновляется извне с мастер-устройства
    private volatile float currentPhase = 0f;

    private final Context context;
    private short[] decodedPcmSamples; // Буфер декодированного аудиофайла в памяти

    public AudioPacerEngine(Context context) {
        this.context = context;
    }

    public void start() {
        if (isRunning) return;
        
        // 1. Декодируем MP3 из ресурсов в сырые PCM-семплы (выполняется один раз при старте)
        if (decodedPcmSamples == null || decodedPcmSamples.length == 0) {
            decodedPcmSamples = loadPcmFromRaw(context, R.raw.o2test_01);
            if (decodedPcmSamples.length == 0) {
                Log.e(TAG, "Ошибка: не удалось декодировать аудиофайл!");
                return;
            }
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
                bufferSize * 2,
                AudioTrack.MODE_STREAM,
                0
        );

        audioTrack.play();

        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Буфер для отправки в AudioTrack (стереопары: L, R)
                short[] outBuffer = new short[bufferSize / 2];
                int pcmIndex = 0; // Указатель позиции в декодированном файле

                double baseFreq = 150.0; // Базовая несущая частота для бинаурального ритма
                double phaseLeft = 0;
                double phaseRight = 0;

                while (isRunning) {
                    float phase = currentPhase; // Фиксация текущей фазы для потока

                    // Динамическая модуляция дельты бинаурального ритма (от 8 Гц до 12 Гц в такт дыханию)
                    double targetDelta = 8.0 + 4.0 * Math.sin(phase * 2.0 * Math.PI);
                    double freqLeft = baseFreq;
                    double freqRight = baseFreq + targetDelta;

                    // Амплитудная огибающая (изохрония) для согласования громкости эмбиента с дыханием
                    float volumeEnvelope = (float) (0.4f + 0.6f * Math.abs(Math.sin(phase * 2.0 * Math.PI)));

                    for (int i = 0; i < outBuffer.length; i += 2) {
                        // --- Аспект 1 & 2: Чтение эмбиента с бесшовным кольцом и амплитудной модуляцией ---
                        short ambientL = 0;
                        short ambientR = 0;

                        if (decodedPcmSamples.length > 0) {
                            ambientL = decodedPcmSamples[pcmIndex];
                            // Если файл моно или стерео, корректно считываем пару
                            if (pcmIndex + 1 < decodedPcmSamples.length) {
                                ambientR = decodedPcmSamples[pcmIndex + 1];
                            }
                            
                            pcmIndex += 2;
                            // Бесшовное зацикливание (looping) по достижении конца буфера
                            if (pcmIndex >= decodedPcmSamples.length) {
                                pcmIndex = 0;
                            }
                        }

                        // Применяем амплитудную огибающую к фоновому звуку
                        short modAmbientL = (short) (ambientL * volumeEnvelope);
                        short modAmbientR = (short) (ambientR * volumeEnvelope);

                        // --- Аспект 3 & 4: Генерация бинауральных тонов с частотной модуляцией ---
                        phaseLeft += 2.0 * Math.PI * freqLeft / SAMPLE_RATE;
                        phaseRight += 2.0 * Math.PI * freqRight / SAMPLE_RATE;

                        if (phaseLeft > 2.0 * Math.PI) phaseLeft -= 2.0 * Math.PI;
                        if (phaseRight > 2.0 * Math.PI) phaseRight -= 2.0 * Math.PI;

                        // Амплитуда бинаурального слоя делается мягкой, чтобы не заглушать эмбиент
                        short binauralL = (short) (Math.sin(phaseLeft) * 1500);
                        short binauralR = (short) (Math.sin(phaseRight) * 1500);

                        // Смешиваем фоновый эмбиент и бинауральный слой в единый микс
                        outBuffer[i]     = (short) clamp(modAmbientL + binauralL);
                        outBuffer[i + 1] = (short) clamp(modAmbientR + binauralR);
                    }

                    if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                        audioTrack.write(outBuffer, 0, outBuffer.length);
                    }
                }
            }
        });

        audioThread.start();
        Log.d(TAG, "Многоконтурный аудиодвижок успешно запущен.");
    }

    /**
     * Вспомогательный метод для декодирования MP3 файла из raw-ресурсов в массив PCM 16-bit
     */
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

    /**
     * Метод принимает текущий прогресс фазы (0.0 ... 1.0) от мастер-часов
     */
    public void updatePhase(float progress) {
        this.currentPhase = progress;
    }

    public void stop() {
        isRunning = false;
        if (audioThread != null) {
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            audioTrack = null;
        }
        Log.d(TAG, "Аудиодвижок остановлен.");
    }
}
