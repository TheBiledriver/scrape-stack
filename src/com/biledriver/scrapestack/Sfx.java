package com.biledriver.scrapestack;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All sounds are synthesized at startup, written as WAVs to the cache dir and played
 * through a SoundPool (low latency, polyphonic, per-stream volume and pitch).
 */
final class Sfx {
    static final int SR = 44100;
    private static final double TAU = Math.PI * 2;

    private final SoundPool pool;
    private final ConcurrentHashMap<String, Integer> ready = new ConcurrentHashMap<>();
    private final HashMap<Integer, String> pending = new HashMap<>();
    private final Random rnd = new Random();
    volatile boolean enabled = true;

    Sfx(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(aa).build();
        pool.setOnLoadCompleteListener((sp, id, status) -> {
            String n;
            synchronized (pending) {
                n = pending.remove(id);
            }
            if (n != null && status == 0) ready.put(n, id);
        });
        final File dir = new File(ctx.getCacheDir(), "sfx");
        Thread t = new Thread(() -> buildAll(dir), "sfx-synth");
        t.start();
    }

    // ------------------------------------------------------------------ playback

    int play(String name, float vol, float rate) {
        if (!enabled) return 0;
        Integer id = ready.get(name);
        if (id == null) return 0;
        vol = clamp(vol, 0f, 1f);
        return pool.play(id, vol, vol, 1, 0, clamp(rate, 0.5f, 2f));
    }

    int playVar(String base, int variants, float vol, float jitter) {
        String n = base + rnd.nextInt(variants);
        return play(n, vol, 1f + (rnd.nextFloat() * 2f - 1f) * jitter);
    }

    int loop(String name, float vol) {
        if (!enabled) return 0;
        Integer id = ready.get(name);
        if (id == null) return 0;
        return pool.play(id, vol, vol, 2, -1, 1f);
    }

    void setVolume(int stream, float v) {
        if (stream != 0) {
            v = clamp(v, 0f, 1f);
            pool.setVolume(stream, v, v);
        }
    }

    void setRate(int stream, float r) {
        if (stream != 0) pool.setRate(stream, clamp(r, 0.5f, 2f));
    }

    void stop(int stream) {
        if (stream != 0) pool.stop(stream);
    }

    void pauseAll() {
        pool.autoPause();
    }

    void resumeAll() {
        pool.autoResume();
    }

