package org.erpiopio.legacygoku1.thor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** A 1240x1080 pixel-art dashboard over the user's original, unmodified artwork. */
public final class Legacy1DashboardView extends View {
    private static final String TAG = "LEGACY1_LOWER";
    private static final float WIDTH = 1240, HEIGHT = 1080;
    private static final RectF[] HOME = {
        new RectF(58, 510, 399, 718), new RectF(414, 510, 741, 718),
        new RectF(58, 739, 399, 970), new RectF(414, 739, 741, 970)
    };
    private static final RectF BACK = new RectF(58, 898, 399, 992);
    private static final float CONTENT_FONT = 30f; // Identical font for all 3 sections.
    private static final float CARD_TOP = 236f, CARD_BOTTOM = 854f;
    private static final float CARD_STEP = 101f, CARD_HEIGHT = 92f;
    private static final float CARD_LEFT = 85f, CARD_RIGHT = 1153f;
    private static final float BUTTON_SIDE_MARGIN = 32f;
    // The enum order is the ALGP ROM's language selector, not the Android locale.
    private static final String[][] WORDS = {
        {"Level","Experience","Next level","HP","Ki","Time","Quests","Inventory","Attacks","Map","Back","Waiting for Goku","No entries","Area","Position","Selected","Locked","Available","Previous","Next","Completed","In progress","Not available","Life","Energy","Flight","Melee","Ki blast","Solar Flare","Kamehameha"},
        {"Niveau","Expérience","Niveau suivant","PV","Ki","Temps","Missions","Inventaire","Attaques","Carte","Retour","En attente de Goku","Aucune entrée","Zone","Position","Sélection","Verrouillé","Disponible","Précédent","Suivant","Terminée","En cours","Indisponible","Vie","Énergie","Vol","Corps à corps","Boule de ki","Morsure du soleil","Kamehameha"},
        {"Level","Erfahrung","Nächstes Level","LP","Ki","Zeit","Missionen","Inventar","Angriffe","Karte","Zurück","Warte auf Goku","Keine Einträge","Gebiet","Position","Ausgewählt","Gesperrt","Verfügbar","Zurück","Weiter","Abgeschlossen","Aktiv","Nicht verfügbar","Leben","Energie","Fliegen","Nahkampf","Ki-Strahl","Sonnenattacke","Kamehameha"},
        {"Nivel","Experiencia","Al sig. nivel","PD","Ki","Tiempo","Misiones","Inventario","Ataques","Mapa","Volver","Esperando a Goku","Sin entradas","Zona","Posición","Seleccionado","Bloqueado","Disponible","Anterior","Siguiente","Completada","En curso","No disponible","Vida","Energía","Vuelo","Cuerpo a cuerpo","Ráfaga de ki","Destello solar","Kamehameha"},
        {"Livello","Esperienza","Prossimo livello","PV","Ki","Tempo","Missioni","Inventario","Attacchi","Mappa","Indietro","In attesa di Goku","Nessuna voce","Area","Posizione","Selezionato","Bloccato","Disponibile","Precedente","Successivo","Completata","In corso","Non disponibile","Vita","Energia","Volo","Corpo a corpo","Sfera di ki","Colpo del sole","Kamehameha"}
    };
    private final Paint paint = new Paint();
    private final Paint bitmapPaint = new Paint();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Bitmap template;
    private final Bitmap sectionTemplate;
    private final Bitmap loadingImage;
    private boolean hasShownGoku;
    private final Typeface font;
    private SoundPool sounds;
    private int pressSound;
    private volatile boolean pressLoaded;
    private int[] state = new int[0];
    private String[] journalNames = new String[0], itemNames = new String[0];
    private long nextNamesPoll;
    private final int[] selectedKeys = {-1, -1, -1};
    private int armedHerbCategory = -1;
    private final float[] scrollOffset = {0, 0, 0};
    private float downY, startScrollOffset;
    private boolean downInList, dragging;
    private int language;
    private int section = -1, pendingSection = -2;
    private int held = -1, pointer = -1;
    private long pulseUntil;
    private float scale = 1, originX, originY;
    private boolean attached, missingNativeLogged;
    private Bitmap zoneMap;
    private float mapZoom = 1f;
    private boolean mapFollow = true;
    private float mapFocusX = .5f, mapFocusY = .5f;
    private int mapLastScene = -1;
    private boolean mapTouchActive, mapGestureMoved;
    private float mapTouchX, mapTouchY, mapDownX, mapDownY;
    private float mapPinchDistance;
    private long mapLastTapTime;
    private float mapLastTapX, mapLastTapY;
    private int zoneDescriptor = -1;
    private int zoneWorldW, zoneWorldH;
    private long lastMapAttempt;
    // Diagnostic: keep native ABI mismatches visible without flooding logcat.
    private long lastStateReportMs;
    // Preserve the map developer's LEGACY1_XY diagnostic channel.
    private long lastXYDiagnosticMs;
    private String lastItemSnapshot = null;
    private final Runnable navigate = () -> {
        section = pendingSection; pendingSection = -2;
        if (section >= 0 && section < 3) scrollOffset[section] = 0;
        held = -1; pulseUntil = 0; invalidate();
    };

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            try {
                int[] next = Legacy1Native.readState();
                final long stateNow = SystemClock.uptimeMillis();
                if (stateNow - lastStateReportMs >= 2000) {
                    lastStateReportMs = stateNow;
                    Log.i(TAG, "STATE_DIAG len=" + (next == null ? -1 : next.length)
                        + " valid=" + (next != null && next.length > 1 ? next[1] : -1)
                        + " lang=" + (next != null && next.length > 2 ? next[2] : -1)
                        + " lvl=" + (next != null && next.length > 4 ? next[4] : -1));
                }
                // The original read-only Goku/Scouter contract has 127 slots.
                // Do not discard it if the expanded 224-slot native ABI is not
                // active yet (e.g. an older libmain.so is running).
                if (next != null && next.length >= 127 && next[0] == 1) {
                    state = next;
                    int equippedAttack = next[115];
                    if (equippedAttack >= 0 && equippedAttack < 3 &&
                        next[215 + equippedAttack] == 1)
                        selectedKeys[2] = equippedAttack;
                    else
                        selectedKeys[2] = -1;
                    if (next.length >= 224 && next[1] == 1) {
                        StringBuilder items = new StringBuilder();
                        for (int i = 0; i < 34; ++i) {
                            int qty = next[80 + i];
                            if (qty > 0)
                                items.append(i).append("x")
                                     .append(qty).append(" ");
                        }
                        String snapshot = "HP=" + next[7] + "/" + next[8]
                            + " KI=" + next[9] + "/" + next[10]
                            + " ITEMS={" + items + "}";
                        if (!snapshot.equals(lastItemSnapshot)) {
                            Log.i("LEGACY1_ITEM_TRACE", snapshot);
                            lastItemSnapshot = snapshot;
                        }
                    }

                    int activeScene = next[19] == 1 ? next[12] : -1;
                    if (activeScene != mapLastScene) {
                        mapLastScene = activeScene;
                        mapZoom = 1f;
                        mapFollow = true;
                        mapFocusX = mapFocusY = .5f;
                        mapTouchActive = false;
                        mapLastTapTime = 0;
                        Log.i("LEGACY1_MAP",
                            "Cambio de escenario: " +
                            Integer.toHexString(activeScene));
                    }
                    if (next[1] == 1 && next[4] > 0 && next[8] > 0)
                        hasShownGoku = true;

                    if (zoneDescriptor != next[12] || next[19] != 1) {
                        zoneMap = null;
                        zoneDescriptor = -1;
                    }

                    if (section == 3 && next[19] == 1 &&
                        next[12] > 0 && next[16] == next[12] &&
                        zoneMap == null &&
                        SystemClock.uptimeMillis() - lastMapAttempt >= 500) {

                        lastMapAttempt = SystemClock.uptimeMillis();
                        int[] raw = Legacy1Native.readMap();

                        if (raw != null && raw.length >= 4) {
                            int w = raw[0], h = raw[1];

                            if (w > 0 && h > 0 &&
                                w <= 512 && h <= 512 &&
                                raw[2] > 0 && raw[3] > 0 &&
                                raw.length >= 4L + (long)w * h) {

                                int[] pixels = new int[w * h];
                                System.arraycopy(raw, 4, pixels, 0, pixels.length);
                                zoneMap = Bitmap.createBitmap(
                                    pixels, w, h, Bitmap.Config.ARGB_8888);

                                zoneWorldW = raw[2];
                                zoneWorldH = raw[3];
                                zoneDescriptor = next[12];

                                Log.i("LEGACY1_MAP",
                                    "Mapa cargado " + w + "x" + h +
                                    " mundo=" + zoneWorldW + "x" + zoneWorldH);
                            }
                        }
                    }
                    long nowXY = android.os.SystemClock.uptimeMillis();
                    if (next[19] == 1 && nowXY - lastXYDiagnosticMs >= 1000) {
                        lastXYDiagnosticMs = nowXY;
                        StringBuilder fields = new StringBuilder();
                        for (int i = 0; i < 46; i++) {
                            fields.append(String.format(Locale.ROOT,
                                "%02X:%04X ", i * 2, next[32 + i] & 0xFFFF));
                        }
                        Log.i("LEGACY1_XY", String.format(Locale.ROOT,
                            "desc=%08X actor=%08X usaXY=%d,%d cam=%d,%d,%d,%d pages=%d,%d fields=%s",
                            next[21], next[20], next[22], next[23],
                            next[24], next[25], next[26], next[27],
                            next[28], next[29], fields.toString()));
                    }
                    if (next[2] >= 0 && next[2] < 5) {
                        if (next[2] != language) {
                            language = next[2];
                            journalNames = itemNames = new String[0];
                            selectedKeys[2] = -1;
                            nextNamesPoll = 0;
                        }
                    }
                    if (selectedKeys[2] >= 0 &&
                        (next.length <= 215 + selectedKeys[2] ||
                         next[215 + selectedKeys[2]] != 1))
                        selectedKeys[2] = -1;
                    final long now = SystemClock.uptimeMillis();
                    if (next.length >= 224 && next[1] == 1 && now >= nextNamesPoll) {
                        String[] journals = Legacy1Native.readJournal();
                        String[] inventory = Legacy1Native.readInventoryNames();
                        if (journals != null && journals.length == 75) journalNames = journals;
                        if (inventory != null && inventory.length == 32) itemNames = inventory;
                        nextNamesPoll = now + 1000;
                    }
                } else { state = new int[0]; }
            } catch (UnsatisfiedLinkError unavailable) {
                state = new int[0];
                if (!missingNativeLogged) {
                    Log.i(TAG, "STATE_DIAG native call unavailable: " + unavailable);
                    missingNativeLogged = true;
                }
            }
            invalidate();
            handler.postDelayed(this, 100);
        }
    };

    public Legacy1DashboardView(Context context) {
        super(context);
        int id = context.getResources().getIdentifier("legacy1_lower_template", "drawable", context.getPackageName());
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        template = BitmapFactory.decodeResource(context.getResources(), id, options);
        int sectionId = context.getResources().getIdentifier("legacy1_section_template", "drawable", context.getPackageName());
        sectionTemplate = BitmapFactory.decodeResource(context.getResources(), sectionId, options);
        int loadingId = context.getResources().getIdentifier(
            "legacy1_loading", "drawable", context.getPackageName());
        loadingImage = loadingId == 0 ? null :
            BitmapFactory.decodeResource(context.getResources(), loadingId, options);
        Typeface loaded;
        try { loaded = Typeface.createFromAsset(context.getAssets(), "legacy1_pixel.ttf"); }
        catch (RuntimeException absent) { loaded = Typeface.MONOSPACE; }
        font = loaded;
        bitmapPaint.setFilterBitmap(false); bitmapPaint.setAntiAlias(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.BLACK);
        setSoundEffectsEnabled(false);
        setContentDescription("Legacy of Goku");
    }

    private String word(int id) { return WORDS[new int[]{0, 1, 2, 4, 3}[language]][id]; }
    private int value(int id) { return state.length > id && state[1] != 0 ? state[id] : -1; }
    private String number(int id) { int v = value(id); return v < 0 ? "—" : Integer.toString(v); }
    private String time() {
        int seconds = value(11);
        return seconds < 0 ? "—:—:—" : String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true;
        sounds = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool, sample, status) -> {
            if (status == 0) {
                if (sample == pressSound) pressLoaded = true;
            }
        });
        int open = getResources().getIdentifier("legacy1_touch", "raw", getContext().getPackageName());
        if (open != 0) pressSound = sounds.load(getContext(), open, 1);
        handler.post(refresh);
    }
    @Override protected void onDetachedFromWindow() {
        attached = false; handler.removeCallbacksAndMessages(null);
        if (sounds != null) { sounds.release(); sounds = null; }
        pressLoaded = false; held = pointer = -1; pendingSection = -2;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        scale = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT);
        originX = (getWidth() - WIDTH * scale) / 2; originY = (getHeight() - HEIGHT * scale) / 2;
        canvas.save(); canvas.translate(originX, originY); canvas.scale(scale, scale);
        if (!hasShownGoku && loadingImage != null) {
            canvas.drawBitmap(loadingImage, null,
                new RectF(0, 0, WIDTH, HEIGHT), bitmapPaint);

            // LEGACY1_LOADING_MULTILANG_ANIMATED
            // Idioma ALGP: EN=0, FR=1, DE=2, IT=3, ES=4.
            String loadingWord;
            switch (language) {
                case 1: loadingWord = "CHARGEMENT"; break;
                case 2: loadingWord = "LADEN"; break;
                case 3: loadingWord = "CARICAMENTO"; break;
                case 4: loadingWord = "CARGANDO"; break;
                default: loadingWord = "LOADING"; break;
            }

            int dotCount = (int) (
                (android.os.SystemClock.uptimeMillis() / 400L) % 4L
            );
            String loadingCaption =
                loadingWord + "...".substring(0, dotCount);

            android.graphics.Paint loadingCaptionPaint =
                new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG
                );

            loadingCaptionPaint.setTypeface(font);
            loadingCaptionPaint.setTextSize(40f);
            loadingCaptionPaint.setTextAlign(
                android.graphics.Paint.Align.CENTER
            );

            loadingCaptionPaint.setStrokeWidth(5f);
            loadingCaptionPaint.setStrokeJoin(
                android.graphics.Paint.Join.ROUND
            );
            loadingCaptionPaint.setStyle(
                android.graphics.Paint.Style.STROKE
            );
            loadingCaptionPaint.setColor(
                android.graphics.Color.rgb(13, 25, 75)
            );

            float textX = WIDTH / 2f;
            float textY = HEIGHT / 2f - (loadingCaptionPaint.ascent() + loadingCaptionPaint.descent()) / 2f;

            canvas.drawText(
                loadingCaption, textX, textY, loadingCaptionPaint
            );

            loadingCaptionPaint.setStyle(
                android.graphics.Paint.Style.FILL
            );
            loadingCaptionPaint.setColor(
                android.graphics.Color.rgb(255, 237, 156)
            );
            loadingCaptionPaint.setShadowLayer(
                5f, 2f, 3f, android.graphics.Color.BLACK
            );

            canvas.drawText(
                loadingCaption, textX, textY, loadingCaptionPaint
            );

            canvas.restore();
            return;
        }
        Bitmap background = section < 0 ? template : sectionTemplate;
        if (background != null) canvas.drawBitmap(background, null, new RectF(0, 0, WIDTH, HEIGHT), bitmapPaint);
        if (section < 0) {
            drawStats(canvas);
            drawGoku(canvas);
            final float buttonFont = uniformHomeButtonFontSize();
            for (int i = 0; i < 4; ++i)
                button(canvas, HOME[i], word(6 + i), i, buttonFont);
        } else {
            text(canvas, word(6 + section), 105, 163, 58f * .85f, 1030, false);
            if (section == 3) {
                drawZoneMap(canvas);
            } else {
                drawSectionEntries(canvas);
            }
            button(canvas, BACK, word(10), 10, 43);
        }
        canvas.restore();
    }


    private RectF mapArea() {
        float bottom = Math.min(HEIGHT - 150f, BACK.top - 18f);
        if (bottom < 350f) bottom = HEIGHT - 150f;
        return new RectF(70f, 220f, WIDTH - 70f, bottom);
    }

    private static float mapClamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void drawZoneMap(Canvas c) {
        RectF area = mapArea();

        if (zoneMap == null || zoneWorldW <= 0 || zoneWorldH <= 0) {
            text(c, word(22), area.left + 35f,
                area.centerY(), 40f, area.width() - 70f, false);
            return;
        }

        float fit = Math.min(
            area.width() / zoneMap.getWidth(),
            area.height() / zoneMap.getHeight());

        float dw = zoneMap.getWidth() * fit * mapZoom;
        float dh = zoneMap.getHeight() * fit * mapZoom;

        int x = value(13);
        int y = value(14);
        boolean positionValid =
            x >= 0 && y >= 0 &&
            x < zoneWorldW && y < zoneWorldH;

        float fx = mapFollow && positionValid
            ? (float)x / zoneWorldW : mapFocusX;
        float fy = mapFollow && positionValid
            ? (float)y / zoneWorldH : mapFocusY;

        float left = area.centerX() - fx * dw;
        float top = area.centerY() - fy * dh;

        if (dw <= area.width()) {
            left = area.centerX() - dw / 2f;
        } else {
            left = Math.max(area.right - dw,
                Math.min(area.left, left));
        }

        if (dh <= area.height()) {
            top = area.centerY() - dh / 2f;
        } else {
            top = Math.max(area.bottom - dh,
                Math.min(area.top, top));
        }

        RectF dst = new RectF(left, top, left + dw, top + dh);

        c.save();
        c.clipRect(area);

        // Original ALGP map over plantilla2.png.
        c.drawBitmap(zoneMap, null, dst, bitmapPaint);

        // Goku's live position, in the same world coordinate space.
        if (positionValid) {
            float px = dst.left + x * dw / zoneWorldW;
            float py = dst.top + y * dh / zoneWorldH;

            paint.reset();
            paint.setAntiAlias(true);

            paint.setColor(0xFF061426);
            c.drawCircle(px, py, 20f, paint);

            paint.setColor(0xFFFFFFFF);
            c.drawCircle(px, py, 15f, paint);

            paint.setColor(0xFFFF9B27);
            c.drawCircle(px, py, 10f, paint);
        }

        c.restore();
    }

    private float mapPointerX(MotionEvent e, int index) {
        return (e.getX(index) - originX) / scale;
    }

    private float mapPointerY(MotionEvent e, int index) {
        return (e.getY(index) - originY) / scale;
    }

    private boolean handleMapGesture(MotionEvent e, int action) {
        if (section != 3 || zoneMap == null) {
            mapTouchActive = false;
            return false;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            float x = mapPointerX(e, 0);
            float y = mapPointerY(e, 0);
            mapTouchActive = mapArea().contains(x, y);

            if (!mapTouchActive) return false;

            mapDownX = mapTouchX = x;
            mapDownY = mapTouchY = y;
            mapPinchDistance = 0f;
            mapGestureMoved = false;
            return true;
        }

        if (!mapTouchActive) return false;

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (e.getPointerCount() >= 2) {
                float dx = mapPointerX(e, 1) - mapPointerX(e, 0);
                float dy = mapPointerY(e, 1) - mapPointerY(e, 0);
                mapPinchDistance = (float)Math.hypot(dx, dy);
                mapGestureMoved = true;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() >= 2) {
                float dx = mapPointerX(e, 1) - mapPointerX(e, 0);
                float dy = mapPointerY(e, 1) - mapPointerY(e, 0);
                float distance = (float)Math.hypot(dx, dy);

                if (mapPinchDistance > 10f && distance > 10f) {
                    mapZoom = Math.max(1f, Math.min(5f,
                        mapZoom * distance / mapPinchDistance));
                }

                mapPinchDistance = distance;
                mapGestureMoved = true;
                invalidate();
                return true;
            }

            float x = mapPointerX(e, 0);
            float y = mapPointerY(e, 0);

            if (mapPinchDistance > 0f) {
                mapPinchDistance = 0f;
                mapTouchX = x;
                mapTouchY = y;
                return true;
            }

            if (!mapGestureMoved &&
                Math.hypot(x - mapDownX, y - mapDownY) > 10.0) {
                mapGestureMoved = true;
            }

            if (mapGestureMoved) {
                if (mapFollow) {
                    int gx = value(13);
                    int gy = value(14);

                    if (gx >= 0 && gy >= 0 &&
                        zoneWorldW > 0 && zoneWorldH > 0) {
                        mapFocusX = mapClamp((float)gx / zoneWorldW);
                        mapFocusY = mapClamp((float)gy / zoneWorldH);
                    }
                    mapFollow = false;
                }

                RectF area = mapArea();
                float fit = Math.min(
                    area.width() / zoneMap.getWidth(),
                    area.height() / zoneMap.getHeight());

                float dw = zoneMap.getWidth() * fit * mapZoom;
                float dh = zoneMap.getHeight() * fit * mapZoom;

                mapFocusX = mapClamp(
                    mapFocusX - (x - mapTouchX) / dw);
                mapFocusY = mapClamp(
                    mapFocusY - (y - mapTouchY) / dh);

                invalidate();
            }

            mapTouchX = x;
            mapTouchY = y;
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            mapPinchDistance = 0f;
            mapGestureMoved = true;

            int remaining = e.getActionIndex() == 0 ? 1 : 0;
            if (remaining < e.getPointerCount()) {
                mapTouchX = mapPointerX(e, remaining);
                mapTouchY = mapPointerY(e, remaining);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            float x = mapPointerX(e, 0);
            float y = mapPointerY(e, 0);
            long now = SystemClock.uptimeMillis();

            if (!mapGestureMoved) {
                if (now - mapLastTapTime < 360 &&
                    Math.hypot(x - mapLastTapX,
                               y - mapLastTapY) < 55.0) {
                    mapFollow = true;
                    mapLastTapTime = 0;
                    Log.i("LEGACY1_MAP", "Seguimiento reactivado");
                } else {
                    mapLastTapTime = now;
                    mapLastTapX = x;
                    mapLastTapY = y;
                }
            } else {
                mapLastTapTime = 0;
            }

            mapTouchActive = false;
            mapGestureMoved = false;
            mapPinchDistance = 0f;
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            mapTouchActive = false;
            mapGestureMoved = false;
            mapPinchDistance = 0f;
            return true;
        }

        return true;
    }

    private void drawStats(Canvas c) {
        text(c, word(0), 145, 179, 64f * .8f, 650, false);
        text(c, number(4), 1130, 179, 64f * .8f, 280, true);
        text(c, word(1), 145, 278, 64f * .8f, 650, false);
        text(c, number(5), 1130, 278, 64f * .8f, 280, true);
        text(c, word(2), 145, 377, 64f * .8f, 650, false);
        text(c, number(6), 1130, 377, 64f * .8f, 280, true);
    }
    private void drawGoku(Canvas c) {
        text(c, word(3) + ":", 820, 589, 56f * .8f, 350, false);
        text(c, number(7), 1170, 659, 58f * .8f, 350, true);
        text(c, word(4) + ":", 820, 741, 56f * .8f, 350, false);
        text(c, number(9), 1170, 809, 58f * .8f, 350, true);
        text(c, word(5), 820, 900, 50f * .8f, 350, false);
        text(c, time(), 1170, 966, 43f * .8f, 350, true);
    }
    // Use ONE font size for all 4 captions, across all 5 game languages.
    // Press Start 2P is intentionally wide; never let "Inventario" touch an edge.
    private float uniformHomeButtonFontSize() {
        float common = 48f;
        for (int lang = 0; lang < WORDS.length; ++lang) {
            for (int item = 6; item <= 9; ++item) {
                final String label = WORDS[lang][item];
                for (RectF rect : HOME) {
                    float available = rect.width() - BUTTON_SIDE_MARGIN * 2f;
                    common = Math.min(common, fit(label, 48f, available));
                }
            }
        }
        return common;
    }

    private static final class Entry {
        final int key;
        final String label;
        final String detail;
        Entry(int key, String label, String detail) {
            this.key = key; this.label = label; this.detail = detail;
        }
    }
    private static String at(String[] names, int index) {
        return index >= 0 && names.length > index && names[index] != null ? names[index] : "";
    }

    // The ROM, not the Android UI, determines which entries exist.
    // 20..77 are RESERVED for the original map/XY diagnostics.
    // 128..202: 75 journal statuses; 203..214: 12 item categories;
    // 215..217: learned attacks; the original map ABI is unchanged.
    private List<Entry> entries() {
        ArrayList<Entry> result = new ArrayList<>();
        if (section == 0) {
            for (int i = 0; i < 75; ++i) {
                int status = value(128 + i);
                String name = at(journalNames, i);
                if (status > 0 && !name.isEmpty())
                    result.add(new Entry(i, name, word(status == 2 ? 20 : 21)));
            }
        } else if (section == 1) {
            for (int i = 0; i < 12; ++i) {
                int count = value(203 + i);
                if (count <= 0) continue;
                String name = at(itemNames, i * 2 + (count == 1 ? 0 : 1));
                if (name.isEmpty()) name = at(itemNames, i * 2);
                if (!name.isEmpty()) result.add(new Entry(i, name, "×" + count));
            }
        } else if (section == 2) {
            for (int i = 0; i < 3; ++i) {
                if (value(215 + i) != 1) continue; // Learned skills ONLY.
                String skill = at(itemNames, 24 + i);
                if (!skill.isEmpty()) result.add(new Entry(i, skill,
                    selectedKeys[2] == i ? word(15) : ""));
            }
        }
        return result;
    }
    private float maxScroll(List<Entry> list) {
        return Math.max(0f, list.size() * CARD_STEP - (CARD_BOTTOM - CARD_TOP));
    }
    private float clampedScroll(List<Entry> list) {
        if (section < 0 || section >= scrollOffset.length) return 0;
        scrollOffset[section] = Math.max(0f,
            Math.min(scrollOffset[section], maxScroll(list)));
        return scrollOffset[section];
    }
    private void drawSectionEntries(Canvas canvas) {
        final List<Entry> list = entries();
        if (list.isEmpty()) {
            String message = value(1) == 1 ? word(12) : word(11);
            text(canvas, message, 120, 318, CONTENT_FONT, 1000, false);
            return;
        }
        final float scroll = clampedScroll(list);
        canvas.save();
        canvas.clipRect(CARD_LEFT - 4, CARD_TOP - 2, CARD_RIGHT + 4, CARD_BOTTOM);
        for (int i = 0; i < list.size(); ++i) {
            final float top = CARD_TOP + i * CARD_STEP - scroll;
            if (top >= CARD_BOTTOM || top + CARD_HEIGHT <= CARD_TOP) continue;
            final Entry entry = list.get(i);
            RectF rect = new RectF(CARD_LEFT, top, CARD_RIGHT, top + CARD_HEIGHT);
            boolean selected = selectedKeys[section] == entry.key;
            boolean pressed = held == 100 + i;
            paint.reset(); paint.setAntiAlias(false); paint.setColor(0xFF082D60);
            canvas.drawPath(bevel(rect, 12), paint);
            RectF inside = new RectF(rect); inside.inset(3, 3);
            paint.setColor(selected || pressed ? 0xFFE7F8FF : 0xFFA5C8EA);
            canvas.drawPath(bevel(inside, 10), paint);
            inside.inset(4, 4);
            paint.setColor(selected || pressed ? 0xC949C8FC : 0xB30A60A9);
            canvas.drawPath(bevel(inside, 8), paint);
            final String detail = entry.detail;
            final float rightReserve = section == 1 ? 170f : 55f;
            // Identical base text size in Quests, Inventory and Attacks.
            text(canvas, entry.label, rect.left + 27, top + (detail.isEmpty() ? 58 : 44),
                CONTENT_FONT, rect.width() - rightReserve - 40, false);
            if (!detail.isEmpty()) {
                if (section == 1)
                    text(canvas, detail, rect.right - 25, top + 60, CONTENT_FONT,
                        155, true);
                else
                    text(canvas, detail, rect.left + 27, top + 75, 20f,
                        rect.width() - 65, false);
            }
        }
        canvas.restore();
    }

    private Path bevel(RectF r, float cut) {
        Path p = new Path(); p.moveTo(r.left + cut, r.top); p.lineTo(r.right - cut, r.top);
        p.lineTo(r.right, r.top + cut); p.lineTo(r.right, r.bottom - cut);
        p.lineTo(r.right - cut, r.bottom); p.lineTo(r.left + cut, r.bottom);
        p.lineTo(r.left, r.bottom - cut); p.lineTo(r.left, r.top + cut); p.close(); return p;
    }
    private void button(Canvas c, RectF rect, String label, int id, float size) {
        boolean pressed = held == id || (held == -1 && SystemClock.uptimeMillis() < pulseUntil && id == lastTarget);
        paint.reset(); paint.setAntiAlias(false); paint.setStyle(Paint.Style.FILL); paint.setColor(0xFF07192C);
        c.drawPath(bevel(rect, 20), paint);
        RectF inside = new RectF(rect); inside.inset(6, 6);
        paint.setColor(0xFFECFCFF); c.drawPath(bevel(inside, 17), paint);
        inside.inset(7, 7); paint.setColor(0xFF214B7E); c.drawPath(bevel(inside, 13), paint);
        inside.inset(5, 5);
        paint.setShader(new LinearGradient(inside.left, inside.top, inside.right, inside.bottom,
            pressed ? new int[]{0xFF79F4FF, 0xFF0088D9} : new int[]{0xFF159EEA, 0xFF0752B1}, null, Shader.TileMode.CLAMP));
        c.drawPath(bevel(inside, 9), paint); paint.setShader(null);
        c.save(); c.clipPath(bevel(inside, 9));
        paint.setColor(pressed ? 0x99FFFFFF : 0x335ADFFF);
        Path shine = new Path();
        shine.moveTo(inside.left - 30, inside.top);
        shine.lineTo(inside.left + 42, inside.top);
        shine.lineTo(inside.left, inside.top + 74);
        shine.lineTo(inside.left - 72, inside.top + 74);
        shine.close(); c.drawPath(shine, paint);
        c.restore();
        paint.setColor(pressed ? 0xFFB4FFFF : 0xFF53CFFF); paint.setStrokeWidth(5);
        c.drawLine(inside.left + 13, inside.top + 5, inside.right - 13, inside.top + 5, paint);
        c.drawLine(inside.left + 5, inside.top + 15, inside.left + 5, inside.bottom - 15, paint);
        float textSize = fit(label, size,
            rect.width() - (id >= 0 && id < 4 ? BUTTON_SIDE_MARGIN * 2f : 32f));
        paint.setTypeface(font); paint.setTextSize(textSize);
        float x = rect.centerX() - paint.measureText(label) / 2;
        text(c, label, x, rect.centerY() + textSize * .38f + (pressed ? 4 : 0), textSize, rect.width() - 32, false);
    }
    private float fit(String text, float size, float maxWidth) {
        paint.reset(); paint.setTypeface(font); paint.setTextSize(size);
        return paint.measureText(text) > maxWidth ? size * maxWidth / paint.measureText(text) : size;
    }
    private void text(Canvas c, String label, float x, float baseline, float size, float maxWidth, boolean right) {
        if (label == null) return;
        size = fit(label, size, maxWidth);
        paint.reset(); paint.setTypeface(font); paint.setTextSize(size); paint.setAntiAlias(false);
        paint.setTextAlign(right ? Paint.Align.RIGHT : Paint.Align.LEFT);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.MITER); paint.setStrokeWidth(Math.max(3, size / 9));
        paint.setColor(0xFF071324); c.drawText(label, x + 3, baseline + 5, paint); c.drawText(label, x, baseline, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, baseline - size, 0, baseline,
            new int[]{0xFFFFF5B1, 0xFFFFD581, 0xFFEE974B}, null, Shader.TileMode.CLAMP));
        c.drawText(label, x, baseline, paint); paint.setShader(null);
    }
    private int lastTarget = -1;
    private int target(float x, float y) {
        if (x < 0 || y < 0 || x > WIDTH || y > HEIGHT) return -1;
        if (pendingSection != -2) return -1;
        if (section < 0) {
            for (int i = 0; i < 4; ++i) if (HOME[i].contains(x, y)) return i;
        } else {
            if (BACK.contains(x, y)) return 10;
            if (x >= CARD_LEFT && x <= CARD_RIGHT && y >= CARD_TOP && y <= CARD_BOTTOM) {
                List<Entry> list = entries();
                float position = y - CARD_TOP + clampedScroll(list);
                int row = (int) (position / CARD_STEP);
                if (row >= 0 && row < list.size() && position - row * CARD_STEP < CARD_HEIGHT)
                    return 100 + row;
            }
        }
        return -1;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (section == 3 && handleMapGesture(event, action))
            return true;
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0);
            final float x = (event.getX() - originX) / scale;
            final float y = (event.getY() - originY) / scale;
            downY = y;
            downInList = section >= 0 && section < 3 &&
                x >= CARD_LEFT && x <= CARD_RIGHT && y >= CARD_TOP && y < CARD_BOTTOM;
            startScrollOffset = section >= 0 && section < 3 ? scrollOffset[section] : 0;
            dragging = false;
            armedHerbCategory = -1;
            held = target(x, y);
            lastTarget = held; pulseUntil = 0;
            if (held >= 0) {
                int actionTarget = held; lastTarget = held;
                pulseUntil = SystemClock.uptimeMillis() + 220;
                // Same press sound on entering AND backing out, on ACTION_DOWN.
                if (sounds != null && pressLoaded) {
                    int stream = sounds.play(pressSound, 1, 1, 1, 0, 1);
                    Log.d(TAG, "touch sound stream=" + stream);
                }
                if (actionTarget >= 100 && section >= 0 && section < 3) {
                    List<Entry> list = entries();
                    int index = actionTarget - 100;
                    if (index >= 0 && index < list.size()) {
                        int key = list.get(index).key;

                        // Primera pulsación: seleccionar.
                        // Segunda pulsación: preparar consumo verificado.
                        if (section == 1 &&
                            selectedKeys[1] == key &&
                            (key == 2 || key == 5))
                            armedHerbCategory = key;

                        if (section != 2)
                            selectedKeys[section] = key;
                    }
                } else if (actionTarget < 4 || actionTarget == 10) {
                    pendingSection = actionTarget == 10 ? -1 : actionTarget;
                    handler.postDelayed(navigate, 120);
                }
                Log.d(TAG, "ACTION_DOWN target=" + actionTarget + " language=" + language);
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (pendingSection != -2) return true;
            int index = event.findPointerIndex(pointer);
            if (index < 0) return true;
            final float y = (event.getY(index) - originY) / scale;
            if (downInList && Math.abs(y - downY) > 12f) dragging = true;
            if (dragging && section >= 0 && section < 3) {
                List<Entry> list = entries();
                scrollOffset[section] = Math.max(0f,
                    Math.min(maxScroll(list), startScrollOffset + downY - y));
                held = -1; pulseUntil = 0;
            } else {
                held = target((event.getX(index) - originX) / scale, y) == lastTarget
                    ? lastTarget : -1;
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer)) {
            // Equip attacks on ACTION_UP, never during scrolling.
            if (action == MotionEvent.ACTION_UP &&
                section == 2 && held >= 100 && !dragging) {

                List<Entry> attacks = entries();
                int row = held - 100;

                if (row >= 0 && row < attacks.size()) {
                    int attack = attacks.get(row).key;

                    if (attack >= 0 && attack < 3 &&
                        value(215 + attack) == 1) {

                        Legacy1Native.equipAttack(attack);
                        selectedKeys[2] = attack;

                        Log.i("LEGACY1_ATTACK",
                            "EQUIP_REQUEST attack=" + attack);
                    }
                }
            }

            // Consumir al SOLTAR, nunca durante un desplazamiento.
            if (action == MotionEvent.ACTION_UP &&
                section == 1 && held >= 100 &&
                !dragging && armedHerbCategory >= 0 &&
                armedHerbCategory == selectedKeys[1] &&
                value(203 + armedHerbCategory) > 0 &&
                value(7) >= 0 && value(8) >= value(7) &&
                value(9) >= 0 && value(10) >= value(9) &&
                (value(8) > value(7) ||
                 (armedHerbCategory == 5 && value(10) > value(9)))) {

                Legacy1Native.consumeInventoryCategory(armedHerbCategory);
                Log.i("LEGACY1_ITEM_TRACE",
                    "SOLICITUD_INFERIOR categoria=" + armedHerbCategory);
            }
            armedHerbCategory = -1;

            if (action == MotionEvent.ACTION_UP && held >= 0) {
                pulseUntil = SystemClock.uptimeMillis() + 140;
                performClick();
            } else pulseUntil = 0;
            held = pointer = -1; downInList = dragging = false;
            invalidate(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}

