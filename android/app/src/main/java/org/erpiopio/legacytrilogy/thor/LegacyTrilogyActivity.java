package org.erpiopio.legacytrilogy.thor;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class LegacyTrilogyActivity extends Activity {
    private Portada portada;
    private TrilogyAudio musica;
    private boolean importando = false;
    private int ultimaSeleccion = -1;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        portada = new Portada();
        setContentView(portada);
        musica = new TrilogyAudio(this);

        if (!BiosManager.isReady(this) && state == null) {
            getWindow().getDecorView().post(() -> BiosManager.promptOnLaunch(this));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BiosManager.REQUEST) {
            BiosManager.onResult(this, resultCode, data);
        } else if (requestCode >= RomManager.REQUEST &&
                   requestCode < RomManager.REQUEST + 3) {
            if (resultCode == RESULT_OK && data != null) {
                importarJuego(requestCode - RomManager.REQUEST, data);
            }
        }
    }

    private void importarJuego(int juego, Intent data) {
        importando = true;
        portada.mostrarPreparacion(true);
        musica.stopPlayback();
        Toast.makeText(this, "Validando ROM...", Toast.LENGTH_SHORT).show();

        RomManager.importRom(this, juego, data, new RomManager.Callback() {
            @Override
            public void onImported(int index) {
                prepararRecursos(index, true);
            }

            @Override
            public void onError(String mensaje) {
                importando = false;
                portada.mostrarPreparacion(false);
                new AlertDialog.Builder(LegacyTrilogyActivity.this)
                    .setTitle("ROM no compatible")
                    .setMessage(mensaje)
                    .setPositiveButton("Aceptar", null)
                    .show();
            }
        });
    }

    private void prepararRecursos(int juego, boolean nuevaRom) {
        importando = true;
        portada.mostrarPreparacion(true);
        musica.stopPlayback();

        TrilogyPreparation.start(this, juego, musica,
            new TrilogyPreparation.Callback() {
                @Override
                public void onReady(int index) {
                    importando = false;
                    portada.mostrarPreparacion(false);
                    if (nuevaRom) portada.activarJuego(index);
                    ultimaSeleccion = index;
                    toqueSeleccionado = index;
                    portada.activarBrilloContinuo(index);
                    musica.select(index);

                    Toast.makeText(LegacyTrilogyActivity.this,
                        RomManager.NAMES[index] + " — recursos preparados",
                        Toast.LENGTH_LONG).show();

                    // Preparado: esperar al segundo toque.
                }

                @Override
                public void onError(String mensaje) {
                    importando = false;
                    portada.mostrarPreparacion(false);
                    new AlertDialog.Builder(LegacyTrilogyActivity.this)
                        .setTitle("Error preparando recursos")
                        .setMessage(mensaje == null ?
                            "No se pudo completar la preparación" : mensaje)
                        .setPositiveButton("Aceptar", null)
                        .show();
                }
            });
    }

    private boolean legacy1EnJuego = false;
    private boolean legacy2EnJuego = false;
    private boolean buusEnJuego = false;
    private int toqueSeleccionado = -1;

    private void elegirEntradaLegacy1() {
        if (importando) return;

        String usuario =
            org.gbarecomp.Legacy1RetroAchievementsSetup.username(this);
        String token =
            org.gbarecomp.Legacy1RetroAchievementsSetup.savedToken(this);

        boolean conectado = !usuario.isEmpty() && !token.isEmpty();

        new AlertDialog.Builder(this)
            .setTitle("Legacy I — RetroAchievements")
            .setMessage(conectado
                ? "Cuenta guardada: " + usuario
                  + "\\n¿Quieres jugar con RetroAchievements?"
                : "Puedes iniciar sesión para activar los logros "
                  + "o jugar sin cuenta.")
            .setPositiveButton(conectado
                ? "Jugar con logros" : "Iniciar sesión",
                (dialog, which) -> {
                    if (conectado) {
                        lanzarLegacy1(false);
                    } else {
                        org.gbarecomp.Legacy1RetroAchievementsSetup
                            .showSettings(this,
                                () -> lanzarLegacy1(false));
                    }
                })
            .setNeutralButton("Jugar sin cuenta",
                (dialog, which) -> lanzarLegacy1(true))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void lanzarLegacy1(boolean sinCuenta) {
        if (importando) return;

        importando = true;
        portada.mostrarPreparacion(true);
        musica.stopPlayback();

        new Thread(() -> {
            try {
                Legacy1RuntimeFiles.prepare(this);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    portada.mostrarPreparacion(false);

                    try {
                        Intent intent = new Intent(
                            this, org.gbarecomp.GbaGameActivity.class);
                        intent.putExtra("trilogy_ra_offline", sinCuenta);

                        legacy1EnJuego = true;
                        startActivity(intent);

                        android.util.Log.i("TRILOGY_LEGACY1",
                            "ENGINE_ACTIVITY_STARTED");
                    } catch (Exception error) {
                        legacy1EnJuego = false;
                        importando = false;
                        musica.select(0);

                        new AlertDialog.Builder(this)
                            .setTitle("No se pudo iniciar Legacy I")
                            .setMessage(error.getMessage())
                            .setPositiveButton("Aceptar", null)
                            .show();
                    }
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    importando = false;
                    portada.mostrarPreparacion(false);
                    musica.select(0);

                    new AlertDialog.Builder(this)
                        .setTitle("Error preparando Legacy I")
                        .setMessage(error.getMessage())
                        .setPositiveButton("Aceptar", null)
                        .show();
                });
            }
        }, "Trilogy-Legacy1-Launch").start();
    }


    private void lanzarLegacy2() {
        if (importando) return;

        importando = true;
        portada.mostrarPreparacion(true);
        musica.stopPlayback();

        new Thread(() -> {
            try {
                Legacy2RuntimeFiles.prepare(this);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    portada.mostrarPreparacion(false);

                    try {
                        Intent intent = new Intent(
                            this,
                            org.erpiopio.legacygoku2.thor.Legacy2GameActivity.class
                        );

                        legacy2EnJuego = true;
                        startActivity(intent);

                        android.util.Log.i(
                            "TRILOGY_LEGACY2",
                            "ENGINE_ACTIVITY_STARTED"
                        );
                    } catch (Exception error) {
                        legacy2EnJuego = false;
                        importando = false;
                        musica.select(1);

                        new AlertDialog.Builder(this)
                            .setTitle("No se pudo iniciar Legacy II")
                            .setMessage(String.valueOf(error.getMessage()))
                            .setPositiveButton("Aceptar", null)
                            .show();
                    }
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    importando = false;
                    portada.mostrarPreparacion(false);
                    musica.select(1);

                    new AlertDialog.Builder(this)
                        .setTitle("Error preparando Legacy II")
                        .setMessage(String.valueOf(error.getMessage()))
                        .setPositiveButton("Aceptar", null)
                        .show();
                });
            }
        }, "Trilogy-Legacy2-Launch").start();
    }

    private void lanzarBuus() {
        if (importando) return;

        importando = true;
        portada.mostrarPreparacion(true);
        musica.stopPlayback();

        new Thread(() -> {
            try {
                BuusRuntimeFiles.prepare(this);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    portada.mostrarPreparacion(false);

                    try {
                        Intent intent = new Intent(
                            this,
                            org.novapowers.buusfury.thor.ThorGameActivity.class
                        );

                        buusEnJuego = true;
                        startActivity(intent);

                        android.util.Log.i(
                            "TRILOGY_BUUS",
                            "ENGINE_ACTIVITY_STARTED"
                        );
                    } catch (Exception error) {
                        buusEnJuego = false;
                        importando = false;
                        musica.select(1);

                        new AlertDialog.Builder(this)
                            .setTitle("No se pudo iniciar Buu's Fury")
                            .setMessage(String.valueOf(error.getMessage()))
                            .setPositiveButton("Aceptar", null)
                            .show();
                    }
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    importando = false;
                    portada.mostrarPreparacion(false);
                    musica.select(1);

                    new AlertDialog.Builder(this)
                        .setTitle("Error preparando Buu's Fury")
                        .setMessage(String.valueOf(error.getMessage()))
                        .setPositiveButton("Aceptar", null)
                        .show();
                });
            }
        }, "Trilogy-Buus-Launch").start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (legacy1EnJuego || legacy2EnJuego || buusEnJuego) {
            toqueSeleccionado = -1;
            buusEnJuego = false;
            legacy1EnJuego = false;
            legacy2EnJuego = false;
            importando = false;
            portada.mostrarPreparacion(false);
            if (musica != null && ultimaSeleccion >= 0)
                musica.select(ultimaSeleccion);
        }

        if (musica != null) musica.onResume();
    }

    @Override
    protected void onPause() {
        if (musica != null) musica.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (musica != null) musica.close();
        super.onDestroy();
    }

    private class Portada extends View {
        private final Bitmap imagen;
        private final Paint gris = new Paint(3);
        private final Paint color = new Paint(3);
        private final Paint brillo = new Paint(3);
        private final Paint bordeBrillo = new Paint(3);
        private final Paint pinturaParpadeo = new Paint(3);
        private int juegoParpadeo = -1;
        private float intensidadParpadeo = 0f;
        private ValueAnimator animadorBrillo;
        private ValueAnimator animadorRecursos;
        private boolean preparandoRecursos = false;
        private int puntosRecursos = 1;
        private final float[] progreso = {0f, 0f, 0f};
        private final String[] juegos = {
            "Legacy of Goku I",
            "Legacy of Goku II",
            "Buu's Fury"
        };

        Portada() {
            super(LegacyTrilogyActivity.this);
            imagen = BitmapFactory.decodeResource(
                getResources(), R.drawable.front
            );
            ColorMatrix matriz = new ColorMatrix();
            matriz.setSaturation(0f);
            gris.setColorFilter(new ColorMatrixColorFilter(matriz));

            brillo.setColor(Color.argb(24, 255, 235, 165));
            pinturaParpadeo.setColor(Color.rgb(255, 250, 240));
            bordeBrillo.setColor(Color.argb(170, 255, 215, 80));
            bordeBrillo.setStyle(Paint.Style.STROKE);
            bordeBrillo.setStrokeWidth(4f * getResources().getDisplayMetrics().density);

            for (int i = 0; i < 3; i++)
                progreso[i] = RomManager.isReady(LegacyTrilogyActivity.this, i) ? 1f : 0f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();
            RectF destino = new RectF(0, 0, w, h);

            canvas.drawBitmap(imagen, null, destino, gris);

            float izqArriba = w * 0.380f;
            float izqAbajo = w * 0.162f;
            float derArriba = w * 0.746f;
            float derAbajo = w * 0.529f;

            for (int i = 0; i < 3; i++) {
                if (progreso[i] <= 0) continue;

                Path zona = new Path();

                if (i == 0) {
                    zona.moveTo(0, 0);
                    zona.lineTo(izqArriba, 0);
                    zona.lineTo(izqAbajo, h);
                    zona.lineTo(0, h);
                } else if (i == 1) {
                    zona.moveTo(izqArriba, 0);
                    zona.lineTo(derArriba, 0);
                    zona.lineTo(derAbajo, h);
                    zona.lineTo(izqAbajo, h);
                } else {
                    zona.moveTo(derArriba, 0);
                    zona.lineTo(w, 0);
                    zona.lineTo(w, h);
                    zona.lineTo(derAbajo, h);
                }

                zona.close();
                canvas.save();
                canvas.clipPath(zona);
                canvas.clipRect(0, h * (1f - progreso[i]), w, h);
                canvas.drawBitmap(imagen, null, destino, color);
                if (progreso[i] >= 0.999f)
                    canvas.drawRect(destino, brillo);
                canvas.restore();

                if (juegoParpadeo == i && intensidadParpadeo > 0f) {
                    canvas.save();
                    canvas.clipPath(zona);
                    pinturaParpadeo.setAlpha(
                        Math.round(intensidadParpadeo * 255f));
                    canvas.drawRect(destino, pinturaParpadeo);
                    canvas.restore();
                }

                if (progreso[i] >= 0.999f)
                    canvas.drawPath(zona, bordeBrillo);
            }

            if (preparandoRecursos) {
                Paint fondo = new Paint(3);
                fondo.setColor(Color.argb(215, 10, 14, 24));

                float ancho = w * 0.82f;
                float alto = h * 0.27f;
                RectF panel = new RectF(
                    (w - ancho) / 2f, (h - alto) / 2f,
                    (w + ancho) / 2f, (h + alto) / 2f);

                canvas.drawRoundRect(panel, 26f, 26f, fondo);

                Paint texto = new Paint(3);
                texto.setColor(Color.rgb(255, 250, 240));
                texto.setTextAlign(Paint.Align.CENTER);
                texto.setTypeface(android.graphics.Typeface.create(
                    "sans-serif", android.graphics.Typeface.BOLD));

                float tamano = Math.min(h * 0.052f, w * 0.041f);
                texto.setTextSize(tamano);

                canvas.drawText("DESCARGANDO RECURSOS",
                    w / 2f, h / 2f - tamano * 0.13f, texto);

                texto.setTextSize(tamano * 1.15f);
                canvas.drawText("...".substring(0, puntosRecursos),
                    w / 2f, h / 2f + tamano * 0.96f, texto);
            }
        }

        void mostrarPreparacion(boolean activo) {
            if (animadorRecursos != null) {
                animadorRecursos.cancel();
                animadorRecursos = null;
            }

            preparandoRecursos = activo;

            if (activo) {
                puntosRecursos = 1;
                animadorRecursos = ValueAnimator.ofInt(1, 3);
                animadorRecursos.setDuration(900);
                animadorRecursos.setRepeatCount(ValueAnimator.INFINITE);
                animadorRecursos.addUpdateListener(anim -> {
                    puntosRecursos = (Integer) anim.getAnimatedValue();
                    invalidate();
                });
                animadorRecursos.start();
            }

            invalidate();
        }

        void activarJuego(int juego) {
            progreso[juego] = 0f;
            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1500);
            anim.addUpdateListener(a -> {
                progreso[juego] = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        void activarBrilloContinuo(int juego) {
            if (animadorBrillo != null) {
                animadorBrillo.cancel();
                animadorBrillo = null;
            }

            juegoParpadeo = juego;
            intensidadParpadeo = 0.12f;

            animadorBrillo = ValueAnimator.ofFloat(0.12f, 0.43f);
            animadorBrillo.setDuration(1000);
            animadorBrillo.setRepeatMode(ValueAnimator.REVERSE);
            animadorBrillo.setRepeatCount(ValueAnimator.INFINITE);
            animadorBrillo.setInterpolator(
                new android.view.animation.AccelerateDecelerateInterpolator()
            );

            animadorBrillo.addUpdateListener(a -> {
                intensidadParpadeo = (Float) a.getAnimatedValue();
                invalidate();
            });

            animadorBrillo.start();
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animadorBrillo != null) {
                animadorBrillo.cancel();
                animadorBrillo = null;
            }
            if (animadorRecursos != null) {
                animadorRecursos.cancel();
                animadorRecursos = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX() / getWidth();
                float y = event.getY() / getHeight();

                float limite1 = 0.380f - 0.218f * y;
                float limite2 = 0.746f - 0.217f * y;

                int juego = x < limite1 ? 0 :
                            x < limite2 ? 1 : 2;

                if (importando) return true;

                // Dos toques consecutivos para cualquiera de los tres juegos.
                boolean segundoToque = toqueSeleccionado == juego;

                if (!BiosManager.isReady(LegacyTrilogyActivity.this)) {
                    toqueSeleccionado = -1;
                    BiosManager.ensure(LegacyTrilogyActivity.this);
                } else if (!RomManager.isReady(LegacyTrilogyActivity.this, juego)) {
                    ultimaSeleccion = -1;
                    toqueSeleccionado = -1;
                    RomManager.choose(LegacyTrilogyActivity.this, juego);
                } else {
                    if (TrilogyPreparation.isPrepared(
                            LegacyTrilogyActivity.this, juego)) {
                        ultimaSeleccion = juego;
                        toqueSeleccionado = juego;
                        activarBrilloContinuo(juego);

                        if (segundoToque) {
                            if (juego == 0) elegirEntradaLegacy1();
                            else if (juego == 1) lanzarLegacy2();
                            else lanzarBuus();
                        } else {
                            musica.select(juego);
                        }
                    } else {
                        toqueSeleccionado = -1;
                        prepararRecursos(juego, false);
                    }
                }
                performClick();
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
