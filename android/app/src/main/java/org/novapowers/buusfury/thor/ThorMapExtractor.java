package org.novapowers.buusfury.thor;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.io.File;
import java.io.IOException;

/**
 * Generates Thor lower-screen maps from the user's imported ROM.
 * Maps must be generated in a staging directory before activation.
 */
final class ThorMapExtractor {

    static {
        System.loadLibrary("trilogy_buus_maps");
    }

    private ThorMapExtractor() {}


    static File mapsDirectory(Context context, String sha1) {
        if (sha1 == null || !sha1.matches("[a-fA-F0-9]{40}")) {
            throw new IllegalArgumentException("Invalid ROM SHA-1");
        }
        return new File(new File(context.getFilesDir(), "thor_maps"),
                sha1.toLowerCase(Locale.ROOT));
    }

    static boolean mapsReady(Context context, String sha1) {
        try {
            File directory = mapsDirectory(context, sha1);
            if (!new File(directory, ".ready").isFile()) return false;
            validateMaps(directory);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static void validateMaps(File directory) throws IOException {
        HashSet<Integer> records = new HashSet<>();
        HashSet<String> areas = new HashSet<>();
        int lines = 0;

        File index = new File(directory, "index.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(index), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 4 ||
                        !fields[0].matches("[0-9A-Fa-f]{6}") ||
                        !areas.add(fields[0])) {
                    throw new IOException("Invalid map index entry");
                }

                if (!fields[1].matches("r_[0-9]+\\.png")) {
                    throw new IOException("Invalid map filename");
                }

                int record = Integer.parseInt(
                    fields[1].substring(2, fields[1].length() - 4));

                if (record < 0 || record >= 421 ||
                        Integer.parseInt(fields[2]) <= 0 ||
                        Integer.parseInt(fields[3]) <= 0) {
                    throw new IOException("Invalid map record");
                }

                records.add(record);
                lines++;
            }
        } catch (NumberFormatException error) {
            throw new IOException("Invalid map index numbers", error);
        }

        if (lines != 452 || records.size() != 421) {
            throw new IOException("Incomplete Thor map index");
        }

        for (int i = 0; i < 421; i++) {
            if (new File(directory, "r_" + i + ".png").length() <= 8) {
                throw new IOException("Missing or empty map " + i);
            }
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Cannot inspect " + file);
            }
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) {
            throw new IOException("Cannot delete " + file);
        }
    }

    static void prepareMaps(Context context, File rom, String sha1)
            throws IOException {
        File destination = mapsDirectory(context, sha1);
        File previousMaps = new File(
                destination.getParentFile(), destination.getName() + ".previous");

        // Recover an interrupted activation before attempting regeneration.
        if (previousMaps.exists() && !destination.exists()) {
            try {
                Os.rename(previousMaps.getAbsolutePath(),
                          destination.getAbsolutePath());
            } catch (ErrnoException error) {
                throw new IOException("Cannot restore previous maps", error);
            }
        }

        if (mapsReady(context, sha1)) {
            if (previousMaps.exists()) deleteTree(previousMaps);
            return;
        }

        if (previousMaps.exists()) {
            throw new IOException("Unresolved previous map backup");
        }

        File root = destination.getParentFile();
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("Cannot create Thor map directory");
        }

        File staging = File.createTempFile("maps-pending-", ".tmp", root);
        if (!staging.delete() || !staging.mkdir()) {
            throw new IOException("Cannot create map staging directory");
        }

        try {
            generateMaps(rom, staging);
            validateMaps(staging);

            try (FileOutputStream marker =
                    new FileOutputStream(new File(staging, ".ready"))) {
                marker.write(sha1.getBytes(StandardCharsets.US_ASCII));
                marker.getFD().sync();
            }

            boolean previousMoved = false;
            try {
                if (destination.exists()) {
                    Os.rename(destination.getAbsolutePath(),
                              previousMaps.getAbsolutePath());
                    previousMoved = true;
                }

                Os.rename(staging.getAbsolutePath(),
                          destination.getAbsolutePath());
            } catch (ErrnoException error) {
                if (previousMoved && !destination.exists()) {
                    try {
                        Os.rename(previousMaps.getAbsolutePath(),
                                  destination.getAbsolutePath());
                    } catch (ErrnoException rollbackError) {
                        error.addSuppressed(rollbackError);
                    }
                }
                throw new IOException("Cannot activate generated maps", error);
            }

            if (previousMaps.exists()) deleteTree(previousMaps);
        } finally {
            deleteTree(staging);
        }
    }

    private static native void generateMapsNative(
        String romPath,
        String outputPath
    ) throws IOException;

    static void generateMaps(File rom, File stagingDirectory)
            throws IOException {

        if (rom == null || !rom.isFile()) {
            throw new IOException("ROM file not found");
        }

        if (stagingDirectory == null) {
            throw new IOException("Missing map output directory");
        }

        generateMapsNative(
            rom.getAbsolutePath(),
            stagingDirectory.getAbsolutePath()
        );
    }
}
