package org.novapowers.buusfury.thor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** Shared, bounded Pixel Operator labels with a hard pixel shadow. */
final class ThorLabelPainter {
    static final int GOLD = Color.rgb(247, 218, 157);
    static final int WHITE = Color.rgb(248, 248, 240);
    static final int PALE = Color.rgb(222, 231, 209);
    static final int VALUE = Color.rgb(180, 182, 178);
    private final Paint paint = new Paint();

    ThorLabelPainter(Context context) {
        paint.setTypeface(Typeface.createFromAsset(context.getAssets(), "buusfury/PixelOperator.ttf"));
        paint.setAntiAlias(false);
        paint.setSubpixelText(false);
        paint.setFilterBitmap(false);
        paint.setHinting(Paint.HINTING_ON);
    }

    float fittedSize(String value, float preferred, float width, boolean bold) {
        paint.setFakeBoldText(bold);
        paint.setTextSize(preferred);
        float measured = paint.measureText(value);
        // Round down to a whole pixel; accents and the shadow must stay in the cell.
        float size = measured > width ? Math.max(1f, (float) Math.floor(preferred * width / measured))
                : preferred;
        paint.setTextSize(size);
        while (size > 1f && paint.measureText(value) > width) paint.setTextSize(--size);
        return size;
    }

    void draw(Canvas canvas, String value, float x, float baseline, float size,
              float width, Paint.Align align, int color, boolean bold) {
        if (value == null || value.isEmpty() || width <= 0f) return;
        paint.setTextSize(fittedSize(value, size, width - 4f, bold));
        paint.setTextAlign(align);
        paint.setColor(Color.rgb(9, 14, 10));
        canvas.drawText(value, Math.round(x) + 3f, Math.round(baseline) + 4f, paint);
        paint.setColor(color);
        canvas.drawText(value, Math.round(x), Math.round(baseline), paint);
    }

    void drawWrapped(Canvas canvas, String value, float left, float top, int width,
                     int height, float size, int maxLines, int color) {
        if (value == null || value.isEmpty()) return;
        TextPaint paragraph = new TextPaint(paint);
        paragraph.setTextAlign(Paint.Align.LEFT);
        paragraph.setFakeBoldText(true);
        StaticLayout layout;
        do {
            paragraph.setTextSize(size);
            layout = StaticLayout.Builder.obtain(value, 0, value.length(), paragraph, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                    .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
            if (layout.getHeight() <= height && layout.getEllipsisCount(layout.getLineCount() - 1) == 0)
                break;
            size -= 1f;
        } while (size >= 24f);
        canvas.save();
        canvas.translate(Math.round(left) + 3f, Math.round(top) + 4f);
        paragraph.setColor(Color.rgb(9, 14, 10));
        layout.draw(canvas);
        canvas.translate(-3f, -4f);
        paragraph.setColor(color);
        layout.draw(canvas);
        canvas.restore();
    }
}
