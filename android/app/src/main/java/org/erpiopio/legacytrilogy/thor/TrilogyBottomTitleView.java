package org.erpiopio.legacytrilogy.thor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

public final class TrilogyBottomTitleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TrilogyBottomTitleView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setShader(new RadialGradient(
            w / 2f, h / 2f, Math.max(w, h) * 0.72f,
            new int[]{0xFF342019, 0xFF080D1D},
            null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        drawTitle(canvas, "DRAGON BALL",
            w / 2f, h * 0.47f, w * 0.83f, h * 0.19f);

        drawTitle(canvas, "LEGACY TRILOGY",
            w / 2f, h * 0.65f, w * 0.89f, h * 0.14f);
    }

    private void drawTitle(Canvas canvas, String text,
            float x, float y, float maxWidth, float size) {

        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(
            "sans-serif-condensed", Typeface.BOLD_ITALIC));
        paint.setTextSkewX(-0.13f);
        paint.setTextSize(size);

        float measured = paint.measureText(text);
        if (measured > maxWidth)
            paint.setTextSize(size * maxWidth / measured);

        float stroke = Math.max(5f, paint.getTextSize() * 0.085f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(stroke * 2.3f);
        paint.setColor(Color.BLACK);
        paint.setShadowLayer(10f, 8f, 9f, Color.BLACK);
        canvas.drawText(text, x, y, paint);
        paint.clearShadowLayer();

        paint.setStrokeWidth(stroke);
        paint.setColor(0xFFB71908);
        canvas.drawText(text, x, y, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
            0, y - paint.getTextSize(), 0, y,
            new int[]{0xFFFFFF93, 0xFFFFCB24, 0xFFFF8508},
            null, Shader.TileMode.CLAMP));
        canvas.drawText(text, x, y, paint);
        paint.setShader(null);
    }
}
