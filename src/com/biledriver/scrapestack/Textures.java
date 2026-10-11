package com.biledriver.scrapestack;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Procedural, deterministic (seeded) layer textures. Everything is drawn at runtime: no image assets. */
final class Textures {
    static final int WOOD = 0, DIRT = 1, WATER = 2, GRASS = 3, FROSTING = 4, LAVA = 5, ICE = 6, SAND = 7,
            BRICK = 8, SLIME = 9, GALAXY = 10, CHOCOLATE = 11, MELON = 12, MINT = 13, MOSAIC = 14;
    static final int REGULAR = 15;
    static final int GOLD = 15, GEMS = 16, RAINBOW = 17;

    static final String[] NAMES = {"Wood", "Dirt", "Water", "Grass", "Frosting", "Lava", "Ice", "Sand",
            "Brick", "Slime", "Galaxy", "Chocolate", "Watermelon", "Peppermint", "Mosaic",
            "Gold Hoard", "Gem Stash", "Rainbow"};
    private static final float[] HUE = {32, 25, 205, 115, 330, 15, 190, 45, 5, 85, 270, 20, 350, 0, 175, 45, 280, 0};
    private static final float[] LUM = {.6f, .3f, .55f, .55f, .82f, .22f, .92f, .8f, .48f, .78f, .1f, .2f, .55f, .85f, .62f, .7f, .25f, .7f};
    static final int[] BASE = {0xFFF5A623, 0xFF8B5A2B, 0xFF0A9BFF, 0xFF3DDC4A, 0xFFFF8FC8, 0xFF3A140C, 0xFFC8F4FF,
            0xFFFFD36B, 0xFFE2483A, 0xFF9DFF2E, 0xFF1A0B3D, 0xFF6B3A1E, 0xFFFF3B5C, 0xFFFFEEEE, 0xFF1FC8C0,
            0xFFFFB800, 0xFF2B0A4A, 0xFFFF6FD8};

    private static final int[] CANDY = {0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE, 0xFFFFFFFF, 0xFFFF9500, 0xFF5AC8FA};

    /** Picks `layers` regular textures, each contrasting strongly with the one above it, plus a treasure layer. */
    static int[] pickStack(Random r, int layers) {
        int[] t = new int[layers + 1];
        boolean[] used = new boolean[REGULAR];
        int prev = -1;
        for (int i = 0; i < layers; i++) {
            int best = 0;
            float bestScore = -1;
            for (int c = 0; c < REGULAR; c++) {
                if (used[c]) continue;
                float s = (prev < 0 ? 0 : contrast(prev, c)) + r.nextFloat() * 0.7f;
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            t[i] = best;
            used[best] = true;
            prev = best;
        }
        t[layers] = GOLD + r.nextInt(3);
        return t;
    }

    static int contrasting(Random r, int a) {
        int best = 0;
        float bs = -1;
        for (int c = 0; c < REGULAR; c++) {
            if (c == a) continue;
            float s = contrast(a, c) + r.nextFloat() * 0.4f;
            if (s > bs) {
                bs = s;
                best = c;
            }
        }
        return best;
    }

    private static float contrast(int a, int b) {
        float dh = Math.abs(HUE[a] - HUE[b]);
        if (dh > 180) dh = 360 - dh;
        return dh / 180f + 1.6f * Math.abs(LUM[a] - LUM[b]);
    }

    static Bitmap generate(int type, int w, int h, long seed) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Random r = new Random(seed);
        float u = w / 540f;
        float area = (w * (float) h) / (540f * 1170f);
        switch (type) {
            case WOOD: wood(c, w, h, r, u); break;
            case DIRT: dirt(c, w, h, r, u, area); break;
            case WATER: water(c, w, h, r, u, area); break;
            case GRASS: grass(c, w, h, r, u, area); break;
            case FROSTING: frosting(c, w, h, r, u, area); break;
            case LAVA: lava(b, c, w, h, r, u, area); break;
            case ICE: ice(b, c, w, h, r, u, area); break;
            case SAND: sand(c, w, h, r, u, area); break;
            case BRICK: brick(c, w, h, r, u); break;
            case SLIME: slime(c, w, h, r, u, area); break;
            case GALAXY: galaxy(c, w, h, r, u, area); break;
            case CHOCOLATE: chocolate(c, w, h, r, u); break;
            case MELON: melon(c, w, h, r, u, area); break;
            case MINT: mint(c, w, h, r, u, area); break;
            case MOSAIC: mosaic(b, c, w, h, r, u); break;
            case GOLD: gold(c, w, h, r, u, area); break;
            case GEMS: gems(c, w, h, r, u, area); break;
            default: rainbow(b, c, w, h, r, u, area); break;
        }
        return b;
    }

    // ------------------------------------------------------------------ helpers

