package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TrilogyAudio {
    private static final String TAG = "TRILOGY_AUDIO";

    static {
        System.loadLibrary("trilogy_audio");
    }

    private static native String nativeGenerate(
        int game, String romPath, String outputPath);

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker =
        Executors.newSingleThreadExecutor();

    private MediaPlayer player;
    private boolean prepared = false;
    private boolean foreground = false;
    private boolean closed = false;
    private volatile int generation = 0;

    public TrilogyAudio(Activity activity) {
        this.activity = activity;
    }


    public interface PreparationCallback {
        void onReady();
        void onError(String message);
    }

    public void stopPlayback() {
        stop();
    }

    // Genera la música en segundo plano sin iniciar reproducción.
    public void prepareOnly(int game, PreparationCallback callback) {
        if (closed || !RomManager.isReady(activity, game)) {
            callback.onError("ROM no disponible");
            return;
        }

        final String hash = activity
            .getSharedPreferences("trilogy_roms", 0)
            .getString("sha1_" + game, "");

        final File rom = RomManager.getRom(activity, game);
        final File folder = new File(activity.getFilesDir(), "music");
        final File wav = new File(folder, "trilogy_" + game + ".wav");

        worker.execute(() -> {
            try {
                if (!folder.isDirectory() && !folder.mkdirs())
                    throw new IOException("No se pudo crear carpeta musical");

                boolean cached = wav.isFile() && wav.length() > 44 &&
                    hash.equals(activity.getSharedPreferences(
                        "trilogy_audio_cache", 0)
                        .getString("sha1_" + game, ""));

                if (!cached) {
                    File raw = new File(folder, "trilogy_" + game + ".raw.wav");
                    File trimmed = new File(folder, "trilogy_" + game + ".trim.wav");

                    try {
                        String error = nativeGenerate(
                            game, rom.getAbsolutePath(),
                            raw.getAbsolutePath());

                        if (error == null || !error.isEmpty())
                            throw new IOException("Extractor: " + error);

                        if (game == 1) {
                            trimHalfSecond(raw, trimmed);
                            Files.move(trimmed.toPath(), wav.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.move(raw.toPath(), wav.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }

                        if (!wav.isFile() || wav.length() <= 44)
                            throw new IOException("Música incompleta");

                        activity.getSharedPreferences(
                            "trilogy_audio_cache", 0)
                            .edit()
                            .putString("sha1_" + game, hash)
                            .apply();

                    } finally {
                        raw.delete();
                        trimmed.delete();
                    }
                }

                main.post(() -> {
                    if (!closed) callback.onReady();
                });

            } catch (Exception ex) {
                Log.e(TAG, "Error preparando música", ex);
                main.post(() -> {
                    if (!closed) callback.onError(ex.getMessage());
                });
            }
        });
    }

    public void select(int game) {
        stop();
        final int request = generation;

        if (!RomManager.isReady(activity, game)) return;

        final String hash = activity
            .getSharedPreferences("trilogy_roms", 0)
            .getString("sha1_" + game, "");

        final File rom = RomManager.getRom(activity, game);
        final File folder = new File(activity.getFilesDir(), "music");
        final File wav = new File(folder, "trilogy_" + game + ".wav");

        boolean cached = wav.isFile() && wav.length() > 44 &&
            hash.equals(activity.getSharedPreferences(
                "trilogy_audio_cache", 0)
                .getString("sha1_" + game, ""));

        if (!cached) {
            Toast.makeText(activity,
                "Preparando música de " + RomManager.NAMES[game] + "...",
                Toast.LENGTH_LONG).show();
        }

        worker.execute(() -> {
            try {
                if (!folder.isDirectory() && !folder.mkdirs())
                    throw new IOException("No se pudo crear carpeta musical");

                boolean valid = wav.isFile() && wav.length() > 44 &&
                    hash.equals(activity.getSharedPreferences(
                        "trilogy_audio_cache", 0)
                        .getString("sha1_" + game, ""));

                if (!valid) {
                    File raw = new File(folder,
                        "trilogy_" + game + ".raw.wav");
                    File trimmed = new File(folder,
                        "trilogy_" + game + ".trim.wav");

                    try {
                        String error = nativeGenerate(
                            game, rom.getAbsolutePath(),
                            raw.getAbsolutePath());

                        if (error == null || !error.isEmpty())
                            throw new IOException("Extractor: " + error);

                        if (game == 1) {
                            trimHalfSecond(raw, trimmed);
                            Files.move(trimmed.toPath(), wav.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.move(raw.toPath(), wav.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }

                        activity.getSharedPreferences(
                            "trilogy_audio_cache", 0)
                            .edit()
                            .putString("sha1_" + game, hash)
                            .apply();

                        Log.i(TAG, "Audio generado: juego=" + game +
                            " bytes=" + wav.length());

                    } finally {
                        raw.delete();
                        trimmed.delete();
                    }
                }

                main.post(() -> {
                    if (!closed && request == generation)
                        playFile(wav, request);
                });

            } catch (Exception ex) {
                Log.e(TAG, "Fallo de extracción", ex);
                main.post(() -> {
                    if (!closed && request == generation)
                        Toast.makeText(activity,
                            "Error de música: " + ex.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void trimHalfSecond(File source, File destination)
        throws IOException {

        try (RandomAccessFile input =
                 new RandomAccessFile(source, "r");
             FileOutputStream output =
                 new FileOutputStream(destination)) {

            byte[] header = new byte[44];
            input.readFully(header);

            if (header[0] != 'R' || header[1] != 'I' ||
                header[2] != 'F' || header[3] != 'F' ||
                header[36] != 'd' || header[37] != 'a' ||
                header[38] != 't' || header[39] != 'a')
                throw new IOException("Cabecera WAV inesperada");

            int sampleRate =
                (header[24] & 255) |
                ((header[25] & 255) << 8) |
                ((header[26] & 255) << 16) |
                ((header[27] & 255) << 24);

            int blockAlign =
                (header[32] & 255) |
                ((header[33] & 255) << 8);

            if (sampleRate != 44100 || blockAlign != 4)
                throw new IOException("Formato WAV inesperado");

            long skip = (sampleRate / 2L) * blockAlign;
            long dataBytes = input.length() - 44 - skip;

            if (dataBytes <= 0 || dataBytes > Integer.MAX_VALUE)
                throw new IOException("WAV demasiado corto o grande");

            writeLittleEndian(header, 4, (int)dataBytes + 36);
            writeLittleEndian(header, 40, (int)dataBytes);

            output.write(header);
            input.seek(44 + skip);

            byte[] buffer = new byte[65536];
            long remaining = dataBytes;

            while (remaining > 0) {
                int n = input.read(buffer, 0,
                    (int)Math.min(buffer.length, remaining));
                if (n < 0) throw new IOException("WAV incompleto");
                output.write(buffer, 0, n);
                remaining -= n;
            }
        }
    }

    private static void writeLittleEndian(
        byte[] data, int offset, int value) {
        for (int i = 0; i < 4; i++)
            data[offset + i] = (byte)(value >>> (8 * i));
    }

    private void playFile(File wav, int request) {
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            prepared = false;

            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

            mp.setDataSource(wav.getAbsolutePath());
            mp.setLooping(true);

            mp.setOnPreparedListener(p -> {
                if (closed || request != generation) return;
                prepared = true;
                if (foreground) p.start();
                Log.i(TAG, "Reproducción preparada: " + wav.getName());
            });

            mp.setOnErrorListener((p, what, extra) -> {
                Log.e(TAG, "MediaPlayer: " + what + "/" + extra);
                if (player == p) stop();
                return true;
            });

            mp.prepareAsync();

        } catch (Exception ex) {
            Log.e(TAG, "No se pudo reproducir WAV", ex);
            stop();
            Toast.makeText(activity,
                "No se pudo reproducir la música",
                Toast.LENGTH_LONG).show();
        }
    }

    private void stop() {
        generation++;
        prepared = false;
        if (player != null) {
            player.release();
            player = null;
        }
    }

    public void onResume() {
        foreground = true;
        if (prepared && player != null && !player.isPlaying())
            player.start();
    }

    public void onPause() {
        foreground = false;
        if (prepared && player != null && player.isPlaying())
            player.pause();
    }

    public void close() {
        closed = true;
        stop();
        worker.shutdownNow();
    }
}
