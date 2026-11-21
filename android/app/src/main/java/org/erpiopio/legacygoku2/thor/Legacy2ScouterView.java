package org.erpiopio.legacygoku2.thor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Cached original regional geometry; only the marker changes each frame. */
final class Legacy2ScouterView {
    private final Context context;
    private final Paint paint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private JSONObject manifest;
    private File directory;
    private int[] palette;
    private Bitmap atlas, marker;
    private int region = -1, reveal = -1;
    private int[] visited;
    private long retryAfter;

    // Caja visible real del contenido del mapa actual.
    private int contentLeft = 0;
    private int contentTop = 0;
    private int contentRight = 240;
    private int contentBottom = 160;

    Legacy2ScouterView(Context c) {
        context = c;
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        glowPaint.setStyle(Paint.Style.FILL);
        markerPaint.setStyle(Paint.Style.FILL);
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 255) | ((b[o + 1] & 255) << 8);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int greenize(int color) {
        int a = (color >>> 24) & 255;
        if (a == 0) return 0;

        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;
        int lum = (r * 30 + g * 59 + b * 11) / 100;

        // Verde oscuro tipo Scouter, sin fluorescencia.
        int rr = 8 + lum * 18 / 100;
        int gg = 20 + lum * 48 / 100;
        int bb = 10 + lum * 19 / 100;

        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private void load() throws Exception {
        directory = Legacy2ScouterResources.directory(context);
        manifest = Legacy2ScouterResources.manifest(directory);

        byte[] raw = Files.readAllBytes(new File(directory, "background.pal").toPath());
        palette = new int[256];
        for (int i = 0; i < 256; ++i) {
            int c = u16(raw, i * 2);
            int r = c & 31, g = (c >> 5) & 31, b = (c >> 10) & 31;
            palette[i] = 0xff000000
                | ((r * 255 / 31) << 16)
                | ((g * 255 / 31) << 8)
                | (b * 255 / 31);
        }

        byte[] icon = Files.readAllBytes(
            new File(directory, "player-marker.indices").toPath());
        int[] colors = new int[64];
        for (int i = 0; i < 64; ++i) {
            int idx = icon[i] & 255;
            colors[i] = idx == 0 ? 0 : greenize(palette[idx]);
        }
        marker = Bitmap.createBitmap(colors, 8, 8, Bitmap.Config.ARGB_8888);
    }

    private void rebuild(int[] state) throws Exception {
        JSONObject group = manifest.getJSONArray("regions").getJSONObject(state[1]);
        byte[] graphics = Files.readAllBytes(
            new File(directory, group.getString("graphics")).toPath());

        int[] pixels = new int[1024 * 1024];
        Arrays.fill(pixels, 0);

        int left = 1024, top = 1024, right = 0, bottom = 0;
        boolean any = false;

        JSONArray rooms = group.getJSONArray("rooms");
        for (int i = 0; i < rooms.length(); ++i) {
            JSONObject room = rooms.getJSONObject(i);
            int bit = room.getInt("visitedBit");
            if (bit < 0 || bit >= 1024)
                throw new IllegalArgumentException("Scouter visited bit");

            boolean seen = (state[11 + bit / 8] & (1 << (bit & 7))) != 0;
            if (!seen && state[9] == 0) continue;

            JSONArray rects = room.getJSONArray("rectangles");
            int offset = 0;
            int k = seen ? 4 : 0;
            if (seen) {
                offset = (rects.getInt(2) - rects.getInt(0))
                    * (rects.getInt(3) - rects.getInt(1)) * 2;
            }

            int l = rects.getInt(k);
            int t = rects.getInt(k + 1);
            int r = rects.getInt(k + 2);
            int b = rects.getInt(k + 3);

            if (l < r && t < b) {
                left = Math.min(left, l * 8);
                top = Math.min(top, t * 8);
                right = Math.max(right, r * 8);
                bottom = Math.max(bottom, b * 8);
                any = true;
            }

            byte[] map = Files.readAllBytes(
                new File(directory, room.getString("tilemap")).toPath());

            for (int y = t; y < b; ++y) {
                for (int x = l; x < r; ++x) {
                    int tile = u16(map, offset);
                    offset += 2;
                    if (tile == 0) continue;

                    for (int yy = 0; yy < 8; ++yy) {
                        for (int xx = 0; xx < 8; ++xx) {
                            int sx = x * 8 + xx;
                            int sy = y * 8 + yy;
                            int a = (tile & 1023) * 64
                                + (((tile & 2048) != 0 ? 7 - yy : yy) * 8)
                                + ((tile & 1024) != 0 ? 7 - xx : xx);
                            if (sx < 0 || sy < 0 || sx >= 1024 || sy >= 1024
                                    || a >= graphics.length) continue;
                            int idx = graphics[a] & 255;
                            if (idx == 0) continue;
                            pixels[sy * 1024 + sx] = greenize(palette[idx]);
                        }
                    }
                }
            }
        }

        Bitmap replacement = Bitmap.createBitmap(
            pixels, 1024, 1024, Bitmap.Config.ARGB_8888);
        if (atlas != null) atlas.recycle();
        atlas = replacement;

        int mx = clamp(state[7], 0, 1023);
        int my = clamp(state[8], 0, 1023);

        if (any) {
            contentLeft = clamp(Math.min(left, mx) - 28, 0, 1023);
            contentTop = clamp(Math.min(top, my) - 28, 0, 1023);
            contentRight = clamp(Math.max(right, mx) + 28, 1, 1024);
            contentBottom = clamp(Math.max(bottom, my) + 28, 1, 1024);
        } else {
            contentLeft = clamp(mx - 96, 0, 1023);
            contentTop = clamp(my - 72, 0, 1023);
            contentRight = clamp(mx + 96, 1, 1024);
            contentBottom = clamp(my + 72, 1, 1024);
        }

        if (contentRight <= contentLeft) contentRight = Math.min(1024, contentLeft + 1);
        if (contentBottom <= contentTop) contentBottom = Math.min(1024, contentTop + 1);

        region = state[1];
        reveal = state[9];
        visited = Arrays.copyOfRange(state, 11, 139);
    }

    boolean draw(Canvas c, RectF viewport, int[] state) {
        if (state == null || state.length != 139 || state[0] != 1
                || state[1] < 0 || state[1] >= 68)
            return false;

        try {
            if (manifest == null) {
                if (android.os.SystemClock.uptimeMillis() < retryAfter)
                    return false;
                load();
            }

            int[] nowVisited = Arrays.copyOfRange(state, 11, 139);
            if (region != state[1] || reveal != state[9]
                    || !Arrays.equals(visited, nowVisited)) {
                rebuild(state);
            }

            if (atlas == null) return false;

            c.save();
            c.clipRect(viewport);

            // Fondo oscuro verdoso, más cercano al Scouter original.
            paint.setColor(Color.rgb(36, 42, 38));
            c.drawRect(viewport, paint);

            float boxLeft = contentLeft;
            float boxTop = contentTop;
            float boxRight = contentRight;
            float boxBottom = contentBottom;
            float contentW = Math.max(1f, boxRight - boxLeft);
            float contentH = Math.max(1f, boxBottom - boxTop);

            // THOR_SCOUTER_FOLLOW:
            // Zoom 2.4 veces mayor, con escalado pixel art.
            float ajuste = Math.min(
                viewport.width() / contentW,
                viewport.height() / contentH
            );

            float zoom = (float) Math.ceil(
                Math.min(24f, Math.max(2f, ajuste * 2.4f))
            );

            // Centrar la cámara en el marcador del personaje.
            float mx = Math.max(boxLeft,
                Math.min(boxRight, (float) state[7]));
            float my = Math.max(boxTop,
                Math.min(boxBottom, (float) state[8]));

            float drawLeft = viewport.centerX() - mx * zoom;
            float drawTop = viewport.centerY() - my * zoom;

            // Respetar los bordes del mapa.
            // Cuando el mapa es pequeño, mantenerlo centrado.
            if (contentW * zoom <= viewport.width()) {
                drawLeft = viewport.centerX()
                    - (boxLeft + boxRight) * 0.5f * zoom;
            } else {
                drawLeft = Math.max(
                    viewport.right - boxRight * zoom,
                    Math.min(
                        viewport.left - boxLeft * zoom,
                        drawLeft
                    )
                );
            }

            if (contentH * zoom <= viewport.height()) {
                drawTop = viewport.centerY()
                    - (boxTop + boxBottom) * 0.5f * zoom;
            } else {
                drawTop = Math.max(
                    viewport.bottom - boxBottom * zoom,
                    Math.min(
                        viewport.top - boxTop * zoom,
                        drawTop
                    )
                );
            }

            c.drawBitmap(atlas, null, new RectF(
                drawLeft,
                drawTop,
                drawLeft + atlas.getWidth() * zoom,
                drawTop + atlas.getHeight() * zoom
            ), paint);

            float x = drawLeft + state[7] * zoom;
            float y = drawTop + state[8] * zoom;

            float pulse = (float) (0.65
                + 0.35 * Math.sin(android.os.SystemClock.uptimeMillis() / 170.0));

            // Halo exterior.
            glowPaint.setColor(Color.argb(
                (int) (22 + 20 * pulse), 150, 255, 190));
            c.drawCircle(x, y, Math.max(14f, zoom * (6.5f + 2.0f * pulse)), glowPaint);

            // Halo interior.
            glowPaint.setColor(Color.argb(
                (int) (90 + 70 * pulse), 165, 255, 205));
            c.drawCircle(x, y, Math.max(8f, zoom * (3.2f + 1.2f * pulse)), glowPaint);

            // Punto central brillante.
            markerPaint.setColor(Color.rgb(255, 110, 150));
            c.drawCircle(x, y, Math.max(4.5f, zoom * 1.15f), markerPaint);

            markerPaint.setColor(Color.rgb(255, 230, 236));
            c.drawCircle(x, y, Math.max(2f, zoom * 0.45f), markerPaint);

            c.restore();
            return true;
        } catch (Exception error) {
            android.util.Log.e("Legacy2Scouter",
                "Cannot render extracted region", error);
            manifest = null;
            retryAfter = android.os.SystemClock.uptimeMillis() + 5000;
            return false;
        }
    }
}