    static int lerp(int a, int b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int alpha(int c, int a) {
        return (c & 0xFFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    private static Paint aa() {
        return new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /** Smooth value noise: a tiny random bitmap upscaled with bilinear filtering. */
    private static void noise(Canvas c, Random r, int w, int h, float cell, int ca, int cb, int alpha) {
        cell = Math.max(2f, cell);
        int nx = (int) (w / cell) + 3, ny = (int) (h / cell) + 3;
        int[] px = new int[nx * ny];
        for (int i = 0; i < px.length; i++) px[i] = lerp(ca, cb, r.nextFloat());
        Bitmap nb = Bitmap.createBitmap(px, nx, ny, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(alpha);
        c.drawBitmap(nb, null, new RectF(-cell, -cell, -cell + nx * cell, -cell + ny * cell), p);
    }

    private static void speckle(Canvas c, Random r, int w, int h, int n, float rmin, float rmax, int[] cols, int amin, int amax) {
        Paint p = aa();
        for (int i = 0; i < n; i++) {
            p.setColor(alpha(cols[r.nextInt(cols.length)], amin + r.nextInt(Math.max(1, amax - amin))));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, rmin + r.nextFloat() * (rmax - rmin), p);
        }
    }

    static void star4(Canvas c, float x, float y, float s, Paint p, Path path) {
        path.reset();
        path.moveTo(x, y - s);
        path.quadTo(x, y, x + s, y);
        path.quadTo(x, y, x, y + s);
        path.quadTo(x, y, x - s, y);
        path.quadTo(x, y, x, y - s);
        path.close();
        c.drawPath(path, p);
    }

    private static Path starPath(float cx, float cy, float R, float ri, int n, float rot) {
        Path p = new Path();
        for (int i = 0; i < n * 2; i++) {
            float rr = (i % 2 == 0) ? R : ri;
            double a = rot + Math.PI * i / n - Math.PI / 2;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    private static void sparkles(Canvas c, Random r, int w, int h, int n, float smin, float smax, int col) {
        Paint p = aa();
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            p.setColor(alpha(col, 150 + r.nextInt(105)));
            star4(c, r.nextFloat() * w, r.nextFloat() * h, smin + r.nextFloat() * (smax - smin), p, path);
        }
    }

    private static Path wavy(float x0, float y0, float x1, float y1, float amp, float freq, float ph, float step) {
        Path p = new Path();
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        float nx = -dy / len, ny = dx / len;
        int n = Math.max(2, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            float o = (float) (Math.sin(t * len * freq + ph) * amp + Math.sin(t * len * freq * 2.7 + ph * 1.7) * amp * 0.35);
            float x = x0 + dx * t + nx * o, y = y0 + dy * t + ny * o;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        return p;
    }

    static final class Vor {
        float[] e;
        int[] id;
        int n;
    }

    /** Jittered-grid Voronoi: e = F2 - F1 (small near cell edges), id = owning cell. */
    private static Vor voronoi(int w, int h, float cs, Random r) {
        int gx = (int) Math.ceil(w / cs) + 1, gy = (int) Math.ceil(h / cs) + 1;
        float[] fx = new float[gx * gy], fy = new float[gx * gy];
        for (int j = 0; j < gy; j++)
            for (int i = 0; i < gx; i++) {
                int k = j * gx + i;
                fx[k] = (i + 0.1f + 0.8f * r.nextFloat()) * cs;
                fy[k] = (j + 0.1f + 0.8f * r.nextFloat()) * cs;
            }
        Vor v = new Vor();
        v.e = new float[w * h];
        v.id = new int[w * h];
        v.n = gx * gy;
        for (int y = 0; y < h; y++) {
            int cy = (int) (y / cs);
            for (int x = 0; x < w; x++) {
                int cx = (int) (x / cs);
                float d1 = 1e18f, d2 = 1e18f;
                int best = 0;
                for (int dj = -1; dj <= 1; dj++) {
                    int jj = cy + dj;
                    if (jj < 0 || jj >= gy) continue;
                    for (int di = -1; di <= 1; di++) {
                        int ii = cx + di;
                        if (ii < 0 || ii >= gx) continue;
                        int kk = jj * gx + ii;
                        float ddx = fx[kk] - x, ddy = fy[kk] - y;
                        float d = ddx * ddx + ddy * ddy;
                        if (d < d1) {
                            d2 = d1;
                            d1 = d;
                            best = kk;
                        } else if (d < d2) d2 = d;
                    }
                }
                int o = y * w + x;
                v.e[o] = (float) (Math.sqrt(d2) - Math.sqrt(d1));
                v.id[o] = best;
            }
        }
        return v;
    }

    private static int hsv(float h, float s, float v) {
        h = ((h % 360) + 360) % 360 / 60f;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ------------------------------------------------------------------ textures

    private static void wood(Canvas c, int w, int h, Random r, float u) {
        c.drawColor(0xFFF5A623);
        int planks = 4 + r.nextInt(2);
        float pw = (float) w / planks;
        Paint p = aa();
        for (int i = 0; i < planks; i++) {
            p.setColor(lerp(0xFFFFB43B, 0xFFE8841A, r.nextFloat()));
            c.drawRect(i * pw, 0, (i + 1) * pw, h, p);
        }
        noise(c, r, w, h, 90 * u, 0xFFFFC75A, 0xFFD9741A, 70);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < planks; i++) {
            float x0 = i * pw;
            c.save();
            c.clipRect(x0, 0, x0 + pw, h);
            for (int g = 0; g < 22; g++) {
                float gx = x0 + r.nextFloat() * pw;
                float amp = (3 + r.nextFloat() * 9) * u;
                float freq = (0.004f + r.nextFloat() * 0.012f) / u;
                p.setStrokeWidth((1 + r.nextFloat() * 2.5f) * u);
                p.setColor(r.nextInt(3) == 0 ? 0x50FFE6A8 : 0x70A9480C);
                c.drawPath(wavy(gx, -10, gx, h + 10, amp, freq, r.nextFloat() * 6.28f, 5 * u), p);
            }
            int knots = r.nextInt(3);
            for (int k = 0; k < knots; k++) {
                float kx = x0 + pw * (0.25f + 0.5f * r.nextFloat()), ky = r.nextFloat() * h;
                for (int ring = 6; ring >= 0; ring--) {
                    float rx = (3 + ring * 3.6f) * u, ry = rx * 1.9f;
                    p.setStrokeWidth(2f * u);
                    p.setColor(ring == 0 ? 0xFF8A3A0C : 0x88A0400C);
                    c.drawOval(kx - rx, ky - ry, kx + rx, ky + ry, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF7A320A);
                c.drawOval(kx - 3 * u, ky - 6 * u, kx + 3 * u, ky + 6 * u, p);
                p.setStyle(Paint.Style.STROKE);
            }
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < planks; i++) {
            float x0 = i * pw;
            int joints = 1 + r.nextInt(2);
            for (int j = 0; j < joints; j++) {
                float jy = r.nextFloat() * h;
                p.setColor(0xFF7A3510);
                c.drawRect(x0, jy, x0 + pw, jy + 3.5f * u, p);
                p.setColor(0x55FFE6A8);
                c.drawRect(x0, jy + 3.5f * u, x0 + pw, jy + 5.5f * u, p);
                for (int s = 0; s < 2; s++) {
                    float nx = x0 + pw * (s == 0 ? 0.2f : 0.8f);
                    p.setColor(0xFF4A2208);
                    c.drawCircle(nx, jy - 8 * u, 4 * u, p);
                    c.drawCircle(nx, jy + 13 * u, 4 * u, p);
                    p.setColor(0xFFD7DDE4);
                    c.drawCircle(nx - u, jy - 9 * u, 2.6f * u, p);
                    c.drawCircle(nx - u, jy + 12 * u, 2.6f * u, p);
                }
            }
        }
        for (int i = 1; i < planks; i++) {
            float x = i * pw;
            p.setColor(0xFF6A2C0A);
            c.drawRect(x - 2.5f * u, 0, x + 2.5f * u, h, p);
            p.setColor(0x66FFE6A8);
            c.drawRect(x + 2.5f * u, 0, x + 4.5f * u, h, p);
        }
    }

    private static void dirt(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFF8B5A2B);
        noise(c, r, w, h, 120 * u, 0xFF6E431F, 0xFFA8713A, 255);
        noise(c, r, w, h, 40 * u, 0xFF5C3518, 0xFFB07A42, 120);
        noise(c, r, w, h, 12 * u, 0xFF4A2A12, 0xFFC08A52, 70);
        speckle(c, r, w, h, (int) (420 * area), 2 * u, 7 * u, new int[]{0xFF5A3417, 0xFF6E4520, 0xFFB98552}, 90, 170);
        Paint p = aa();
        // roots
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 10 * area + 2; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, a = r.nextFloat() * 6.28f, L = (60 + r.nextFloat() * 120) * u;
            p.setStrokeWidth((1.5f + r.nextFloat() * 2) * u);
            p.setColor(0x995A3010);
            c.drawPath(wavy(x, y, x + (float) Math.cos(a) * L, y + (float) Math.sin(a) * L, 6 * u, 0.05f / u, r.nextFloat() * 6f, 4 * u), p);
        }
        // worms
        p.setStrokeWidth(8 * u);
        for (int i = 0; i < 3; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, a = r.nextFloat() * 6.28f, L = (70 + r.nextFloat() * 40) * u;
            Path wp = wavy(x, y, x + (float) Math.cos(a) * L, y + (float) Math.sin(a) * L, 9 * u, 0.06f / u, r.nextFloat() * 6f, 3 * u);
            p.setColor(0xFF7A3A2A);
            p.setStrokeWidth(10 * u);
            c.drawPath(wp, p);
            p.setColor(0xFFFF8FA3);
            p.setStrokeWidth(7 * u);
            c.drawPath(wp, p);
        }
        // pebbles
        p.setStyle(Paint.Style.FILL);
        int[] pal = {0xFFFFD166, 0xFFEF476F, 0xFF06D6A0, 0xFF4CC9F0, 0xFFF8F9FA, 0xFFFF9F1C, 0xFFB388EB, 0xFFA0A4AC};
        int n = (int) (80 * area);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            float rx = (5 + r.nextFloat() * 11) * u, ry = rx * (0.55f + r.nextFloat() * 0.35f);
            c.save();
            c.rotate(r.nextFloat() * 180, x, y);
            p.setColor(0x66000000);
            c.drawOval(x - rx + 2 * u, y - ry + 3 * u, x + rx + 2 * u, y + ry + 3 * u, p);
            int col = pal[r.nextInt(pal.length)];
            p.setColor(shade(col, 0.75f));
            c.drawOval(x - rx, y - ry, x + rx, y + ry, p);
            p.setColor(col);
            c.drawOval(x - rx, y - ry, x + rx - 1.5f * u, y + ry - 1.5f * u, p);
            p.setColor(0x99FFFFFF);
            c.drawOval(x - rx * 0.6f, y - ry * 0.7f, x - rx * 0.05f, y - ry * 0.2f, p);
            c.restore();
        }
    }

    private static void water(Canvas c, int w, int h, Random r, float u, float area) {
        Paint p = aa();
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF14C8FF, 0xFF0560E8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        noise(c, r, w, h, 140 * u, 0xFF00D2FF, 0xFF0050D0, 110);
        noise(c, r, w, h, 50 * u, 0xFF30E0FF, 0xFF0040B0, 60);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        // caustics
        for (int i = 0; i < 70 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, a = r.nextFloat() * 6.28f, L = (40 + r.nextFloat() * 120) * u;
            p.setStrokeWidth((2 + r.nextFloat() * 3) * u);
            p.setColor(0x5590F0FF);
            c.drawPath(wavy(x, y, x + (float) Math.cos(a) * L, y + (float) Math.sin(a) * L, 8 * u, 0.05f / u, r.nextFloat() * 6, 4 * u), p);
        }
        // wave crests
        for (float y = 10 * u; y < h; y += 30 * u) {
            float x = -r.nextFloat() * 60 * u;
            while (x < w) {
                float ww = (26 + r.nextFloat() * 40) * u;
                float yy = y + (r.nextFloat() - 0.5f) * 10 * u;
                Path wp = new Path();
                wp.moveTo(x, yy);
                wp.quadTo(x + ww * 0.5f, yy - 10 * u, x + ww, yy);
                p.setStrokeWidth(4 * u);
                p.setColor(0x7700339A);
                c.save();
                c.translate(0, 3 * u);
                c.drawPath(wp, p);
                c.restore();
                p.setStrokeWidth(3.5f * u);
                p.setColor(0xDDFFFFFF);
                c.drawPath(wp, p);
                x += ww + (20 + r.nextFloat() * 60) * u;
            }
        }
        p.setStyle(Paint.Style.FILL);
        sparkles(c, r, w, h, (int) (60 * area), 4 * u, 10 * u, 0xFFFFFFFF);
    }

    private static void grass(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFF2FCB3F);
        noise(c, r, w, h, 100 * u, 0xFF1EAE30, 0xFF63F05A, 220);
        int[] pal = {0xFF1FA831, 0xFF3EE04F, 0xFF7CFC5A, 0xFF19932A, 0xFFA6FF6E, 0xFF2BC23C};
        Paint p = aa();
        Path blade = new Path();
        int n = (int) (9000 * area);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w;
            float y = (i / (float) n) * (h + 30 * u) + (r.nextFloat() - 0.5f) * 20 * u;
            float len = (10 + r.nextFloat() * 18) * u, lean = (r.nextFloat() - 0.5f) * 14 * u, bw = (2.5f + r.nextFloat() * 2.5f) * u;
            blade.reset();
            blade.moveTo(x - bw / 2, y);
            blade.quadTo(x + lean * 0.3f, y - len * 0.6f, x + lean, y - len);
            blade.quadTo(x + lean * 0.2f + bw * 0.3f, y - len * 0.5f, x + bw / 2, y);
            blade.close();
            p.setColor(pal[r.nextInt(pal.length)]);
            c.drawPath(blade, p);
        }
        int[] fl = {0xFFFFFFFF, 0xFFFFE135, 0xFFFF6EC7, 0xFFB98CFF, 0xFFFF5A5A};
        for (int i = 0; i < 45 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, pr = (3.5f + r.nextFloat() * 2) * u;
            p.setColor(fl[r.nextInt(fl.length)]);
            for (int k = 0; k < 5; k++) {
                double a = k * Math.PI * 2 / 5;
                c.drawCircle(x + (float) Math.cos(a) * pr, y + (float) Math.sin(a) * pr, pr, p);
            }
            p.setColor(0xFFFFB300);
            c.drawCircle(x, y, pr * 0.8f, p);
        }
    }

    private static void frosting(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFFFF8FC8);
        noise(c, r, w, h, 160 * u, 0xFFFF7AB8, 0xFFFFB3DA, 255);
        Paint p = aa();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 45 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rr = (20 + r.nextFloat() * 40) * u, st = r.nextFloat() * 360;
            p.setStrokeWidth(6 * u);
            p.setColor(0x33C0407A);
            c.drawArc(x - rr, y - rr + 4 * u, x + rr, y + rr + 4 * u, st, 140, false, p);
            p.setColor(0x66FFFFFF);
            c.drawArc(x - rr, y - rr, x + rr, y + rr, st, 140, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        int n = (int) (900 * area);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            float l = (10 + r.nextFloat() * 7) * u, sw = 4.2f * u;
            c.save();
            c.rotate(r.nextFloat() * 180, x, y);
            p.setColor(0x55000000);
            c.drawRoundRect(x - l / 2 + u, y - sw / 2 + 1.5f * u, x + l / 2 + u, y + sw / 2 + 1.5f * u, sw / 2, sw / 2, p);
            p.setColor(CANDY[r.nextInt(CANDY.length)]);
            c.drawRoundRect(x - l / 2, y - sw / 2, x + l / 2, y + sw / 2, sw / 2, sw / 2, p);
            p.setColor(0x88FFFFFF);
            c.drawRoundRect(x - l / 2 + sw * 0.4f, y - sw * 0.35f, x + l / 2 - sw * 0.4f, y - sw * 0.05f, sw * 0.2f, sw * 0.2f, p);
            c.restore();
        }
    }

    private static void lava(Bitmap b, Canvas c, int w, int h, Random r, float u, float area) {
        Vor v = voronoi(w, h, 72 * u, r);
        int[] crust = new int[v.n];
        for (int i = 0; i < v.n; i++) crust[i] = lerp(0xFF2A0E08, 0xFF55200F, r.nextFloat());
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            float e = v.e[i] / u;
            float glow = (float) Math.exp(-e / 6.5);
            int col = lerp(crust[v.id[i]], 0xFFFF4A00, glow);
            if (e < 4.5f) col = lerp(col, 0xFFFFE45C, (4.5f - e) / 2.8f);
            px[i] = col;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        noise(c, r, w, h, 26 * u, 0x00000000, 0x55000000, 255);
        noise(c, r, w, h, 140 * u, 0x00FF6A00, 0x40FF3000, 255);
        speckle(c, r, w, h, (int) (160 * area), 1.5f * u, 4 * u, new int[]{0xFFFFA000, 0xFFFFD040, 0xFFFF5A00}, 140, 255);
    }

    private static void ice(Bitmap b, Canvas c, int w, int h, Random r, float u, float area) {
        Vor v = voronoi(w, h, 110 * u, r);
        int[] tint = new int[v.n];
        for (int i = 0; i < v.n; i++) tint[i] = lerp(0xFFA0E6FF, 0xFFE8FCFF, r.nextFloat());
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            float e = v.e[i] / u;
            int t = tint[v.id[i]];
            int col;
            if (e < 1.8f) col = 0xFFFFFFFF;
            else if (e < 5f) col = lerp(0xFF5CC8F2, t, (e - 1.8f) / 3.2f);
            else col = lerp(t, shade(t, 0.93f), Math.min(1f, (e - 5) / 60f));
            px[i] = col;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        Paint p = aa();
        c.save();
        c.rotate(-28, w / 2f, h / 2f);
        for (int i = 0; i < 7; i++) {
            float y = r.nextFloat() * h * 1.4f - h * 0.2f, th = (10 + r.nextFloat() * 50) * u;
            p.setColor(alpha(0xFFFFFFFF, 30 + r.nextInt(40)));
            c.drawRect(-w, y, w * 2, y + th, p);
        }
        c.restore();
        speckle(c, r, w, h, (int) (900 * area), 0.6f * u, 2 * u, new int[]{0xFFFFFFFF}, 120, 255);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * u);
        for (int i = 0; i < 70 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rr = (2 + r.nextFloat() * 6) * u;
            p.setColor(0xAAFFFFFF);
            c.drawCircle(x, y, rr, p);
        }
        p.setStyle(Paint.Style.FILL);
        sparkles(c, r, w, h, (int) (25 * area), 5 * u, 12 * u, 0xFFFFFFFF);
    }

