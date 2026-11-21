package org.erpiopio.legacygoku2.thor;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Display;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.gbarecomp.GbaGameActivity;

public final class Legacy2GameActivity extends GbaGameActivity
        implements DisplayManager.DisplayListener {

    // LEGACY2_TRILOGY_ISOLATION
    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL2", "legacy2_engine"};
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private DisplayManager displays;
    private Presentation panel;
    private boolean active;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        displays = (DisplayManager)
                getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        displays.registerDisplayListener(this, ui);
        updatePanel();
    }

    @Override
    protected void onPause() {
        active = false;
        displays.unregisterDisplayListener(this);
        closePanel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        closePanel();
        super.onDestroy();
    }

    private void closePanel() {
        Presentation old = panel;
        panel = null;
        if (old != null) old.dismiss();
    }

    private void updatePanel() {
        if (!active || displays == null) return;

        int current = getWindowManager()
                .getDefaultDisplay().getDisplayId();

        Display target = null;

        for (Display display : displays.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display.isValid()
                    && display.getDisplayId() != current) {
                target = display;
                break;
            }
        }

        if (target == null) {
            closePanel();
            return;
        }

        if (panel != null
                && panel.getDisplay().getDisplayId()
                    == target.getDisplayId()) return;

        closePanel();

        Legacy2MenuPresentation next =
                new Legacy2MenuPresentation(this, target);

        next.setOnDismissListener(dialog -> {
            if (panel == next) panel = null;
        });

        try {
            next.show();
            panel = next;
        } catch (RuntimeException error) {
            next.dismiss();
        }
    }

    @Override
    public void onDisplayAdded(int id) {
        updatePanel();
    }

    @Override
    public void onDisplayRemoved(int id) {
        updatePanel();
    }

    @Override
    public void onDisplayChanged(int id) {
        updatePanel();
    }
}