    void release() {
        pool.release();
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    // ------------------------------------------------------------------ synthesis

    private void buildAll(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        Random r = new Random(77);
        // short UI sounds first so menus feel alive immediately
        add(dir, "pop", pop());
        add(dir, "whoosh", whoosh(r));
        add(dir, "scrape", scrapeLoop(r));
        add(dir, "rake", rakeLoop(r));
        add(dir, "dryer", dryerLoop(r));
        add(dir, "washer", washerLoop(r));
        for (int i = 0; i < 3; i++) add(dir, "scoop" + i, scoop(r, i));
        for (int i = 0; i < 3; i++) add(dir, "hammer" + i, hammer(r, i));
        add(dir, "chime", chime(r));
        add(dir, "fanfare", fanfare(r));
    }

    private void add(File dir, String name, float[] data) {
        try {
            File f = new File(dir, name + ".wav");
            writeWav(f, data);
            synchronized (pending) {
                int id = pool.load(f.getAbsolutePath(), 1);
                pending.put(id, name);
            }
        } catch (Exception ignored) {
        }
    }

    private static void writeWav(File f, float[] d) throws Exception {
        int n = d.length;
        ByteBuffer bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'R', 'I', 'F', 'F'});
        bb.putInt(36 + n * 2);
        bb.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        bb.putInt(16);
        bb.putShort((short) 1);
        bb.putShort((short) 1);
        bb.putInt(SR);
        bb.putInt(SR * 2);
        bb.putShort((short) 2);
        bb.putShort((short) 16);
        bb.put(new byte[]{'d', 'a', 't', 'a'});
        bb.putInt(n * 2);
        for (float v : d) {
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            bb.putShort((short) (v * 32767));
        }
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(bb.array());
        } finally {
            fos.close();
        }
    }

    private static float lpA(double fc) {
        return (float) (1 - Math.exp(-TAU * fc / SR));
    }

    private static float[] norm(float[] x, float peak) {
        float m = 1e-6f;
        for (float v : x) m = Math.max(m, Math.abs(v));
        float g = peak / m;
        for (int i = 0; i < x.length; i++) x[i] *= g;
        return x;
    }

    /** x has n + F samples; the tail is crossfaded into the head so the loop is seamless. */
    private static float[] loopify(float[] x, int n) {
        int f = x.length - n;
        float[] o = new float[n];
        System.arraycopy(x, 0, o, 0, n);
        for (int i = 0; i < f; i++) {
            float t = (float) i / f;
            o[i] = x[i] * t + x[n + i] * (1 - t);
        }
        return o;
    }

    private static float[] fadeOut(float[] x, float sec) {
        int f = Math.min(x.length, (int) (SR * sec));
        for (int i = 0; i < f; i++) x[x.length - 1 - i] *= (float) i / f;
        return x;
    }

    /** Gritty, grainy scraping texture. */
    private static float[] scrapeLoop(Random r) {
        int n = (int) (SR * 0.9), F = (int) (SR * 0.06);
        float[] x = new float[n + F];
        float lp1 = 0, lp2 = 0, env = 0;
        int next = 0;
        float a1 = lpA(5200), a2 = lpA(650);
        float dec = (float) Math.exp(-1.0 / (SR * 0.007));
        for (int i = 0; i < x.length; i++) {
            if (i >= next) {
                env = Math.min(1.8f, env + 0.5f + r.nextFloat() * 0.9f);
                next = i + (int) (SR * (0.003 + r.nextFloat() * 0.016));
            }
            env *= dec;
            float w = r.nextFloat() * 2 - 1;
            lp1 += a1 * (w - lp1);
            lp2 += a2 * (lp1 - lp2);
            x[i] = (lp1 - lp2) * (0.22f + env);
        }
        return norm(loopify(x, n), 0.85f);
    }

    /** Rhythmic tine clicks with little metallic pings. */
    private static float[] rakeLoop(Random r) {
        int n = (int) (SR * 0.8), F = (int) (SR * 0.05);
        float[] x = new float[n + F];
        float lp = 0, a = lpA(3000);
        for (int i = 0; i < x.length; i++) {
            float w = r.nextFloat() * 2 - 1;
            lp += a * (w - lp);
            x[i] = (w - lp) * 0.10f;
        }
        double t = 0;
        while (t < (n + F) / (double) SR) {
            int i0 = (int) (t * SR);
            double f = 1300 + r.nextFloat() * 2200;
            double dec = 0.004 + r.nextFloat() * 0.007;
            float amp = 0.45f + r.nextFloat() * 0.55f;
            int len = (int) (dec * 6 * SR);
            for (int j = 0; j < len && i0 + j < x.length; j++) {
                double e = Math.exp(-j / (dec * SR));
                double en = Math.exp(-j / (0.0018 * SR));
                x[i0 + j] += (float) (amp * (Math.sin(TAU * f * j / SR) * e * 0.7 + (r.nextFloat() * 2 - 1) * en * 0.8));
            }
            t += 0.012 + r.nextFloat() * 0.024;
        }
        return norm(loopify(x, n), 0.8f);
    }

    /** Motor hum plus rushing air. Tonal parts use whole cycles per second so they loop cleanly. */
    private static float[] dryerLoop(Random r) {
        int n = SR, F = (int) (SR * 0.08);
        float[] x = new float[n + F];
        float brown = 0, l1 = 0, l2 = 0;
        float a1 = lpA(4200), a2 = lpA(600);
        for (int i = 0; i < x.length; i++) {
            double t = (double) i / SR;
            float w = r.nextFloat() * 2 - 1;
            brown = brown * 0.996f + w * 0.03f;
            l1 += a1 * (w - l1);
            l2 += a2 * (l1 - l2);
            float air = (l1 - l2) * (float) (0.85 + 0.15 * Math.sin(TAU * 7 * t));
            float hum = (float) (0.22 * Math.sin(TAU * 120 * t) + 0.12 * Math.sin(TAU * 240 * t)
                    + 0.06 * Math.sin(TAU * 360 * t) + 0.035 * Math.sin(TAU * 1480 * t));
            x[i] = air * 1.4f + hum * 0.55f + brown * 0.6f;
        }
        return norm(loopify(x, n), 0.8f);
    }

    /** High-pressure hiss with spatter crackle. */
    private static float[] washerLoop(Random r) {
        int n = (int) (SR * 0.8), F = (int) (SR * 0.06);
        float[] x = new float[n + F];
        float lp = 0, lp2 = 0, a = lpA(2600), a2 = lpA(10000);
        float spat = 0;
        int next = 0;
        float sdec = (float) Math.exp(-1.0 / (SR * 0.0015));
        for (int i = 0; i < x.length; i++) {
            double t = (double) i / SR;
            float w = r.nextFloat() * 2 - 1;
            lp += a * (w - lp);
            float hp = w - lp;
            lp2 += a2 * (hp - lp2);
            if (i >= next) {
                spat = 0.4f + r.nextFloat() * 1.2f;
                next = i + (int) (SR * (0.001 + r.nextFloat() * 0.006));
            }
            spat *= sdec;
            x[i] = lp2 * 0.6f + hp * spat * 0.5f + (float) (0.08 * Math.sin(TAU * 55 * t));
        }
        return norm(loopify(x, n), 0.75f);
    }

    /** Wet "shlorp" scoop with a crumbly tail. */
    private static float[] scoop(Random r, int v) {
        int n = (int) (SR * 0.45);
        float[] x = new float[n];
        double ph = 0;
        double pitch = 0.85 + v * 0.17;
        float lp = 0, lp2 = 0, a = lpA(1400), a2 = lpA(900);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double f = (140 + 520 * Math.exp(-t / 0.045)) * pitch;
            ph += TAU * f / SR;
            double tone = Math.sin(ph) * (1 - Math.exp(-t / 0.003)) * Math.exp(-t / 0.075) * 0.8;
            float w = r.nextFloat() * 2 - 1;
            lp += a * (w - lp);
            lp2 += a2 * (w - lp2);
            double wet = lp * Math.exp(-t / 0.05) * 1.6;
            double pat = t > 0.11 ? lp2 * Math.exp(-(t - 0.11) / 0.04) * 1.0 : 0;
            x[i] = (float) (tone + wet + pat);
        }
        for (int c = 0; c < 6; c++) {
            int i0 = (int) (SR * (0.14 + r.nextFloat() * 0.25));
            float amp = 0.12f + r.nextFloat() * 0.12f;
            for (int j = 0; j < SR * 0.01 && i0 + j < n; j++)
                x[i0 + j] += (float) ((r.nextFloat() * 2 - 1) * amp * Math.exp(-j / (SR * 0.0018)));
        }
        return fadeOut(norm(x, 0.9f), 0.02f);
    }

    /** Heavy thump + click + glassy shatter. */
    private static float[] hammer(Random r, int v) {
        int n = (int) (SR * 0.65);
        float[] x = new float[n];
        double ph = 0;
        float lp = 0, b1 = 0, b2 = 0;
        float a = lpA(3500), ab1 = lpA(2500), ab2 = lpA(300);
        double pitch = 0.9 + v * 0.1;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double f = (52 + 110 * Math.exp(-t / 0.025)) * pitch;
            ph += TAU * f / SR;
            double thump = Math.sin(ph) * Math.exp(-t / 0.14);
            float w = r.nextFloat() * 2 - 1;
            lp += a * (w - lp);
            double click = (w - lp) * Math.exp(-t / 0.0035) * 0.9;
            b1 += ab1 * (w - b1);
            b2 += ab2 * (b1 - b2);
            double crunch = (b1 - b2) * Math.exp(-t / 0.05) * 1.4;
            x[i] = (float) (thump + click + crunch);
        }
        for (int c = 0; c < 30; c++) {
            double t0 = 0.008 + Math.pow(r.nextFloat(), 2) * 0.4;
            int i0 = (int) (t0 * SR);
            double f = 2200 + r.nextFloat() * 4300;
            double dec = 0.004 + r.nextFloat() * 0.008;
            double amp = 0.35 * Math.exp(-t0 / 0.12) * (0.5 + r.nextFloat());
            for (int j = 0; j < dec * 6 * SR && i0 + j < n; j++)
                x[i0 + j] += (float) (amp * Math.sin(TAU * f * j / SR) * Math.exp(-j / (dec * SR)));
        }
        return fadeOut(norm(x, 0.95f), 0.03f);
    }

    private static double bell(double f, double t) {
        if (t < 0) return 0;
        return (Math.sin(TAU * f * t) + 0.35 * Math.sin(TAU * 2 * f * t) * Math.exp(-t / 0.1)
                + 0.12 * Math.sin(TAU * 3 * f * t)) * (1 - Math.exp(-t / 0.002));
    }

    /** Bright ascending arpeggio for clearing a layer. */
    private static float[] chime(Random r) {
        int n = (int) (SR * 1.1);
        float[] x = new float[n];
        double[] notes = {1046.5, 1318.5, 1568.0, 2093.0};
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double s = 0;
            for (int k = 0; k < notes.length; k++) {
                double tt = t - k * 0.075;
                if (tt >= 0) s += bell(notes[k], tt) * Math.exp(-tt / 0.38) * 0.3;
            }
            x[i] = (float) s;
        }
        sparkle(x, r, 0.25, 0.8, 14, 0.06);
        return fadeOut(norm(x, 0.8f), 0.05f);
    }

    /** Bigger celebration for clearing the whole stack. */
    private static float[] fanfare(Random r) {
        int n = (int) (SR * 2.0);
        float[] x = new float[n];
        double[] arp = {523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0};
        double[] chord = {1046.5, 1318.5, 1568.0, 2093.0};
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double s = 0;
            for (int k = 0; k < arp.length; k++) {
                double tt = t - k * 0.07;
                if (tt >= 0) s += bell(arp[k], tt) * Math.exp(-tt / 0.3) * 0.22;
            }
            double tc = t - 0.5;
            if (tc >= 0) {
                double vib = 1 + 0.004 * Math.sin(TAU * 5.5 * tc);
                for (double f : chord) s += bell(f * vib, tc) * Math.exp(-tc / 0.9) * 0.2;
            }
            x[i] = (float) s;
        }
        sparkle(x, r, 0.5, 1.7, 40, 0.07);
        return fadeOut(norm(x, 0.85f), 0.1f);
    }

    private static void sparkle(float[] x, Random r, double from, double to, int count, double amp) {
        for (int c = 0; c < count; c++) {
            int i0 = (int) ((from + r.nextFloat() * (to - from)) * SR);
            double f = 3000 + r.nextFloat() * 4500;
            for (int j = 0; j < SR * 0.06 && i0 + j < x.length; j++)
                x[i0 + j] += (float) (amp * Math.sin(TAU * f * j / SR) * Math.exp(-j / (SR * 0.012)));
        }
    }

    /** Soft UI pop. */
    private static float[] pop() {
        int n = (int) (SR * 0.11);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double f = 420 + 650 * Math.exp(-t / 0.012);
            ph += TAU * f / SR;
            x[i] = (float) (Math.sin(ph) * Math.exp(-t / 0.03) * (1 - Math.exp(-t / 0.001)));
        }
        return fadeOut(norm(x, 0.8f), 0.01f);
    }

    /** Airy swoosh for dialogs and screen changes. */
    private static float[] whoosh(Random r) {
        int n = (int) (SR * 0.35);
        float[] x = new float[n];
        float l1 = 0, l2 = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            float a1 = lpA(800 + 3800 * Math.sin(Math.PI * t)), a2 = lpA(400);
            float w = r.nextFloat() * 2 - 1;
            l1 += a1 * (w - l1);
            l2 += a2 * (l1 - l2);
            double env = Math.pow(Math.sin(Math.PI * t), 2);
            x[i] = (float) ((l1 - l2) * env);
        }
        return norm(x, 0.6f);
    }
}
