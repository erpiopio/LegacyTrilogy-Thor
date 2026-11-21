package org.gbarecomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Legacy I only. RetroAchievements account configuration.
 *
 * The password is never persisted. The login token is encrypted
 * with a non-exportable key from Android Keystore.
 *
 * The native achievement runtime is integrated separately.
 */
public final class Legacy1RetroAchievementsSetup {
    private static final String PREFS = "legacy1_retroachievements";
    private static final String KEY_ALIAS = "legacy1_ra_token_v1";
    private static final String ENDPOINT =
        "https://retroachievements.org/dorequest.php";

    private Legacy1RetroAchievementsSetup() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hardcorePreferred(Context context) {
        return prefs(context).getBoolean("hardcore", true);
    }

    public static String username(Context context) {
        return prefs(context).getString("username", "");
    }

    private static int dp(Context context, int value) {
        return Math.round(
            value * context.getResources().getDisplayMetrics().density);
    }

    public static View attach(Activity activity, View original) {
        Button entry = new Button(activity);
        entry.setAllCaps(false);
        entry.setText("RetroAchievements");
        entry.setOnClickListener(v -> showSettings(activity, null));

        if (original instanceof ScrollView) {
            View child = ((ScrollView) original).getChildAt(0);
            if (child instanceof LinearLayout) {
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(activity, 14), dp(activity, 12),
                                dp(activity, 14), dp(activity, 12));

                GradientDrawable background = new GradientDrawable();
                background.setColor(0xFF173654);
                background.setCornerRadius(dp(activity, 14));
                card.setBackground(background);

                TextView title = new TextView(activity);
                title.setText("RETROACHIEVEMENTS");
                title.setTextColor(Color.WHITE);
                title.setTypeface(null, Typeface.BOLD);
                title.setTextSize(18);
                card.addView(title);

                TextView status = new TextView(activity);
                status.setTextColor(0xFFD5E7FA);
                status.setTextSize(14);
                status.setText(statusText(activity));
                card.addView(status);

                entry.setText("Configurar cuenta y Hardcore");
                card.addView(entry);

                LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(dp(activity, 10), dp(activity, 10),
                                  dp(activity, 10), dp(activity, 14));
                ((LinearLayout) child).addView(card, params);
                return original;
            }
        }

        // Fallback: retain the existing launcher untouched.
        FrameLayout frame = new FrameLayout(activity);
        frame.addView(original, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams buttonParams =
            new FrameLayout.LayoutParams(-2, -2,
                                        Gravity.TOP | Gravity.RIGHT);
        buttonParams.setMargins(0, dp(activity, 16),
                                dp(activity, 12), 0);
        frame.addView(entry, buttonParams);
        return frame;
    }

    private static String statusText(Context context) {
        String account = username(context);
        String connected = account.isEmpty()
            ? "Cuenta sin configurar"
            : "Cuenta guardada: " + account;
        String mode = hardcorePreferred(context)
            ? "Hardcore: preferido"
            : "Modo casual: preferido";
        return connected + "\n" + mode
            + "\nLogros en el juego: pendientes de conectar al motor";
    }

