package org.gbarecomp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** LEGACY1_RA_OVERLAY_V1: achievements on the upper display only. */
public final class Legacy1RaOverlay {
    private static volatile WeakReference<GbaGameActivity> current =
        new WeakReference<>(null);

    private static final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private static final ExecutorService images =
        Executors.newSingleThreadExecutor();

    private static boolean showing;

    private Legacy1RaOverlay() {}

    private static final class Entry {
        final String title, description, badgeUrl;
        final int points;

        Entry(String t, String d, int p, String u) {
            title = t == null ? "Logro" : t;
            description = d == null ? "" : d;
            points = p;
            badgeUrl = u == null ? "" : u;
        }
    }

    public static void attach(GbaGameActivity activity) {
        current = new WeakReference<>(activity);
        queue.clear();
        showing = false;
    }

    public static void show(
        String title, String description, int points, String badgeUrl) {

        GbaGameActivity activity = current.get();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (current.get() != activity ||
                activity.isFinishing() ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()))
                return;

            queue.addLast(new Entry(title, description, points, badgeUrl));
            if (!showing) showNext(activity);
        });
    }

    private static int dp(Activity activity, float size) {
        return Math.round(
            size * activity.getResources().getDisplayMetrics().density);
    }

    private static TextView text(
        Activity activity, String value, int sp, int color, boolean bold) {

        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static void showNext(GbaGameActivity activity) {
        if (current.get() != activity) return;

        Entry entry = queue.pollFirst();
        if (entry == null) {
            showing = false;
            return;
        }
        showing = true;

        FrameLayout root = activity.findViewById(android.R.id.content);
        if (root == null) {
            showing = false;
            return;
        }

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 13),
                         dp(activity, 16), dp(activity, 13));
        panel.setClickable(false);
        panel.setFocusable(false);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(22, 27, 41));
        background.setCornerRadius(dp(activity, 17));
        background.setStroke(dp(activity, 2), 0xFFFFC44A);
        panel.setBackground(background);
        panel.setElevation(dp(activity, 12));

        ImageView badge = new ImageView(activity);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        badge.setImageResource(android.R.drawable.btn_star_big_on);

        LinearLayout.LayoutParams badgeParams =
            new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 64));
        badgeParams.rightMargin = dp(activity, 13);
        panel.addView(badge, badgeParams);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        labels.addView(text(
            activity, "★ LOGRO CONSEGUIDO · SOFTCORE",
            11, 0xFFFFC44A, true));

        TextView title = text(activity, entry.title, 17, Color.WHITE, true);
        title.setMaxLines(2);
        labels.addView(title);

        if (!entry.description.isEmpty()) {
            TextView desc = text(
                activity, entry.description, 12, 0xFFCFD6E3, false);
            desc.setMaxLines(2);
            labels.addView(desc);
        }

        labels.addView(text(
            activity, "+" + entry.points + " puntos",
            13, 0xFFFFC44A, true));

        panel.addView(labels, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        int screenWidth = activity.getResources()
            .getDisplayMetrics().widthPixels;
        int width = Math.min(
            dp(activity, 470),
            Math.max(dp(activity, 240), screenWidth - dp(activity, 40)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(activity, 25);

        root.addView(panel, params);

        panel.setAlpha(0f);
        panel.setTranslationY(-dp(activity, 35));
        panel.animate().alpha(1f).translationY(0f)
            .setDuration(300).start();

        loadBadge(activity, entry.badgeUrl, badge, panel);

        panel.postDelayed(() -> {
            panel.animate().alpha(0f)
                .translationY(-dp(activity, 25))
                .setDuration(300)
                .withEndAction(() -> {
                    root.removeView(panel);
                    if (current.get() == activity)
                        showNext(activity);
                }).start();
        }, 5000);
    }

    private static void loadBadge(
        GbaGameActivity activity, String address,
        ImageView target, LinearLayout panel) {

        if (address.isEmpty()) return;

        images.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(address);
                String host = url.getHost().toLowerCase(
                    java.util.Locale.ROOT);

                if (!"https".equalsIgnoreCase(url.getProtocol()) ||
                    !(host.equals("retroachievements.org") ||
                      host.endsWith(".retroachievements.org")))
                    return;

                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);

                try (java.io.InputStream in = connection.getInputStream();
                     java.io.ByteArrayOutputStream out =
                         new java.io.ByteArrayOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = in.read(buffer)) != -1) {
                        if (out.size() + size > 524288)
                            return;
                        out.write(buffer, 0, size);
                    }

                    byte[] bytes = out.toByteArray();
                    bitmap = BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.length);
                }
            } catch (Exception ignored) {
                // Badge is optional; never block gameplay.
            } finally {
                if (connection != null) connection.disconnect();
            }

            final Bitmap ready = bitmap;
            if (ready != null) {
                activity.runOnUiThread(() -> {
                    if (panel.getParent() != null &&
                        current.get() == activity)
                        target.setImageBitmap(ready);
                });
            }
        });
    }
}
