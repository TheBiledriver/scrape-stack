package com.biledriver.scrapestack;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Tool glyphs in unit space. Each tool is drawn vertically with its working end ("tip")
 * at y = -0.5 and its handle toward y = +0.5.
 */
final class ToolArt {
    static final int SCRAPER = 0, SPOON = 1, RAKE = 2, DRYER = 3, WASHER = 4, HAMMER = 5;
    private static final Path[] BODY = new Path[6], ACC = new Path[6];
    private static final float[] CX = new float[6], CY = new float[6];
    private static final int[] BODY_COL = {0xFFFF4D6D, 0xFFDDE5EE, 0xFFC9853F, 0xFFFF66C4, 0xFFFFD000, 0xFFC9853F};
    private static final int[] ACC_COL = {0xFFE6EEF7, 0xFFF5F9FF, 0xFFFFC233, 0xFF3A2A5C, 0xFF2A2A3A, 0xFF8A9BB3};
    private static final int INK = 0xFF2B0F45;

    static {
        for (int i = 0; i < 6; i++) {
            BODY[i] = new Path();
            ACC[i] = new Path();
        }
        // scraper / putty knife
        Path a = ACC[SCRAPER];
        a.moveTo(-0.27f, -0.5f);
        a.lineTo(0.27f, -0.5f);
        a.lineTo(0.12f, -0.1f);
        a.lineTo(-0.12f, -0.1f);
        a.close();
        rr(BODY[SCRAPER], -0.13f, -0.13f, 0.13f, -0.03f, 0.02f);
        rr(BODY[SCRAPER], -0.095f, -0.05f, 0.095f, 0.5f, 0.08f);
        // spoon
        ACC[SPOON].addOval(new RectF(-0.17f, -0.52f, 0.17f, -0.08f), Path.Direction.CW);
        rr(BODY[SPOON], -0.045f, -0.14f, 0.045f, 0.46f, 0.045f);
        BODY[SPOON].addOval(new RectF(-0.075f, 0.32f, 0.075f, 0.5f), Path.Direction.CW);
        // rake
        rr(ACC[RAKE], -0.33f, -0.33f, 0.33f, -0.25f, 0.03f);
        for (int i = -2; i <= 2; i++) {
            float x = i * 0.15f;
            rr(ACC[RAKE], x - 0.03f, -0.5f, x + 0.03f, -0.28f, 0.03f);
        }
        rr(BODY[RAKE], -0.035f, -0.3f, 0.035f, 0.5f, 0.035f);
        // blow dryer
        rr(ACC[DRYER], -0.13f, -0.5f, 0.13f, -0.34f, 0.03f);
        ACC[DRYER].addCircle(0, 0.16f, 0.08f, Path.Direction.CW);
        rr(BODY[DRYER], -0.18f, -0.38f, 0.18f, 0.28f, 0.16f);
        Path h = BODY[DRYER];
        h.moveTo(0.06f, 0.02f);
        h.lineTo(0.44f, 0.2f);
        h.lineTo(0.38f, 0.34f);
        h.lineTo(0.02f, 0.18f);
        h.close();
        // power washer
        rr(ACC[WASHER], -0.055f, -0.5f, 0.055f, -0.4f, 0.02f);
        rr(ACC[WASHER], -0.06f, 0.06f, 0.06f, 0.12f, 0.02f);
        rr(BODY[WASHER], -0.028f, -0.42f, 0.028f, 0.08f, 0.02f);
        rr(BODY[WASHER], -0.09f, 0.02f, 0.11f, 0.2f, 0.05f);
        Path g = BODY[WASHER];
        g.moveTo(0.0f, 0.15f);
        g.lineTo(0.12f, 0.15f);
        g.lineTo(0.22f, 0.48f);
        g.lineTo(0.08f, 0.5f);
        g.close();
        // hammer
        rr(ACC[HAMMER], -0.32f, -0.5f, 0.32f, -0.3f, 0.05f);
        rr(BODY[HAMMER], -0.05f, -0.33f, 0.05f, 0.5f, 0.04f);

        RectF b = new RectF(), b2 = new RectF();
        for (int i = 0; i < 6; i++) {
            BODY[i].computeBounds(b, true);
            ACC[i].computeBounds(b2, true);
            b.union(b2);
            CX[i] = b.centerX();
            CY[i] = b.centerY();
        }
    }

    private static void rr(Path p, float l, float t, float r, float b, float rad) {
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
    }

    /** Flat single-colour icon for the toolbar. */
    static void icon(Canvas c, int t, float cx, float cy, float s, int color, Paint p) {
        c.save();
        c.translate(cx, cy);
        c.rotate(t == DRYER ? -35 : 35);
        c.scale(s, s);
        c.translate(-CX[t], -CY[t]);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(BODY[t], p);
        c.drawPath(ACC[t], p);
        c.restore();
    }

    /** Full-colour tool drawn with its tip at (x, y), rotated by deg (0 = pointing up). */
    static void cursor(Canvas c, int t, float x, float y, float s, float deg, float a, int load, Paint p) {
        int al = (int) (255 * a);
        p.setStyle(Paint.Style.FILL);
        // soft drop shadow in screen space
        c.save();
        c.translate(x + s * 0.05f, y + s * 0.08f);
        c.rotate(deg);
        c.scale(s, s);
        c.translate(0, 0.5f);
        p.setColor(0xFF000000);
        p.setAlpha((int) (70 * a));
        c.drawPath(BODY[t], p);
        c.drawPath(ACC[t], p);
        c.restore();

        c.save();
        c.translate(x, y);
        c.rotate(deg);
        c.scale(s, s);
        c.translate(0, 0.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(0.05f);
        p.setColor(INK);
        p.setAlpha(al);
        c.drawPath(BODY[t], p);
        c.drawPath(ACC[t], p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(BODY_COL[t]);
        p.setAlpha(al);
        c.drawPath(BODY[t], p);
        p.setColor(ACC_COL[t]);
        p.setAlpha(al);
        c.drawPath(ACC[t], p);
        if (load != 0) {
            p.setColor(load);
            p.setAlpha(al);
            c.drawOval(-0.13f, -0.47f, 0.13f, -0.15f, p);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (110 * a));
            c.drawOval(-0.09f, -0.43f, -0.01f, -0.33f, p);
        }
        // little highlight on the working end
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (90 * a));
        c.drawRect(-0.02f, -0.48f, 0.02f, -0.4f, p);
        c.restore();
    }
}
