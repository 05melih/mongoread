package com.clarinet.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Notes: C4 to C5 (one octave) + C5 to G5
    private static final String[] NOTE_NAMES = {
        "Do", "Re", "Mi", "Fa", "Sol", "La", "Si",
        "Do²", "Re²", "Mi²", "Fa²", "Sol²"
    };

    private static final double[] FREQUENCIES = {
        261.63, // C4
        293.66, // D4
        329.63, // E4
        349.23, // F4
        392.00, // G4
        440.00, // A4
        493.88, // B4
        523.25, // C5
        587.33, // D5
        659.25, // E5
        698.46, // F5
        783.99  // G5
    };

    private static final int[] BUTTON_IDS = {
        R.id.btn_do, R.id.btn_re, R.id.btn_mi, R.id.btn_fa,
        R.id.btn_sol, R.id.btn_la, R.id.btn_si,
        R.id.btn_do2, R.id.btn_re2, R.id.btn_mi2,
        R.id.btn_fa2, R.id.btn_sol2
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            final double freq = FREQUENCIES[i];
            Button btn = findViewById(BUTTON_IDS[i]);
            btn.setOnClickListener(v -> ClarinetSynth.playNote(freq));
        }
    }
}
