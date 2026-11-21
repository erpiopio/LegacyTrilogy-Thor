package org.gbarecomp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTPS transport for Legacy I's embedded rcheevos client. */
public final class Legacy1RaHttp {
    private static final ExecutorService WORKERS =
        Executors.newFixedThreadPool(2);

    private Legacy1RaHttp() {}

    // LEGACY1_RA_OVERLAY_V1
    public static void achievementUnlocked(
        String title, String description, int points, String badgeUrl) {
        Legacy1RaOverlay.show(title, description, points, badgeUrl);
    }

    public static void request(
        long id, String url, String post, String contentType) {

        WORKERS.execute(() -> {
            int status = 503;
            String body = "";
            HttpURLConnection connection = null;

            try {
                URL endpoint = new URL(url);
                String host = endpoint.getHost().toLowerCase(
                    java.util.Locale.ROOT);

                if (!"https".equalsIgnoreCase(endpoint.getProtocol()) ||
                    !(host.equals("retroachievements.org") ||
                      host.endsWith(".retroachievements.org"))) {
                    throw new SecurityException(
                        "Only official RetroAchievements HTTPS is allowed");
                }

                connection = (HttpURLConnection)
                    endpoint.openConnection();

                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty(
                    "User-Agent", "LegacyOfGokuThor/0.1.0");
                connection.setRequestProperty(
                    "Accept", "application/json");

                if (post != null && !post.isEmpty()) {
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty(
                        "Content-Type",
                        contentType == null || contentType.isEmpty()
                            ? "application/x-www-form-urlencoded"
                            : contentType);

                    byte[] bytes = post.getBytes(
                        StandardCharsets.UTF_8);

                    try (OutputStream out =
                             connection.getOutputStream()) {
                        out.write(bytes);
                    }
                }

                status = connection.getResponseCode();

                InputStream stream = status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();

                if (stream != null) {
                    try (InputStream in = stream;
                         ByteArrayOutputStream out =
                             new ByteArrayOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (out.size() + count > 1048576) {
                                status = 503;
                                out.reset();
                                break;
                            }
                            out.write(buffer, 0, count);
                        }

                        body = out.toString("UTF-8");
                    }
                }
            } catch (Exception ignored) {
                // Never log URLs, tokens, request bodies or passwords.
                status = 503;
                body = "";
            } finally {
                if (connection != null) connection.disconnect();
            }

            GbaNative.completeRaHttp(id, status, body);
        });
    }
}
