package org.erpiopio.legacygoku2.thor;

/** Shared drawing/touch transform; the Thor's 1240x1080 panel stays 1:1. */
final class Legacy2MenuViewport {
    static final float WIDTH = 1240f, HEIGHT = 1080f;
    final float scale, left, top;

    Legacy2MenuViewport(int width, int height) {
        scale = width > 0 && height > 0
            ? Math.min(width / WIDTH, height / HEIGHT) : 1f;
        left = (width - WIDTH * scale) / 2f;
        top = (height - HEIGHT * scale) / 2f;
    }

    float x(float screenX) { return (screenX - left) / scale; }
    float y(float screenY) { return (screenY - top) / scale; }
    boolean contains(float screenX, float screenY) {
        float x = x(screenX), y = y(screenY);
        return x >= 0f && x <= WIDTH && y >= 0f && y <= HEIGHT;
    }
}
