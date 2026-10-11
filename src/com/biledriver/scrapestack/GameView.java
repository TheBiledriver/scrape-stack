package com.biledriver.scrapestack;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GameView extends View {
    static final int LAYERS = 6;
    static final float CLEAR_AT = 0.955f;
    static final int T_SCRAPER = 0, T_SPOON = 1, T_RAKE = 2, T_DRYER = 3, T_WASHER = 4, T_HAMMER = 5, NT = 6;
    static final String[] TOOL_NAMES = {"SCRAPER", "SPOON", "RAKE", "BLOW DRYER", "POWER WASHER", "HAMMER"};
    static final int[] TOOL_COL = {0xFFFF4D6D, 0xFF4DA3FF, 0xFFFFB020, 0xFFFF66C4, 0xFF19D3C5, 0xFF9B5CFF};
    static final int[] PARTY = {0xFFFF4D6D, 0xFFFFD23F, 0xFF2EE6A6, 0xFF4DA3FF, 0xFFB06BFF, 0xFFFF8A3D, 0xFFFFFFFF};
    static final int[] TITLE_COL = {0xFFFFD23F, 0xFFFF5FA2, 0xFF2EE6A6, 0xFF4DA3FF, 0xFFFF8A3D, 0xFFB06BFF};
    static final int INK = 0xFF2B0F45;
    static final int SCR_MENU = 0, SCR_LOADING = 1, SCR_GAME = 2;

    static final int P_CHUNK = 0, P_DROP = 1, P_DUST = 2, P_SHARD = 3, P_RING = 4, P_CONFETTI = 5, P_SPARK = 6, P_STRAND = 7;
    static final int MAXP = 900;
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP],
            pl = new float[MAXP], pm = new float[MAXP], ps = new float[MAXP], pr = new float[MAXP], pvr = new float[MAXP];
    private final int[] pc = new int[MAXP], pt = new int[MAXP];
    private int pn;

    private final float dp;
    private final Sfx sfx;
    private final Haptics hap;
    private final SharedPreferences prefs;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Random rnd = new Random();
    private final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);

    private int W, H, bw, bh;
    private static final float K = 0.5f; // bitmap pixels per view pixel
    private final RectF full = new RectF();
    private float insetTop, insetBottom;
    private int screen = SCR_MENU;
    private long lastNs;
    private float time;

    // ---- stack state
    private long seed;
    private int stackNo, idx;
    private int[] types;
    private Bitmap top, below, scratch;
    private Canvas topCanvas, scratchCanvas;
    private Future<Bitmap> belowFuture;
    private int token;
    private boolean clearing, complete, covDirty, confirmOpen;
    private float clearT, completeT, coverage, covTimer, confirmA;
    private int[] covRow;

    // ---- tools / touch
    private int tool = T_SCRAPER;
    private boolean touching, blockUntilUp, homeDown;
    private int ptrId = -1;
    private float tx, ty, lx, ly, dirX = 0, dirY = -1, speed;
    private long lastEvtMs;
    private float spoonAcc, hammerAcc, matRecent, loopVol;
    private int loopStream;
    private float spoonLoadT = 9, hammerT = 9, cursorFade, shake;
    private int spoonLoadCol, sampleCol = 0xFFFFFFFF;
    private float bannerT = -1, toolToastT = -1;
    private String banner = "";

    // ---- paints & scratch geometry
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint topPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint lightPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint clrFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clrStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dstOut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pushPaint = new Paint();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), path2 = new Path(), tri = new Path();
    private final RectF rf = new RectF(), rf2 = new RectF();
    private final Rect ri = new Rect();

    // ---- layout rects
    private final RectF barRect = new RectF(), homeRect = new RectF(), lockRect = new RectF();

    // ---- screen lock (app pinning)
    private final MainActivity host;
    private boolean pinned, lockDown;
    private float pinPoll, lockToastT = -1, frameDt, uiA = 1, uiHold, holdT;
    private int holdTicks;
    private static final float HOLD_SECS = 2f;
    private String lockToast = "";
    private final RectF btnA = new RectF(), btnB = new RectF(), btnC = new RectF(), btnD = new RectF(), card = new RectF();
    private int pressedBtn = -1;

    // ---- menu
    private Bitmap menuA, menuB;
    private boolean menuGen;
    private final float[] bubX = new float[7], bubY = new float[7], bubR = new float[7], bubVX = new float[7], bubVY = new float[7];
    private boolean hasSave;
    private String saveDesc = "";

    public GameView(MainActivity a) {
        super(a);
        host = a;
        dp = getResources().getDisplayMetrics().density;
        prefs = a.getSharedPreferences("scrape", Context.MODE_PRIVATE);
        sfx = new Sfx(a);
        hap = new Haptics(a);
        sfx.enabled = prefs.getBoolean("sound", true);
        hap.enabled = prefs.getBoolean("buzz", true);
        setFocusable(true);

        clrFill.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clrFill.setStyle(Paint.Style.FILL);
        clrStroke.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clrStroke.setStyle(Paint.Style.STROKE);
        clrStroke.setStrokeCap(Paint.Cap.ROUND);
        clrStroke.setStrokeJoin(Paint.Join.ROUND);
        dstOut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        dstOut.setColor(0xFF000000);
        shadowPaint.setColorFilter(new PorterDuffColorFilter(0x78000000, PorterDuff.Mode.SRC_IN));
        lightPaint.setColorFilter(new PorterDuffColorFilter(0x55FFFFFF, PorterDuff.Mode.SRC_IN));
        tri.moveTo(0, -1);
        tri.lineTo(0.9f, 0.7f);
        tri.lineTo(-0.8f, 0.5f);
        tri.close();
        refreshSave();
    }

    // =================================================================== lifecycle

    void onHostPause() {
        stopLoop();
        touching = false;
        if (screen == SCR_GAME) saveState(true);
        sfx.pauseAll();
    }

    void onHostResume() {
        sfx.resumeAll();
        lastNs = 0;
        invalidate();
    }

    void release() {
        sfx.release();
        exec.shutdown();
    }

    boolean onBack() {
        if (screen == SCR_GAME) {
            if (confirmOpen) closeConfirm();
            else openConfirm();
            return true;
        }
        return screen == SCR_LOADING;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout dc = in.getDisplayCutout();
            if (dc != null) {
                insetTop = dc.getSafeInsetTop();
                insetBottom = dc.getSafeInsetBottom();
            }
        }
        return super.onApplyWindowInsets(in);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        int nbw = Math.max(1, (int) Math.ceil(w * K)), nbh = Math.max(1, (int) Math.ceil(h * K));
        if (top != null && (nbw != bw || nbh != bh)) {
            top = rescale(top, nbw, nbh);
            topCanvas = new Canvas(top);
            topCanvas.scale(K, K);
            if (below != null) below = rescale(below, nbw, nbh);
            scratch = null;
        }
        bw = nbw;
        bh = nbh;
        // stop the side edges from triggering the system back-swipe mid-scrape (Android caps this at 200dp per edge)
        if (Build.VERSION.SDK_INT >= 29) {
            int ew = (int) (40 * dp), eh = (int) (200 * dp), ey = Math.max(0, h / 2 - eh / 2);
            java.util.List<Rect> ex = new java.util.ArrayList<>();
            ex.add(new Rect(0, ey, ew, ey + eh));
            ex.add(new Rect(w - ew, ey, w, ey + eh));
            setSystemGestureExclusionRects(ex);
        }
        full.set(0, 0, bw / K, bh / K);
        for (int i = 0; i < bubX.length; i++) {
            bubR[i] = (40 + rnd.nextFloat() * 70) * dp;
            bubX[i] = rnd.nextFloat() * w;
            bubY[i] = rnd.nextFloat() * h;
            double a = rnd.nextFloat() * Math.PI * 2;
            float sp = (12 + rnd.nextFloat() * 22) * dp;
            bubVX[i] = (float) Math.cos(a) * sp;
            bubVY[i] = (float) Math.sin(a) * sp;
        }
        if (!menuGen && w > 0 && h > 0) {
            menuGen = true;
            final int mw = Math.max(1, w / 3), mh = Math.max(1, h / 3);
            final long s = rnd.nextLong();
            exec.submit(() -> {
                Random r = new Random(s);
                int a = r.nextInt(Textures.REGULAR);
                int b = Textures.contrasting(r, a);
                final Bitmap ba = Textures.generate(a, mw, mh, s);
                final Bitmap bb = Textures.generate(b, mw, mh, s + 1);
                post(() -> {
                    menuA = ba;
                    menuB = bb;
                });
            });
        }
    }

    private static Bitmap rescale(Bitmap b, int w, int h) {
        Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
        return s.isMutable() ? s : s.copy(Bitmap.Config.ARGB_8888, true);
    }

    // =================================================================== save / load

    private File topFile() {
        return new File(getContext().getFilesDir(), "top.png");
    }

    private static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static int[] split(String s) {
        try {
            String[] parts = s.split(",");
            int[] r = new int[parts.length];
            for (int i = 0; i < parts.length; i++) r[i] = Integer.parseInt(parts[i].trim());
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshSave() {
        hasSave = prefs.getBoolean("has", false);
        int[] t = split(prefs.getString("types", ""));
        if (t == null || t.length != LAYERS + 1) hasSave = false;
        if (hasSave) {
            int i = prefs.getInt("idx", 0), s = prefs.getInt("stack", 1);
            if (i >= LAYERS) saveDesc = "Stack " + s + " · treasure found!";
            else saveDesc = "Stack " + s + " · Layer " + (i + 1) + "/" + LAYERS + " · " + Textures.NAMES[t[i]];
        } else saveDesc = "no saved stack yet";
    }

    private void saveState(boolean sync) {
        if (types == null) return;
        prefs.edit().putBoolean("has", true).putLong("seed", seed).putInt("stack", stackNo)
                .putInt("idx", Math.min(idx, LAYERS)).putString("types", join(types)).apply();
        if (top != null && idx < LAYERS) {
            final File f = topFile();
            if (sync) writePng(top, f);
            else {
                final Bitmap copy = top.copy(Bitmap.Config.ARGB_8888, false);
                exec.submit(() -> writePng(copy, f));
            }
        }
        refreshSave();
    }

    private static void writePng(Bitmap b, File f) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream fos = new FileOutputStream(tmp);
            try {
                b.compress(Bitmap.CompressFormat.PNG, 100, fos);
            } finally {
                fos.close();
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f);
        } catch (Exception ignored) {
        }
    }

    private Bitmap loadTopPng(int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inMutable = true;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(topFile().getPath(), o);
            if (b == null) return null;
            if (b.getWidth() != w || b.getHeight() != h) b = rescale(b, w, h);
            if (!b.isMutable() || b.getConfig() != Bitmap.Config.ARGB_8888) b = b.copy(Bitmap.Config.ARGB_8888, true);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private long layerSeed(long s, int i) {
        return s * 31L + i * 1000003L + 17;
    }

    private void newStack() {
        stackNo = Math.max(stackNo, prefs.getInt("stack", 0)) + 1;
        seed = rnd.nextLong();
        types = Textures.pickStack(new Random(seed), LAYERS);
        idx = 0;
        startLoading(false);
    }

    private void resumeGame() {
        if (types != null && top != null) {
            screen = SCR_GAME;
            lastNs = 0;
            return;
        }
        int[] t = split(prefs.getString("types", ""));
        if (t == null || t.length != LAYERS + 1) {
            newStack();
            return;
        }
        types = t;
        seed = prefs.getLong("seed", 1);
        stackNo = prefs.getInt("stack", 1);
        idx = Math.max(0, Math.min(LAYERS, prefs.getInt("idx", 0)));
        startLoading(true);
    }

    private void startLoading(final boolean fromDisk) {
        screen = SCR_LOADING;
        final int my = ++token;
        clearing = false;
        complete = false;
        confirmOpen = false;
        confirmA = 0;
        coverage = 0;
        pn = 0;
        bannerT = -1;
        touching = false;
        stopLoop();
        if (belowFuture != null) belowFuture.cancel(false);
        belowFuture = null;
        final int[] ty = types;
        final long sd = seed;
        final int ix = idx, w = bw, h = bh;
        exec.submit(() -> {
            Bitmap t = null;
            if (fromDisk && ix < LAYERS) t = loadTopPng(w, h);
            if (t == null) t = Textures.generate(ty[ix], w, h, layerSeed(sd, ix));
            final Bitmap tt = t;
            final Bitmap b = ix < LAYERS ? Textures.generate(ty[ix + 1], w, h, layerSeed(sd, ix + 1)) : null;
            post(() -> {
                if (my != token) return;
                install(tt, b);
            });
        });
    }

    private void install(Bitmap t, Bitmap b) {
        top = t;
        topCanvas = new Canvas(top);
        topCanvas.scale(K, K);
        below = b;
        if (scratch == null || scratch.getWidth() != bw || scratch.getHeight() != bh) {
            scratch = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            scratchCanvas = new Canvas(scratch);
            scratchCanvas.scale(K, K);
        }
        complete = idx >= LAYERS;
        completeT = complete ? 3f : 0;
        covDirty = true;
        covTimer = 0;
        screen = SCR_GAME;
        lastNs = 0;
        saveState(false);
        sfx.play("whoosh", 0.5f, 1.1f);
    }

    private void requestBelow() {
        final int ty = types[idx + 1];
        final long s = layerSeed(seed, idx + 1);
        final int w = bw, h = bh;
        belowFuture = exec.submit(() -> Textures.generate(ty, w, h, s));
    }

    private void goMenu() {
        stopLoop();
        touching = false;
        confirmOpen = false;
        confirmA = 0;
        saveState(false);
        screen = SCR_MENU;
        pressedBtn = -1;
        sfx.play("whoosh", 0.6f, 0.9f);
    }

    private void openConfirm() {
        if (confirmOpen) return;
        confirmOpen = true;
        stopLoop();
        touching = false;
        pressedBtn = -1;
        sfx.play("whoosh", 0.5f, 1.25f);
        hap.pulse(14, 80, 0);
    }

    private void closeConfirm() {
        confirmOpen = false;
        pressedBtn = -1;
        sfx.play("pop", 0.6f, 0.9f);
    }

    // =================================================================== frame loop

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        time += dt;
        frameDt = dt;
        if (lockDown || homeDown) {
            holdT += dt;
            int ticks = (int) (holdT / (HOLD_SECS / 4));
            if (ticks > holdTicks && ticks < 4) {
                holdTicks = ticks;
                hap.pulse(10, 60 + 40 * ticks, 0);
                sfx.play("pop", 0.35f, 0.8f + 0.15f * ticks);
            }
            if (holdT >= HOLD_SECS) {
                if (lockDown) toggleLock();
                else openConfirm();
                lockDown = false;
                homeDown = false;
            }
        }
        if (W == 0) {
            postInvalidateOnAnimation();
            return;
        }
        if (screen == SCR_MENU) drawMenu(c, dt);
        else if (screen == SCR_LOADING) drawLoading(c);
        else {
            update(dt);
            drawGame(c);
        }
        postInvalidateOnAnimation();
    }

    private boolean canScrape() {
        return screen == SCR_GAME && top != null && !complete && !clearing && idx < LAYERS && !confirmOpen;
    }

    private void update(float dt) {
        if (below == null && belowFuture != null && belowFuture.isDone()) {
            try {
                below = belowFuture.get();
            } catch (Exception ignored) {
            }
            belowFuture = null;
        }
        if (touching && !blockUntilUp && canScrape()) {
            if (tool == T_DRYER) dryerStep(dt);
            else if (tool == T_WASHER) washerStep(dt);
        }
        updateLoop(dt);

        covTimer -= dt;
        if (covDirty && covTimer <= 0 && canScrape()) {
            covTimer = 0.12f;
            covDirty = false;
            coverage = computeCoverage();
            if (coverage >= CLEAR_AT) startClear();
        }
        if (clearing) {
            clearT += dt;
            if (clearT >= 0.5f) advanceLayer();
        }
        if (complete) {
            completeT += dt;
            if (rnd.nextFloat() < dt * 8)
                spawn(P_SPARK, rnd.nextFloat() * W, rnd.nextFloat() * H, 0, 0, 0.8f, (8 + rnd.nextFloat() * 12) * dp, 0xFFFFFFFF);
        }

        shake *= (float) Math.exp(-dt * 14);
        matRecent *= (float) Math.exp(-dt * 5);
        speed *= (float) Math.exp(-dt * 7);
        if (bannerT >= 0) {
            bannerT += dt;
            if (bannerT > 1.7f) bannerT = -1;
        }
        if (toolToastT >= 0) {
            toolToastT += dt;
            if (toolToastT > 1.3f) toolToastT = -1;
        }
        hammerT += dt;
        spoonLoadT += dt;
        if (touching) cursorFade = 1;
        else cursorFade = Math.max(0, cursorFade - dt * (tool == T_HAMMER ? 2.5f : 6f));
        if (touching && !blockUntilUp) uiHold = 0.45f;
        else uiHold -= dt;
        float uiTarget = uiHold > 0 ? 0 : 1;
        uiA += (uiTarget - uiA) * Math.min(1, dt * (uiTarget < uiA ? 14 : 7));
        float target = confirmOpen ? 1 : 0;
        confirmA += (target - confirmA) * Math.min(1, dt * 14);
        updateParticles(dt);
    }

    private float computeCoverage() {
        if (covRow == null || covRow.length != bw) covRow = new int[bw];
        int step = 5, total = 0, clear = 0;
        for (int y = 2; y < bh; y += step) {
            top.getPixels(covRow, 0, bw, 0, y, bw, 1);
            for (int x = 2; x < bw; x += step) {
                total++;
                if ((covRow[x] >>> 24) < 70) clear++;
            }
        }
        return total == 0 ? 1 : clear / (float) total;
    }

    private void startClear() {
        clearing = true;
        clearT = 0;
        blockUntilUp = touching;
        stopLoop();
        sfx.play("chime", 0.9f, 1f);
        if (idx + 1 < LAYERS) {
            hap.wave(new long[]{0, 30, 60, 30, 60, 50}, new int[]{0, 170, 0, 200, 0, 255});
            banner = "LAYER " + (idx + 1) + " CLEARED!";
            bannerT = 0;
        }
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float sp = (300 + rnd.nextFloat() * 800) * dp;
            spawn(P_CONFETTI, W / 2f, H * 0.42f, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 300 * dp,
                    2.2f + rnd.nextFloat(), (4 + rnd.nextFloat() * 3) * dp, PARTY[rnd.nextInt(PARTY.length)]);
        }
        for (int i = 0; i < 26; i++)
            spawn(P_SPARK, rnd.nextFloat() * W, rnd.nextFloat() * H, 0, 0, 0.5f + rnd.nextFloat() * 0.4f, (8 + rnd.nextFloat() * 14) * dp, 0xFFFFFFFF);
    }

    private void advanceLayer() {
        clearing = false;
        idx++;
        if (below == null) {
            if (belowFuture != null) {
                try {
                    below = belowFuture.get();
                } catch (Exception ignored) {
                }
                belowFuture = null;
            }
            if (below == null) below = Textures.generate(types[idx], bw, bh, layerSeed(seed, idx));
        }
        top = below;
        below = null;
        topCanvas = new Canvas(top);
        topCanvas.scale(K, K);
        coverage = 0;
        covDirty = true;
        if (idx < LAYERS) {
            requestBelow();
        } else {
            complete = true;
            completeT = 0;
            sfx.play("fanfare", 1f, 1f);
            hap.wave(new long[]{0, 40, 50, 40, 50, 40, 50, 140}, new int[]{0, 200, 0, 220, 0, 240, 0, 255});
            for (int i = 0; i < 170; i++)
                spawn(P_CONFETTI, rnd.nextFloat() * W, -20 * dp - rnd.nextFloat() * H * 0.4f, (rnd.nextFloat() - 0.5f) * 200 * dp,
                        (100 + rnd.nextFloat() * 300) * dp, 3.5f + rnd.nextFloat() * 1.5f, (4 + rnd.nextFloat() * 3) * dp, PARTY[rnd.nextInt(PARTY.length)]);
            saveState(false);
        }
    }

    // =================================================================== tools

    private float sampleMat(float x, float y, float r) {
        int hit = 0, col = 0;
        for (int i = 0; i < 6; i++) {
            double a = i * 1.047 + rnd.nextFloat();
            float rr = i == 0 ? 0 : r * (0.3f + 0.7f * rnd.nextFloat());
            int bx = (int) ((x + Math.cos(a) * rr) * K), by = (int) ((y + Math.sin(a) * rr) * K);
            if (bx < 0 || by < 0 || bx >= bw || by >= bh) continue;
            int c = top.getPixel(bx, by);
            if ((c >>> 24) > 110) {
                hit++;
                if (col == 0) col = c | 0xFF000000;
            }
        }
        if (col != 0) sampleCol = col;
        return hit / 6f;
    }

    private int jitter(int c) {
        return Textures.shade(c, 0.8f + rnd.nextFloat() * 0.4f);
    }

    private void toolDown(float x, float y) {
        tx = lx = x;
        ty = ly = y;
        spoonAcc = 0;
        hammerAcc = 0;
        speed = 0;
        cursorFade = 1;
        switch (tool) {
            case T_SCRAPER: scrapeSeg(x, y, x, y); break;
            case T_SPOON: scoop(x, y); break;
            case T_RAKE: rakeSeg(x, y, x, y); break;
            case T_HAMMER: smash(x, y); break;
            default: break;
        }
        startLoop();
    }

    private void toolMove(float x, float y) {
        if (!canScrape()) return;
        float dx = x - lx, dy = y - ly;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return;
        if (len > 2 * dp) {
            float nx = dx / len, ny = dy / len;
            dirX = dirX * 0.7f + nx * 0.3f;
            dirY = dirY * 0.7f + ny * 0.3f;
            float dl = (float) Math.hypot(dirX, dirY);
            if (dl < 1e-3f) {
                dirX = nx;
                dirY = ny;
            } else {
                dirX /= dl;
                dirY /= dl;
            }
        }
        switch (tool) {
            case T_SCRAPER: scrapeSeg(lx, ly, x, y); break;
            case T_SPOON:
                spoonAcc += len;
                if (spoonAcc >= 34 * dp) {
                    spoonAcc = 0;
                    scoop(x, y);
                }
                break;
            case T_RAKE: rakeSeg(lx, ly, x, y); break;
            case T_WASHER: washerSeg(lx, ly, x, y); break;
            case T_HAMMER:
                hammerAcc += len;
                if (hammerAcc >= 80 * dp) {
                    hammerAcc = 0;
                    smash(x, y);
                }
                break;
            default: break;
        }
        lx = x;
        ly = y;
        tx = x;
        ty = y;
    }

    private void toolUp() {
        touching = false;
        stopLoop();
    }

    private String loopName(int t) {
        switch (t) {
            case T_SCRAPER: return "scrape";
            case T_RAKE: return "rake";
            case T_DRYER: return "dryer";
            case T_WASHER: return "washer";
            default: return null;
        }
    }

    private void startLoop() {
        stopLoop();
        String n = loopName(tool);
        if (n != null) {
            loopStream = sfx.loop(n, 0f);
            loopVol = 0;
        }
    }

    private void stopLoop() {
        if (loopStream != 0) {
            sfx.stop(loopStream);
            loopStream = 0;
        }
    }

    private void updateLoop(float dt) {
        if (loopStream == 0) return;
        float target;
        switch (tool) {
            case T_DRYER: target = 0.45f + 0.45f * matRecent; break;
            case T_WASHER: target = 0.35f + 0.6f * matRecent; break;
            default: target = Math.min(1f, speed / (650 * dp)) * Math.min(1f, matRecent * 1.5f); break;
        }
        loopVol += (target - loopVol) * Math.min(1, dt * 18);
        sfx.setVolume(loopStream, loopVol);
        if (tool == T_SCRAPER || tool == T_RAKE) sfx.setRate(loopStream, 0.85f + 0.35f * Math.min(1, speed / (900 * dp)));
        else if (tool == T_DRYER) sfx.setRate(loopStream, 0.95f + 0.1f * matRecent);
    }

    /** Scraper: a hard-edged, slightly ragged stroke. */
    private void scrapeSeg(float x0, float y0, float x1, float y1) {
        float R = 21 * dp;
        float m = sampleMat(x1, y1, R);
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        clrStroke.setStrokeWidth(R * 1.8f);
        topCanvas.drawLine(x0, y0, x1, y1, clrStroke);
        int n = 1 + (int) (len / (5 * dp));
        for (int i = 0; i < n; i++) {
            float t = (i + rnd.nextFloat()) / n;
            float cx = x0 + (x1 - x0) * t, cy = y0 + (y1 - y0) * t;
            double a = rnd.nextFloat() * Math.PI * 2;
            float rr = R * (0.7f + 0.3f * rnd.nextFloat());
            topCanvas.drawCircle(cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, R * (0.2f + 0.25f * rnd.nextFloat()), clrFill);
        }
        covDirty = true;
        if (m > 0.05f) {
            matRecent = Math.max(matRecent, m);
            int cnt = Math.min(6, (int) (m * (1 + len / (7 * dp))) + (rnd.nextFloat() < m ? 1 : 0));
            for (int i = 0; i < cnt; i++) {
                spawn(P_CHUNK, x1 + (rnd.nextFloat() - 0.5f) * R * 1.6f, y1 + (rnd.nextFloat() - 0.5f) * R * 1.6f,
                        dirX * 180 * dp * rnd.nextFloat() + (rnd.nextFloat() - 0.5f) * 260 * dp,
                        dirY * 180 * dp * rnd.nextFloat() - (120 + rnd.nextFloat() * 280) * dp,
                        0.5f + rnd.nextFloat() * 0.4f, (2.5f + rnd.nextFloat() * 3.5f) * dp, jitter(sampleCol));
            }
            hap.tick((int) (55 + 100 * m));
        }
    }

    /** Spoon: carves out big, smooth blobs and flings chunky clods. */
    private void scoop(float x, float y) {
        float R = 46 * dp;
        float m = sampleMat(x, y, R * 0.7f);
        blob(path, x, y, R, 1.3f, (float) Math.atan2(dirY, dirX), 16, 0.16f, false);
        topCanvas.drawPath(path, clrFill);
        covDirty = true;
        sfx.playVar("scoop", 3, m > 0.05f ? 0.55f + 0.45f * m : 0.15f, 0.1f);
        if (m > 0.05f) {
            matRecent = Math.max(matRecent, m);
            hap.pulse(26, 120 + (int) (120 * m), 30);
            spoonLoadT = 0;
            spoonLoadCol = sampleCol;
            int cnt = 8 + (int) (12 * m);
            for (int i = 0; i < cnt; i++) {
                spawn(P_CHUNK, x + (rnd.nextFloat() - 0.5f) * R, y + (rnd.nextFloat() - 0.5f) * R,
                        (rnd.nextFloat() - 0.5f) * 700 * dp, -(300 + rnd.nextFloat() * 650) * dp,
                        0.8f + rnd.nextFloat() * 0.5f, (4 + rnd.nextFloat() * 8) * dp, jitter(sampleCol));
            }
            spawn(P_RING, x, y, 0, 0, 0.3f, R * 0.5f, 0xFFFFFFFF);
        }
    }

    /** Builds a closed blob (smooth or jagged) into p. */
    private void blob(Path out, float cx, float cy, float R, float elong, float ang, int n, float rough, boolean jagged) {
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float[] rr = new float[n];
        for (int i = 0; i < n; i++) rr[i] = 1 - rough + rough * 2 * rnd.nextFloat();
        float[] vx = new float[n], vy = new float[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            float r = jagged ? rr[i] : (rr[i] + rr[(i + 1) % n] + rr[(i + n - 1) % n]) / 3f;
            float lx0 = (float) Math.cos(a) * R * r * elong, ly0 = (float) Math.sin(a) * R * r;
            vx[i] = cx + lx0 * ca - ly0 * sa;
            vy[i] = cy + lx0 * sa + ly0 * ca;
        }
        out.reset();
        if (jagged) {
            for (int i = 0; i < n; i++) {
                if (i == 0) out.moveTo(vx[i], vy[i]);
                else out.lineTo(vx[i], vy[i]);
            }
        } else {
            out.moveTo((vx[n - 1] + vx[0]) / 2, (vy[n - 1] + vy[0]) / 2);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                out.quadTo(vx[i], vy[i], (vx[i] + vx[j]) / 2, (vy[i] + vy[j]) / 2);
            }
        }
        out.close();
    }

    /** Rake: five parallel tines that comb furrows through the layer. */
    private void rakeSeg(float x0, float y0, float x1, float y1) {
        float nx = -dirY, ny = dirX;
        float sp = 11 * dp, w = 6 * dp;
        float m = sampleMat(x1, y1, sp * 2.2f);
        clrStroke.setStrokeWidth(w);
        for (int i = -2; i <= 2; i++) {
            float ox = nx * sp * i, oy = ny * sp * i;
            topCanvas.drawLine(x0 + ox, y0 + oy, x1 + ox, y1 + oy, clrStroke);
            if (rnd.nextFloat() < 0.5f)
                topCanvas.drawCircle(x1 + ox + (rnd.nextFloat() - 0.5f) * 4 * dp, y1 + oy + (rnd.nextFloat() - 0.5f) * 4 * dp,
                        w * (0.4f + 0.4f * rnd.nextFloat()), clrFill);
        }
        covDirty = true;
        if (m > 0.05f) {
            matRecent = Math.max(matRecent, m);
            if (rnd.nextFloat() < 0.7f) {
                int i = rnd.nextInt(5) - 2;
                spawn(P_STRAND, x1 + nx * sp * i, y1 + ny * sp * i, -dirX * 160 * dp + (rnd.nextFloat() - 0.5f) * 120 * dp,
                        -dirY * 160 * dp - (100 + rnd.nextFloat() * 200) * dp, 0.6f + rnd.nextFloat() * 0.3f,
                        (4 + rnd.nextFloat() * 4) * dp, jitter(sampleCol));
            }
            hap.tick((int) (40 + 90 * m));
        }
    }

    /** Blow dryer: shoves material along the air cone (and off the screen edge), eroding it as it goes. */
    private void dryerStep(float dt) {
        float ang = (float) Math.atan2(dirY, dirX) + (float) Math.sin(time * 21) * 0.07f;
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang), nx = -dy, ny = dx;
        float ax = tx + dx * 12 * dp, ay = ty + dy * 12 * dp;
        float L = 145 * dp, w0 = 12 * dp, w1 = 60 * dp;
        path.reset();
        path.moveTo(ax + nx * w0, ay + ny * w0);
        path.lineTo(ax + dx * L + nx * w1, ay + dy * L + ny * w1);
        path.quadTo(ax + dx * (L + 24 * dp), ay + dy * (L + 24 * dp), ax + dx * L - nx * w1, ay + dy * L - ny * w1);
        path.lineTo(ax - nx * w0, ay - ny * w0);
        path.close();
        path.computeBounds(rf, true);
        ri.set((int) Math.floor(rf.left * K) - 1, (int) Math.floor(rf.top * K) - 1, (int) Math.ceil(rf.right * K) + 1, (int) Math.ceil(rf.bottom * K) + 1);
        if (!ri.intersect(0, 0, bw, bh)) return;

        int hit = 0, col = 0;
        for (int i = 0; i < 5; i++) {
            float t = 0.15f + rnd.nextFloat() * 0.85f, s = (rnd.nextFloat() * 2 - 1) * 0.8f;
            float wdt = w0 + (w1 - w0) * t;
            float sx = ax + dx * L * t + nx * wdt * s, sy = ay + dy * L * t + ny * wdt * s;
            int bx = (int) (sx * K), by = (int) (sy * K);
            if (bx < 0 || by < 0 || bx >= bw || by >= bh) continue;
            int cc = top.getPixel(bx, by);
            if ((cc >>> 24) > 110) {
                hit++;
                if (col == 0) col = cc | 0xFF000000;
                if (rnd.nextFloat() < 0.6f)
                    spawn(P_DUST, sx, sy, dx * (500 + rnd.nextFloat() * 500) * dp + nx * (rnd.nextFloat() - 0.5f) * 220 * dp,
                            dy * (500 + rnd.nextFloat() * 500) * dp + ny * (rnd.nextFloat() - 0.5f) * 220 * dp,
                            0.35f + rnd.nextFloat() * 0.3f, (2 + rnd.nextFloat() * 3.5f) * dp, jitter(cc | 0xFF000000));
            }
        }
        float m = hit / 5f;
        if (col != 0) sampleCol = col;

        float push = 380 * dp * dt;
        rf2.set(ri.left / K, ri.top / K, ri.right / K, ri.bottom / K);
        scratchCanvas.save();
        scratchCanvas.clipRect(rf2);
        scratchCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        scratchCanvas.clipPath(path);
        scratchCanvas.drawBitmap(top, null, full, null);
        scratchCanvas.restore();
        topCanvas.drawPath(path, clrFill);
        pushPaint.setAlpha((int) (255 * Math.exp(-dt * 2.4)));
        rf2.offset(dx * push, dy * push);
        topCanvas.drawBitmap(scratch, ri, rf2, pushPaint);
        dstOut.setAlpha((int) (255 * (1 - Math.exp(-dt * 6))));
        topCanvas.drawCircle(ax + dx * 18 * dp, ay + dy * 18 * dp, 16 * dp, dstOut);
        covDirty = true;
        if (m > 0.05f) {
            matRecent = Math.max(matRecent, m);
            hap.pulse(16, 45 + (int) (60 * m), 75);
        }
    }

    private void washerBlast(float x, float y, float strength) {
        dstOut.setAlpha((int) (255 * Math.min(1f, strength)));
        topCanvas.drawCircle(x, y, 19 * dp, dstOut);
    }

    /** Power washer: continuous blast while held - hold still to drill through, sweep to wash. */
    private void washerStep(float dt) {
        float m = sampleMat(tx, ty, 22 * dp);
        washerBlast(tx, ty, 1 - (float) Math.exp(-dt * 7));
        dstOut.setAlpha(110);
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float r = (18 + rnd.nextFloat() * 22) * dp;
            topCanvas.drawCircle(tx + (float) Math.cos(a) * r, ty + (float) Math.sin(a) * r, (1.5f + rnd.nextFloat() * 3) * dp, dstOut);
        }
        covDirty = true;
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float sp = (250 + rnd.nextFloat() * 500) * dp;
            spawn(P_DROP, tx + (rnd.nextFloat() - 0.5f) * 14 * dp, ty + (rnd.nextFloat() - 0.5f) * 14 * dp,
                    (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 200 * dp, 0.3f + rnd.nextFloat() * 0.3f,
                    (1.5f + rnd.nextFloat() * 2) * dp, rnd.nextBoolean() ? 0xFFFFFFFF : 0xFFBFF3FF);
        }
        if (m > 0.05f) {
            matRecent = Math.max(matRecent, m);
            if (rnd.nextFloat() < 0.8f) {
                double a = rnd.nextFloat() * Math.PI * 2;
                float sp = (200 + rnd.nextFloat() * 400) * dp;
                spawn(P_CHUNK, tx, ty, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 250 * dp,
                        0.4f + rnd.nextFloat() * 0.3f, (2 + rnd.nextFloat() * 2.5f) * dp, jitter(sampleCol));
            }
            hap.pulse(10, 35 + (int) (70 * m), 55);
        }
    }

    private void washerSeg(float x0, float y0, float x1, float y1) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, (int) (len / (6 * dp)));
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n;
            washerBlast(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 0.13f);
        }
        covDirty = true;
    }

    /** Hammer: smashes a jagged crater with cracks radiating out. */
    private void smash(float x, float y) {
        float R = 52 * dp;
        float m = sampleMat(x, y, R * 0.6f);
        blob(path, x, y, R * 0.75f, 1f, rnd.nextFloat() * 6.28f, 13, 0.35f, true);
        topCanvas.drawPath(path, clrFill);
        int nc = 6 + rnd.nextInt(4);
        for (int c = 0; c < nc; c++) {
            double a = c * Math.PI * 2 / nc + (rnd.nextFloat() - 0.5f) * 0.6f;
            float cx = x + (float) Math.cos(a) * R * 0.55f, cy = y + (float) Math.sin(a) * R * 0.55f;
            float len = R * (1.0f + 1.5f * rnd.nextFloat());
            int segs = 6;
            float w = 6 * dp;
            for (int s = 0; s < segs; s++) {
                a += (rnd.nextFloat() - 0.5f) * 0.7f;
                float sl = len / segs;
                float nx = cx + (float) Math.cos(a) * sl, ny = cy + (float) Math.sin(a) * sl;
                clrStroke.setStrokeWidth(Math.max(1.4f * dp, w * (1 - s / (float) segs)));
                topCanvas.drawLine(cx, cy, nx, ny, clrStroke);
                if (rnd.nextFloat() < 0.25f && s < segs - 2) {
                    double b = a + (rnd.nextBoolean() ? 0.8 : -0.8);
                    clrStroke.setStrokeWidth(Math.max(1.2f * dp, w * 0.5f * (1 - s / (float) segs)));
                    topCanvas.drawLine(nx, ny, nx + (float) Math.cos(b) * sl * 1.4f, ny + (float) Math.sin(b) * sl * 1.4f, clrStroke);
                }
                cx = nx;
                cy = ny;
            }
        }
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float d = R * (0.7f + 0.4f * rnd.nextFloat());
            float cx = x + (float) Math.cos(a) * d, cy = y + (float) Math.sin(a) * d, s = (6 + rnd.nextFloat() * 8) * dp;
            path2.reset();
            path2.moveTo(cx + s, cy);
            path2.lineTo(cx - s * 0.6f, cy + s * 0.8f);
            path2.lineTo(cx - s * 0.5f, cy - s * 0.9f);
            path2.close();
            topCanvas.drawPath(path2, clrFill);
        }
        covDirty = true;
        shake = 11 * dp;
        hammerT = 0;
        cursorFade = 1;
        sfx.playVar("hammer", 3, m > 0.05f ? 0.6f + 0.4f * m : 0.3f, 0.08f);
        hap.wave(new long[]{0, 38, 25, 22}, new int[]{0, 255, 0, 120});
        if (m > 0.05f) {
            int cnt = 10 + (int) (14 * m);
            for (int i = 0; i < cnt; i++) {
                double a = rnd.nextFloat() * Math.PI * 2;
                float sp = (300 + rnd.nextFloat() * 700) * dp;
                spawn(P_SHARD, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 350 * dp,
                        0.7f + rnd.nextFloat() * 0.5f, (4 + rnd.nextFloat() * 7) * dp, jitter(sampleCol));
            }
        }
        spawn(P_RING, x, y, 0, 0, 0.35f, R * 0.5f, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            spawn(P_DUST, x, y, (float) Math.cos(a) * 250 * dp, (float) Math.sin(a) * 250 * dp, 0.5f,
                    (6 + rnd.nextFloat() * 6) * dp, 0xCCFFFFFF);
        }
    }

    private void selectTool(int i) {
        if (i < 0 || i >= NT) return;
        tool = i;
        toolToastT = 0;
        sfx.play("pop", 0.7f, 0.75f + 0.12f * i);
        hap.pulse(12, 90, 0);
    }

    // =================================================================== particles

    private void spawn(int type, float x, float y, float vx, float vy, float life, float size, int col) {
        if (pn >= MAXP) return;
        int i = pn++;
        px[i] = x;
        py[i] = y;
        pvx[i] = vx;
        pvy[i] = vy;
        pl[i] = life;
        pm[i] = life;
        ps[i] = size;
        pc[i] = col;
        pt[i] = type;
        pr[i] = rnd.nextFloat() * 360;
        pvr[i] = (rnd.nextFloat() - 0.5f) * 720;
    }

    private void updateParticles(float dt) {
        for (int i = 0; i < pn; i++) {
            pl[i] -= dt;
            if (pl[i] <= 0) {
                int j = --pn;
                px[i] = px[j]; py[i] = py[j]; pvx[i] = pvx[j]; pvy[i] = pvy[j]; pl[i] = pl[j]; pm[i] = pm[j];
                ps[i] = ps[j]; pr[i] = pr[j]; pvr[i] = pvr[j]; pc[i] = pc[j]; pt[i] = pt[j];
                i--;
                continue;
            }
            float g = 0, drag = 0;
            switch (pt[i]) {
                case P_CHUNK: g = 2300; drag = 0.4f; break;
                case P_SHARD: g = 2100; drag = 0.3f; break;
                case P_DROP: g = 1800; drag = 0.8f; break;
                case P_STRAND: g = 1500; drag = 1f; break;
                case P_CONFETTI: g = 320; drag = 1.6f; break;
                case P_DUST: drag = 3.5f; break;
                default: break;
            }
            pvy[i] += g * dp * dt;
            float f = (float) Math.exp(-drag * dt);
            pvx[i] *= f;
            pvy[i] *= f;
            if (pt[i] == P_CONFETTI) pvx[i] += (float) Math.sin(time * 4 + i) * 80 * dp * dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            pr[i] += pvr[i] * dt;
        }
    }

    private void drawParticles(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < pn; i++) {
            float a = pl[i] / pm[i], s = ps[i];
            p.setColor(pc[i]);
            switch (pt[i]) {
                case P_CHUNK:
                    p.setAlpha((int) (255 * Math.min(1, a * 2.5f)));
                    c.save();
                    c.translate(px[i], py[i]);
                    c.rotate(pr[i]);
                    c.drawRect(-s, -s * 0.8f, s, s * 0.8f, p);
                    c.restore();
                    break;
                case P_SHARD:
                    p.setAlpha((int) (255 * Math.min(1, a * 2.5f)));
                    c.save();
                    c.translate(px[i], py[i]);
                    c.rotate(pr[i]);
                    c.scale(s, s);
                    c.drawPath(tri, p);
                    c.restore();
                    break;
                case P_DROP:
                    p.setAlpha((int) (255 * Math.min(1, a * 2)));
                    c.drawCircle(px[i], py[i], s * (0.4f + 0.6f * a), p);
                    break;
                case P_DUST:
                    p.setAlpha((int) (((pc[i] >>> 24) * 0.6f) * a));
                    c.drawCircle(px[i], py[i], s * (1 + (1 - a) * 1.8f), p);
                    break;
                case P_RING:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(5 * dp * a);
                    p.setAlpha((int) (220 * a));
                    c.drawCircle(px[i], py[i], s * (1 + (1 - a) * 2.2f), p);
                    p.setStyle(Paint.Style.FILL);
                    break;
                case P_CONFETTI:
                    p.setAlpha((int) (255 * Math.min(1, a * 3)));
                    c.save();
                    c.translate(px[i], py[i]);
                    c.rotate(pr[i]);
                    c.scale(1, (float) Math.cos(pr[i] * 0.05f));
                    c.drawRect(-s, -s * 0.55f, s, s * 0.55f, p);
                    c.restore();
                    break;
                case P_SPARK:
                    p.setAlpha(255);
                    Textures.star4(c, px[i], py[i], s * (float) Math.sin(Math.PI * a), p, path2);
                    break;
                case P_STRAND:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.6f * dp);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setAlpha((int) (255 * Math.min(1, a * 2.5f)));
                    c.drawArc(px[i] - s, py[i] - s, px[i] + s, py[i] + s, pr[i], 220, false, p);
                    p.setStyle(Paint.Style.FILL);
                    break;
                default:
                    break;
            }
        }
    }

    // =================================================================== game drawing

    private void drawGame(Canvas c) {
        float sx = 0, sy = 0;
        if (shake > 0.3f) {
            sx = (rnd.nextFloat() - 0.5f) * 2 * shake;
            sy = (rnd.nextFloat() - 0.5f) * 2 * shake;
        }
        c.save();
        c.translate(sx, sy);
        c.drawColor(0xFF120428);
        if (below != null) c.drawBitmap(below, null, full, bmpPaint);
        else if (types != null && idx + 1 < types.length) c.drawColor(Textures.BASE[types[idx + 1]]);
        if (top != null) {
            if (idx < LAYERS) {
                float fa = clearing ? Math.max(0, 1 - clearT / 0.45f) : 1;
                int al = (int) (255 * fa);
                c.save();
                if (clearing) {
                    float sc = 1 + 0.06f * (1 - fa);
                    c.scale(sc, sc, W / 2f, H / 2f);
                }
                shadowPaint.setAlpha(al);
                lightPaint.setAlpha(al);
                topPaint.setAlpha(al);
                rf.set(full);
                rf.offset(3.5f * dp, 6 * dp);
                c.drawBitmap(top, null, rf, shadowPaint);
                rf.set(full);
                rf.offset(-1.5f * dp, -1.5f * dp);
                c.drawBitmap(top, null, rf, lightPaint);
                c.drawBitmap(top, null, full, topPaint);
                c.restore();
            } else {
                c.drawBitmap(top, null, full, bmpPaint);
            }
        }
        drawParticles(c);
        if (!complete) drawCursor(c);
        c.restore();

        // the GUI fades away while scraping so nothing covers the layer
        if (uiA > 0.01f) {
            int layer = c.saveLayerAlpha(0, 0, W, H, (int) (255 * uiA));
            drawHud(c);
            drawLock(c, frameDt);
            if (!complete) drawToolbar(c);
            c.restoreToCount(layer);
        }
        if (bannerT >= 0) drawBanner(c);
        if (complete) drawComplete(c);
        if (confirmA > 0.01f) drawConfirm(c);
    }

    private void drawCursor(Canvas c) {
        if (cursorFade <= 0.01f) return;
        float a = Math.min(1f, cursorFade);
        float deg = (float) Math.toDegrees(Math.atan2(dirX, -dirY));
        switch (tool) {
            case T_DRYER: {
                double ang = Math.atan2(dirY, dirX);
                float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang), nx = -dy, ny = dx;
                if (touching) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(2.5f * dp);
                    for (int i = 0; i < 14; i++) {
                        float t = (time * 2.6f + i / 14f) % 1f;
                        float lat = ((i * 0.618f) % 1f) * 2 - 1;
                        float wdt = 12 * dp + 48 * dp * t;
                        float bx = tx + dx * (16 * dp + 150 * dp * t) + nx * wdt * lat * 0.8f;
                        float by = ty + dy * (16 * dp + 150 * dp * t) + ny * wdt * lat * 0.8f;
                        p.setColor(0xFFFFFFFF);
                        p.setAlpha((int) (190 * a * (1 - t) * Math.min(1, t * 5)));
                        float sl = 22 * dp * (0.5f + t);
                        c.drawLine(bx, by, bx + dx * sl, by + dy * sl, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                }
                ToolArt.cursor(c, T_DRYER, tx - dx * 2 * dp, ty - dy * 2 * dp, 96 * dp, deg, a, 0, p);
                break;
            }
            case T_HAMMER: {
                float s = 100 * dp, base = -30;
                float swing;
                if (hammerT < 0.06f) swing = -50 + 50 * (hammerT / 0.06f);
                else swing = -40 * Math.min(1f, (hammerT - 0.06f) / 0.3f);
                float raise = -swing / 50f;
                double br = Math.toRadians(base);
                float pivX = tx + (float) -Math.sin(br) * s * 0.95f, pivY = ty + (float) Math.cos(br) * s * 0.95f;
                c.save();
                c.rotate(swing, pivX, pivY);
                float sc = 1 + 0.22f * raise;
                c.scale(sc, sc, pivX, pivY);
                ToolArt.cursor(c, T_HAMMER, tx, ty, s, base, a, 0, p);
                c.restore();
                break;
            }
            case T_WASHER: {
                if (touching) {
                    p.setColor(0xFFFFFFFF);
                    p.setAlpha((int) (70 + 40 * Math.sin(time * 40)));
                    c.drawCircle(tx, ty, 22 * dp, p);
                    p.setAlpha(150);
                    c.drawCircle(tx, ty, 10 * dp, p);
                }
                ToolArt.cursor(c, T_WASHER, tx - dirX * 18 * dp, ty - dirY * 18 * dp, 92 * dp, deg, a, 0, p);
                break;
            }
            case T_RAKE:
                ToolArt.cursor(c, T_RAKE, tx, ty, 86 * dp, deg + 180, a, 0, p);
                break;
            case T_SPOON:
                ToolArt.cursor(c, T_SPOON, tx, ty, 90 * dp, deg, a, spoonLoadT < 0.45f ? spoonLoadCol : 0, p);
                break;
            default:
                ToolArt.cursor(c, T_SCRAPER, tx, ty, 86 * dp, deg, a, 0, p);
                break;
        }
    }

    private float topY() {
        return Math.max(insetTop, 0) + 14 * dp;
    }

    /** Padlock button in the top-right corner: pins the app so system gestures can't knock you out of it. */
    private void drawLock(Canvas c, float dt) {
        pinPoll -= dt;
        if (pinPoll <= 0) {
            pinPoll = 0.4f;
            pinned = host.isPinned();
        }
        float y0 = topY();
        float hr = 21 * dp, hx = W - 14 * dp - hr, hy = y0 + hr;
        lockRect.set(hx - hr - 8 * dp, hy - hr - 8 * dp, hx + hr + 8 * dp, hy + hr + 8 * dp);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawCircle(hx + 1.5f * dp, hy + 2.5f * dp, hr, p);
        p.setColor(pinned ? 0xFFFFD23F : (lockDown ? 0xEE3A2460 : 0xCC201436));
        c.drawCircle(hx, hy, hr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(pinned ? 2.5f * dp : 1.5f * dp);
        p.setColor(pinned ? INK : 0x55FFFFFF);
        c.drawCircle(hx, hy, hr, p);
        int ic = pinned ? INK : 0xFFFFFFFF;
        // shackle: closed when pinned, swung open when not
        p.setStrokeWidth(2.8f * dp);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(ic);
        float lift = pinned ? 0 : 4 * dp, shift = pinned ? 0 : 5 * dp;
        rf.set(hx - 6 * dp + shift, hy - 12 * dp - lift, hx + 6 * dp + shift, hy + 0 * dp - lift);
        c.drawArc(rf, 180, 180, false, p);
        c.drawLine(hx + 6 * dp + shift, hy - 6 * dp - lift, hx + 6 * dp + shift, hy - 1 * dp, p);
        if (pinned) c.drawLine(hx - 6 * dp, hy - 6 * dp, hx - 6 * dp, hy - 1 * dp, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(hx - 9 * dp, hy - 2 * dp, hx + 9 * dp, hy + 10 * dp, 2.5f * dp, 2.5f * dp, p);
        p.setColor(pinned ? 0xFFFFD23F : 0xFF201436);
        c.drawCircle(hx, hy + 3.5f * dp, 2 * dp, p);
        if (lockDown) drawHoldRing(c, hx, hy, hr);

        if (lockToastT >= 0) {
            lockToastT += dt;
            if (lockToastT > 2.4f) lockToastT = -1;
            else {
                float a = lockToastT < 0.15f ? lockToastT / 0.15f : (lockToastT > 2f ? 1 - (lockToastT - 2f) / 0.4f : 1);
                outlined(c, lockToast, W / 2f, y0 + 92 * dp, 17 * dp, Textures.alpha(0xFFFFD23F, (int) (255 * a)),
                        Textures.alpha(INK, (int) (255 * a)), 5 * dp, black);
            }
        }
    }

    /** Ring around a held button that fills clockwise over HOLD_SECS. */
    private void drawHoldRing(Canvas c, float cx, float cy, float r) {
        float f = Math.min(1f, holdT / HOLD_SECS);
        float rr = r + 6 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(6 * dp);
        p.setColor(0x88000000);
        c.drawCircle(cx, cy, rr, p);
        p.setStrokeWidth(4.5f * dp);
        p.setColor(0xFFFFD23F);
        rf.set(cx - rr, cy - rr, cx + rr, cy + rr);
        c.drawArc(rf, -90, 360 * f, false, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void toggleLock() {
        boolean on = !host.isPinned();
        host.setPinned(on);
        pinPoll = 0.3f;
        lockToast = on ? "SCREEN LOCKED · hold the lock to free it" : "SCREEN UNLOCKED";
        lockToastT = 0;
        sfx.play("pop", 0.8f, on ? 0.8f : 1.2f);
        hap.pulse(20, 150, 0);
    }

    private void drawHud(Canvas c) {
        float y0 = topY();
        float hr = 21 * dp, hx = 14 * dp + hr, hy = y0 + hr;
        homeRect.set(hx - hr - 8 * dp, hy - hr - 8 * dp, hx + hr + 8 * dp, hy + hr + 8 * dp);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawCircle(hx + 1.5f * dp, hy + 2.5f * dp, hr, p);
        p.setColor(homeDown ? 0xEE3A2460 : 0xCC201436);
        c.drawCircle(hx, hy, hr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        p.setColor(0x55FFFFFF);
        c.drawCircle(hx, hy, hr, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(hx - 10 * dp, hy - 0.5f * dp);
        path.lineTo(hx, hy - 10 * dp);
        path.lineTo(hx + 10 * dp, hy - 0.5f * dp);
        path.close();
        c.drawPath(path, p);
        c.drawRoundRect(hx - 7 * dp, hy - 3 * dp, hx + 7 * dp, hy + 8.5f * dp, 1.5f * dp, 1.5f * dp, p);
        p.setColor(0xFF201436);
        c.drawRoundRect(hx - 2.2f * dp, hy + 2 * dp, hx + 2.2f * dp, hy + 8.5f * dp, 1 * dp, 1 * dp, p);
        if (homeDown) drawHoldRing(c, hx, hy, hr);

        if (types == null) return;
        String s = idx < LAYERS ? "LAYER " + (idx + 1) + "/" + LAYERS + "  ·  " + Textures.NAMES[types[idx]].toUpperCase()
                : "STACK " + stackNo + " CLEARED";
        tp.setTypeface(bold);
        tp.setTextSize(13 * dp);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setStyle(Paint.Style.FILL);
        float tw = tp.measureText(s);
        float pw = Math.max(tw + 36 * dp, 170 * dp), ph = 40 * dp;
        float cx = W / 2f;
        rf.set(cx - pw / 2, y0, cx + pw / 2, y0 + ph);
        p.setColor(0x55000000);
        c.drawRoundRect(rf.left + 1.5f * dp, rf.top + 2.5f * dp, rf.right + 1.5f * dp, rf.bottom + 2.5f * dp, ph / 2, ph / 2, p);
        p.setColor(0xCC201436);
        c.drawRoundRect(rf, ph / 2, ph / 2, p);
        tp.setColor(0xFFFFFFFF);
        c.drawText(s, cx, y0 + 18 * dp, tp);
        if (idx < LAYERS) {
            float bl = rf.left + 18 * dp, br = rf.right - 18 * dp, by = y0 + 27 * dp;
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(bl, by, br, by + 5 * dp, 2.5f * dp, 2.5f * dp, p);
            float f = clearing ? 1 : Math.min(1f, coverage / CLEAR_AT);
            if (f > 0.01f) {
                p.setShader(new LinearGradient(bl, 0, br, 0, 0xFF7CFF4F, 0xFF2EE6A6, Shader.TileMode.CLAMP));
                c.drawRoundRect(bl, by, bl + (br - bl) * f, by + 5 * dp, 2.5f * dp, 2.5f * dp, p);
                p.setShader(null);
            }
        }
        // stack dots: one per layer in its own colour
        float dy = y0 + ph + 10 * dp, sp = 13 * dp;
        float dx0 = cx - sp * LAYERS / 2f;
        for (int i = 0; i <= LAYERS; i++) {
            float dxx = dx0 + i * sp;
            boolean cur = i == idx;
            float r = cur ? 5.5f * dp : 4 * dp;
            p.setColor(0x66000000);
            c.drawCircle(dxx, dy + 1 * dp, r + 1.5f * dp, p);
            p.setColor(i == LAYERS ? 0xFFFFD23F : Textures.BASE[types[i]]);
            p.setAlpha(i < idx ? 90 : 255);
            if (i == LAYERS && idx < LAYERS) {
                p.setColor(0xFFFFFFFF);
                p.setAlpha(150);
                Textures.star4(c, dxx, dy, r * 1.4f, p, path2);
            } else c.drawCircle(dxx, dy, r, p);
            if (cur) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.8f * dp);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(dxx, dy, r + 1.8f * dp, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawToolbar(Canvas c) {
        float m = 12 * dp, h = 70 * dp;
        float bottom = H - Math.max(insetBottom, 0) - 16 * dp;
        barRect.set(m, bottom - h, W - m, bottom);
        float rad = 26 * dp;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(barRect.left + 2 * dp, barRect.top + 4 * dp, barRect.right + 2 * dp, barRect.bottom + 4 * dp, rad, rad, p);
        p.setColor(0xDD1B1030);
        c.drawRoundRect(barRect, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(barRect, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
        float slot = barRect.width() / NT;
        for (int i = 0; i < NT; i++) {
            float cx = barRect.left + slot * (i + 0.5f), cy = barRect.centerY();
            if (i == tool) {
                float pulse = 1 + 0.04f * (float) Math.sin(time * 5);
                p.setColor(TOOL_COL[i]);
                c.drawCircle(cx, cy, 25 * dp * pulse, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * dp);
                p.setColor(0xCCFFFFFF);
                c.drawCircle(cx, cy, 25 * dp * pulse, p);
                p.setStyle(Paint.Style.FILL);
            }
            ToolArt.icon(c, i, cx, cy, 34 * dp, i == tool ? 0xFFFFFFFF : 0xB3FFFFFF, p);
        }
        if (toolToastT >= 0) {
            float a = toolToastT < 0.15f ? toolToastT / 0.15f : (toolToastT > 1f ? Math.max(0, 1 - (toolToastT - 1f) / 0.3f) : 1);
            float lift = (1 - Math.min(1, toolToastT / 0.15f)) * 10 * dp;
            outlined(c, TOOL_NAMES[tool], W / 2f, barRect.top - 16 * dp + lift, 20 * dp, Textures.alpha(TOOL_COL[tool], (int) (255 * a)),
                    Textures.alpha(INK, (int) (255 * a)), 6 * dp, black);
        }
    }

    private void drawBanner(Canvas c) {
        float t = bannerT;
        float sc = t < 0.3f ? easeOutBack(t / 0.3f) : 1;
        float a = t > 1.3f ? Math.max(0, 1 - (t - 1.3f) / 0.4f) : 1;
        c.save();
        c.translate(W / 2f, H * 0.42f);
        c.rotate(-4);
        c.scale(sc, sc);
        float size = Math.min(40 * dp, W * 0.09f);
        outlined(c, banner, 0, 0, size, Textures.alpha(0xFFFFE14D, (int) (255 * a)), Textures.alpha(INK, (int) (255 * a)), size * 0.2f, black);
        c.restore();
    }

    private void drawComplete(Canvas c) {
        float a = Math.max(0, Math.min(1, (completeT - 0.6f) / 0.5f));
        if (a <= 0) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        p.setAlpha((int) (90 * a));
        c.drawRect(0, 0, W, H, p);
        float size = Math.min(54 * dp, W * 0.12f);
        c.save();
        float sc = 0.6f + 0.4f * easeOutBack(a);
        c.scale(sc, sc, W / 2f, H * 0.3f);
        wavyTitle(c, "STACK", W / 2f, H * 0.27f, size, 0, (int) (255 * a));
        wavyTitle(c, "CLEARED!", W / 2f, H * 0.27f + size * 1.1f, size, 2, (int) (255 * a));
        c.restore();
        String found = "You found: " + Textures.NAMES[types[LAYERS]].toUpperCase();
        outlined(c, found, W / 2f, H * 0.27f + size * 2.1f, 18 * dp, Textures.alpha(0xFFFFFFFF, (int) (255 * a)),
                Textures.alpha(INK, (int) (255 * a)), 5 * dp, black);
        float bw2 = Math.min(W - 64 * dp, 300 * dp);
        btnA.set(W / 2f - bw2 / 2, H * 0.58f, W / 2f + bw2 / 2, H * 0.58f + 70 * dp);
        btnB.set(W / 2f - bw2 / 2 + 40 * dp, btnA.bottom + 24 * dp, W / 2f + bw2 / 2 - 40 * dp, btnA.bottom + 24 * dp + 54 * dp);
        c.save();
        c.translate(0, (1 - a) * 60 * dp);
        drawButton(c, btnA, 0xFF2EE6A6, "NEXT STACK", null, pressedBtn == 0, true);
        drawButton(c, btnB, 0xFF7B5CFF, "MENU", null, pressedBtn == 1, true);
        c.restore();
    }

    private void drawConfirm(Canvas c) {
        float a = confirmA;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0A0418);
        p.setAlpha((int) (160 * a));
        c.drawRect(0, 0, W, H, p);
        float cw = Math.min(W - 48 * dp, 340 * dp), ch = 244 * dp;
        card.set(W / 2f - cw / 2, H / 2f - ch / 2, W / 2f + cw / 2, H / 2f + ch / 2);
        c.save();
        float sc = 0.8f + 0.2f * easeOutBack(Math.min(1, a));
        c.scale(sc, sc, W / 2f, H / 2f);
        int al = (int) (255 * Math.min(1, a * 1.5f));
        p.setColor(0xFF000000);
        p.setAlpha(al / 3);
        c.drawRoundRect(card.left, card.top + 8 * dp, card.right, card.bottom + 8 * dp, 28 * dp, 28 * dp, p);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(al);
        c.drawRoundRect(card, 28 * dp, 28 * dp, p);
        p.setColor(0xFFFFD23F);
        p.setAlpha(al);
        c.drawCircle(W / 2f, card.top, 26 * dp, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * dp);
        p.setColor(INK);
        p.setAlpha(al);
        c.drawCircle(W / 2f, card.top, 26 * dp, p);
        p.setStyle(Paint.Style.FILL);
        // little house in the badge
        float hx = W / 2f, hy = card.top + 1 * dp;
        p.setColor(INK);
        p.setAlpha(al);
        path.reset();
        path.moveTo(hx - 12 * dp, hy - 0.5f * dp);
        path.lineTo(hx, hy - 12 * dp);
        path.lineTo(hx + 12 * dp, hy - 0.5f * dp);
        path.close();
        c.drawPath(path, p);
        c.drawRoundRect(hx - 8.5f * dp, hy - 3 * dp, hx + 8.5f * dp, hy + 10 * dp, 2 * dp, 2 * dp, p);

        tp.setTypeface(black);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setStyle(Paint.Style.FILL);
        tp.setTextSize(26 * dp);
        tp.setColor(INK);
        tp.setAlpha(al);
        c.drawText("Back to menu?", W / 2f, card.top + 66 * dp, tp);
        tp.setTypeface(bold);
        tp.setTextSize(15 * dp);
        tp.setColor(0xFF6B5A86);
        tp.setAlpha(al);
        c.drawText("Your layers are saved — tap", W / 2f, card.top + 98 * dp, tp);
        c.drawText("RESUME to dig right back in.", W / 2f, card.top + 119 * dp, tp);
        float gap = 12 * dp, bwh = (cw - 40 * dp - gap) / 2;
        btnC.set(card.left + 20 * dp, card.bottom - 82 * dp, card.left + 20 * dp + bwh, card.bottom - 26 * dp);
        btnD.set(btnC.right + gap, btnC.top, btnC.right + gap + bwh, btnC.bottom);
        drawButton(c, btnC, 0xFFE9E3F5, "STAY", null, pressedBtn == 2, true, INK, a);
        drawButton(c, btnD, 0xFFFF4D6D, "MENU", null, pressedBtn == 3, true, 0xFFFFFFFF, a);
        c.restore();
    }

    // =================================================================== menu

    private void drawMenu(Canvas c, float dt) {
        if (menuA != null) c.drawBitmap(menuA, null, rf2Full(), bmpPaint);
        else {
            p.setShader(new LinearGradient(0, 0, 0, H, 0xFFFF5FA2, 0xFF7B5CFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);
        }
        for (int i = 0; i < bubX.length; i++) {
            bubX[i] += bubVX[i] * dt;
            bubY[i] += bubVY[i] * dt;
            if (bubX[i] < -bubR[i]) bubX[i] = W + bubR[i];
            if (bubX[i] > W + bubR[i]) bubX[i] = -bubR[i];
            if (bubY[i] < -bubR[i]) bubY[i] = H + bubR[i];
            if (bubY[i] > H + bubR[i]) bubY[i] = -bubR[i];
            float r = bubR[i] * (1 + 0.05f * (float) Math.sin(time * 1.5f + i));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66000000);
            c.drawCircle(bubX[i] + 3 * dp, bubY[i] + 6 * dp, r, p);
            if (menuB != null) {
                path.reset();
                path.addCircle(bubX[i], bubY[i], r, Path.Direction.CW);
                c.save();
                c.clipPath(path);
                c.drawBitmap(menuB, null, rf2Full(), bmpPaint);
                c.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * dp);
            p.setColor(0xAAFFFFFF);
            c.drawCircle(bubX[i], bubY[i], r, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setShader(new LinearGradient(0, H * 0.35f, 0, H, 0x00120428, 0xCC120428, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        float size = Math.min(70 * dp, W * 0.16f);
        float ty0 = Math.max(insetTop, 0) + H * 0.16f;
        wavyTitle(c, "SCRAPE", W / 2f, ty0, size, 0, 255);
        wavyTitle(c, "STACK", W / 2f, ty0 + size * 1.05f, size, 3, 255);
        outlined(c, "peel · scoop · blast · smash", W / 2f, ty0 + size * 1.75f, 16 * dp, 0xFFFFFFFF, INK, 5 * dp, bold);

        float bwid = Math.min(W - 64 * dp, 320 * dp);
        float by = H * 0.5f;
        btnA.set(W / 2f - bwid / 2, by, W / 2f + bwid / 2, by + 78 * dp);
        btnB.set(W / 2f - bwid / 2, btnA.bottom + 26 * dp, W / 2f + bwid / 2, btnA.bottom + 26 * dp + 70 * dp);
        drawButton(c, btnA, 0xFF2EE6A6, "RESUME", saveDesc, pressedBtn == 0, hasSave);
        drawButton(c, btnB, 0xFFFF5FA2, "NEW STACK", null, pressedBtn == 1, true);

        float tw = Math.min((W - 64 * dp - 14 * dp) / 2, 150 * dp);
        float tyy = Math.min(btnB.bottom + 40 * dp, H - Math.max(insetBottom, 0) - 70 * dp);
        btnC.set(W / 2f - tw - 7 * dp, tyy, W / 2f - 7 * dp, tyy + 46 * dp);
        btnD.set(W / 2f + 7 * dp, tyy, W / 2f + 7 * dp + tw, tyy + 46 * dp);
        drawButton(c, btnC, sfx.enabled ? 0xFF7B5CFF : 0xFF5A5470, sfx.enabled ? "SOUND: ON" : "SOUND: OFF", null, pressedBtn == 2, true);
        drawButton(c, btnD, hap.enabled ? 0xFF7B5CFF : 0xFF5A5470, hap.enabled ? "BUZZ: ON" : "BUZZ: OFF", null, pressedBtn == 3, true);
        drawLock(c, dt);
    }

    private RectF rf2Full() {
        rf2.set(0, 0, W, H);
        return rf2;
    }

    private void drawLoading(Canvas c) {
        if (menuA != null) c.drawBitmap(menuA, null, rf2Full(), bmpPaint);
        else c.drawColor(0xFF7B5CFF);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA120428);
        c.drawRect(0, 0, W, H, p);
        outlined(c, "MIXING LAYERS…", W / 2f, H * 0.45f, 26 * dp, 0xFFFFFFFF, INK, 7 * dp, black);
        for (int i = 0; i < 4; i++) {
            float bounce = (float) Math.abs(Math.sin(time * 6 + i * 0.7f));
            p.setColor(TITLE_COL[i]);
            c.drawCircle(W / 2f + (i - 1.5f) * 26 * dp, H * 0.53f - bounce * 18 * dp, 9 * dp, p);
        }
    }

    private void wavyTitle(Canvas c, String s, float cx, float y, float size, int off, int alpha) {
        tp.setTypeface(black);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        float spacing = size * 0.02f;
        float total = 0;
        for (int i = 0; i < s.length(); i++) total += tp.measureText(s, i, i + 1) + spacing;
        float x = cx - total / 2;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            float w = tp.measureText(ch);
            float yy = y + (float) Math.sin(time * 3 + i * 0.6f + off) * size * 0.06f;
            float rot = (float) Math.sin(time * 2.3f + i + off) * 4;
            c.save();
            c.rotate(rot, x + w / 2, yy - size * 0.35f);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(INK);
            tp.setAlpha(alpha);
            c.drawText(ch, x + size * 0.03f, yy + size * 0.08f, tp);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeJoin(Paint.Join.ROUND);
            tp.setStrokeWidth(size * 0.13f);
            c.drawText(ch, x, yy, tp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(TITLE_COL[(i + off) % TITLE_COL.length]);
            tp.setAlpha(alpha);
            c.drawText(ch, x, yy, tp);
            c.restore();
            x += w + spacing;
        }
        tp.setTextAlign(Paint.Align.CENTER);
    }

    private void outlined(Canvas c, String s, float x, float y, float size, int fill, int stroke, float sw, Typeface tf) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setStrokeWidth(sw);
        tp.setColor(stroke);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(fill);
        c.drawText(s, x, y, tp);
    }

    private void drawButton(Canvas c, RectF r, int col, String label, String sub, boolean pressed, boolean enabled) {
        drawButton(c, r, col, label, sub, pressed, enabled, 0xFFFFFFFF, 1f);
    }

    private void drawButton(Canvas c, RectF r, int col, String label, String sub, boolean pressed, boolean enabled, int textCol, float a) {
        float depth = 6 * dp, d = pressed && enabled ? 4 * dp : 0;
        float rad = r.height() / 2;
        int base = enabled ? col : 0xFF8E86A3;
        int al = (int) (255 * Math.min(1, a * 1.5f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        p.setAlpha(al / 3);
        c.drawRoundRect(r.left, r.top + depth + 3 * dp, r.right, r.bottom + depth + 3 * dp, rad, rad, p);
        p.setColor(Textures.shade(base, 0.68f));
        p.setAlpha(al);
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom + depth, rad, rad, p);
        p.setColor(base);
        p.setAlpha(al);
        c.drawRoundRect(r.left, r.top + d, r.right, r.bottom + d, rad, rad, p);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(al / 5);
        c.drawRoundRect(r.left + rad * 0.5f, r.top + d + 5 * dp, r.right - rad * 0.5f, r.top + d + r.height() * 0.38f, rad * 0.3f, rad * 0.3f, p);
        boolean dark = textCol != 0xFFFFFFFF;
        float big = Math.min(23 * dp, r.height() * 0.4f);
        if (sub == null) {
            if (dark) {
                tp.setTypeface(black);
                tp.setTextSize(big);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setStyle(Paint.Style.FILL);
                tp.setColor(textCol);
                tp.setAlpha(al);
                c.drawText(label, r.centerX(), r.centerY() + d + big * 0.36f, tp);
            } else {
                outlined(c, label, r.centerX(), r.centerY() + d + big * 0.36f, big, Textures.alpha(textCol, al),
                        Textures.alpha(Textures.shade(base, 0.45f), al), big * 0.22f, black);
            }
        } else {
            outlined(c, label, r.centerX(), r.centerY() + d - 2 * dp, big, Textures.alpha(textCol, al),
                    Textures.alpha(Textures.shade(base, 0.45f), al), big * 0.22f, black);
            tp.setTypeface(bold);
            tp.setTextSize(12.5f * dp);
            tp.setStyle(Paint.Style.FILL);
            tp.setColor(enabled ? Textures.shade(base, 0.35f) : 0xFF4A4458);
            tp.setAlpha(al);
            c.drawText(sub, r.centerX(), r.centerY() + d + 19 * dp, tp);
        }
    }

    private static float easeOutBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // =================================================================== input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        // home and padlock buttons need a long hold (see HOLD_SECS); the action fires from onDraw once the ring fills
        if (screen != SCR_LOADING && !confirmOpen) {
            if (a == MotionEvent.ACTION_DOWN) {
                boolean lock = lockRect.contains(x, y);
                boolean home = !lock && screen == SCR_GAME && homeRect.contains(x, y);
                if (lock || home) {
                    lockDown = lock;
                    homeDown = home;
                    holdT = 0;
                    holdTicks = 0;
                    hap.pulse(10, 60, 0);
                    return true;
                }
            }
            if (lockDown || homeDown) {
                RectF r = lockDown ? lockRect : homeRect;
                boolean released = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
                if (released || (a == MotionEvent.ACTION_MOVE && !r.contains(x, y))) {
                    if (holdT < HOLD_SECS) {
                        lockToast = lockDown ? (pinned ? "HOLD 2 SECONDS TO UNLOCK" : "HOLD 2 SECONDS TO LOCK")
                                : "HOLD 2 SECONDS FOR MENU";
                        lockToastT = 0;
                    }
                    lockDown = false;
                    homeDown = false;
                }
                return true;
            }
        }
        if (screen == SCR_MENU) {
            menuTouch(a, x, y);
            return true;
        }
        if (screen != SCR_GAME) return true;
        if (confirmOpen || confirmA > 0.3f) {
            confirmTouch(a, x, y);
            return true;
        }
        if (complete) {
            completeTouch(a, x, y);
            return true;
        }
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                ptrId = e.getPointerId(0);
                blockUntilUp = false;
                if (barRect.contains(x, y)) {
                    float slot = barRect.width() / NT;
                    selectTool((int) ((x - barRect.left) / slot));
                    blockUntilUp = true;
                    return true;
                }
                if (canScrape()) {
                    touching = true;
                    lastEvtMs = e.getEventTime();
                    toolDown(x, y);
                } else blockUntilUp = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (touching && !blockUntilUp) {
                    int pi = e.findPointerIndex(ptrId);
                    if (pi < 0) return true;
                    float total = 0, ox = lx, oy = ly;
                    int hs = e.getHistorySize();
                    for (int h = 0; h < hs && touching && !blockUntilUp; h++) {
                        float hx = e.getHistoricalX(pi, h), hy = e.getHistoricalY(pi, h);
                        total += (float) Math.hypot(hx - ox, hy - oy);
                        ox = hx;
                        oy = hy;
                        toolMove(hx, hy);
                    }
                    if (touching && !blockUntilUp) {
                        float cx = e.getX(pi), cy = e.getY(pi);
                        total += (float) Math.hypot(cx - ox, cy - oy);
                        toolMove(cx, cy);
                    }
                    long et = e.getEventTime();
                    float edt = Math.max(0.004f, (et - lastEvtMs) / 1000f);
                    lastEvtMs = et;
                    speed = speed * 0.5f + 0.5f * (total / edt);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(e.getActionIndex()) == ptrId) {
                    toolUp();
                    blockUntilUp = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                toolUp();
                blockUntilUp = false;
                ptrId = -1;
                return true;
            default:
                return true;
        }
    }

    private int hitMenu(float x, float y) {
        if (btnA.contains(x, y)) return 0;
        if (btnB.contains(x, y)) return 1;
        if (btnC.contains(x, y)) return 2;
        if (btnD.contains(x, y)) return 3;
        return -1;
    }

    private void menuTouch(int a, float x, float y) {
        if (a == MotionEvent.ACTION_DOWN) {
            pressedBtn = hitMenu(x, y);
            if (pressedBtn == 0 && !hasSave) pressedBtn = -1;
            if (pressedBtn >= 0) hap.pulse(10, 70, 0);
        } else if (a == MotionEvent.ACTION_UP) {
            int h = hitMenu(x, y);
            int pb = pressedBtn;
            pressedBtn = -1;
            if (h != pb || h < 0) return;
            sfx.play("pop", 0.8f, 1f);
            switch (h) {
                case 0: resumeGame(); break;
                case 1: newStack(); break;
                case 2:
                    sfx.enabled = !sfx.enabled;
                    prefs.edit().putBoolean("sound", sfx.enabled).apply();
                    if (sfx.enabled) sfx.play("pop", 0.8f, 1.2f);
                    break;
                case 3:
                    hap.enabled = !hap.enabled;
                    prefs.edit().putBoolean("buzz", hap.enabled).apply();
                    hap.pulse(40, 200, 0);
                    break;
                default: break;
            }
        } else if (a == MotionEvent.ACTION_CANCEL) pressedBtn = -1;
    }

    private void confirmTouch(int a, float x, float y) {
        if (!confirmOpen) return;
        if (a == MotionEvent.ACTION_DOWN) {
            if (btnC.contains(x, y)) pressedBtn = 2;
            else if (btnD.contains(x, y)) pressedBtn = 3;
            else if (!card.contains(x, y)) pressedBtn = 4;
            else pressedBtn = -1;
        } else if (a == MotionEvent.ACTION_UP) {
            int pb = pressedBtn;
            pressedBtn = -1;
            if (pb == 2 && btnC.contains(x, y)) closeConfirm();
            else if (pb == 3 && btnD.contains(x, y)) {
                sfx.play("pop", 0.8f, 1f);
                goMenu();
            } else if (pb == 4 && !card.contains(x, y)) closeConfirm();
        } else if (a == MotionEvent.ACTION_CANCEL) pressedBtn = -1;
    }

    private void completeTouch(int a, float x, float y) {
        if (a == MotionEvent.ACTION_DOWN) {
            if (completeT > 1.1f && btnA.contains(x, y)) pressedBtn = 0;
            else if (completeT > 1.1f && btnB.contains(x, y)) pressedBtn = 1;
            else {
                pressedBtn = -1;
                // tapping the treasure makes it sparkle
                for (int i = 0; i < 6; i++)
                    spawn(P_SPARK, x + (rnd.nextFloat() - 0.5f) * 80 * dp, y + (rnd.nextFloat() - 0.5f) * 80 * dp, 0, 0,
                            0.6f, (8 + rnd.nextFloat() * 10) * dp, 0xFFFFFFFF);
                sfx.play("pop", 0.5f, 1.5f + rnd.nextFloat() * 0.4f);
            }
        } else if (a == MotionEvent.ACTION_UP) {
            int pb = pressedBtn;
            pressedBtn = -1;
            if (pb == 0 && btnA.contains(x, y)) {
                sfx.play("pop", 0.8f, 1f);
                newStack();
            } else if (pb == 1 && btnB.contains(x, y)) {
                sfx.play("pop", 0.8f, 1f);
                goMenu();
            }
        } else if (a == MotionEvent.ACTION_CANCEL) {
            pressedBtn = -1;
        }
    }
}
