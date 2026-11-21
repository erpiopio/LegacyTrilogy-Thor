package org.novapowers.buusfury.thor;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Reviewed cartridge identities. Language recognition never implies compatibility. */
final class ThorRomVariant {
    static final String EN_GOODTOOLS = "f1c4b07554d2a3b1ad2f325307051e775ce68087";
    static final String ES_REVIEWED = "ee667f656f6c3a128b9d59c657b3686e58b23eb5";

    final String sha1;
    final long size;
    final String detectedLanguage;
    final boolean compatible;

    private ThorRomVariant(String sha1, long size, String language, boolean compatible) {
        this.sha1 = sha1;
        this.size = size;
        this.detectedLanguage = language;
        this.compatible = compatible;
    }

    static ThorRomVariant identify(String sha1, long size) {
        String normalized = sha1 == null ? "" : sha1.toLowerCase(Locale.ROOT);
        if (EN_GOODTOOLS.equals(normalized))
            return new ThorRomVariant(normalized, size, "en", size == 8388608L);
        if (ES_REVIEWED.equals(normalized))
            return new ThorRomVariant(normalized, size, "es", size == 10514228L);
        // BG3E, file names, Android locale and translation-looking text are not identities.
        return new ThorRomVariant(normalized, size, "unknown", false);
    }

    static ThorRomVariant inspect(InputStream input) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        byte[] buffer = new byte[65536];
        long total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (n == 0) continue;
            digest.update(buffer, 0, n);
            total += n;
        }
        StringBuilder hex = new StringBuilder(40);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return identify(hex.toString(), total);
    }

    String interfaceLanguage() {
        return compatible ? detectedLanguage : "en";
    }
}
