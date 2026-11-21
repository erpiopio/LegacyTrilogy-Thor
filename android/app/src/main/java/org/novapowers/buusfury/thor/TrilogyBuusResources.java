package org.novapowers.buusfury.thor;

import android.content.Context;
import java.io.File;

public final class TrilogyBuusResources {
    private TrilogyBuusResources() {}


    public static boolean isReady(Context context, String sha1) {
        return ThorMapExtractor.mapsReady(context, sha1)
            && ThorEquipmentExtractor.ready(context, sha1)
            && ThorInventoryExtractor.ready(context, sha1)
            && ThorPortraitExtractor.ready(context, sha1)
            && ThorSkillsExtractor.ready(context, sha1);
    }

    public static void prepare(Context context, File rom, String sha1)
            throws Exception {
        ThorMapExtractor.prepareMaps(context, rom, sha1);
        ThorEquipmentExtractor.prepare(context, rom, sha1);
        ThorInventoryExtractor.prepare(context, rom, sha1);
        ThorPortraitExtractor.prepare(context, rom, sha1);
        ThorSkillsExtractor.prepare(context, rom, sha1);

        if (!isReady(context, sha1))
            throw new java.io.IOException("Recursos de Buu's Fury incompletos");
    }
}
