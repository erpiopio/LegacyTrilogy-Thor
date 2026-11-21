package org.novapowers.buusfury.thor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Localized copies of the bundled catalogs, never a translator of live ROM text. */
final class ThorContentText {
    private final JSONObject catalog;

    ThorContentText(Context context, String languageTag) {
        String tag = ThorUiStrings.normalize(languageTag);
        try (InputStream input = context.getAssets().open("buusfury/" + "i18n/content-" + tag + ".json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            int size;
            while ((size = input.read(block)) != -1) output.write(block, 0, size);
            catalog = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load lower-screen catalog: " + tag, error);
        }
    }

    /** Call only while reading a bundled catalog. Text actually read from a ROM bypasses this. */
    String localize(String kind, int id, String original) {
        JSONArray entries = catalog.optJSONArray(kind);
        if (entries == null || id < 0 || id >= entries.length() || entries.isNull(id))
            return original;
        return entries.optString(id, original);
    }
}
