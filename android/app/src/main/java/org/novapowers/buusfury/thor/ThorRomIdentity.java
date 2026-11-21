package org.novapowers.buusfury.thor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Connects the verified active cartridge to the lower-screen presentation. */
final class ThorRomIdentity {
    private static final String PREFERENCES = "thor_rom_identity";
    private static volatile String verifiedLanguage = "en";
    private static volatile String verifiedPath = "";
    private static volatile String verifiedSha1 = "";

    private ThorRomIdentity() { }

    static String activeLanguage(Context context) {
        File rom = activeFile(context);
        if (!rom.getAbsolutePath().equals(verifiedPath)) verifyActive(context, rom);
        return verifiedLanguage;
    }

    static File activeFile(Context context) {
        return new File(context.getFilesDir(), "buusfury.gba");
    }

    static String activeSha1() { return verifiedSha1; }

    /** Always hashes actual storage: also migrates legacy installs without identity prefs. */
    static synchronized boolean verifyActive(Context context, File rom) {
        ThorRomVariant variant;
        try (FileInputStream input = new FileInputStream(rom)) {
            variant = ThorRomVariant.inspect(input);
        } catch (IOException error) {
            verifiedLanguage = "en";
            verifiedSha1 = "";
            verifiedPath = rom.getAbsolutePath();
            Log.w("ThorRomIdentity", "ROM unavailable; detectedLanguage=unknown, compatible=false, interfaceLanguage=en (fallback)", error);
            return false;
        }
        log(variant);
        verifiedLanguage = variant.interfaceLanguage();
        verifiedSha1 = variant.compatible ? variant.sha1 : "";
        verifiedPath = rom.getAbsolutePath();
        if (!variant.compatible) return false;
        persist(context, variant);
        return true;
    }

    static void validateImport(String sha1, long size) throws IOException {
        ThorRomVariant variant = ThorRomVariant.identify(sha1, size);
        log(variant);
        if (!variant.compatible)
            throw new IOException("Unverified ROM: execution is disabled. Detected language: " +
                    variant.detectedLanguage + ". Interface fallback: en. SHA-1: " + variant.sha1);
    }

    static synchronized void imported(Context context, File rom, String sha1, long size) {
        ThorRomVariant variant = ThorRomVariant.identify(sha1, size);
        if (!variant.compatible) throw new IllegalArgumentException("Unverified ROM cannot become active");
        persist(context, variant);
        verifiedLanguage = variant.interfaceLanguage();
        verifiedSha1 = variant.sha1;
        verifiedPath = rom.getAbsolutePath();
        log(variant);
    }

    private static void persist(Context context, ThorRomVariant variant) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (variant.sha1.equals(prefs.getString("active_sha1", "")) &&
                variant.interfaceLanguage().equals(prefs.getString("active_language", ""))) return;
        boolean saved = prefs.edit()
                .putString("active_sha1", variant.sha1)
                .putString("active_language", variant.interfaceLanguage())
                .putLong("active_size", variant.size)
                .putString("language_" + variant.sha1, variant.detectedLanguage)
                .commit();
        if (!saved) Log.e("ThorRomIdentity", "Could not persist verified ROM identity; startup will rehash");
    }

    private static void log(ThorRomVariant variant) {
        Log.i("ThorRomIdentity", "sha1=" + variant.sha1 + " size=" + variant.size +
                " detectedLanguage=" + variant.detectedLanguage +
                " compatible=" + variant.compatible +
                " interfaceLanguage=" + variant.interfaceLanguage());
    }
}
