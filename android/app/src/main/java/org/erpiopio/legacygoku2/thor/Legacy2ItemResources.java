
package org.erpiopio.legacygoku2.thor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

final class Legacy2ItemResources {
    private static final String SHA1 =
        "db36ff52fcd63f753f9d66439aa3d2216701c326";

    private static native void extractNative(
        String rom, String output) throws IOException;

    static File directory(Context context) {
        return new File(context.getFilesDir(), "items/" + SHA1 + "/v3");
    }

    static JSONObject manifest(Context context) throws Exception {
        File directory = directory(context);
        validate(directory);
        return new JSONObject(new String(
            Files.readAllBytes(
                new File(directory, "manifest.json").toPath()),
            StandardCharsets.UTF_8));
    }

    static void validate(File directory) throws Exception {
        JSONObject data = new JSONObject(new String(
            Files.readAllBytes(
                new File(directory, "manifest.json").toPath()),
            StandardCharsets.UTF_8));

        if (data.getInt("extractorVersion") != 3 ||
            !SHA1.equals(data.getString("romSha1")) ||
            !"ALFP".equals(data.getString("romCode")))
            throw new IOException("Invalid item manifest");

        File palette = new File(directory, "items.pal");
        if (!palette.isFile() || palette.length() != 512)
            throw new IOException("Missing item palette");

        File largePalette = new File(directory, "items-large.pal");
        if (!largePalette.isFile() || largePalette.length() != 512)
            throw new IOException("Missing large item palette");

        JSONArray items = data.getJSONArray("items");

        if (items.length() != 46)
            throw new IOException("Incomplete item catalog");

        int count = 0;
        int[] fields = {20, 36, 40, 44};

        for (int id = 0; id < 46; ++id) {
            JSONObject item = items.getJSONObject(id);

            if (item.getInt("id") != id)
                throw new IOException("Incorrect item ID");

            JSONObject graphics = item.getJSONObject("graphics");

            for (int field : fields) {
                String key = Integer.toString(field);
                if (!graphics.has(key)) continue;

                JSONObject image = graphics.getJSONObject(key);
                String filename = "item_" + id + "_" + field + ".idx";

                int width = image.getInt("width");
                int height = image.getInt("height");

                if (!filename.equals(image.getString("file")) ||
                    width != height ||
                    (width != 16 && width != 32))
                    throw new IOException("Invalid item image");

                File file = new File(directory, filename);

                if (!file.isFile() ||
                    file.length() != width * height)
                    throw new IOException("Missing item image " + filename);

                ++count;
            }
        }

        if (count != 167 ||
            data.getInt("graphicsCount") != count)
            throw new IOException("Incomplete item graphics");
    }

    private static void removeTree(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null)
                for (File child : children) removeTree(child);
        }
        file.delete();
    }

    static synchronized void prepare(Context context, File rom)
            throws Exception {

        File target = directory(context);

        try {
            validate(target);
            return;
        } catch (Exception missing) {
            // Regenerar desde la ROM original.
        }

        if (rom.length() != 8388608)
            throw new IOException("Invalid ALFP ROM size");

        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        try (FileInputStream in = new FileInputStream(rom)) {
            byte[] buffer = new byte[65536];
            int size;
            while ((size = in.read(buffer)) != -1)
                digest.update(buffer, 0, size);
        }

        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest())
            hash.append(String.format(Locale.ROOT, "%02x", value & 255));

        if (!SHA1.contentEquals(hash))
            throw new IOException("Item extraction requires verified ALFP");

        System.loadLibrary("trilogy_legacy2_resources");

        File parent = target.getParentFile();

        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Cannot create item cache directory");

        File staging = new File(parent, "staging-" + UUID.randomUUID());

        if (!staging.mkdir())
            throw new IOException("Cannot stage item extraction");

        try {
            extractNative(rom.getAbsolutePath(), staging.getAbsolutePath());
            validate(staging);

            if (target.exists()) {
                File backup = new File(
                    parent, "previous-" + System.currentTimeMillis());

                if (!target.renameTo(backup))
                    throw new IOException("Cannot preserve item cache");

                if (!staging.renameTo(target)) {
                    backup.renameTo(target);
                    throw new IOException("Cannot activate item cache");
                }
            } else if (!staging.renameTo(target)) {
                throw new IOException("Cannot activate item resources");
            }
        } finally {
            if (staging.exists()) removeTree(staging);
        }
    }
}
