package org.erpiopio.legacygoku2.thor;

import android.content.Context;
import java.io.File;

public final class TrilogyLegacy2Resources {
    private TrilogyLegacy2Resources() {}

    public static void prepare(Context context, File rom)
            throws Exception {
        Legacy2ScouterResources.prepare(context, rom);
        Legacy2ItemResources.prepare(context, rom);
        Legacy2SkillResources.prepare(context, rom);
    }
}
