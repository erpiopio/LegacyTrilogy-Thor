package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class BiosManager {
    public static final int REQUEST = 4201;
    private static final String SHA1 =
        "300c20df6731a33952ded8c436f7f186d25d3492";

    private BiosManager() {}

    private static File destination(Activity a) {
        return new File(a.getFilesDir(), "gba_bios.bin");
    }

    private static byte[] validate(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;

        while ((n = input.read(buffer)) != -1) {
            if (out.size() + n > 16384)
                throw new Exception("La BIOS supera los 16 KB");
            out.write(buffer, 0, n);
        }

        byte[] bytes = out.toByteArray();

        if (bytes.length != 16384)
            throw new Exception("La BIOS debe tener exactamente 16 KB");

        byte[] hash = MessageDigest.getInstance("SHA-1").digest(bytes);
        StringBuilder hex = new StringBuilder();

        for (byte b : hash) {
            int v = b & 255;
            hex.append("0123456789abcdef".charAt(v >>> 4));
            hex.append("0123456789abcdef".charAt(v & 15));
        }

        if (!SHA1.equals(hex.toString()))
            throw new Exception("La BIOS no coincide con la BIOS GBA esperada");

        return bytes;
    }

    public static boolean isReady(Activity a) {
        try (InputStream in = new FileInputStream(destination(a))) {
            validate(in);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void ensure(Activity a) {
        if (!isReady(a)) choose(a);
    }

    public static void promptOnLaunch(Activity a) {
        if (isReady(a)) return;

        new AlertDialog.Builder(a)
            .setTitle("Agregar BIOS GBA")
            .setMessage(
                "Para utilizar Legacy Trilogy necesitas importar " +
                "una BIOS original de Game Boy Advance.")
            .setPositiveButton("Agregar BIOS GBA",
                (dialog, which) -> choose(a))
            .setNegativeButton("Salir",
                (dialog, which) -> a.finish())
            .setCancelable(false)
            .show();
    }

    private static void choose(Activity a) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        try {
            a.startActivityForResult(intent, REQUEST);
        } catch (Exception ex) {
            new AlertDialog.Builder(a)
                .setTitle("No se puede abrir el explorador")
                .setMessage(ex.getMessage())
                .setPositiveButton("Cerrar", (d, w) -> a.finish())
                .setCancelable(false)
                .show();
        }
    }

    private static void retry(Activity a, String message) {
        new AlertDialog.Builder(a)
            .setTitle("BIOS de Game Boy Advance")
            .setMessage(message)
            .setPositiveButton("Seleccionar BIOS", (d, w) -> choose(a))
            .setNegativeButton("Salir", (d, w) -> a.finish())
            .setCancelable(false)
            .show();
    }

    public static void onResult(Activity a, int result, Intent data) {
        if (result != Activity.RESULT_OK ||
            data == null || data.getData() == null) {
            retry(a, "Necesitamos una BIOS válida para los tres juegos.");
            return;
        }

        File temp = new File(a.getFilesDir(), "gba_bios.tmp");

        try {
            byte[] bytes;
            try (InputStream in =
                     a.getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new Exception("Archivo inaccesible");
                bytes = validate(in);
            }

            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
            }

            Files.move(
                temp.toPath(),
                destination(a).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );

            Log.i("LEGACY_TRILOGY_BIOS", "BIOS_OK 16384 bytes");
            Toast.makeText(a,
                "BIOS validada y guardada correctamente",
                Toast.LENGTH_LONG).show();

        } catch (Exception ex) {
            Log.e("LEGACY_TRILOGY_BIOS", "BIOS_ERROR", ex);
            retry(a, "No se ha podido importar:\n" + ex.getMessage());
        } finally {
            temp.delete();
        }
    }
}
