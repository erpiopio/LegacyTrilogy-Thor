package org.erpiopio.legacygoku1.thor;

import android.app.Activity;
import android.app.Application;
import android.app.Presentation;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;


/**
 * Self-starting AYN Thor secondary display. The primary SDL activity is left
 * untouched. The second screen hosts a touch dashboard with emulation-thread snapshots.
 */
public final class Legacy1SecondScreenProvider extends ContentProvider
        implements Application.ActivityLifecycleCallbacks, DisplayManager.DisplayListener {

    private static final String TAG = "LEGACY1_DUAL";
    private final Handler main = new Handler(Looper.getMainLooper());
    private DisplayManager displayManager;
    private Activity foreground;
    private BottomPresentation bottom;
    private int bottomId = -1;

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        if (context == null || !(context.getApplicationContext() instanceof Application)) {
            Log.w(TAG, "Application no disponible");
            return true;
        }
        final Application application = (Application) context.getApplicationContext();
        displayManager = (DisplayManager) application.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(this, main);
        }
        application.registerActivityLifecycleCallbacks(this);
        Log.i(TAG, "Soporte de pantalla inferior registrado");
        return true;
    }

    private Display findBottomDisplay(Activity activity) {
        if (displayManager == null) return null;
        final int ownId = activity.getWindowManager().getDefaultDisplay().getDisplayId();
        Display winner = null;
        int winnerScore = -1;
        for (Display candidate : displayManager.getDisplays()) {
            if (candidate.getDisplayId() == ownId ||
                    candidate.getState() == Display.STATE_OFF) continue;
            final Point size = new Point();
            candidate.getRealSize(size);
            // AYN Thor Max lower panel is 1240x1080. Also support other
            // secondary displays, but never open a window on the SDL display.
            final int shortSide = Math.min(size.x, size.y);
            final int longSide = Math.max(size.x, size.y);
            final int score = (shortSide == 1080 && longSide == 1240) ? 100 : 1;
            if (score > winnerScore) {
                winner = candidate;
                winnerScore = score;
            }
        }
        return winner;
    }

    private void ensureVisible() {
        final Activity activity = foreground;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        // Trilogy: el proveedor de Legacy I solo atiende a Goku y al lanzador.
        // Legacy II controla su propia pantalla inferior.
        String clase = activity.getClass().getName();
        if (!clase.equals("org.gbarecomp.GbaGameActivity") &&
            !clase.equals("org.erpiopio.legacytrilogy.thor.LegacyTrilogyActivity")) {
            dismissBottom();
            return;
        }
        final Display selected = findBottomDisplay(activity);
        if (selected == null) {
            dismissBottom();
            Log.i(TAG, "Pantalla secundaria no detectada todavia");
            return;
        }
        boolean isGame = activity instanceof org.gbarecomp.GbaGameActivity;
        if (bottom != null && bottom.isShowing() &&
                bottomId == selected.getDisplayId() &&
                bottom.legacy1Game == isGame)
            return;
        dismissBottom();
        try {
            bottom = new BottomPresentation(activity, selected, isGame);
            bottom.show();
            bottomId = selected.getDisplayId();
            Log.i(TAG, "Pantalla inferior activa, display=" + bottomId);
        } catch (RuntimeException problem) {
            Log.e(TAG, "No se pudo abrir la pantalla inferior", problem);
            dismissBottom();
        }
    }

    private void dismissBottom() {
        if (bottom != null) {
            try { bottom.dismiss(); } catch (RuntimeException ignored) { }
            bottom = null;
        }
        bottomId = -1;
    }

    @Override public void onActivityResumed(Activity activity) {
        foreground = activity;
        main.post(this::ensureVisible);
    }
    @Override public void onActivityPaused(Activity activity) {
        if (foreground == activity) {
            foreground = null;
            dismissBottom();
        }
    }
    @Override public void onActivityDestroyed(Activity activity) {
        if (foreground == activity) {
            foreground = null;
            dismissBottom();
        }
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    @Override public void onDisplayAdded(int displayId) { main.post(this::ensureVisible); }
    @Override public void onDisplayChanged(int displayId) { main.post(this::ensureVisible); }
    @Override public void onDisplayRemoved(int displayId) { main.post(this::ensureVisible); }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values,
                                String selection, String[] selectionArgs) { return 0; }

    private static final class BottomPresentation extends Presentation {
        final boolean legacy1Game;

        BottomPresentation(Context context, Display display, boolean game) {
            super(context, display);
            legacy1Game = game;
            setCancelable(false);
            setCanceledOnTouchOutside(false);
        }

        @Override protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.rgb(5, 9, 20)));
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                 WindowManager.LayoutParams.MATCH_PARENT);
                window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            setContentView(legacy1Game
                ? new Legacy1DashboardView(getContext())
                : new org.erpiopio.legacytrilogy.thor.TrilogyBottomTitleView(getContext()));
        }
    }
}

