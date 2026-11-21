package org.novapowers.buusfury.thor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.*;
import java.util.Locale;

final class ThorSkillsExtractor {
    private static final String SPANISH =
        "ee667f656f6c3a128b9d59c657b3686e58b23eb5";

    private static final String GOODTOOLS =
        "f1c4b07554d2a3b1ad2f325307051e775ce68087";

    private static boolean supported(String sha) {
        return sha != null &&
            (SPANISH.equalsIgnoreCase(sha) ||
             GOODTOOLS.equalsIgnoreCase(sha));
    }

    private ThorSkillsExtractor() {}

    private static File directory(Context ctx, String sha) {
        return new File(ctx.getFilesDir(), "thor_skills/" + sha);
    }

    private static String name(int id, boolean dim) {
        return "thor_skill_" + id + (dim ? "_dim" : "") + ".png";
    }

    static boolean ready(Context ctx, String sha) {
        if (!supported(sha)) return false;
        File dir = directory(ctx, sha.toLowerCase(Locale.ROOT));
        if (!new File(dir, ".ready").isFile()) return false;

        for (int i = 0; i < 24; i++)
            for (int d = 0; d < 2; d++) {
                File png = new File(dir, name(i, d == 1));
                if (!png.isFile() || png.length() < 8) return false;
            }

        return true;
    }

    private static long u32(RandomAccessFile f) throws IOException {
        return Integer.toUnsignedLong(
            Integer.reverseBytes(f.readInt()));
    }

    private static void delete(File path) {
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children != null)
                for (File child : children) delete(child);
        }
        path.delete();
    }

    static synchronized void prepare(
            Context ctx, File rom, String sha) throws IOException {

        // Las 48 habilidades se han verificado en ambas ROM.
        if (!supported(sha)) return;
        if (ready(ctx, sha)) return;

        File destination = directory(ctx, sha.toLowerCase(Locale.ROOT));
        File parent = destination.getParentFile();
        File stage = new File(parent, sha.toLowerCase(Locale.ROOT) + ".staging");

        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("No se puede crear thor_skills");

        delete(stage);
        if (!stage.mkdirs())
            throw new IOException("No se puede crear staging");

        try (RandomAccessFile file =
                 new RandomAccessFile(rom, "r")) {

            if (file.length() < 0x71DAF8L + 24L * 24L)
                throw new IOException("ROM demasiado pequeña");

            int[] palette = new int[256];
            file.seek(0x5652CL);

            for (int i = 0; i < 256; i++) {
                int v = file.readUnsignedByte()
                    | (file.readUnsignedByte() << 8);

                int r = (v & 31) * 255 / 31;
                int g = ((v >> 5) & 31) * 255 / 31;
                int b = ((v >> 10) & 31) * 255 / 31;

                palette[i] = Color.rgb(r, g, b);
            }

            palette[0] = Color.TRANSPARENT;

            byte[] tiles = new byte[512];

            for (int id = 0; id < 24; id++) {
                file.seek(0x71DAF8L + id * 24L + 4L);

                long normal = u32(file);
                long dark = u32(file);

                for (int variant = 0; variant < 2; variant++) {
                    long pointer = variant == 0 ? normal : dark;
                    long offset = pointer - 0x08000000L;

                    if (offset < 0 || offset + 512 > file.length())
                        throw new IOException(
                            "Puntero inválido para habilidad " + id);

                    file.seek(offset);
                    file.readFully(tiles);

                    Bitmap image = Bitmap.createBitmap(
                        21, 11, Bitmap.Config.ARGB_8888);

                    for (int y = 0; y < 11; y++) {
                        for (int x = 0; x < 21; x++) {
                            int tile = (y / 8) * 4 + x / 8;
                            int index = tiles[
                                tile * 64 + (y % 8) * 8 + x % 8
                            ] & 255;

                            image.setPixel(x, y, palette[index]);
                        }
                    }

                    File output = new File(
                        stage, name(id, variant == 1));

                    try (FileOutputStream stream =
                             new FileOutputStream(output)) {
                        if (!image.compress(
                                Bitmap.CompressFormat.PNG, 100, stream))
                            throw new IOException(
                                "Error escribiendo " + output.getName());
                    }

                    image.recycle();
                }
            }

            if (!new File(stage, ".ready").createNewFile())
                throw new IOException("Error creando marcador");

            if (destination.exists()) delete(destination);

            if (!stage.renameTo(destination))
                throw new IOException("Error instalando habilidades");

        } catch (IOException | RuntimeException e) {
            delete(stage);
            throw e;
        }
    }

    static InputStream open(Context ctx, String name)
            throws IOException {

        if (!name.matches(
                "thor_skill_([0-9]|1[0-9]|2[0-3])(_dim)?\\.png"))
            throw new IOException("Nombre de habilidad inválido");

        String sha = ctx.getSharedPreferences("trilogy_roms", 0).getString("sha1_2", "");

        if (!ready(ctx, sha))
            prepare(ctx, new File(ctx.getFilesDir(), "buusfury.gba"), sha);

        if (!ready(ctx, sha))
            throw new IOException(
                "Habilidades no disponibles para esta ROM");

        return new FileInputStream(
            new File(directory(ctx, sha.toLowerCase(Locale.ROOT)), name));
    }
}
