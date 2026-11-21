package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;

public final class Legacy2RuntimeFiles {
    private static final String ROM_SHA1 =
        "db36ff52fcd63f753f9d66439aa3d2216701c326";

    private static final String BIOS_SHA1 =
        "300c20df6731a33952ded8c436f7f186d25d3492";

    private Legacy2RuntimeFiles() {}

    private static String sha1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;

            while ((count = input.read(buffer)) != -1)
                digest.update(buffer, 0, count);
        }

        StringBuilder result = new StringBuilder();

        for (byte value : digest.digest()) {
            int number = value & 255;
            result.append("0123456789abcdef".charAt(number >>> 4));
            result.append("0123456789abcdef".charAt(number & 15));
        }

        return result.toString();
    }

    private static void install(
            File source, File destination,
            long expectedSize, String expectedSha1) throws Exception {

        if (!source.isFile() ||
            source.length() != expectedSize ||
            !expectedSha1.equals(sha1(source))) {
            throw new IOException("Archivo original no válido: "
                + source.getName());
        }

        if (destination.isFile() &&
            destination.length() == expectedSize &&
            expectedSha1.equals(sha1(destination))) {
            return;
        }

        File parent = destination.getParentFile();

        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("No se pudo crear " + parent);

        File temporary = new File(parent,
            destination.getName() + ".trilogy-tmp");

        try {
            Files.copy(source.toPath(), temporary.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

            if (temporary.length() != expectedSize ||
                !expectedSha1.equals(sha1(temporary)))
                throw new IOException("Error verificando la copia");

            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            temporary.delete();
        }
    }

    // Ejecutar desde un hilo de trabajo, nunca desde el hilo gráfico.
    public static void prepare(Activity activity) throws Exception {
        if (!RomManager.isReady(activity, 1))
            throw new IOException("Importa primero la ROM de Legacy II");

        if (!BiosManager.isReady(activity))
            throw new IOException("Importa primero la BIOS GBA");

        File root = activity.getFilesDir();

        install(
            RomManager.getRom(activity, 1),
            new File(root, "roms/legacy_of_goku_ii_europe_alfp.gba"),
            8388608L, ROM_SHA1);

        install(
            new File(root, "gba_bios.bin"),
            new File(root, "bios/gba_bios.bin"),
            16384L, BIOS_SHA1);

        android.util.Log.i("TRILOGY_LEGACY2",
            "ROM_AND_BIOS_READY");
    }
}
