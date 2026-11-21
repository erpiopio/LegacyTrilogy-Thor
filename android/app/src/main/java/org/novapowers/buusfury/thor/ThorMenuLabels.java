package org.novapowers.buusfury.thor;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Locale;

/** Positions and styles recovered from the former 1240 x 1080 templates. */
final class ThorMenuLabels {
    private final ThorUiStrings ui;
    private final ThorLabelPainter labels;

    ThorMenuLabels(ThorUiStrings ui, ThorLabelPainter labels) {
        this.ui = ui;
        this.labels = labels;
    }

    private void title(Canvas canvas, String key, float width) {
        labels.draw(canvas, ui.text(key).toUpperCase(Locale.ROOT), 620f, 104f,
                108f, width, Paint.Align.CENTER, ThorLabelPainter.GOLD, true);
    }

    private void nav(Canvas canvas, String key, float x, float width, Paint.Align align) {
        labels.draw(canvas, ui.text(key), x, 120f, 84f, width, align,
                ThorLabelPainter.WHITE, false);
    }

    private void stat(Canvas canvas, String key, float x, float y, float width) {
        labels.draw(canvas, ui.text(key).toUpperCase(Locale.ROOT) + ":", x, y, 84f, width, Paint.Align.LEFT,
                ThorLabelPainter.GOLD, false);
    }

    void draw(Canvas canvas, int section) {
        switch (section) {
            case 1:
                title(canvas, "journal", 560f);
                nav(canvas, "status", 1224f, 350f, Paint.Align.RIGHT);
                break;
            case 2:
                title(canvas, "inventory", 470f);
                nav(canvas, "status", 16f, 292f, Paint.Align.LEFT);
                nav(canvas, "equipment", 1224f, 360f, Paint.Align.RIGHT);
                break;
            case 4:
                title(canvas, "equipment", 480f);
                nav(canvas, "inventory", 16f, 340f, Paint.Align.LEFT);
                nav(canvas, "map", 1224f, 350f, Paint.Align.RIGHT);
                for (int i = 0; i < 4; i++)
                    stat(canvas, ATTRIBUTE_KEYS[i], 770f, 285f + i * 90f, 156f);
                break;
            case 5:
                labels.draw(canvas, ui.text("status").toUpperCase(Locale.ROOT),
                        620f, 78f, 84f, 560f, Paint.Align.CENTER, ThorLabelPainter.GOLD, false);
                labels.draw(canvas, ui.text("inventory").toUpperCase(Locale.ROOT),
                        620f, 1056f, 84f, 690f, Paint.Align.CENTER, ThorLabelPainter.GOLD, false);
                break;
            default:
                title(canvas, section == 3 ? "skills" : "status", 520f);
                nav(canvas, "journal", 16f, 292f, Paint.Align.LEFT);
                nav(canvas, "inventory", 1224f, 350f, Paint.Align.RIGHT);
                if (section == 0) {
                    labels.draw(canvas, ui.text("skills"), 1187f, 981f, 84f, 396f,
                            Paint.Align.RIGHT, ThorLabelPainter.GOLD, false);
                    stat(canvas, "hp", 325f, 233f, 132f);
                    stat(canvas, "energy", 325f, 324f, 132f);
                    stat(canvas, "statLevel", 728f, 233f, 153f);
                    stat(canvas, "experience", 728f, 324f, 153f);
                    stat(canvas, "nextLevel", 728f, 415f, 266f);
                    for (int i = 0; i < 4; i++)
                        stat(canvas, ATTRIBUTE_KEYS[i], 755f, 555f + i * 90f, 143f);
                    stat(canvas, "money", 55f, 905f, 188f);
                    stat(canvas, "time", 55f, 998f, 230f);
                }
                break;
        }
    }

    private static final String[] ATTRIBUTE_KEYS = {"strength", "power", "endurance", "speed"};
}
