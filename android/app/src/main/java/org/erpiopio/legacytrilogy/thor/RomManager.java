package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

public final class RomManager {
    public static final int REQUEST = 4300;

    public static final String[] NAMES = {
        "Legacy of Goku I",
        "Legacy of Goku II",
        "Buu's Fury"
    };

    private static final String[][] HASHES = {
        {"c2691355247b03083578071d4d4a017f5599be20"},
        {"db36ff52fcd63f753f9d66439aa3d2216701c326"},
        {
            "f1c4b07554d2a3b1ad2f325307051e775ce68087",
            "e65738e9d67688309f09811a54f495523ec9aada",
            "ee667f656f6c3a128b9d59c657b3686e58b23eb5"
        }
    };

    private static final String[] FILES = {
        "legacy1.gba", "legacy2.gba", "buusfury.gba"
    };

    private RomManager() {}

    public interface Callback {
        void onImported(int game);
        void onError(String message);
    }

    public static File getRom(Activity a, int game) {
        if (game < 0 || game >= 3)
            throw new IllegalArgumentException("Juego desconocido");
        return new File(a.getFilesDir(), FILES[game]);
    }

    public static boolean isReady(Activity a, int game) {
        File f = getRom(a, game);
        if (!f.isFile()) return false;
        long size = f.length();
        if (size != 8388608L &&
            !(game == 2 && size == 10514228L))
            return false;

        String saved = a.getSharedPreferences("trilogy_roms", 0)
            .getString("sha1_" + game, "");

        for (String expected : HASHES[game])
            if (expected.equals(saved)) return true;

        return false;
    }

    public static void choose(Activity a, int game) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        a.startActivityForResult(i, REQUEST + game);
    }

    public static void importRom(
        Activity a, int game, Intent data, Callback callback
    ) {
        if (game < 0 || game >= 3 ||
            data == null || data.getData() == null) {
            callback.onError("No se ha seleccionado una ROM");
            return;
        }

        Uri uri = data.getData();

        new Thread(() -> {
            File dest = getRom(a, game);
            File temp = new File(a.getFilesDir(),
                FILES[game] + ".tmp");

            try {
                MessageDigest digest =
                    MessageDigest.getInstance("SHA-1");
                long total = 0;

                try (InputStream in =
                         a.getContentResolver().openInputStream(uri);
                     FileOutputStream out =
                         new FileOutputStream(temp)) {

                    if (in == null)
                        throw new IOException("Archivo inaccesible");

                    byte[] buffer = new byte[65536];
                    int n;

                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > 10514228L)
                            throw new IOException("ROM demasiado grande");
                        digest.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                }

                if (total != 8388608L &&
                    !(game == 2 && total == 10514228L))
                    throw new IOException("Tamaño de ROM incorrecto");

                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest())
                    hash.append(String.format(
                        Locale.ROOT, "%02x", b & 255));

                boolean valid = false;
                for (String expected : HASHES[game])
                    if (expected.equals(hash.toString()))
                        valid = true;

                if (!valid)
                    throw new IOException(
                        "Esta ROM no coincide con las versiones compatibles de "
                        + NAMES[game]);

                java.nio.file.Files.move(
                    temp.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                a.getSharedPreferences("trilogy_roms", 0)
                    .edit()
                    .putString("sha1_" + game, hash.toString())
                    .apply();

                a.getSharedPreferences("trilogy_preparation", 0)
                    .edit()
                    .remove("sha1_" + game)
                    .commit();

                a.runOnUiThread(() -> callback.onImported(game));

            } catch (Exception ex) {
                a.runOnUiThread(() ->
                    callback.onError(ex.getMessage()));
            } finally {
                temp.delete();
            }
        }, "Trilogy-ROM-Import").start();
    }
}
