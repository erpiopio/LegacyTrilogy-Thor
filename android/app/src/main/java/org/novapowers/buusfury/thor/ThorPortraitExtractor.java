package org.novapowers.buusfury.thor;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

final class ThorPortraitExtractor {
    private static final String GOODTOOLS_SHA1 =
        "f1c4b07554d2a3b1ad2f325307051e775ce68087";
    private static final String SPANISH_SHA1 =
        "ee667f656f6c3a128b9d59c657b3686e58b23eb5";

    private static final int[] IDS = {0, 1, 2, 3, 4, 5, 6, 8};

    // THOR_PORTRAITS_ROM_8
    private static final int[] PORTRAIT_IDS = {0, 1, 2, 3, 4, 5, 6, 8};

    static {
        System.loadLibrary("trilogy_buus_maps");
    }

    private ThorPortraitExtractor() {}

    static boolean applies(String sha1) {
        return SPANISH_SHA1.equalsIgnoreCase(sha1)
            || GOODTOOLS_SHA1.equalsIgnoreCase(sha1);
    }

    private static File directory(Context context, String sha1) {
        return new File(
            new File(context.getFilesDir(), "thor_portraits"),
            sha1.toLowerCase(Locale.ROOT));
    }

    private static boolean generated(String name) {
        if (!name.matches("thor_portrait_[0-8]\\.png"))
            return false;

        int id = Integer.parseInt(name.substring(14, 15));
        return Arrays.binarySearch(IDS, id) >= 0;
    }


    private static boolean portraitGenerated(String name) {
        return name.matches("thor_portrait_(?:[0-6]|8)\\.png");
    }

    static InputStream open(
            Context context, String sha1, String name)
            throws IOException {
        if (applies(sha1) && (generated(name))) {
            return new FileInputStream(
                new File(directory(context, sha1), name));
        }
        return context.getAssets().open(name);
    }

    private static void validate(File directory) throws IOException {
        for (int id : IDS) {
            String name = String.format(
                Locale.US, "thor_portrait_%d.png", id);
            if (new File(directory, name).length() <= 8)
                throw new IOException("Missing inventory icon: " + name);
        }

        for (int id : PORTRAIT_IDS) {
            String name = String.format(
                Locale.US, "thor_portrait_%d.png", id);
            if (new File(directory, name).length() <= 8)
                throw new IOException("Missing portrait: " + name);
        }
    }

    static boolean ready(Context context, String sha1) {
        if (!applies(sha1)) return true;
        try {
            File directory = directory(context, sha1);
            if (!new File(directory, ".ready").isFile())
                return false;
            validate(directory);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static void deleteTree(File path) throws IOException {
        if (!path.exists()) return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children == null)
                throw new IOException("Cannot inspect " + path);
            for (File child : children) deleteTree(child);
        }
        if (!path.delete())
            throw new IOException("Cannot delete " + path);
    }

    static synchronized void prepare(
            Context context, File rom, String sha1) throws IOException {
        if (!applies(sha1)) return;

        File destination = directory(context, sha1);
        File previous = new File(
            destination.getParentFile(),
            destination.getName() + ".previous");

        if (previous.exists() && !destination.exists())
            Files.move(previous.toPath(), destination.toPath());

        if (ready(context, sha1)) {
            if (previous.exists()) deleteTree(previous);
            return;
        }

        if (previous.exists())
            throw new IOException("Unresolved inventory backup");

        if (rom == null || !rom.isFile())
            throw new IOException("ROM unavailable");

        File root = destination.getParentFile();
        if (!root.isDirectory() && !root.mkdirs())
            throw new IOException("Cannot create inventory directory");

        File staging = Files.createTempDirectory(
            root.toPath(), "inventory-pending-").toFile();

        try {
            generateNative(
                rom.getAbsolutePath(), staging.getAbsolutePath());

            validate(staging);

            try (FileOutputStream marker = new FileOutputStream(
                    new File(staging, ".ready"))) {
                marker.write(
                    sha1.getBytes(StandardCharsets.US_ASCII));
                marker.getFD().sync();
            }

            boolean backedUp = false;

            if (destination.exists()) {
                Files.move(destination.toPath(), previous.toPath());
                backedUp = true;
            }

            try {
                Files.move(staging.toPath(), destination.toPath());
            } catch (IOException error) {
                if (backedUp && !destination.exists()) {
                    try {
                        Files.move(previous.toPath(), destination.toPath());
                    } catch (IOException rollback) {
                        error.addSuppressed(rollback);
                    }
                }
                throw error;
            }

            if (backedUp) deleteTree(previous);
        } finally {
            deleteTree(staging);
        }
    }

    private static native void generateNative(
        String romPath, String outputPath) throws IOException;
}
