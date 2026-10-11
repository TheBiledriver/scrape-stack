package com.biledriver.scrapestack;

import android.content.Context;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Small wrapper around the vibrator with rate limiting so drag ticks never pile up. */
final class Haptics {
    private final Vibrator vib;
    private final boolean amp;
    private long last;
    volatile boolean enabled = true;

    Haptics(Context ctx) {
        vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        amp = vib != null && vib.hasVibrator() && vib.hasAmplitudeControl();
    }

    /** A light tick used while dragging across material. */
    void tick(int amplitude) {
        pulse(amp ? 12 : 7, amplitude, 42);
    }

    void pulse(int ms, int amplitude, int minGapMs) {
        if (!enabled || vib == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < minGapMs) return;
        last = now;
        try {
            int a = amp ? Math.max(1, Math.min(255, amplitude)) : VibrationEffect.DEFAULT_AMPLITUDE;
            vib.vibrate(VibrationEffect.createOneShot(ms, a));
        } catch (Exception ignored) {
        }
    }

    void wave(long[] timings, int[] amps) {
        if (!enabled || vib == null) return;
        last = SystemClock.uptimeMillis();
        try {
            if (amp) vib.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
            else vib.vibrate(VibrationEffect.createWaveform(timings, -1));
        } catch (Exception ignored) {
        }
    }
}
