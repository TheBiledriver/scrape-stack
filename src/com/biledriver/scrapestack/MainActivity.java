package com.biledriver.scrapestack;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView game;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        game = new GameView(this);
        setContentView(game);
        hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    /** True while the app is pinned to the screen (Home/Back/Recents/notification shade blocked). */
    boolean isPinned() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        return am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    void setPinned(boolean on) {
        try {
            if (on) startLockTask();
            else stopLockTask();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        game.onHostResume();
    }

    @Override
    protected void onPause() {
        game.onHostPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        game.release();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!game.onBack()) super.onBackPressed();
    }
}