    private static void sand(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFFFFD36B);
        noise(c, r, w, h, 120 * u, 0xFFFFC34D, 0xFFFFE38F, 255);
        Paint p = aa();
        p.setStyle(Paint.Style.STROKE);
        float ph = r.nextFloat() * 6;
        for (float y = -10 * u; y < h + 20 * u; y += 17 * u) {
            ph += 0.4f + r.nextFloat() * 0.3f;
            Path wp = new Path();
            for (float x = -10 * u; x <= w + 10 * u; x += 6 * u) {
                float yy = y + (float) (Math.sin(x * 0.02 / u + ph) * 6 * u + Math.sin(x * 0.051 / u + ph * 2) * 2.5 * u);
                if (x <= -10 * u) wp.moveTo(x, yy);
                else wp.lineTo(x, yy);
            }
            p.setStrokeWidth(3 * u);
            p.setColor(0x70D99A2E);
            c.drawPath(wp, p);
            c.save();
            c.translate(0, -2.5f * u);
            p.setStrokeWidth(2 * u);
            p.setColor(0x80FFF3C4);
            c.drawPath(wp, p);
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        speckle(c, r, w, h, (int) (3500 * area), 0.5f * u, 1.6f * u, new int[]{0xFFC08030, 0xFFFFFFFF, 0xFFE07050, 0xFF9A7040}, 90, 200);
        int[] shell = {0xFFFF9EB5, 0xFFFFC9A8, 0xFFFFFFFF, 0xFFC7A6FF};
        for (int i = 0; i < 9 * area + 2; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = (10 + r.nextFloat() * 8) * u;
            c.save();
            c.rotate(r.nextFloat() * 360, x, y);
            p.setColor(0x55000000);
            c.drawArc(x - s + 2 * u, y - s + 3 * u, x + s + 2 * u, y + s + 3 * u, 180, 180, true, p);
            int col = shell[r.nextInt(shell.length)];
            p.setColor(col);
            c.drawArc(x - s, y - s, x + s, y + s, 180, 180, true, p);
            p.setColor(shade(col, 0.8f));
            c.drawRect(x - s * 0.3f, y, x + s * 0.3f, y + s * 0.3f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f * u);
            p.setColor(shade(col, 0.75f));
            for (int k = 1; k < 6; k++) {
                double a = Math.PI + Math.PI * k / 6;
                c.drawLine(x, y, x + (float) Math.cos(a) * s, y + (float) Math.sin(a) * s, p);
            }
            p.setStyle(Paint.Style.FILL);
            c.restore();
        }
        for (int i = 0; i < 4 * area + 2; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = (14 + r.nextFloat() * 8) * u;
            float rot = r.nextFloat() * 6;
            p.setColor(0x55000000);
            c.drawPath(starPath(x + 2 * u, y + 3 * u, s, s * 0.45f, 5, rot), p);
            p.setColor(0xFFFF7A3D);
            c.drawPath(starPath(x, y, s, s * 0.45f, 5, rot), p);
            p.setColor(0xFFFFC08A);
            for (int k = 0; k < 5; k++) {
                double a = rot + Math.PI * 2 * k / 5 - Math.PI / 2;
                c.drawCircle(x + (float) Math.cos(a) * s * 0.45f, y + (float) Math.sin(a) * s * 0.45f, 1.6f * u, p);
            }
        }
    }

