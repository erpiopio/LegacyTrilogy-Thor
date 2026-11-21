package org.novapowers.buusfury.thor;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

final class ThorEquipmentExtractor {
    private static final String SPANISH_SHA1 =
        "ee667f656f6c3a128b9d59c657b3686e58b23eb5";

    static {
        System.loadLibrary("trilogy_buus_maps");
    }

    private ThorEquipmentExtractor() {}

    static boolean applies(String sha1) {
        return SPANISH_SHA1.equalsIgnoreCase(sha1)
            || "f1c4b07554d2a3b1ad2f325307051e775ce68087".equalsIgnoreCase(sha1);
    }

    private static File directory(Context context, String sha1) {
        return new File(
            new File(context.getFilesDir(), "thor_equipment"),
            sha1.toLowerCase(Locale.ROOT));
    }

    static File icon(Context context, String sha1, String name) {
        if (!applies(sha1) || !name.matches("thor_equipment_[0-9]{3}\\.png"))
            throw new IllegalArgumentException("Invalid equipment resource");
        return new File(directory(context, sha1), name);
    }

    private static void validate(File directory) throws IOException {
        for (int i = 0; i < 162; i++) {
            String name = String.format(
                Locale.US, "thor_equipment_%03d.png", i);
            if (new File(directory, name).length() <= 8)
                throw new IOException("Missing equipment image: " + name);
        }
    }

    static boolean ready(Context context, String sha1) {
        if (!applies(sha1)) return true;
        try {
            File directory = directory(context, sha1);
            if (!new File(directory, ".ready").isFile()) return false;
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
        if (!applies(sha1) || ready(context, sha1)) return;
        if (rom == null || !rom.isFile())
            throw new IOException("ROM unavailable");

        File destination = directory(context, sha1);
        File root = destination.getParentFile();
        if (!root.isDirectory() && !root.mkdirs())
            throw new IOException("Cannot create equipment directory");

        File previous = new File(root, destination.getName() + ".previous");
        if (previous.exists())
            throw new IOException("Equipment backup requires recovery");

        File staging = Files.createTempDirectory(
            root.toPath(), "equipment-pending-").toFile();

        try {
            generateEquipmentNative(
                rom.getAbsolutePath(), staging.getAbsolutePath());

            validate(staging);

            try (FileOutputStream marker = new FileOutputStream(
                    new File(staging, ".ready"))) {
                marker.write(sha1.getBytes(StandardCharsets.US_ASCII));
                marker.getFD().sync();
            }

            boolean backedUp = false;
            if (destination.exists()) {
                Files.move(destination.toPath(), previous.toPath());
                backedUp = true;
            }

            try {
                Files.move(staging.toPath(), destination.toPath());
            } catch (IOException failure) {
                if (backedUp && !destination.exists()) {
                    try {
                        Files.move(previous.toPath(), destination.toPath());
                    } catch (IOException rollback) {
                        failure.addSuppressed(rollback);
                    }
                }
                throw failure;
            }

            if (backedUp) deleteTree(previous);
        } finally {
            deleteTree(staging);
        }
    }

    private static native void generateEquipmentNative(
        String romPath, String outputPath) throws IOException;
}