    private static EditText field(
        Activity activity, String hint, boolean password) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(0xFF182B42);
        input.setHintTextColor(0xFF66788A);
        input.setInputType(password
            ? InputType.TYPE_CLASS_TEXT |
              InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT);
        return input;
    }

    public static void showSettings(Activity activity, Runnable onSuccess) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 8),
                        dp(activity, 20), 0);

        EditText user = field(activity, "Usuario RetroAchievements", false);
        user.setText(username(activity));

        EditText password = field(activity, "Contraseña", true);

        CheckBox hardcore = new CheckBox(activity);
        hardcore.setText("Preferir modo Hardcore");
        hardcore.setChecked(hardcorePreferred(activity));

        TextView notice = new TextView(activity);
        notice.setText(
            "La contraseña solo se usa para iniciar sesión. " +
            "Se guardará un token cifrado.\n\n" +
            "Hardcore se activará realmente cuando el motor de " +
            "logros esté integrado y sea compatible.");
        notice.setTextSize(13);
        notice.setTextColor(0xFF52657A);

        form.addView(user);
        form.addView(password);
        form.addView(hardcore);
        form.addView(notice);

        new AlertDialog.Builder(activity)
            .setTitle("RetroAchievements")
            .setView(form)
            .setPositiveButton("Conectar", (dialog, which) -> {
                prefs(activity).edit()
                    .putBoolean("hardcore", hardcore.isChecked())
                    .apply();

                String name = user.getText().toString().trim();
                String secret = password.getText().toString();

                if (name.isEmpty() || secret.isEmpty()) {
                    message(activity, "Introduce usuario y contraseña.");
                    return;
                }

                login(activity, name, secret, onSuccess);
            })
            .setNeutralButton("Desconectar", (dialog, which) -> {
                prefs(activity).edit()
                    .remove("username")
                    .remove("token_iv")
                    .remove("token_data")
                    .putBoolean("hardcore", hardcore.isChecked())
                    .apply();
                message(activity, "Cuenta desconectada.");
            })
            .setNegativeButton("Cancelar", (dialog, which) -> {
                prefs(activity).edit()
                    .putBoolean("hardcore", hardcore.isChecked())
                    .apply();
            })
            .show();
    }

    private static void message(Activity activity, String text) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            new AlertDialog.Builder(activity)
                .setTitle("RetroAchievements")
                .setMessage(text)
                .setPositiveButton("Aceptar", null)
                .show();
        }
    }

    private static void login(
        Activity activity, String username, String password, Runnable onSuccess) {

        new Thread(() -> {
            String result;
            boolean authenticated = false;
            try {
                String body =
                    "r=login2&u=" +
                    URLEncoder.encode(username, "UTF-8") +
                    "&p=" +
                    URLEncoder.encode(password, "UTF-8");

                HttpURLConnection connection =
                    (HttpURLConnection) new URL(ENDPOINT).openConnection();

                try {
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                    connection.setRequestProperty(
                        "User-Agent",
                        "LegacyOfGokuThor/0.1.0 (Android)");

                    byte[] data = body.getBytes(StandardCharsets.UTF_8);

                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(data);
                    }

                    if (connection.getResponseCode() != 200)
                        throw new Exception("Error HTTP " +
                            connection.getResponseCode());

                    ByteArrayOutputStream response =
                        new ByteArrayOutputStream();

                    try (InputStream in = connection.getInputStream()) {
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = in.read(buffer)) >= 0) {
                            if (response.size() + count > 65536)
                                throw new Exception("Respuesta demasiado grande");
                            response.write(buffer, 0, count);
                        }
                    }

                    JSONObject json = new JSONObject(
                        response.toString("UTF-8"));

                    String token = json.optString("Token", "");
                    if (!json.optBoolean("Success", false) ||
                        token.isEmpty()) {
                        throw new Exception(json.optString(
                            "Error", "No se pudo iniciar sesión"));
                    }

                    saveToken(activity, username, token);
                    authenticated = true;
                    result = "Cuenta autenticada: " + username +
                        "\nToken guardado de forma cifrada." +
                        "\n\nEl motor de logros se conectará en " +
                        "el siguiente paso.";
                } finally {
                    connection.disconnect();
                }
            } catch (Exception error) {
                result = "No se pudo conectar: " + error.getMessage();
            }

            final String finalResult = result;
            final boolean connected = authenticated;

            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed())
                    return;

                if (connected && onSuccess != null) {
                    new AlertDialog.Builder(activity)
                        .setTitle("RetroAchievements")
                        .setMessage("Cuenta conectada correctamente.")
                        .setPositiveButton("Entrar al juego",
                            (dialog, which) -> onSuccess.run())
                        .setNegativeButton("Más tarde", null)
                        .show();
                } else {
                    message(activity, finalResult);
                }
            });
        }, "Legacy1-RA-Login").start();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);

        SecretKey existing =
            (SecretKey) store.getKey(KEY_ALIAS, null);

        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        generator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT |
            KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());

        return generator.generateKey();
    }

    private static void saveToken(
        Context context, String username, String token) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());

        byte[] encrypted = cipher.doFinal(
            token.getBytes(StandardCharsets.UTF_8));

        prefs(context).edit()
            .putString("username", username)
            .putString("token_iv",
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("token_data",
                Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit();
    }

    public static String savedToken(Context context) {
        try {
            String iv = prefs(context).getString("token_iv", "");
            String data = prefs(context).getString("token_data", "");
            if (iv.isEmpty() || data.isEmpty()) return "";

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(
                    128, Base64.decode(iv, Base64.NO_WRAP)));

            byte[] decoded = cipher.doFinal(
                Base64.decode(data, Base64.NO_WRAP));

            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }
}