    private static void brick(Canvas c, int w, int h, Random r, float u) {
        c.drawColor(0xFFF3E7D3);
        noise(c, r, w, h, 20 * u, 0xFFE6D6BA, 0xFFFFF6E6, 255);
        Paint p = aa();
        int[] pal = {0xFFE2483A, 0xFFD13C2F, 0xFFF0604A, 0xFFC53324, 0xFFE85A3B, 0xFFFF6F55};
        float bh = 30 * u, bwid = 66 * u, m = 4.5f * u;
        int row = 0;
        for (float y = -bh * 0.3f; y < h; y += bh, row++) {
            float off = (row % 2) * bwid / 2;
            for (float x = -off; x < w; x += bwid) {
                float l = x + m / 2, t = y + m / 2, rr = x + bwid - m / 2, bb = y + bh - m / 2;
                int col = pal[r.nextInt(pal.length)];
                p.setColor(shade(col, 0.72f));
                c.drawRoundRect(l, t, rr, bb, 3 * u, 3 * u, p);
                p.setColor(col);
                c.drawRoundRect(l, t, rr, bb - 3 * u, 3 * u, 3 * u, p);
                p.setColor(0x55FFFFFF);
                c.drawRoundRect(l + 2 * u, t + 1.5f * u, rr - 2 * u, t + 4 * u, 2 * u, 2 * u, p);
                for (int k = 0; k < 6; k++) {
                    p.setColor(r.nextBoolean() ? 0x44000000 : 0x33FFFFFF);
                    c.drawCircle(l + r.nextFloat() * (rr - l), t + r.nextFloat() * (bb - t), (0.8f + r.nextFloat() * 1.6f) * u, p);
                }
            }
        }
        noise(c, r, w, h, 30 * u, 0x00000000, 0x30000000, 255);
    }

