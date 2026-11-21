package org.novapowers.buusfury.thor;

import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.gbarecomp.GbaGameActivity;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** One SDL Activity for the game; the second screen is a plain Android window. */
public final class ThorGameActivity extends GbaGameActivity
        implements DisplayManager.DisplayListener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler inputTimers = new Handler(Looper.getMainLooper());
    private final Set<Integer> heldKeys = new HashSet<>();
    private DisplayManager displays;
    private ThorMenuPresentation panel;
    private FrameLayout overlay;
    private View localPanel;
    private Button panelButton;
    private int selectedDisplay = -1;
    private boolean resumed;
    private String loadedRomSha1 = "";
    private Intent relaunchAfterDestroy;

    private void validateTrilogyPayload() throws IOException {
        if (!ThorRomIdentity.verifyActive(this, ThorRomIdentity.activeFile(this)))
            throw new IOException("Unverified ROM: return to setup and import a reviewed EN/ES variant.");
        loadedRomSha1 = ThorRomIdentity.activeSha1();
        ThorSkillsExtractor.prepare(
            this, ThorRomIdentity.activeFile(this), loadedRomSha1);
        // Preparar los iconos nuevos al arrancar, incluso tras actualizar.
        ThorInventoryExtractor.prepare(
            this, ThorRomIdentity.activeFile(this), loadedRomSha1);
        ThorPortraitExtractor.prepare(
            this, ThorRomIdentity.activeFile(this), loadedRomSha1);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean verified = ThorRomIdentity.verifyActive(this, ThorRomIdentity.activeFile(this));
        if (verified && loadedRomSha1.equals(ThorRomIdentity.activeSha1())) return;
        // singleTask can deliver Play to an existing native game. Quit and join
        // the previous SDL thread before starting the newly selected cartridge.
        relaunchAfterDestroy = new Intent(this,
                verified ? ThorGameActivity.class : org.erpiopio.legacytrilogy.thor.LegacyTrilogyActivity.class);
        finish();
    }

    /** SDL's pinned HID manager receives this Activity as its Context. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    // Lint cannot inspect this filter parameter. The custom USB branch below
    // handles API 33+; the audited remaining SDL filters are system-only.
    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33 && filter != null &&
                filter.hasAction("org.libsdl.app.USB_PERMISSION")) {
            // Android 14+ requires explicit export state for this custom action.
            // Permission completions are app-scoped; USB attach/detach are system broadcasts.
            return super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        return super.registerReceiver(receiver, filter);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL2", "buusfury_engine"};
    }

    @Override
    protected void onCreate(Bundle state) {
        try {
            validateTrilogyPayload();
        } catch (IOException error) {
            throw new IllegalStateException(
                "No se pudieron validar los recursos de Buu's Fury", error);
        }

        File saves = new File(getFilesDir(), "saves");
        if (!saves.isDirectory() && !saves.mkdirs())
            throw new IllegalStateException("No se puede crear el directorio de partidas");
        super.onCreate(state);
        displays = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        overlay = new FrameLayout(this);
        panelButton = new Button(this);
        panelButton.setText("Panel / pantallas");
        panelButton.setVisibility(View.GONE);
        panelButton.setAllCaps(false);
        panelButton.setOnClickListener(v -> choosePanelDisplay());
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.END);
        overlay.addView(panelButton, position);
        addContentView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (state != null) selectedDisplay = state.getInt("panelDisplay", -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        resumed = true;
        if (displays != null) {
            displays.registerDisplayListener(this, ui);
            updatePanel();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (displays != null) displays.unregisterDisplayListener(this);
        closePanel();
        removeLocalPanel();
        releaseKeys();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        closePanel();
        releaseKeys();
        super.onDestroy();
        if (relaunchAfterDestroy != null) startActivity(relaunchAfterDestroy);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("panelDisplay", selectedDisplay);
        super.onSaveInstanceState(state);
    }

    private ArrayList<Display> candidates() {
        ArrayList<Display> result = new ArrayList<>();
        Display current = getWindowManager().getDefaultDisplay();
        if (displays == null) return result;
        for (Display display : displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display.isValid() && display.getDisplayId() != current.getDisplayId())
                result.add(display);
        }
        return result;
    }

    private void choosePanelDisplay() {
        releaseKeys();
        ArrayList<Display> available = new ArrayList<>();
        Display current = getWindowManager().getDefaultDisplay();
        for (Display display : displays.getDisplays()) {
            if (display.isValid() && display.getDisplayId() != current.getDisplayId())
                available.add(display);
        }
        String[] labels = new String[available.size() + 1];
        labels[0] = "Panel en esta pantalla (cerrar al terminar)";
        for (int index = 0; index < available.size(); ++index) {
            Display display = available.get(index);
            Display.Mode mode = display.getMode();
            labels[index + 1] = display.getName() + " - " + mode.getPhysicalWidth() +
                " x " + mode.getPhysicalHeight() + " (id " + display.getDisplayId() + ")" +
                ((display.getFlags() & Display.FLAG_PRESENTATION) == 0
                    ? " - requiere otro modo de pantalla" : "");
        }
        new AlertDialog.Builder(this)
            .setTitle("Destino del panel tactil")
            .setItems(labels, (dialog, which) -> {
                if (which > 0 && (available.get(which - 1).getFlags() & Display.FLAG_PRESENTATION) == 0) {
                    Toast.makeText(this,
                        "Este panel aun no admite esa pantalla. Usa el panel local.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                selectedDisplay = which == 0 ? -2 : available.get(which - 1).getDisplayId();
                closePanel();
                updatePanel();
            }).show();
    }

    private void updatePanel() {
        if (!resumed) return;
        ArrayList<Display> available = candidates();
        Display target = null;
        for (Display display : available) {
            if (display.getDisplayId() == selectedDisplay) target = display;
        }
        // One system-recommended display is unambiguous. Never assume display id 1.
        if (target == null && selectedDisplay == -1 && available.size() == 1) {
            target = available.get(0);
            selectedDisplay = target.getDisplayId();
        }
        if (target == null) {
            closePanel();
            if (selectedDisplay == -2) showLocalPanel();
            else {
                removeLocalPanel();
                panelButton.setText(available.isEmpty() ? "Panel (una pantalla)" : "Elegir pantalla");
            }
            return;
        }
        removeLocalPanel();
        if (panel != null && panel.getDisplay().getDisplayId() == target.getDisplayId()) return;
        closePanel();
        ThorMenuPresentation next = new ThorMenuPresentation(this, target, this);
        panel = next;
        next.setOnDismissListener(dialog -> {
            releaseKeys();
            if (panel == next) {
                panel = null;
                // Presentation can cancel on a metrics change after our display
                // callback has already run. Reconcile once cancellation completes.
                if (resumed) ui.post(this::updatePanel);
            }
        });
        try {
            next.show();
            panelButton.setText("Panel: " + target.getName());
        } catch (WindowManager.InvalidDisplayException | SecurityException unavailable) {
            panel = null;
            selectedDisplay = -1;
            Toast.makeText(this, "Pantalla no disponible; usa el panel local", Toast.LENGTH_LONG).show();
        }
    }

    private void showLocalPanel() {
        removeLocalPanel();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Button close = new Button(this);
        close.setText("Cerrar panel");
        close.setOnClickListener(v -> {
            selectedDisplay = -1;
            removeLocalPanel();
            releaseKeys();
        });
        content.addView(close);
        content.addView(ThorMenuPresentation.createContent(this, this),
            new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.topMargin = dp(48);
        overlay.addView(content, params);
        localPanel = content;
    }

    private void removeLocalPanel() {
        if (localPanel != null) {
            overlay.removeView(localPanel);
            localPanel = null;
            releaseKeys();
        }
    }

    private void closePanel() {
        ThorMenuPresentation previous = panel;
        panel = null;
        if (previous != null) previous.dismiss();
    }

    void keyDown(int key) {
        if (!resumed || !heldKeys.add(key)) return;
        try { SDLActivity.onNativeKeyDown(key); }
        catch (UnsatisfiedLinkError unavailable) { heldKeys.remove(key); }
    }

    void keyUp(int key) {
        if (!heldKeys.remove(key)) return;
        try { SDLActivity.onNativeKeyUp(key); }
        catch (UnsatisfiedLinkError unavailable) { /* Activity is shutting down. */ }
    }

    void pulseKey(int key) {
        if (heldKeys.contains(key)) return;
        keyDown(key);
        inputTimers.postDelayed(() -> keyUp(key), 120);
    }

    void togglePause() {
        keyDown(KeyEvent.KEYCODE_SHIFT_LEFT);
        keyDown(KeyEvent.KEYCODE_P);
        inputTimers.postDelayed(() -> {
            keyUp(KeyEvent.KEYCODE_P);
            keyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
        }, 120);
    }

    private void releaseKeys() {
        inputTimers.removeCallbacksAndMessages(null);
        for (int key : new HashSet<>(heldKeys)) keyUp(key);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onDisplayAdded(int id) { updatePanel(); }
    @Override public void onDisplayRemoved(int id) {
        if (id == selectedDisplay) selectedDisplay = -1;
        updatePanel();
    }
    @Override public void onDisplayChanged(int id) { updatePanel(); }
}
