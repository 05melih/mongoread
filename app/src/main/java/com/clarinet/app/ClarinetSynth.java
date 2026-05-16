package com.clarinet.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public class ClarinetSynth {

    private static final int SAMPLE_RATE = 44100;
    private static final int DURATION_MS = 1500;

    // Clarinet-like: odd harmonics with amplitude envelope
    public static void playNote(double frequency) {
        new Thread(() -> {
            int numSamples = (SAMPLE_RATE * DURATION_MS) / 1000;
            short[] buffer = new short[numSamples];

            double attackSamples = SAMPLE_RATE * 0.03;
            double releaseSamples = SAMPLE_RATE * 0.4;
            double sustainStart = numSamples - releaseSamples;

            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;

                // Odd harmonics only (clarinet characteristic)
                double sample = 0;
                sample += 1.00 * Math.sin(2 * Math.PI * frequency * 1 * t);
                sample += 0.75 * Math.sin(2 * Math.PI * frequency * 3 * t);
                sample += 0.50 * Math.sin(2 * Math.PI * frequency * 5 * t);
                sample += 0.25 * Math.sin(2 * Math.PI * frequency * 7 * t);
                sample += 0.10 * Math.sin(2 * Math.PI * frequency * 9 * t);
                sample += 0.05 * Math.sin(2 * Math.PI * frequency * 11 * t);

                // Normalize
                sample /= 2.65;

                // Amplitude envelope (attack + sustain + release)
                double env;
                if (i < attackSamples) {
                    env = i / attackSamples;
                } else if (i > sustainStart) {
                    env = 1.0 - ((i - sustainStart) / releaseSamples);
                    env = Math.max(0, env);
                } else {
                    env = 1.0;
                }

                // Slight vibrato
                double vibrato = 1.0 + 0.003 * Math.sin(2 * Math.PI * 5.5 * t);
                sample *= vibrato;

                buffer[i] = (short) (sample * env * Short.MAX_VALUE * 0.85);
            }

            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(buffer.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            track.write(buffer, 0, buffer.length);
            track.play();

            try {
                Thread.sleep(DURATION_MS + 100);
            } catch (InterruptedException ignored) {}

            track.stop();
            track.release();
        }).start();
    }
}