    private static void slime(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFF9DFF2E);
        noise(c, r, w, h, 130 * u, 0xFF74E81A, 0xFFCDFF5E, 255);
        noise(c, r, w, h, 40 * u, 0xFF5ED40F, 0xFFE6FF8A, 90);
        Paint p = aa();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 28 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, a = r.nextFloat() * 6.28f, L = (80 + r.nextFloat() * 160) * u;
            Path wp = wavy(x, y, x + (float) Math.cos(a) * L, y + (float) Math.sin(a) * L, 14 * u, 0.03f / u, r.nextFloat() * 6, 4 * u);
            p.setStrokeWidth(9 * u);
            p.setColor(0x3336A800);
            c.drawPath(wp, p);
            p.setStrokeWidth(3 * u);
            p.setColor(0x55F4FFB0);
            c.drawPath(wp, p);
        }
        for (int i = 0; i < 170 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rr = (3 + r.nextFloat() * r.nextFloat() * 20) * u;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawCircle(x, y, rr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, rr * 0.12f));
            p.setColor(0x9946B800);
            c.drawCircle(x, y, rr, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xEEFFFFFF);
            c.drawCircle(x - rr * 0.38f, y - rr * 0.38f, rr * 0.22f, p);
        }
    }

    private static void galaxy(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFF12062E);
        noise(c, r, w, h, 220 * u, 0xFF12062E, 0xFF5B1A9E, 255);
        noise(c, r, w, h, 130 * u, 0x00FF2D95, 0xCCFF2D95, 150);
        noise(c, r, w, h, 95 * u, 0x0000E5FF, 0xAA00E5FF, 120);
        noise(c, r, w, h, 30 * u, 0x00000000, 0x66000000, 255);
        speckle(c, r, w, h, (int) (1400 * area), 0.5f * u, 1.8f * u, new int[]{0xFFFFFFFF, 0xFFFFF3C4, 0xFFC4E8FF}, 120, 255);
        Paint p = aa();
        int[] glow = {0xFFFF6EC7, 0xFF6EE7FF, 0xFFFFE66E, 0xFFFFFFFF};
        for (int i = 0; i < 60 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            int col = glow[r.nextInt(glow.length)];
            p.setColor(alpha(col, 60));
            c.drawCircle(x, y, 5 * u, p);
            p.setColor(col);
            c.drawCircle(x, y, 1.8f * u, p);
        }
        sparkles(c, r, w, h, (int) (16 * area), 8 * u, 16 * u, 0xFFFFFFFF);
        for (int i = 0; i < 2; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rr = (18 + r.nextFloat() * 16) * u;
            int col = glow[r.nextInt(3)];
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * u);
            p.setColor(0xCCFFFFFF);
            c.save();
            c.rotate(-20, x, y);
            c.drawOval(x - rr * 1.8f, y - rr * 0.45f, x + rr * 1.8f, y + rr * 0.45f, p);
            c.restore();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(x - rr * 0.4f, y - rr * 0.4f, rr * 1.4f, 0xFFFFFFFF, col, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rr, p);
            p.setShader(null);
        }
    }

    private static void chocolate(Canvas c, int w, int h, Random r, float u) {
        c.drawColor(0xFF4A2512);
        Paint p = aa();
        float s = 92 * u;
        for (float y = -s * 0.4f; y < h; y += s) {
            for (float x = -s * 0.25f; x < w; x += s) {
                float l = x + 3 * u, t = y + 3 * u, rr = x + s - 3 * u, bb = y + s - 3 * u;
                p.setColor(0xFF331709);
                c.drawRoundRect(l, t, rr, bb, 8 * u, 8 * u, p);
                p.setColor(0xFF9A5C34);
                c.drawRoundRect(l, t, rr - 4 * u, bb - 4 * u, 8 * u, 8 * u, p);
                p.setShader(new LinearGradient(l, t, rr, bb, 0xFF84492A, 0xFF5E3017, Shader.TileMode.CLAMP));
                c.drawRoundRect(l + 8 * u, t + 8 * u, rr - 9 * u, bb - 9 * u, 5 * u, 5 * u, p);
                p.setShader(null);
                p.setColor(0x26FFFFFF);
                c.save();
                c.clipRect(l + 8 * u, t + 8 * u, rr - 9 * u, bb - 9 * u);
                c.rotate(-35, l, t);
                c.drawRect(l - s, t + s * 0.15f, l + s * 2, t + s * 0.3f, p);
                c.restore();
            }
        }
        noise(c, r, w, h, 18 * u, 0x00000000, 0x22000000, 255);
    }

    private static void melon(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFFFF3B5C);
        noise(c, r, w, h, 90 * u, 0xFFFF2A50, 0xFFFF7088, 255);
        noise(c, r, w, h, 25 * u, 0xFFFF5070, 0xFFFF9AAE, 80);
        Paint p = aa();
        for (int i = 0; i < 500 * area; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, l = (4 + r.nextFloat() * 8) * u;
            p.setColor(r.nextBoolean() ? 0x44FFFFFF : 0x33B0102A);
            c.save();
            c.rotate(r.nextFloat() * 360, x, y);
            c.drawOval(x - l, y - l * 0.3f, x + l, y + l * 0.3f, p);
            c.restore();
        }
        Path seed = new Path();
        seed.moveTo(0, -1);
        seed.cubicTo(0.9f, -0.2f, 0.7f, 1, 0, 1);
        seed.cubicTo(-0.7f, 1, -0.9f, -0.2f, 0, -1);
        seed.close();
        for (float y = 20 * u; y < h; y += 64 * u) {
            for (float x = 15 * u; x < w; x += 58 * u) {
                float sx = x + (r.nextFloat() - 0.5f) * 30 * u, sy = y + (r.nextFloat() - 0.5f) * 30 * u;
                boolean pale = r.nextInt(6) == 0;
                c.save();
                c.translate(sx, sy);
                c.rotate((r.nextFloat() - 0.5f) * 60);
                c.scale((pale ? 4.5f : 6.5f) * u, (pale ? 7f : 10f) * u);
                p.setColor(pale ? 0xFFF5E6C8 : 0xFF1E1414);
                c.drawPath(seed, p);
                if (!pale) {
                    p.setColor(0x99FFFFFF);
                    c.drawOval(-0.4f, -0.5f, -0.05f, 0.1f, p);
                }
                c.restore();
            }
        }
    }

    private static void mint(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFFFFF7F7);
        Paint p = aa();
        c.save();
        c.rotate(35, w / 2f, h / 2f);
        float d = (float) Math.hypot(w, h);
        float sw = 70 * u;
        for (float x = -d; x < d * 2; x += sw * 2) {
            p.setColor(0xFFFF2A3D);
            c.drawRect(x, -d, x + sw, d * 2, p);
            p.setColor(0xFFFF8A96);
            c.drawRect(x + sw + sw * 0.42f, -d, x + sw + sw * 0.58f, d * 2, p);
            p.setColor(0x33FFFFFF);
            c.drawRect(x + sw * 0.15f, -d, x + sw * 0.3f, d * 2, p);
        }
        c.restore();
        noise(c, r, w, h, 70 * u, 0x00FFFFFF, 0x40FFFFFF, 255);
        speckle(c, r, w, h, (int) (600 * area), 0.6f * u, 1.8f * u, new int[]{0xFFFFFFFF}, 150, 255);
        sparkles(c, r, w, h, (int) (20 * area), 5 * u, 11 * u, 0xFFFFFFFF);
    }

    private static void mosaic(Bitmap b, Canvas c, int w, int h, Random r, float u) {
        Vor v = voronoi(w, h, 44 * u, r);
        int[] pal = {0xFF1FC8C0, 0xFF0FA3B1, 0xFF56E0D2, 0xFF2E86DE, 0xFFFFFFFF, 0xFF7FE7DC, 0xFF14B8A6, 0xFF0A7FA0};
        int[] cell = new int[v.n];
        for (int i = 0; i < v.n; i++) cell[i] = r.nextInt(14) == 0 ? 0xFFFFD23F : pal[r.nextInt(pal.length)];
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            float e = v.e[i] / u;
            int cc = cell[v.id[i]];
            if (e < 3f) px[i] = 0xFFF4F1E6;
            else if (e < 6f) px[i] = lerp(shade(cc, 0.78f), cc, (e - 3f) / 3f);
            else px[i] = cc;
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        noise(c, r, w, h, 14 * u, 0x00FFFFFF, 0x30FFFFFF, 255);
        noise(c, r, w, h, 120 * u, 0x00000000, 0x22000000, 255);
    }

    private static void gold(Canvas c, int w, int h, Random r, float u, float area) {
        c.drawColor(0xFFE09000);
        noise(c, r, w, h, 80 * u, 0xFFFFB800, 0xFFB86800, 255);
        Paint p = aa();
        int n = (int) (230 * area);
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w, y = (i / (float) n) * (h + 60 * u) - 30 * u;
            float rr = (22 + r.nextFloat() * 14) * u;
            c.save();
            c.translate(x, y);
            c.rotate(r.nextFloat() * 360);
            c.scale(1, 0.55f + r.nextFloat() * 0.45f);
            p.setColor(0x77000000);
            c.drawCircle(3 * u, 5 * u, rr, p);
            p.setColor(0xFFB06A00);
            c.drawCircle(0, 0, rr, p);
            p.setShader(new RadialGradient(-rr * 0.35f, -rr * 0.35f, rr * 1.3f,
                    new int[]{0xFFFFF5B0, 0xFFFFC21A, 0xFFE39A00}, null, Shader.TileMode.CLAMP));
            c.drawCircle(0, 0, rr * 0.9f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * u);
            p.setColor(0x99B87800);
            c.drawCircle(0, 0, rr * 0.68f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xBBFFE36E);
            c.drawPath(starPath(0, 0, rr * 0.45f, rr * 0.2f, 5, 0), p);
            c.restore();
        }
        sparkles(c, r, w, h, (int) (50 * area), 6 * u, 16 * u, 0xFFFFFFFF);
    }

    private static void gems(Canvas c, int w, int h, Random r, float u, float area) {
        Paint p = aa();
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF3B0E66, 0xFF120428, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        noise(c, r, w, h, 120 * u, 0x00FF2DB0, 0x55FF2DB0, 255);
        int[] pal = {0xFFFF1F4B, 0xFF00E676, 0xFF2979FF, 0xFFD500F9, 0xFFFFC400, 0xFFE0F7FF, 0xFFFF6D00, 0xFF00E5FF};
        int n = (int) (150 * area);
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w, y = (i / (float) n) * (h + 60 * u) - 30 * u;
            float R = (16 + r.nextFloat() * 18) * u;
            int sides = 5 + r.nextInt(4);
            float rot = r.nextFloat() * 6.28f;
            int col = pal[r.nextInt(pal.length)];
            float[] vx = new float[sides], vy = new float[sides];
            for (int k = 0; k < sides; k++) {
                double a = rot + Math.PI * 2 * k / sides;
                vx[k] = x + (float) Math.cos(a) * R;
                vy[k] = y + (float) Math.sin(a) * R;
            }
            p.setColor(0x88000000);
            path.reset();
            for (int k = 0; k < sides; k++) {
                if (k == 0) path.moveTo(vx[k] + 3 * u, vy[k] + 5 * u);
                else path.lineTo(vx[k] + 3 * u, vy[k] + 5 * u);
            }
            path.close();
            c.drawPath(path, p);
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                path.reset();
                path.moveTo(x, y);
                path.lineTo(vx[k], vy[k]);
                path.lineTo(vx[k2], vy[k2]);
                path.close();
                float f = 0.65f + 0.6f * (float) ((Math.sin(rot + k * 1.7) + 1) / 2);
                p.setColor(shade(col, f));
                c.drawPath(path, p);
            }
            path.reset();
            for (int k = 0; k < sides; k++) {
                float ix = x + (vx[k] - x) * 0.5f, iy = y + (vy[k] - y) * 0.5f;
                if (k == 0) path.moveTo(ix, iy);
                else path.lineTo(ix, iy);
            }
            path.close();
            p.setColor(lerp(col, 0xFFFFFFFF, 0.35f));
            c.drawPath(path, p);
            p.setColor(0xEEFFFFFF);
            star4(c, x - R * 0.3f, y - R * 0.3f, R * 0.35f, p, new Path());
        }
        sparkles(c, r, w, h, (int) (40 * area), 6 * u, 14 * u, 0xFFFFFFFF);
    }

    private static void rainbow(Bitmap b, Canvas c, int w, int h, Random r, float u, float area) {
        int[] px = new int[w * h];
        float cx = w * 0.5f, cy = h * 1.05f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / u;
                float hue = d * 0.55f + (float) Math.sin(x * 0.01 / u) * 10;
                px[y * w + x] = hsv(hue, 0.72f, 1f);
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        Paint p = aa();
        for (int i = 0; i < 6 * area + 3; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = (16 + r.nextFloat() * 14) * u;
            p.setColor(0x33000000);
            for (int k = 0; k < 5; k++) c.drawCircle(x + (k - 2) * s * 0.8f + 3 * u, y + (k % 2 == 0 ? 0 : -s * 0.5f) + 5 * u, s, p);
            p.setColor(0xFFFFFFFF);
            for (int k = 0; k < 5; k++) c.drawCircle(x + (k - 2) * s * 0.8f, y + (k % 2 == 0 ? 0 : -s * 0.5f), s, p);
        }
        sparkles(c, r, w, h, (int) (60 * area), 5 * u, 14 * u, 0xFFFFFFFF);
    }
}
