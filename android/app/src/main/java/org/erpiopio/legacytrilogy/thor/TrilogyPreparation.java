package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import java.io.File;

import org.erpiopio.legacygoku2.thor.TrilogyLegacy2Resources;
import org.novapowers.buusfury.thor.TrilogyBuusResources;

public final class TrilogyPreparation {

    public interface Callback {
        void onReady(int game);
        void onError(String message);
    }

    private TrilogyPreparation() {}

    public static boolean isPrepared(Activity activity, int game) {
        if (!RomManager.isReady(activity, game)) return false;

        String hash = activity.getSharedPreferences("trilogy_roms", 0)
            .getString("sha1_" + game, "");

        if (hash.isEmpty() || !hash.equals(
            activity.getSharedPreferences("trilogy_preparation", 0)
                .getString("sha1_" + game, ""))) return false;

        if (game == 2 &&
                !TrilogyBuusResources.isReady(activity, hash))
            return false;

        File wav = new File(activity.getFilesDir(),
            "music/trilogy_" + game + ".wav");

        return wav.isFile() && wav.length() > 44 &&
            hash.equals(activity.getSharedPreferences(
                "trilogy_audio_cache", 0)
                .getString("sha1_" + game, ""));
    }

    public static void start(
            Activity activity, int game,
            TrilogyAudio audio, Callback callback) {

        new Thread(() -> {
            try {
                if (!RomManager.isReady(activity, game))
                    throw new IllegalStateException("ROM no disponible");

                File rom = RomManager.getRom(activity, game);

                if (game == 1) {
                    TrilogyLegacy2Resources.prepare(activity, rom);
                } else if (game == 2) {
                    String hash = activity
                        .getSharedPreferences("trilogy_roms", 0)
                        .getString("sha1_" + game, "");

                    TrilogyBuusResources.prepare(activity, rom, hash);
                }

                activity.runOnUiThread(() ->
                    audio.prepareOnly(game,
                        new TrilogyAudio.PreparationCallback() {
                            @Override
                            public void onReady() {
                                String hash = activity
                                    .getSharedPreferences("trilogy_roms", 0)
                                    .getString("sha1_" + game, "");

                                boolean saved = activity
                                    .getSharedPreferences(
                                        "trilogy_preparation", 0)
                                    .edit()
                                    .putString("sha1_" + game, hash)
                                    .commit();

                                if (saved) callback.onReady(game);
                                else callback.onError(
                                    "No se pudo guardar la preparación");
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        })
                );

            } catch (Exception error) {
                activity.runOnUiThread(() ->
                    callback.onError(error.getMessage()));
            }
        }, "Trilogy-Resource-Preparation").start();
    }
}
