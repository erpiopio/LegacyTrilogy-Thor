package org.novapowers.buusfury.thor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.HashMap;

/** Mini/mapa de escenario autónomo. Solo lectura; nunca escribe estado GBA. */
final class ThorAutomaticMap {
    private static final class Entry {
        final String png;
        final int worldWidth, worldHeight;
        Entry(String p, int w, int h) { png = p; worldWidth = w; worldHeight = h; }
    }
    private final File mapsDirectory;
    private final HashMap<Integer, Entry> entries = new HashMap<>();
    private final Paint paint = new Paint();
    private Bitmap bitmap;
    private Entry current;
    private int area = -1;
    private float gokuX, gokuY;
    private boolean positionValid;

    ThorAutomaticMap(Context ctx) {
        File directory = null;
        try {
            if (!ThorRomIdentity.verifyActive(
                    ctx, ThorRomIdentity.activeFile(ctx))) {
                throw new IOException("ROM no verificada");
            }

            String sha1 = ThorRomIdentity.activeSha1();

            if (!ThorMapExtractor.mapsReady(ctx, sha1)) {
                throw new IOException("Mapas privados no preparados");
            }

            directory = ThorMapExtractor.mapsDirectory(ctx, sha1);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(new File(directory, "index.csv")),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length != 4) continue;
                entries.put(Integer.parseInt(f[0], 16),
                    new Entry(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3])));
            }
            }
        } catch (Exception e) {
            entries.clear();
            directory = null;
            android.util.Log.e("THOR_AUTO_MAP", "No se pudo cargar el índice", e);
        }
        mapsDirectory = directory;
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
    }

    void update(int[] snapshot) {
        if (snapshot == null || snapshot.length < 9 || snapshot[0] != 1) return;
        int next = snapshot[7] & 0xFFFFFF;
        // Native: x e y en coordenadas de mundo con precisión fija 24.8.
        gokuX = snapshot[1] / 256f;
        gokuY = snapshot[2] / 256f;
        positionValid = true;
        if (next == area) return;
        area = next;
        current = entries.get(next);
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
        if (current == null) return;
        try (java.io.InputStream is = new FileInputStream(new File(mapsDirectory, current.png))) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            bitmap = BitmapFactory.decodeStream(is, null, options);
        } catch (Exception e) {
            android.util.Log.e("THOR_AUTO_MAP", "No se pudo cargar " + current.png, e);
        }
    }

    boolean hasMap() { return bitmap != null && current != null; }

    void draw(Canvas canvas, RectF panel) {
        canvas.save();
        canvas.clipRect(panel);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(9, 14, 22));
        canvas.drawRect(panel, paint);

        if (!hasMap()) {
            paint.setColor(Color.rgb(190, 200, 210));
            paint.setTextSize(33f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("MAPA NO DISPONIBLE",
                panel.centerX(), panel.centerY(), paint);
            canvas.restore();
            return;
        }

        // THOR_HQ_MAP_V2: zoom entero, sin interpolación.
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);

        int zoom = Math.max(2, (int) Math.ceil(Math.max(
            panel.width() / bitmap.getWidth(),
            panel.height() / bitmap.getHeight())));

        float drawW = bitmap.getWidth() * zoom;
        float drawH = bitmap.getHeight() * zoom;

        float px = bitmap.getWidth() / 2f;
        float py = bitmap.getHeight() / 2f;

        if (positionValid && current.worldWidth > 0
                && current.worldHeight > 0) {
            px = Math.max(0f, Math.min(bitmap.getWidth(),
                gokuX * bitmap.getWidth() / current.worldWidth));
            py = Math.max(0f, Math.min(bitmap.getHeight(),
                gokuY * bitmap.getHeight() / current.worldHeight));
        }

        // Cámara centrada en Goku, limitada por los bordes del mapa.
        float left = panel.centerX() - px * zoom;
        float top = panel.centerY() - py * zoom;

        if (drawW <= panel.width())
            left = panel.centerX() - drawW / 2f;
        else
            left = Math.min(panel.left,
                Math.max(panel.right - drawW, left));

        if (drawH <= panel.height())
            top = panel.centerY() - drawH / 2f;
        else
            top = Math.min(panel.top,
                Math.max(panel.bottom - drawH, top));

        paint.setColor(Color.WHITE);
        canvas.drawBitmap(bitmap, null,
            new RectF(left, top, left + drawW, top + drawH), paint);

        if (positionValid && current.worldWidth > 0
                && current.worldHeight > 0) {
            float x = left + px * zoom;
            float y = top + py * zoom;

            paint.setColor(Color.rgb(255, 205, 35));
            canvas.drawCircle(x, y, 9f, paint);
            paint.setColor(Color.rgb(255, 36, 25));
            canvas.drawCircle(x, y, 6f, paint);
        }

        canvas.restore();
    }
}
