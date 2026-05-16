package com.clarinet.app;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int SAMPLE_RATE = 44100;

    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;
    private volatile double currentFrequency = 0;
    private Thread audioThread;

    private static final String[] NOTE_NAMES_TR = {
        "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si", "Do'"
    };
    private static final double[] FREQUENCIES = {
        261.63, 277.18, 293.66, 311.13, 329.63,
        349.23, 369.99, 392.00, 415.30, 440.00,
        466.16, 493.88, 523.25
    };
    private static final boolean[] IS_SHARP = {
        false, true, false, true, false,
        false, true, false, true, false,
        true, false, false
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0d1117"));
        root.setPadding(12, 30, 12, 20);

        TextView title = new TextView(this);
        title.setText("KLARNET");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#c9d1d9"));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tusa basin ve tutun  |  Press & hold a key");
        sub.setTextSize(11);
        sub.setTextColor(Color.parseColor("#8b949e"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 4, 0, 24);
        root.addView(sub);

        // White keys row
        LinearLayout whiteRow = new LinearLayout(this);
        whiteRow.setOrientation(LinearLayout.HORIZONTAL);
        whiteRow.setWeightSum(7);

        // Black keys row (with spacers)
        LinearLayout blackRow = new LinearLayout(this);
        blackRow.setOrientation(LinearLayout.HORIZONTAL);
        blackRow.setWeightSum(14);

        // Indices of white and black notes
        int[] whiteIdx = {0, 2, 4, 5, 7, 9, 11, 12};   // C D E F G A B C'
        int[] blackIdx = {1, 3, -1, 6, 8, 10, -1};      // C# D# (gap) F# G# A# (gap)

        // Build white keys
        for (int w = 0; w < whiteIdx.length; w++) {
            final int ni = whiteIdx[w];
            Button btn = makeKey(NOTE_NAMES_TR[ni], FREQUENCIES[ni], false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    dpToPx(160), 1.0f);
            lp.setMargins(2, 0, 2, 0);
            btn.setLayoutParams(lp);
            whiteRow.addView(btn);
        }

        // Build black keys with spacers
        for (int b = 0; b < blackIdx.length; b++) {
            int ni = blackIdx[b];
            if (ni == -1) {
                // spacer
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        dpToPx(100), 2.0f);
                spacer.setLayoutParams(lp);
                blackRow.addView(spacer);
            } else {
                Button btn = makeKey(NOTE_NAMES_TR[ni], FREQUENCIES[ni], true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        dpToPx(100), 2.0f);
                lp.setMargins(2, 0, 2, 0);
                btn.setLayoutParams(lp);
                blackRow.addView(btn);
            }
        }

        root.addView(blackRow);
        root.addView(whiteRow);

        // Octave info label
        TextView octLabel = new TextView(this);
        octLabel.setText("Oktav: 4  (C4 - C5)");
        octLabel.setTextSize(12);
        octLabel.setTextColor(Color.parseColor("#8b949e"));
        octLabel.setGravity(Gravity.CENTER);
        octLabel.setPadding(0, 16, 0, 0);
        root.addView(octLabel);

        setContentView(root);
    }

    private Button makeKey(String label, double freq, boolean isBlack) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(isBlack ? 9 : 13);
        btn.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        btn.setPadding(0, 0, 0, isBlack ? 4 : 8);

        int normalColor = isBlack ? Color.parseColor("#21262d") : Color.parseColor("#f0f6fc");
        int pressColor  = Color.parseColor("#238636");
        int textColor   = isBlack ? Color.parseColor("#c9d1d9") : Color.parseColor("#0d1117");

        btn.setBackgroundColor(normalColor);
        btn.setTextColor(textColor);

        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    btn.setBackgroundColor(pressColor);
                    btn.setTextColor(Color.WHITE);
                    startNote(freq);
                } else if (action == MotionEvent.ACTION_UP ||
                           action == MotionEvent.ACTION_CANCEL) {
                    btn.setBackgroundColor(normalColor);
                    btn.setTextColor(textColor);
                    stopNote();
                }
                return true;
            }
        });

        return btn;
    }

    private void startNote(double frequency) {
        stopNote();
        currentFrequency = frequency;
        isPlaying = true;

        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int bufSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                bufSize = Math.max(bufSize, 4096);

                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufSize,
                        AudioTrack.MODE_STREAM);

                audioTrack.play();

                short[] buffer = new short[bufSize];
                double phase = 0;
                double freq = currentFrequency;
                int sampleIdx = 0;
                int attackLen = (int)(SAMPLE_RATE * 0.025); // 25ms attack

                while (isPlaying) {
                    double phaseInc = 2.0 * Math.PI * freq / SAMPLE_RATE;
                    for (int i = 0; i < buffer.length && isPlaying; i++) {
                        // Clarinet harmonics: odd harmonics dominant
                        double s = 0;
                        s += Math.sin(phase)       * 0.50;  // 1st
                        s += Math.sin(2 * phase)   * 0.04;  // 2nd (weak)
                        s += Math.sin(3 * phase)   * 0.30;  // 3rd (strong)
                        s += Math.sin(4 * phase)   * 0.02;  // 4th (weak)
                        s += Math.sin(5 * phase)   * 0.15;  // 5th
                        s += Math.sin(6 * phase)   * 0.01;  // 6th (weak)
                        s += Math.sin(7 * phase)   * 0.07;  // 7th
                        s += Math.sin(9 * phase)   * 0.03;  // 9th

                        // Attack envelope
                        double env = (sampleIdx < attackLen)
                                ? (double) sampleIdx / attackLen
                                : 1.0;

                        buffer[i] = (short)(s * env * 18000);

                        phase += phaseInc;
                        if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                        sampleIdx++;
                    }
                    audioTrack.write(buffer, 0, buffer.length);
                }

                // Release fade
                double fade = 1.0;
                int fadeLen = (int)(SAMPLE_RATE * 0.05);
                for (int f = 0; f < fadeLen; f += bufSize) {
                    int chunk = Math.min(bufSize, fadeLen - f);
                    double phaseInc = 2.0 * Math.PI * freq / SAMPLE_RATE;
                    for (int i = 0; i < chunk; i++) {
                        double s = 0;
                        s += Math.sin(phase)     * 0.50;
                        s += Math.sin(3 * phase) * 0.30;
                        s += Math.sin(5 * phase) * 0.15;
                        s += Math.sin(7 * phase) * 0.07;
                        double env = 1.0 - (double)(f + i) / fadeLen;
                        buffer[i] = (short)(s * env * 18000);
                        phase += phaseInc;
                        if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                    }
                    audioTrack.write(buffer, 0, chunk);
                }

                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        });

        audioThread.start();
    }

    private void stopNote() {
        isPlaying = false;
        if (audioThread != null) {
            try {
                audioThread.join(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNote();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNote();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
