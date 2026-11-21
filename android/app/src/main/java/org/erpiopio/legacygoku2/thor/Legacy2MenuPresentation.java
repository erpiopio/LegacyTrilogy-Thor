package org.erpiopio.legacygoku2.thor;

import org.erpiopio.legacytrilogy.thor.R;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

final class Legacy2MenuPresentation extends Presentation {

    private EstadoView vista;

    // JNI: consulta una copia publicada por el motor GBA.
    // Nunca lee directamente la memoria del juego.
    private static native Object[] getMenuSnapshot(int personajeId);
    private static native int[] getScouterSnapshot();
    private static native boolean isPlayerActorPresent();
    private static native void requestUseItem(int itemId);
    private static native boolean requestSelectSkill(int characterId, int slot, int generation);

    Legacy2MenuPresentation(Context context, Display display) {
        super(context, display);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        vista = new EstadoView(getContext());
        setContentView(vista);

        Window window = getWindow();
        if (window != null) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
            window.setLayout(-1, -1);

            // La pantalla inferior debe utilizar los 1240x1080 completos.
            // Evitar que Android reserve 55px para las barras del sistema.
            window.setDecorFitsSystemWindows(false);
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            );

            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );

            android.view.WindowInsetsController controlador =
                window.getInsetsController();
            if (controlador != null) {
                controlador.hide(android.view.WindowInsets.Type.systemBars());
            }
        }
    }

    // Punto de entrada para el futuro snapshot nativo ALFP.
    // Los valores deben venir del hilo del motor, nunca leyendo
    // directamente la memoria GBA desde el hilo gráfico Android.
    void publicarEstado(int idioma, String personaje,
                       int nivel, int pv, int pvMax,
                       int pe, int peMax, int experiencia) {
        if (vista == null) return;
        vista.post(() -> vista.actualizar(
            idioma, personaje, nivel, pv, pvMax,
            pe, peMax, experiencia
        ));
    }

    // Entrada para el futuro extractor ALFP.
    // Debe recibir una copia ARGB del personaje activo.
    void publicarRetratoDesdeMemoria(
            int[] argb, int ancho, int alto) {
        if (vista == null) return;

        final int[] copia = argb != null ? argb.clone() : null;

        vista.post(() -> {
            Legacy2EstadoModel.Snapshot snapshot = vista.modelo.snapshot();
            if (snapshot != null && snapshot.activeId == vista.modelo.selectedId())
                vista.actualizarRetrato(copia, ancho, alto);
        });
    }

    private static final class EstadoView extends View {

        private static final float W = 1240f;
        private static final float H = 1080f;
        private int ultimoAnchoDiagnostico = -1;
        private int ultimoAltoDiagnostico = -1;

        // Índices de la ROM europea ALFP:
        // 0 inglés, 1 francés, 2 alemán, 3 italiano, 4 español.
        private static final String[][] TEXTOS = {
            {"JOURNAL", "STATUS", "ITEMS",
             "CHARACTER", "LEVEL", "HEALTH",
             "ENERGY", "EXPERIENCE", "GAME DATA PENDING"},
            {"JOURNAL", "STATUT", "OBJETS",
             "PERSONNAGE", "NIVEAU", "SANTÉ",
             "ÉNERGIE", "EXPÉRIENCE", "DONNÉES EN ATTENTE"},
            {"TAGEBUCH", "STATUS", "GEGENSTÄNDE",
             "CHARAKTER", "LEVEL", "LEBEN",
             "ENERGIE", "ERFAHRUNG", "SPIELDATEN AUSSTEHEND"},
            {"DIARIO", "STATO", "OGGETTI",
             "PERSONAGGIO", "LIVELLO", "SALUTE",
             "ENERGIA", "ESPERIENZA", "DATI IN ATTESA"},
            {"DIARIO", "ESTADO", "OBJETOS",
             "PERSONAJE", "NIVEL", "SALUD",
             "ENERGÍA", "EXPERIENCIA", "DATOS PENDIENTES"}
        };

        private static final String[] MENSAJES_CARGA = {
            "LOADING", "CHARGEMENT", "LADEN",
            "CARICAMENTO", "CARGANDO"
        };

        private static final String[][] TEXTOS_DIARIO = {
            {"QUESTS", "FINISHED",
             "JOURNAL DATA PENDING", "UNVERIFIED QUESTS",
             "NO PENDING QUESTS", "NO COMPLETED QUESTS"},
            {"MISSIONS", "TERMINÉES",
             "DONNÉES DU JOURNAL EN ATTENTE", "MISSIONS NON VÉRIFIÉES",
             "AUCUNE MISSION EN COURS", "AUCUNE MISSION TERMINÉE"},
            {"AUFGABEN", "ERLEDIGT",
             "TAGEBUCHDATEN AUSSTEHEND", "UNBESTÄTIGTE AUFGABEN",
             "KEINE OFFENEN AUFGABEN", "KEINE ERLEDIGTEN AUFGABEN"},
            {"MISSIONI", "FINITE",
             "DATI DEL DIARIO IN ATTESA", "MISSIONI NON VERIFICATE",
             "NESSUNA MISSIONE IN SOSPESO", "NESSUNA MISSIONE COMPLETATA"},
            {"MISIONES", "TERMINADO",
             "DATOS DEL DIARIO PENDIENTES", "MISIONES SIN VERIFICAR",
             "NO HAY MISIONES PENDIENTES", "NO HAY MISIONES TERMINADAS"}
        };

        private static final String[][] TEXTOS_EXTRA = {
            {"HP", "EP", "STR", "POW", "END", "EXP", "NEXT",
             "INVENTORY IS EMPTY"},
            {"PV", "PE", "FOR", "PUI", "RES", "EXP", "SUI",
             "INVENTAIRE VIDE"},
            {"LP", "EP", "STK", "KRF", "AUS", "EXP", "NÄC",
             "INVENTAR IST LEER"},
            {"PV", "PE", "FOR", "POT", "RES", "ESP", "PRO",
             "INVENTARIO VUOTO"},
            {"PV", "PE", "FRZ", "POD", "RES", "EXP", "SIG",
             "EL INVENTARIO ESTÁ VACÍO"}
        };

        private String[] rotulosLocalizados() {
            return TEXTOS_EXTRA[
                idioma >= 0 && idioma < TEXTOS_EXTRA.length ? idioma : 4
            ];
        }

        private final Paint arte = new Paint();
        private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint formas = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint botonTexto = new Paint();
        private final Paint estadoTexto = new Paint();
        private final Paint estadoPixel = new Paint();
        private final Paint capaPestana = new Paint();
        private final Legacy2EstadoModel modelo = new Legacy2EstadoModel();
        private int retratoPersonaje = -1;
        private int paginaEstado = 0; // 0 estadísticas, 1 habilidades.
        private int flechaPulsada = 0;
        private float toqueX, toqueY, ultimoToqueY;
        private boolean toqueArrastrado;
        private boolean gestoCancelado;
        private float scrollHabilidades;
        private float maxScrollHabilidades;
        private Habilidad[] habilidades = new Habilidad[0];
        private int habilidadesPersonaje = -1;
        // Selección confirmada por ALFP: nunca se adelanta al tocar una fila.
        private int habilidadActivaPersonaje = -1;
        private int habilidadActiva = -1;
        private boolean puedeSeleccionarHabilidad;
        private int habilidadesGeneracion = -1;
        private int[] habilidadesIconIds = new int[0];
        private final RectF areaHabilidades = new RectF(230f, 275f, 1130f, 808f);

        private static final class Habilidad {
            final String titulo, descripcion;
            final int disponible; // -1 desconocida; 0 bloqueada; 1 desbloqueada.
            final RectF limite = new RectF();
            Habilidad(String titulo, String descripcion, int disponible) {
                this.titulo = titulo;
                this.descripcion = descripcion;
                this.disponible = disponible;
            }
        }
        private int fuerza = -1;
        private int poder = -1;
        private int resistencia = -1;
        private int siguiente = -1;


        // THOR_LEGACY2_LOADING
        private final Bitmap cargaImagen;
        private final Paint textoCarga = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean cargando = true;
        private long primeraDeteccionPersonaje = -1L;

        private final Bitmap fondoNebulosa;
        private final Paint pinturaNebulosa = new Paint();
        private android.graphics.BitmapShader mosaicoNebulosa;
        private final android.graphics.Matrix matrizNebulosa =
            new android.graphics.Matrix();
        private final Bitmap plantilla;
        private final Bitmap pestana;
        private final Bitmap cartel;
        private final Bitmap marcoMapa;
        private final Legacy2ScouterView scouter;
        private int contadorMapaProbe;

        // Retrato recibido desde la memoria del juego.
        // Nunca cargado desde un PNG incluido en la APK.
        private Bitmap retratoMemoria;
        private int[] retratoPixels;
        private int retratoAncho, retratoAlto;


        private final SoundPool sonidoPestanas = new SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build();
        private final int sonidoPestanasId =
            sonidoPestanas.load(getContext(), R.raw.sound, 1);

        private final int sonidoFlechaId =
            sonidoPestanas.load(getContext(), R.raw.sound2, 1);

        private void reproducirFlecha() {
            sonidoPestanas.play(sonidoFlechaId, 1f, 1f, 1, 0, 1f);
        }

        private void reproducirCambioPestana() {
            sonidoPestanas.play(sonidoPestanasId, 1f, 1f, 1, 0, 1f);
        }

        // THOR_LEGACY2_MAP_BUTTON
        private static final String[] NOMBRES_MAPA = {
            "MAP", "CARTE", "KARTE", "MAPPA", "MAPA"
        };
        private boolean radarDisponible = false;
        private int seccion = 1; // ESTADO
        private int paginaDiario = 0; // 0=MISIONES, 1=TERMINADO
        private final float[] scrollDiario = new float[2];
        private final float[] maxScrollDiario = new float[2];

        // Misiones originales recibidas del motor GBA.
        private static final class Mision {
            final int id;
            final String texto;
            final int estado; // -1 desconocida, 0 oculta, 1 pendiente, 2 terminada
            final int categoria;

            Mision(int id, String texto, int estado, int categoria) {
                this.id = id;
                this.texto = texto;
                this.estado = estado;
                this.categoria = categoria;
            }
        }

        private Mision[] misiones = new Mision[0];
        private boolean diarioDisponible = false;
        private final RectF areaDiario =
            new RectF(180f, 280f, 1080f, 820f);
        private int idioma = -1; // Pendiente de snapshot ALFP
        private int fase = 0;

        // Inventario publicado por el motor GBA.
        private static final class Objeto {
            final int id;
            final String nombre;
            final String descripcion;
            final int cantidad;

            Objeto(int id, String nombre, String descripcion, int cantidad) {
                this.id = id;
                this.nombre = nombre;
                this.descripcion = descripcion;
                this.cantidad = cantidad;
            }
        }

        private Objeto[] objetos = new Objeto[0];
        private boolean objetosDisponibles = false;
        private float scrollObjetos = 0f;
        private float maxScrollObjetos = 0f;
        private final RectF areaObjetos =
            new RectF(135f, 210f, 1130f, 850f);

        // Inventario original ALFP: iconos indexados extraídos de la ROM.
        private final Bitmap[][] imagenesObjetos = new Bitmap[46][4];
        private boolean imagenesObjetosCargadas = false;
        private int objetoSeleccionado = -1;
        private static final float ALTO_FILA_OBJETO = 150f;

        private String personaje;
        private int nivel = -1;
        private int pv = -1, pvMax = -1;
        private int pe = -1, peMax = -1;
        private int experiencia = -1;

        EstadoView(Context context) {
            super(context);
            scouter = new Legacy2ScouterView(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);

            arte.setFilterBitmap(false);
            arte.setAntiAlias(false);

            texto.setTypeface(Typeface.create(
                "monospace", Typeface.BOLD
            ));
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setShadowLayer(2f, 2f, 3f, Color.BLACK);


            botonTexto.setTextAlign(Paint.Align.CENTER);
            botonTexto.setTextSize(68f);
            botonTexto.setAntiAlias(false);
            botonTexto.setFilterBitmap(false);

            try {
                botonTexto.setTypeface(
                    Typeface.createFromAsset(
                        context.getAssets(), "pixel_operator.ttf"));
            } catch (RuntimeException error) {
                try {
                    botonTexto.setTypeface(
                        Typeface.createFromAsset(
                            context.getAssets(), "pixel_operator.otf"));
                } catch (RuntimeException ignored) {
                    botonTexto.setTypeface(
                        Typeface.create("monospace", Typeface.BOLD));
                }
            }

            cargaImagen = cargar(context, "loading.png");
            if (cargaImagen == null)
                throw new IllegalStateException("Falta loading.png");

            textoCarga.setTypeface(botonTexto.getTypeface());
            textoCarga.setTextAlign(Paint.Align.CENTER);
            textoCarga.setTextSize(64f);
            textoCarga.setColor(Color.WHITE);
            textoCarga.setShadowLayer(4f, 3f, 3f, Color.BLACK);

            fondoNebulosa = cargar(context, "fondo_nebulosa.png");
            if (fondoNebulosa != null) {
                mosaicoNebulosa = new android.graphics.BitmapShader(
                    fondoNebulosa,
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT);
                pinturaNebulosa.setShader(mosaicoNebulosa);
                pinturaNebulosa.setFilterBitmap(false);
                pinturaNebulosa.setAntiAlias(false);
            }
            plantilla = cargar(context, "plantilla.png");
            pestana = cargar(context, "pestana.png");
            cartel = cargar(context, "cartel.png");
            marcoMapa = cargar(context, "mapa.png");
            estadoTexto.setTypeface(botonTexto.getTypeface());
            estadoTexto.setAntiAlias(false);
            estadoTexto.setFilterBitmap(false);
            estadoPixel.setAntiAlias(false);
            capaPestana.setAntiAlias(false);
            capaPestana.setFilterBitmap(false);
        }

        private Bitmap cargar(Context context, String name) {
            BitmapFactory.Options opciones =
                new BitmapFactory.Options();
            opciones.inScaled = false;

            try (InputStream in =
                    context.getAssets().open(name)) {
                return BitmapFactory.decodeStream(
                    in, null, opciones
                );
            } catch (IOException error) {
                android.util.Log.e(
                    "Legacy2Menu", "Recurso: " + name, error
                );
                return null;
            }
        }

        void actualizar(int lang, String name,
                        int lv, int hp, int maxHp,
                        int energy, int maxEnergy, int xp) {
            if (modelo.snapshot() != null) return;
            if (lang >= 0 && lang < TEXTOS.length)
                idioma = lang;

            if (personaje != null &&
                !personaje.equals(name)) {
                limpiarRetrato();
            }

            personaje = name;
            nivel = lv;
            pv = hp;
            pvMax = maxHp;
            pe = energy;
            peMax = maxEnergy;
            experiencia = xp;
            invalidate();
        }

        void actualizarRetrato(int[] pixels, int width, int height) {
            if (pixels == null || width <= 0 || height <= 0 ||
                (long) width * height > 1048576L ||
                pixels.length != (long) width * height) {
                boolean habiaRetrato = retratoMemoria != null;
                limpiarRetrato();
                if (habiaRetrato) invalidate();
                return;
            }

            if (retratoMemoria != null && width == retratoAncho &&
                    height == retratoAlto && Arrays.equals(pixels, retratoPixels))
                return;

            // Los píxeles son una copia privada previamente
            // decodificada desde la memoria/ROM importada.
            retratoMemoria = Bitmap.createBitmap(
                pixels, width, height, Bitmap.Config.ARGB_8888
            );
            retratoPixels = pixels.clone();
            retratoAncho = width;
            retratoAlto = height;
            invalidate();
        }

        private void limpiarRetrato() {
            retratoMemoria = null;
            retratoPixels = null;
            retratoAncho = retratoAlto = 0;
        }

        private String[] labels() {
            return TEXTOS[idioma >= 0 && idioma < TEXTOS.length ? idioma : 4];
        }

        private void imagen(Canvas c, Bitmap bitmap,
                            float x, float y,
                            float w, float h, int alpha) {
            if (bitmap == null) return;
            arte.setAlpha(alpha);
            c.drawBitmap(bitmap, null,
                new RectF(x, y, x + w, y + h), arte);
            arte.setAlpha(255);
        }

        private void rotulo(Canvas c, String valor,
                            float x, float y,
                            float size, int color) {
            texto.setTextSize(size);
            texto.setColor(color);
            c.drawText(valor, x, y, texto);
        }

        private void fondoAnimado(Canvas c) {
            c.drawColor(Color.rgb(29, 10, 64));
            if (mosaicoNebulosa == null) return;

            // Textura original repetida en ambas direcciones.
            // Dos mosaicos de ancho: sin espejos ni huecos.
            float escala = Math.max(
                W / (fondoNebulosa.getWidth() * 2f),
                H / (fondoNebulosa.getHeight() * 2f));

            float periodoX = fondoNebulosa.getWidth() * escala;
            float periodoY = fondoNebulosa.getHeight() * escala;

            double segundos =
                android.os.SystemClock.uptimeMillis() / 1000.0;

            // Movimiento continuo diagonal hacia derecha y abajo.
            float dx = (float)((segundos * 18.0) % periodoX);
            float dy = (float)((segundos * 12.0) % periodoY);

            matrizNebulosa.reset();
            matrizNebulosa.setScale(escala, escala);
            matrizNebulosa.postTranslate(dx, dy);

            mosaicoNebulosa.setLocalMatrix(matrizNebulosa);

            // El shader une automáticamente los bordes.
            c.drawRect(0f, 0f, W, H, pinturaNebulosa);

            // Animación fluida, sin afectar al juego superior.
            postInvalidateDelayed(33L);
        }

        // La sección activa se sitúa siempre en el centro.
        private int[] ordenBotonesSecciones() {
            if (seccion == 0)
                return new int[]{1, 0, 2};
            if (seccion == 2)
                return new int[]{0, 2, 1};
            return new int[]{0, 1, 2};
        }

        // Traducir la posición física al botón que se muestra.
        private int seccionBotonEn(float x, float y) {
            int posicion = -1;

            if (x >= 100f && x <= 440f &&
                    y >= 950f && y <= 1080f)
                posicion = 0;
            else if (x >= 450f && x <= 790f &&
                    y >= 890f && y <= 1040f)
                posicion = 1;
            else if (x >= 800f && x <= 1170f &&
                    y >= 950f && y <= 1080f)
                posicion = 2;

            if (posicion < 0) return -1;
            return ordenBotonesSecciones()[posicion];
        }

        private void dibujarBoton(Canvas c, String nombre,
                                  float x, float y) {
            // Sombra negra marcada y sin desenfoque.
            botonTexto.setColor(Color.BLACK);
            c.drawText(nombre, x + 6f, y + 7f, botonTexto);
            c.drawText(nombre, x + 3f, y + 4f, botonTexto);

            // Texto Pixel Operator blanco.
            botonTexto.setColor(Color.WHITE);
            c.drawText(nombre, x, y, botonTexto);
        }

        private void pestañas(Canvas c) {
            // Lienzo gráfico original, sin modificaciones.
            if (pestana != null) {
                arte.setAlpha(255);
                c.save();
                if (seccion == 0 || (seccion == 1 && modelo.count() > 1)) {
                    // La cabecera se gestiona por separado.
                    // La parte inferior conserva su PNG original.
                    c.clipRect(0f, 340f, 1240f, 1080f);
                }
                c.drawBitmap(pestana, 0f, 0f, arte);
                c.restore();
            }

            String[] nombres = labels();
            int[] orden = ordenBotonesSecciones();

            // Negrita y tamaño ampliado solo para las tres secciones.
            // No alterar el botón MAPA.
            float tamanoAnterior = botonTexto.getTextSize();
            boolean negritaAnterior = botonTexto.isFakeBoldText();

            botonTexto.setTextSize(tamanoAnterior * 1.15f);
            botonTexto.setFakeBoldText(true);

            dibujarBoton(c, nombres[orden[0]], 210f, 1040f);
            dibujarBoton(c, nombres[orden[1]], 620f, 990f);
            dibujarBoton(c, nombres[orden[2]], 1030f, 1040f);

            botonTexto.setTextSize(tamanoAnterior);
            botonTexto.setFakeBoldText(negritaAnterior);
        }




        // CAPA TRASERA: se dibuja ANTES de plantilla.png.

        // Siempre DETRAS de plantilla.png.
        private void pestañaPosteriorDiario(Canvas c) {
            if (seccion != 0 || pestana == null) return;

            float x = paginaDiario == 0 ? 80f : 0f;
            final float y = 20f; // MISMA ALTURA EN AMBAS

            c.save();
            c.clipRect(0f, 0f, 1240f, 340f);

            capaPestana.setAlpha(255);
            capaPestana.setColorFilter(
                new LightingColorFilter(
                    Color.rgb(95, 95, 95), 0));

            c.drawBitmap(pestana, x, y, capaPestana);

            capaPestana.setColorFilter(null);
            c.restore();
        }

        // Siempre DELANTE de plantilla.png.
        private void pestañaFrontalDiario(Canvas c) {
            if (seccion != 0 || pestana == null) return;

            float x = paginaDiario == 0 ? 0f : 80f;

            c.save();
            c.clipRect(0f, 0f, 1240f, 340f);

            arte.setAlpha(255);
            arte.setColorFilter(null);
            c.drawBitmap(pestana, x, 0f, arte);

            c.restore();
        }


        // Posicion de un personaje dentro de las pestañas disponibles.
        private int indicePestanaEstado(int personajeId) {
            Legacy2EstadoModel.Snapshot datos = modelo.snapshot();
            if (datos == null) return -1;

            for (int i = 0; i < datos.availableIds.length; i++) {
                if (datos.availableIds[i] == personajeId)
                    return i;
            }
            return -1;
        }

        // Una pestaña trasera por cada personaje no seleccionado.
        // Se dibujan ANTES de plantilla.png.
        private void pestañasPosteriores(Canvas c) {
            if (pestana == null || seccion != 1 ||
                    modelo.count() < 2) return;

            int[] orden = modelo.layerOrder();

            c.save();
            c.clipRect(0f, 0f, 1240f, 340f);

            for (int n = 0; n + 1 < orden.length; ++n) {
                int profundidad = orden.length - 1 - n;
                int indice = indicePestanaEstado(orden[n]);

                if (indice < 0) continue;

                float x = indice * 80f;
                float y = profundidad * 20f;

                // Cada nivel posterior se oscurece mas.
                int luz = Math.max(
                    45, 95 - (profundidad - 1) * 20
                );

                capaPestana.setAlpha(255);
                capaPestana.setColorFilter(
                    new LightingColorFilter(
                        Color.rgb(luz, luz, luz), 0
                    )
                );

                c.drawBitmap(pestana, x, y, capaPestana);
            }

            capaPestana.setColorFilter(null);
            c.restore();
        }

        // Personaje seleccionado: pestaña principal DELANTE.
        private void pestañaFrontalEstado(Canvas c) {
            if (pestana == null || seccion != 1 ||
                    modelo.count() < 2) return;

            int indice = indicePestanaEstado(modelo.selectedId());
            if (indice < 0) return;

            c.save();
            c.clipRect(0f, 0f, 1240f, 340f);

            arte.setAlpha(255);
            arte.setColorFilter(null);

            c.drawBitmap(pestana, indice * 80f, 0f, arte);

            c.restore();
        }

        private void aplicarSeleccion() {
            Legacy2EstadoModel.CharacterState dato = modelo.selected();
            int id = modelo.selectedId();
            if (id != retratoPersonaje) {
                limpiarRetrato();
                retratoPersonaje = id;
            }
            if (id != habilidadesPersonaje) {
                habilidades = new Habilidad[0];
                habilidadesIconIds = new int[0];
                habilidadesPersonaje = id;
                scrollHabilidades = maxScrollHabilidades = 0f;
            }
            personaje = dato == null ? null : Legacy2EstadoModel.NAMES[dato.id];
            nivel = dato == null ? -1 : dato.level;
            pv = dato == null ? -1 : dato.hp;
            pvMax = dato == null ? -1 : dato.hpMax;
            pe = dato == null ? -1 : dato.energy;
            peMax = dato == null ? -1 : dato.energyMax;
            fuerza = dato == null ? -1 : dato.strength;
            poder = dato == null ? -1 : dato.power;
            resistencia = dato == null ? -1 : dato.resistance;
            experiencia = dato == null ? -1 : dato.experience;
            // ALFP termina en nivel 50: nunca conservar un SIG anterior.
            siguiente = dato == null || dato.level >= 50 ? -1 : dato.nextExperience;
        }

        private void limpiarDatosAlfp() {
            modelo.clear();
            aplicarSeleccion();
            limpiarRetrato();
            habilidades = new Habilidad[0];
            habilidadesIconIds = new int[0];
            actualizarSeleccionHabilidad(null);
            objetos = new Objeto[0];
            misiones = new Mision[0];
            objetosDisponibles = diarioDisponible = false;
            radarDisponible = false;
            scrollObjetos = maxScrollObjetos = 0f;
            Arrays.fill(scrollDiario, 0f);
            Arrays.fill(maxScrollDiario, 0f);
        }

        private void actualizarDatosAlfp() {
            try {
                Legacy2MenuSnapshot copia = Legacy2MenuSnapshot.decode(
                    getMenuSnapshot(modelo.selectedId()));
                if (copia == null) {
                    limpiarDatosAlfp();
                    return;
                }
                modelo.update(copia.state);
                idioma = copia.state.language;
                aplicarSeleccion();
                int[] imagen = copia.portrait;
                if (modelo.selectedId() == copia.selectedId && imagen != null &&
                        imagen.length >= 2 && imagen[0] > 0 && imagen[1] > 0 &&
                        (long) imagen[0] * imagen[1] <= 1048576L &&
                        (long) imagen[0] * imagen[1] == imagen.length - 2) {
                    int[] pixels = new int[imagen.length - 2];
                    System.arraycopy(imagen, 2, pixels, 0, pixels.length);
                    actualizarRetrato(pixels, imagen[0], imagen[1]);
                } else actualizarRetrato(null, 0, 0);
                actualizarHabilidades(modelo.selectedId() == copia.selectedId
                    ? copia.skills : null);
                actualizarSeleccionHabilidad(modelo.selectedId() == copia.selectedId
                    ? copia.skillSelection : null);
                habilidadesIconIds = modelo.selectedId() == copia.selectedId &&
                    copia.skillIconIds != null &&
                    copia.skillIconIds.length == habilidades.length
                        ? copia.skillIconIds.clone() : new int[0];
                actualizarObjetos(copia.items);
                actualizarDiario(copia.journal);
            } catch (UnsatisfiedLinkError ignored) {
                limpiarDatosAlfp();
            }
        }

        private void actualizarDiario(String[] datos) {
            if (datos == null || datos.length != 43 * 4) {
                diarioDisponible = false;
                radarDisponible = false;
                return;
            }

            Mision[] nuevas = new Mision[43];

            for (int n = 0; n < 43; ++n) {
                int base = n * 4;

                try {
                    int id = Integer.parseInt(datos[base]);
                    String texto = datos[base + 1];
                    int estado = Integer.parseInt(datos[base + 2]);
                    int categoria = Integer.parseInt(datos[base + 3]);

                    if (id != n || texto == null ||
                            estado < -1 || estado > 2 ||
                            categoria < 0 || categoria > 2) {
                        diarioDisponible = false;
                        return;
                    }

                    nuevas[n] = new Mision(
                        id, texto, estado, categoria);

                } catch (NumberFormatException error) {
                    diarioDisponible = false;
                    return;
                }
            }

            misiones = nuevas;
            diarioDisponible = true;
            // Misión 34: entregar la Pieza del Explorador a Bulma.
            radarDisponible = nuevas[34].estado == 2;
        }

        private void actualizarObjetos(String[] datos) {
            // Invalid data must not retain a stale inventory.
            objetosDisponibles = false;
            objetos = new Objeto[0];
            if (datos == null || datos.length != 46 * 4)
                return;

            ArrayList<Objeto> nuevos = new ArrayList<>();


            for (int n = 0; n < 46; ++n) {
                int base = n * 4;

                try {
                    int id = Integer.parseInt(datos[base]);
                    int cantidad = Integer.parseInt(datos[base + 3]);

                    if (id != n || cantidad < 0)
                        return;

                    String nombre = datos[base + 1];
                    String descripcion = datos[base + 2];

                    if (nombre == null || descripcion == null ||
                            nombre.isEmpty() || descripcion.isEmpty())
                        return;
                    if (cantidad > 0) {
                        nuevos.add(new Objeto(
                            id, nombre, descripcion, cantidad));
                    }
                } catch (NumberFormatException error) {
                    return;
                }
            }

            objetos = nuevos.toArray(new Objeto[0]);
            objetosDisponibles = true;

        }

        private void actualizarHabilidades(String[] datos) {
            if (datos == null || datos.length == 0 || datos.length % 3 != 0) {
                habilidades = new Habilidad[0];
                return;
            }
            Habilidad[] nuevas = new Habilidad[datos.length / 3];
            for (int n = 0; n < nuevas.length; ++n) {
                String titulo = datos[n * 3];
                String descripcion = datos[n * 3 + 1];
                String estado = datos[n * 3 + 2];
                if (titulo == null || descripcion == null ||
                        titulo.isEmpty() || descripcion.isEmpty() ||
                        !("1".equals(estado) || "0".equals(estado) || "-1".equals(estado))) {
                    habilidades = new Habilidad[0];
                    return;
                }
                int disponible = "1".equals(estado) ? 1 : "0".equals(estado) ? 0 : -1;
                nuevas[n] = new Habilidad(titulo, descripcion, disponible);
            }
            habilidades = nuevas;
        }

        private void actualizarSeleccionHabilidad(int[] seleccion) {
            habilidadActivaPersonaje = -1;
            habilidadActiva = -1;
            puedeSeleccionarHabilidad = false;
            habilidadesGeneracion = -1;
            if (seleccion == null || seleccion.length != 4 ||
                    seleccion[0] < 0 || seleccion[0] >= 6 ||
                    seleccion[1] < -1 || seleccion[1] >= habilidades.length ||
                    (seleccion[2] != 0 && seleccion[2] != 1)) return;
            habilidadActivaPersonaje = seleccion[0];
            habilidadActiva = seleccion[1];
            puedeSeleccionarHabilidad = seleccion[2] == 1;
            habilidadesGeneracion = seleccion[3];
        }

        private void cambiarPersonaje(int direccion) {
            if (!modelo.move(direccion)) return;
            aplicarSeleccion();
            // Una única invalidación reconstruye fondo, capas, retrato y
            // todos los campos. La selección no se envía al jugador GBA.
            invalidate();
        }


        private String numero(int n) {
            return n < 0 ? "-" : Integer.toString(n);
        }

        private String fraccion(int a, int b) {
            return a < 0 || b < 0 ? "-" : a + "/" + b;
        }

        private void escribir(Canvas c, String valor,
                              float x, float y, float size,
                              int color, Paint.Align align) {
            estadoTexto.setTextAlign(align);
            estadoTexto.setTextSize(size);
            estadoTexto.setColor(Color.BLACK);
            c.drawText(valor, x + 4f, y + 5f, estadoTexto);
            estadoTexto.setColor(color);
            c.drawText(valor, x, y, estadoTexto);
        }

        private void rect(Canvas c, float x, float y,
                          float w, float h, int color) {
            estadoPixel.setColor(color);
            c.drawRect(x, y, x + w, y + h, estadoPixel);
        }

        private void barraVida(Canvas c, float y,
                               int actual, int max, int color) {
            float x = 316f, w = 388f, h = 49f;
            rect(c, x + 6, y + 8, w, h,
                 Color.rgb(23, 48, 65));
            rect(c, x, y, w, h, Color.rgb(35, 77, 98));
            if (actual >= 0 && max > 0) {
                float ratio = Math.max(0f,
                    Math.min(1f, (float)actual / max));
                rect(c, x, y, w * ratio, h, color);
            }
        }

        // 25 segmentos, 4 puntos por segmento completo.
        // Cada rojo tiene una sombra desplazada hacia abajo/derecha.
        private void barra25(Canvas c, float y, int puntos) {
            float x = 316f, paso = 15.5f;
            int valor = Math.min(100, Math.max(0, puntos));

            for (int i = 0; i < 25; i++) {
                float xx = x + i * paso;

                rect(c, xx + 3, y + 5, 10, 46,
                     Color.rgb(25, 55, 75));
                rect(c, xx, y, 10, 46,
                     Color.rgb(80, 134, 168));

                float fraccion = Math.min(4, Math.max(0, valor - i * 4)) / 4f;
                if (fraccion > 0f) {
                    rect(c, xx + 2, y + 4, 10 * fraccion, 46,
                         Color.rgb(136, 18, 27));
                    rect(c, xx - 2, y - 2, 10 * fraccion, 46,
                         Color.rgb(245, 28, 36));
                }
            }
        }

        private void botonGris(Canvas c, int x, int y,
                               boolean claro) {
            int base = claro ? Color.rgb(183,178,178)
                             : Color.rgb(92,92,92);

            rect(c, x + 7, y + 9, 77, 80, Color.BLACK);
            rect(c, x, y, 78, 82, base);
            rect(c, x + 7, y + 7, 62, 66,
                 claro ? Color.rgb(211,208,208)
                       : Color.rgb(112,112,112));
        }

        private void cabeceraEstado(Canvas c) {
            String nombre = personaje == null
                || personaje.isEmpty() ? "-" : personaje.toUpperCase(java.util.Locale.ROOT);

            // 20 px a la izquierda, 15 % mayor, negrita pixelada.
            boolean negritaAnterior = estadoTexto.isFakeBoldText();
            estadoTexto.setFakeBoldText(true);

            escribir(c, nombre, 290f, 151f, 62.1f,
                     Color.WHITE, Paint.Align.CENTER);
            escribir(c, labels()[4] + ": " + numero(nivel),
                     290f, 204f, 56.35f,
                     Color.rgb(255,197,27), Paint.Align.CENTER);

            estadoTexto.setFakeBoldText(negritaAnterior);
        }

        private void estado(Canvas c) {
            int blanco = Color.WHITE;
            int amarillo = Color.rgb(255,197,27);
            cabeceraEstado(c);

            escribir(c, rotulosLocalizados()[0], 296, 321, 51,
                     blanco, Paint.Align.RIGHT);
            barraVida(c, 271, pv, pvMax,
                      Color.rgb(255,18,31));
            escribir(c, fraccion(pv,pvMax), 716, 366, 47,
                     blanco, Paint.Align.RIGHT);

            escribir(c, rotulosLocalizados()[1], 296, 430, 51,
                     blanco, Paint.Align.RIGHT);
            barraVida(c, 379, pe, peMax,
                      Color.rgb(27,249,27));
            escribir(c, fraccion(pe,peMax), 716, 476, 47,
                     blanco, Paint.Align.RIGHT);

            escribir(c, rotulosLocalizados()[2], 296, 537, 48,
                     blanco, Paint.Align.RIGHT);
            barra25(c, 485, fuerza);
            escribir(c, fuerza < 0 ? "-/100" : fuerza+"/100",
                     716, 581, 45, blanco, Paint.Align.RIGHT);

            escribir(c, rotulosLocalizados()[3], 296, 643, 48,
                     blanco, Paint.Align.RIGHT);
            barra25(c, 590, poder);
            escribir(c, poder < 0 ? "-/100" : poder+"/100",
                     716, 686, 45, blanco, Paint.Align.RIGHT);

            escribir(c, rotulosLocalizados()[4], 296, 748, 48,
                     blanco, Paint.Align.RIGHT);
            barra25(c, 695, resistencia);
            escribir(c, resistencia < 0 ? "-/100"
                     : resistencia+"/100",
                     716, 792, 45, blanco, Paint.Align.RIGHT);

            // Retrato: el bitmap vendrá exclusivamente de la ROM
            // importada o de una copia de memoria del motor.
            rect(c, 792, 288, 330, 330,
                 Color.rgb(34,52,79));
            rect(c, 784, 280, 330, 330,
                 Color.rgb(207,211,215));
            rect(c, 792, 288, 314, 314,
                 Color.rgb(92,195,218));

            if (retratoMemoria != null) {
                float escala = Math.min(
                    312f/retratoMemoria.getWidth(),
                    312f/retratoMemoria.getHeight());

                float w = retratoMemoria.getWidth()*escala;
                float h = retratoMemoria.getHeight()*escala;

                imagen(c, retratoMemoria,
                    793f+(312f-w)/2f,
                    289f+(312f-h)/2f, w, h, 255);
            }

            escribir(c, rotulosLocalizados()[5], 792, 695, 47,
                     amarillo, Paint.Align.LEFT);
            escribir(c, numero(experiencia), 1120, 695, 47,
                     blanco, Paint.Align.RIGHT);

            escribir(c, rotulosLocalizados()[6], 792, 755, 47,
                     amarillo, Paint.Align.LEFT);
            escribir(c, numero(siguiente), 1120, 755, 47,
                     blanco, Paint.Align.RIGHT);

            controlesEstado(c);
        }

        private void controlesEstado(Canvas c) {
            flechaVertical(c, 124, 292, false, paginaEstado == 1);
            flechaVertical(c, 124, 677, true,
                paginaEstado == 0 && modelo.count() > 0);
        }

        private void flechaVertical(Canvas c, int x, int y,
                                    boolean abajo, boolean habilitada) {
            botonGris(c, x, y, habilitada);
            android.graphics.Path p = new android.graphics.Path();
            float centroX = x + 39f, centroY = y + 41f;
            p.moveTo(centroX - 23f, centroY + (abajo ? -14f : 14f));
            p.lineTo(centroX + 23f, centroY + (abajo ? -14f : 14f));
            p.lineTo(centroX, centroY + (abajo ? 19f : -19f));
            p.close();
            estadoPixel.setColor(habilitada ? Color.rgb(38,38,38) : Color.rgb(69,69,69));
            c.drawPath(p, estadoPixel);
        }

        private ArrayList<String> envolver(String contenido, float ancho, float size) {
            ArrayList<String> lineas = new ArrayList<>();
            estadoTexto.setTextSize(size);
            for (String parrafo : contenido.replace("\r", "").split("\n", -1)) {
                StringBuilder linea = new StringBuilder();
                for (String palabra : parrafo.split("\\s+")) {
                    if (palabra.isEmpty()) continue;
                    String candidata = linea.length() == 0 ? palabra : linea + " " + palabra;
                    if (estadoTexto.measureText(candidata) > ancho && linea.length() > 0) {
                        lineas.add(linea.toString());
                        linea.setLength(0);
                    }
                    if (linea.length() > 0) linea.append(' ');
                    for (int n = 0; n < palabra.length(); ++n) {
                        if (linea.length() > 0 &&
                                estadoTexto.measureText(linea.toString() + palabra.charAt(n)) > ancho) {
                            lineas.add(linea.toString());
                            linea.setLength(0);
                        }
                        linea.append(palabra.charAt(n));
                    }
                }
                lineas.add(linea.toString());
            }
            return lineas;
        }

        private void dibujarHabilidades(Canvas c) {
            cabeceraEstado(c);
            int id = modelo.selectedId();
            int guardado = c.save();
            c.clipRect(areaHabilidades);
            if (id < 0 || habilidades.length == 0) {
                rotulo(c, labels()[8], 680f, 540f, 38f, Color.WHITE);
                maxScrollHabilidades = 0f;
            } else {
                // La fuente, el color y la sombra son los de MISIONES.
                Paint titulo = new Paint(botonTexto);
                titulo.setTextAlign(Paint.Align.LEFT);
                titulo.setTextSize(52f);
                titulo.setFakeBoldText(true);
                Paint tipo = new Paint(titulo);
                tipo.setTextSize(40f);
                Paint contenido = new Paint(titulo);
                contenido.setTextSize(48f);

                final float separacion = 18f;
                final float iconoLado = 120f;
                final float textoX = areaHabilidades.left + 164f;
                final float anchoTexto = areaHabilidades.right - textoX - 20f;
                ArrayList<ArrayList<String>> titulos = new ArrayList<>();
                ArrayList<ArrayList<String>> tipos = new ArrayList<>();
                ArrayList<ArrayList<String>> descripciones = new ArrayList<>();
                float[] alturas = new float[habilidades.length];
                float total = 0f;
                for (int n = 0; n < habilidades.length; ++n) {
                    ArrayList<String> nombre = envolverDiario(
                        habilidades[n].titulo, titulo, anchoTexto);
                    ArrayList<String> tipoLineas = envolverDiario(
                        tipoHabilidad(n), tipo, anchoTexto);
                    ArrayList<String> lineas = envolverDiario(
                        habilidades[n].descripcion, contenido, anchoTexto);
                    titulos.add(nombre);
                    tipos.add(tipoLineas);
                    descripciones.add(lineas);
                    // Nunca caben más de tres filas en el panel; las
                    // descripciones largas aumentan la fila y se desplazan.
                    alturas[n] = Math.max(areaHabilidades.height() / 3f,
                        24f + nombre.size() * 60f + tipoLineas.size() * 48f +
                            lineas.size() * 62f);
                    total += alturas[n] + separacion;
                }
                maxScrollHabilidades = Math.max(0f,
                    total - separacion - areaHabilidades.height());
                scrollHabilidades = Math.max(0f, Math.min(maxScrollHabilidades, scrollHabilidades));
                float y = areaHabilidades.top - scrollHabilidades;
                for (int n = 0; n < habilidades.length; ++n) {
                    Habilidad habilidad = habilidades[n];
                    habilidad.limite.set(areaHabilidades.left, y,
                        areaHabilidades.right, y + alturas[n]);
                    boolean seleccionada = n > 0 && habilidad.disponible == 1 &&
                        habilidadActivaPersonaje == id && habilidadActiva == n;
                    if (seleccionada) {
                        // Solo la técnica realmente activa recibe el borde y
                        // una sombra interior leve; el resto es transparente.
                        Paint seleccion = new Paint();
                        seleccion.setColor(Color.argb(34, 0, 0, 0));
                        c.drawRect(habilidad.limite.left + 4f, y + 4f,
                            habilidad.limite.right - 4f, y + alturas[n] - 4f, seleccion);
                        seleccion.setStyle(Paint.Style.STROKE);
                        seleccion.setStrokeWidth(5f);
                        seleccion.setColor(Color.rgb(255,197,27));
                        c.drawRect(habilidad.limite.left + 3f, y + 3f,
                            habilidad.limite.right - 3f, y + alturas[n] - 3f, seleccion);
                    }
                    if (habilidad.disponible == 1) {
                        Bitmap icono = cargarIconoHabilidad(id, n);
                        if (icono != null) {
                            float escalaIcono = iconoLado /
                                Math.max(icono.getWidth(), icono.getHeight());
                            float anchoIcono = icono.getWidth() * escalaIcono;
                            float altoIcono = icono.getHeight() * escalaIcono;
                            float iconoX = areaHabilidades.left + 18f +
                                (iconoLado - anchoIcono) / 2f;
                            float iconoY = y + 20f + (iconoLado - altoIcono) / 2f;
                            c.drawBitmap(icono, null,
                                new RectF(iconoX, iconoY,
                                    iconoX + anchoIcono, iconoY + altoIcono), arte);
                        }
                    }
                    float lineaY = y + 50f;
                    for (String linea : titulos.get(n)) {
                        textoHabilidad(c, linea, textoX, lineaY, titulo);
                        lineaY += 60f;
                    }
                    lineaY -= 15f;
                    for (String linea : tipos.get(n)) {
                        textoHabilidad(c, linea, textoX, lineaY, tipo);
                        lineaY += 48f;
                    }
                    lineaY += 2f;
                    for (String linea : descripciones.get(n)) {
                        textoHabilidad(c, linea, textoX, lineaY, contenido);
                        lineaY += 62f;
                    }
                    y += alturas[n] + separacion;
                }
            }
            c.restoreToCount(guardado);
            controlesEstado(c);
        }

        private void textoHabilidad(Canvas c, String texto,
                float x, float y, Paint pintura) {
            pintura.setColor(Color.BLACK);
            c.drawText(texto, x + 3f, y + 3f, pintura);
            pintura.setColor(Color.rgb(255, 170, 155));
            c.drawText(texto, x, y, pintura);
        }

        private String tipoHabilidad(int slot) {
            final String[][] tipos = {
                {"PHYSICAL ATTACK", "ENERGY TECHNIQUE"},
                {"ATTAQUE PHYSIQUE", "TECHNIQUE ÉNERGÉTIQUE"},
                {"KÖRPERLICHER ANGRIFF", "ENERGIETECHNIK"},
                {"ATTACCO FISICO", "TECNICA ENERGETICA"},
                {"ATAQUE FÍSICO", "TÉCNICA ENERGÉTICA"}
            };
            int lang = idioma >= 0 && idioma < tipos.length ? idioma : 4;
            return tipos[lang][slot == 0 ? 0 : 1];
        }

        private Bitmap cargarIconoHabilidad(int character, int slot) {
            if (character < 0 || character >= 6 || slot < 0 ||
                    slot >= habilidadesIconIds.length) return null;
            return Legacy2SkillResources.icon(getContext(), character, slot,
                habilidadesIconIds[slot]);
        }

        private void cargarImagenesObjetos() {
            if (imagenesObjetosCargadas) return;

            java.io.File carpeta =
                Legacy2ItemResources.directory(getContext());

            java.io.File archivoPequeno =
                new java.io.File(carpeta, "items.pal");
            java.io.File archivoGrande =
                new java.io.File(carpeta, "items-large.pal");

            if (!archivoPequeno.isFile() ||
                    !archivoGrande.isFile()) return;

            try {
                int[][] paletas = new int[2][256];
                java.io.File[] archivos = {
                    archivoPequeno, archivoGrande
                };

                for (int p = 0; p < 2; p++) {
                    byte[] datos = java.nio.file.Files.readAllBytes(
                        archivos[p].toPath());

                    if (datos.length != 512) return;

                    for (int i = 0; i < 256; i++) {
                        int valor = (datos[i * 2] & 255) |
                            ((datos[i * 2 + 1] & 255) << 8);

                        int rojo = (valor & 31) * 255 / 31;
                        int verde = ((valor >> 5) & 31) * 255 / 31;
                        int azul = ((valor >> 10) & 31) * 255 / 31;

                        paletas[p][i] = i == 0
                            ? Color.TRANSPARENT
                            : Color.rgb(rojo, verde, azul);
                    }
                }

                int[] campos = {20, 36, 40, 44};

                for (int id = 0; id < 46; id++) {
                    for (int variante = 0;
                            variante < campos.length; variante++) {

                        java.io.File archivo = new java.io.File(
                            carpeta,
                            "item_" + id + "_" +
                            campos[variante] + ".idx"
                        );

                        if (!archivo.isFile()) continue;

                        byte[] indices =
                            java.nio.file.Files.readAllBytes(
                                archivo.toPath());

                        int lado = indices.length == 256 ? 16 :
                            indices.length == 1024 ? 32 : 0;

                        if (lado == 0) continue;

                        // La variante 3 (+0x2C) usa la paleta grande.
                        int[] paleta = variante == 3
                            ? paletas[1] : paletas[0];

                        int[] pixels = new int[indices.length];

                        for (int n = 0; n < pixels.length; n++)
                            pixels[n] = paleta[indices[n] & 255];

                        imagenesObjetos[id][variante] =
                            Bitmap.createBitmap(
                                pixels, lado, lado,
                                Bitmap.Config.ARGB_8888);
                    }
                }

                imagenesObjetosCargadas = true;

            } catch (Exception error) {
                android.util.Log.e("Legacy2Items",
                    "Error cargando gráficos originales", error);
            }
        }


        private Bitmap imagenObjeto(int id, boolean grande) {
            if (id < 0 || id >= imagenesObjetos.length)
                return null;

            // +0x24: variante habitual de lista.
            // +0x2C: ilustración grande de 32x32.
            int[] prioridad = grande
                ? new int[]{3, 2, 1, 0}
                : new int[]{1, 0, 2, 3};

            for (int variante : prioridad) {
                Bitmap imagen = imagenesObjetos[id][variante];
                if (imagen != null)
                    return imagen;
            }
            return null;
        }



        private void dibujarObjetos(Canvas c) {
            cargarImagenesObjetos();

            final float inicioY = 250f;
            final float finalY = 850f;
            final float centroGrandeX = 903f;

            // Cabecera OBJETO: misma posición y estilo que MISIONES.
            // Orden ALFP: inglés, francés, alemán, italiano, español.
            final String[] titulosObjeto = {
                "ITEM", "OBJET", "GEGENSTAND", "OGGETTO", "OBJETO"
            };
            final int idiomaObjeto =
                idioma >= 0 && idioma < titulosObjeto.length ? idioma : 4;
            final String tituloObjeto = titulosObjeto[idiomaObjeto];

            Paint tituloObjetoPaint = new Paint(botonTexto);
            tituloObjetoPaint.setTextAlign(Paint.Align.CENTER);
            tituloObjetoPaint.setTextSize(48.3f);
            tituloObjetoPaint.setFakeBoldText(true);

            // Sombra negra idéntica al DIARIO.
            tituloObjetoPaint.setColor(Color.BLACK);
            c.save();
            c.scale(1.2f, 1.2f, 333f, 165f);
            c.drawText(tituloObjeto, 333f, 165f, tituloObjetoPaint);
            c.restore();

            // Texto blanco idéntico al DIARIO.
            tituloObjetoPaint.setColor(Color.WHITE);
            c.save();
            c.scale(1.2f, 1.2f, 330f, 162f);
            c.drawText(tituloObjeto, 330f, 162f, tituloObjetoPaint);
            c.restore();

            int guardado = c.save();
            c.clipRect(areaObjetos);

            if (!objetosDisponibles || objetos.length == 0) {
                maxScrollObjetos = 0f;
                scrollObjetos = 0f;

                escribir(c, objetosDisponibles
                        ? rotulosLocalizados()[7] : labels()[8],
                    630f, 545f, 42f,
                    Color.WHITE, Paint.Align.CENTER);

                c.restoreToCount(guardado);
                return;
            }

            maxScrollObjetos = Math.max(0f,
                objetos.length * ALTO_FILA_OBJETO -
                (finalY - inicioY));

            scrollObjetos = Math.max(0f,
                Math.min(maxScrollObjetos, scrollObjetos));

            // LISTA: margen superior de 40 px.
            // Hasta cuatro elementos, sin fondos ni separadores.
            int lista = c.save();
            c.clipRect(140f, inicioY, 669f, finalY);

            for (int n = 0; n < objetos.length; n++) {
                Objeto objeto = objetos[n];

                float y = inicioY +
                    n * ALTO_FILA_OBJETO - scrollObjetos;

                if (y >= finalY ||
                    y + ALTO_FILA_OBJETO <= inicioY)
                    continue;

                boolean seleccionado =
                    objetoSeleccionado == objeto.id;

                // Cantidad a la IZQUIERDA del icono.
                escribir(c, "x" + objeto.cantidad,
                    148f, y + 92f, 34f,
                    Color.rgb(255, 209, 96),
                    Paint.Align.LEFT);

                // Icono original pequeño.
                Bitmap icono = imagenObjeto(objeto.id, false);

                if (icono != null)
                    c.drawBitmap(icono, null,
                        new RectF(
                            210f, y + 19f,
                            326f, y + 135f),
                        arte);

                // Nombre del objeto.
                String nombre = objeto.nombre;

                float tamano = nombre.length() > 18
                    ? 34.8f : 43.2f;

                if (nombre.length() > 21)
                    nombre = nombre.substring(0, 20) + "…";

                // Mantener el incremento sin cortar nombres largos.
                estadoTexto.setTextSize(tamano);
                float anchoNombre = estadoTexto.measureText(nombre);
                if (anchoNombre > 312f)
                    tamano *= 312f / anchoNombre;

                escribir(c, nombre,
                    342f, y + 89f, tamano,
                    seleccionado
                        ? Color.rgb(255, 209, 96)
                        : Color.WHITE,
                    Paint.Align.LEFT);
            }

            c.restoreToCount(lista);

            // IMAGEN AMPLIADA.
            Objeto elegido = null;

            for (Objeto objeto : objetos) {
                if (objeto.id == objetoSeleccionado) {
                    elegido = objeto;
                    break;
                }
            }

            if (elegido == null)
                elegido = objetos[0];

            // Mismo margen superior que la lista.
            float tamanoNombreGrande = 51.6f;

            estadoTexto.setTextSize(tamanoNombreGrande);
            float anchoNombreGrande =
                estadoTexto.measureText(elegido.nombre);

            if (anchoNombreGrande > 440f)
                tamanoNombreGrande *= 440f / anchoNombreGrande;

            escribir(c, elegido.nombre,
                centroGrandeX, 321f, tamanoNombreGrande,
                Color.WHITE, Paint.Align.CENTER);

            Bitmap grande = imagenObjeto(elegido.id, true);

            if (grande != null) {
                float lado = 260f;

                c.drawBitmap(grande, null,
                    new RectF(
                        centroGrandeX - lado / 2f, 360f,
                        centroGrandeX + lado / 2f, 360f + lado),
                    arte);
            }

            // Descripción centrada respecto a la imagen ampliada.
            ArrayList<String> lineas = envolver(
                elegido.descripcion, 425f, 34f);

            float lineaY = 689f;

            for (String linea : lineas) {
                if (lineaY > 839f) break;

                escribir(c, linea,
                    centroGrandeX, lineaY, 34f,
                    Color.WHITE, Paint.Align.CENTER);

                lineaY += 45f;
            }

            c.restoreToCount(guardado);
        }





        // Ajustar líneas con la fuente pixelada real del DIARIO.
        private ArrayList<String> envolverDiario(
                String contenido, Paint pintura, float ancho) {

            ArrayList<String> resultado = new ArrayList<>();

            for (String parrafo : contenido.replace("\r", "").split("\n", -1)) {
                StringBuilder linea = new StringBuilder();

                for (String palabra : parrafo.trim().split("\\s+")) {
                    if (palabra.isEmpty()) continue;

                    String candidata = linea.length() == 0
                        ? palabra : linea + " " + palabra;

                    if (pintura.measureText(candidata) <= ancho) {
                        linea.setLength(0);
                        linea.append(candidata);
                        continue;
                    }

                    if (linea.length() > 0) {
                        resultado.add(linea.toString());
                        linea.setLength(0);
                    }

                    if (pintura.measureText(palabra) <= ancho) {
                        linea.append(palabra);
                    } else {
                        // Dividir también palabras demasiado largas.
                        for (int i = 0; i < palabra.length(); ++i) {
                            char letra = palabra.charAt(i);
                            if (linea.length() > 0 &&
                                    pintura.measureText(linea.toString() + letra) > ancho) {
                                resultado.add(linea.toString());
                                linea.setLength(0);
                            }
                            linea.append(letra);
                        }
                    }
                }

                if (linea.length() > 0)
                    resultado.add(linea.toString());
                else if (parrafo.trim().isEmpty())
                    resultado.add("");
            }

            return resultado;
        }


        // Iconos pixelados: estrellas de misiones y marcas de completadas.
        private void dibujarIconoDiario(
                Canvas c, float x, float y,
                boolean terminada, int categoria) {

            Paint p = new Paint();
            p.setAntiAlias(false);

            if (terminada) {
                p.setColor(Color.argb(120, 255, 170, 215));
                c.drawRect(x - 27f, y - 27f, x + 27f, y + 27f, p);

                p.setColor(Color.argb(210, 0, 0, 0));
                c.drawRect(x - 25f + 6f, y - 25f + 6f,
                    x + 25f + 6f, y + 25f + 6f, p);

                p.setColor(Color.WHITE);
                c.drawRect(x - 25f, y - 25f, x + 25f, y + 25f, p);

                android.graphics.Path marca =
                    new android.graphics.Path();
                marca.moveTo(x - 16f, y + 1f);
                marca.lineTo(x - 5f, y + 13f);
                marca.lineTo(x + 19f, y - 15f);

                p.setColor(Color.rgb(255, 65, 145));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(9f);
                p.setStrokeJoin(Paint.Join.MITER);
                c.drawPath(marca, p);

            } else {
                // Color según la categoría original de la ROM ALFP.
                final int colorEstrella;
                if (categoria == 0) {
                    colorEstrella = Color.rgb(255, 205, 65); // Oro
                } else if (categoria == 1) {
                    colorEstrella = Color.rgb(210, 225, 245); // Plata
                } else {
                    colorEstrella = Color.rgb(206, 130, 78); // Bronce
                }

                android.graphics.Path estrella =
                    new android.graphics.Path();

                for (int i = 0; i < 10; i++) {
                    double angulo =
                        -Math.PI / 2.0 + i * Math.PI / 5.0;
                    float radio = i % 2 == 0 ? 35f : 15f;
                    float px = x + radio * (float)Math.cos(angulo);
                    float py = y + radio * (float)Math.sin(angulo);

                    if (i == 0) estrella.moveTo(px, py);
                    else estrella.lineTo(px, py);
                }
                estrella.close();

                Paint glow = new Paint();
                glow.setAntiAlias(false);
                glow.setStyle(Paint.Style.FILL);
                glow.setColor(Color.argb(110,
                    Color.red(colorEstrella),
                    Color.green(colorEstrella),
                    Color.blue(colorEstrella)));
                c.save();
                c.scale(1.18f, 1.18f, x, y);
                c.drawPath(estrella, glow);
                c.restore();

                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(170, 0, 0, 0));
                c.save();
                c.translate(7f, 7f);
                c.drawPath(estrella, p);
                c.restore();

                p.setColor(Color.BLACK);
                c.save();
                c.translate(4f, 4f);
                c.drawPath(estrella, p);
                c.restore();

                p.setColor(colorEstrella);
                c.drawPath(estrella, p);
            }
        }

        private void dibujarDiario(Canvas c) {
            final String[] textosDiario = TEXTOS_DIARIO[
                idioma >= 0 && idioma < TEXTOS_DIARIO.length ? idioma : 4
            ];
            final String titulo =
                paginaDiario == 0 ? textosDiario[0] : textosDiario[1];

            // Cabecera activa, con la misma fuente pixelada
            // que el resto de la interfaz.
            Paint tituloPaint = new Paint(botonTexto);
            tituloPaint.setTextAlign(Paint.Align.CENTER);
            tituloPaint.setTextSize(48.3f);
            tituloPaint.setFakeBoldText(true);
            tituloPaint.setColor(Color.BLACK);
            {
                float tituloDiarioX = 353f + (paginaDiario == 1 ? 80f : 0f);
                float tituloDiarioY = (155f) + 10f;
                c.save();
                c.scale(1.2f, 1.2f, tituloDiarioX, tituloDiarioY);
                c.drawText(titulo, tituloDiarioX, tituloDiarioY, tituloPaint);
                c.restore();
            }
            tituloPaint.setColor(Color.WHITE);
            {
                float tituloDiarioX = 350f + (paginaDiario == 1 ? 80f : 0f);
                float tituloDiarioY = (152f) + 10f;
                c.save();
                c.scale(1.2f, 1.2f, tituloDiarioX, tituloDiarioY);
                c.drawText(titulo, tituloDiarioX, tituloDiarioY, tituloPaint);
                c.restore();
            }

            // El cartel original ya dibuja la textura del panel.
            // Aquí solo dibujamos su contenido, sin flechas internas.
            int guardado = c.save();
            c.clipRect(areaDiario);



            // Misiones originales de la ROM, con presentación ampliada.
            final int estadoVisible = paginaDiario == 0 ? 1 : 2;

            Paint contenido = new Paint(botonTexto);
            contenido.setTextAlign(Paint.Align.LEFT);
            contenido.setTextSize(48f);
            contenido.setFakeBoldText(true);

            ArrayList<ArrayList<String>> entradas = new ArrayList<>();
            ArrayList<Mision> misionesVisibles = new ArrayList<>();
            int desconocidas = 0;

            if (diarioDisponible) {
                for (Mision mision : misiones) {
                    if (mision.estado == -1) ++desconocidas;

                    if (mision.estado == estadoVisible &&
                            mision.texto != null &&
                            !mision.texto.isEmpty()) {
                        entradas.add(envolverDiario(
                            mision.texto, contenido,
                            areaDiario.width() - 136f));
                        misionesVisibles.add(mision);
                    }
                }
            }

            final float alturaLinea = 62f;
            final float espacioMisiones = 30f;
            float alturaTotal = 80f;

            for (ArrayList<String> lineas : entradas) {
                alturaTotal +=
                    Math.max(1, lineas.size()) * alturaLinea
                    + espacioMisiones;
            }

            maxScrollDiario[paginaDiario] = Math.max(
                0f, alturaTotal - areaDiario.height());

            scrollDiario[paginaDiario] = Math.max(
                0f, Math.min(
                    maxScrollDiario[paginaDiario],
                    scrollDiario[paginaDiario]));

            c.translate(0f, -scrollDiario[paginaDiario]);

            if (entradas.isEmpty()) {
                String mensaje;

                if (!diarioDisponible) {
                    mensaje = textosDiario[2];
                } else if (desconocidas > 0) {
                    mensaje = textosDiario[3];
                } else {
                    mensaje = paginaDiario == 0
                        ? textosDiario[4] : textosDiario[5];
                }

                contenido.setTextAlign(Paint.Align.CENTER);
                contenido.setTextSize(35f);
                contenido.setColor(Color.BLACK);
                c.drawText(mensaje, 623f, 543f, contenido);
                contenido.setColor(Color.rgb(255, 170, 155));
                c.drawText(mensaje, 620f, 540f, contenido);

            } else {
                float y = areaDiario.top + 62f;
                float x = areaDiario.left + 107f;
                int indice = 0;

                for (ArrayList<String> lineas : entradas) {
                    float iconoX = areaDiario.left + 52f;
                    float altoBloque =
                        Math.max(1, lineas.size()) * alturaLinea;
                    float iconoY =
                        y - 18f + (altoBloque - alturaLinea) / 2f;

                    dibujarIconoDiario(
                        c, iconoX, iconoY,
                        paginaDiario == 1,
                        misionesVisibles.get(indice).categoria);

                    for (String linea : lineas) {
                        contenido.setColor(Color.BLACK);
                        c.drawText(linea, x + 3f, y + 3f, contenido);

                        contenido.setColor(Color.rgb(255, 170, 155));
                        c.drawText(linea, x, y, contenido);

                        y += alturaLinea;
                    }

                    y += espacioMisiones;
                    indice++;
                }
            }

            c.restoreToCount(guardado);
        }

        private void flecha(Canvas c, float x,
                            float y, boolean derecha, boolean habilitada) {
            int guardado = c.saveLayerAlpha(x - 45f, y - 50f,
                x + 55f, y + 60f, habilitada ? 255 : 105);
            botonGris(c, (int)(x-39), (int)(y-41), false);

            android.graphics.Path p =
                new android.graphics.Path();

            if (derecha) {
                p.moveTo(x-18, y-24);
                p.lineTo(x+20, y);
                p.lineTo(x-18, y+24);
            } else {
                p.moveTo(x+18, y-24);
                p.lineTo(x-20, y);
                p.lineTo(x+18, y+24);
            }

            p.close();
            estadoPixel.setColor(Color.rgb(43,43,43));
            c.drawPath(p, estadoPixel);
            c.restoreToCount(guardado);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.save();
            Legacy2MenuViewport viewport =
                new Legacy2MenuViewport(getWidth(), getHeight());
            if (getWidth() != ultimoAnchoDiagnostico ||
                    getHeight() != ultimoAltoDiagnostico) {
                ultimoAnchoDiagnostico = getWidth();
                ultimoAltoDiagnostico = getHeight();
                android.util.Log.i("L2_PLANTILLA",
                    "VIEW=" + getWidth() + "x" + getHeight() +
                    " ROOT=" + getRootView().getWidth() +
                    "x" + getRootView().getHeight() +
                    " SCALE=" + viewport.scale +
                    " LEFT=" + viewport.left +
                    " TOP=" + viewport.top);
            }
            canvas.translate(viewport.left, viewport.top);
            canvas.scale(viewport.scale, viewport.scale);

            actualizarDatosAlfp();

            // Ocultar el loading únicamente cuando el actor real
            // del personaje ya existe dentro del escenario.
            // Una vez oculto, no volver a mostrarlo.
            // Mantener loading.png durante 5 segundos desde
            // la primera detección del escenario jugable.
            if (cargando) {
                long ahora = android.os.SystemClock.uptimeMillis();

                if (primeraDeteccionPersonaje < 0L &&
                        isPlayerActorPresent()) {
                    primeraDeteccionPersonaje = ahora;
                }

                if (primeraDeteccionPersonaje >= 0L &&
                        ahora - primeraDeteccionPersonaje >= 5000L) {
                    cargando = false;
                }
            }

            if (cargando) {
                canvas.drawBitmap(cargaImagen, null,
                    new RectF(0f, 0f, W, H), arte);

                long tiempo = android.os.SystemClock.uptimeMillis();
                int cantidad = 1 + (int)((tiempo / 500L) % 3L);
                int idiomaCarga = idioma >= 0 && idioma < MENSAJES_CARGA.length
                    ? idioma : 4;
                String mensaje = MENSAJES_CARGA[idiomaCarga] +
                    "...".substring(0, cantidad);

                float centroY = H / 2f -
                    (textoCarga.ascent() + textoCarga.descent()) / 2f;

                canvas.drawText(mensaje, W / 2f, centroY, textoCarga);

                canvas.restore();
                postInvalidateDelayed(500L - tiempo % 500L);
                return;
            }

            fondoAnimado(canvas);
            if (seccion == 3) {
                dibujarMapa(canvas);
                canvas.restore();
                postInvalidateDelayed(33);
                return;
            }
            pestañasPosteriores(canvas);
            pestañaPosteriorDiario(canvas);

            // LAYER 2: panel azul original.
            if (plantilla != null) {
                canvas.drawBitmap(plantilla, 0f, 0f, arte);
            } else {
                formas.setColor(Color.rgb(31, 65, 128));
                canvas.drawRoundRect(
                    95, 250, 1145, 1020,
                    25, 25, formas
                );
            }

            // LAYER 3: pestañas y orden de profundidad.
            pestañas(canvas);
            // Pestañas de DIARIO por encima del panel principal.
            pestañaFrontalDiario(canvas);
            pestañaFrontalEstado(canvas);

            // Cartel ornamental.
            if ((seccion != 1 || paginaEstado == 1) && cartel != null) {
                // ESTADO solo usa el cartel en su subpágina de habilidades.
                // Lienzo completo original.
                canvas.drawBitmap(cartel, 0f, 0f, arte);
            }

            // LAYER 4: información de sección.
            if (seccion == 1) {
                if (paginaEstado == 0) estado(canvas);
                else dibujarHabilidades(canvas);
            } else if (seccion == 0) {
                dibujarDiario(canvas);
            } else if (seccion == 2) {
                dibujarObjetos(canvas);
            } else if (seccion == 3) {
                int lang = idioma >= 0 && idioma < 5 ? idioma : 4;
                rotulo(canvas, NOMBRES_MAPA[lang],
                    620, 530, 48, Color.WHITE);
            } else {
                rotulo(canvas, labels()[seccion],
                    620, 530, 48, Color.WHITE);
                rotulo(canvas, labels()[8],
                    620, 610, 26,
                    Color.rgb(203, 199, 235));
            }

            // LAYER 5: controles 100% por código.
            if (seccion == 0) {
                flecha(canvas, 84, 86, false,
                    paginaDiario == 1);
                flecha(canvas, 1156, 86, true,
                    paginaDiario == 0);
            } else {
                boolean navegar =
                    seccion == 1 && modelo.count() > 1;
                flecha(canvas, 84, 86, false, navegar);
                flecha(canvas, 1156, 86, true, navegar);
            }

            // MAPA: zona superior, 50 píxeles a la derecha del centro.
            if (seccion != 3 && radarDisponible) {
                int lang = idioma >= 0 && idioma < 5 ? idioma : 4;
                float tamanoAnteriorMapa = botonTexto.getTextSize();
                boolean negritaAnteriorMapa =
                    botonTexto.isFakeBoldText();

                botonTexto.setTextSize(tamanoAnteriorMapa * 1.15f);
                botonTexto.setFakeBoldText(true);

                dibujarBoton(canvas, NOMBRES_MAPA[lang], 870f, 110f);

                botonTexto.setTextSize(tamanoAnteriorMapa);
                botonTexto.setFakeBoldText(negritaAnteriorMapa);
            }

            canvas.restore();

            fase = (fase + 1) % 100000;
            postInvalidateDelayed(80);
        }

        private boolean volverDesdeMapa(float x, float y) {
            return x >= 450f && x <= 790f && y >= 975f && y <= H;
        }

        private void dibujarMapa(Canvas c) {
            // Mantener la proporción de la plantilla suministrada (1346x1168).
            float escala = Math.min(W / 1346f, H / 1168f);
            float izquierda = (W - 1346f * escala) / 2f;
            float arriba = (H - 1168f * escala) / 2f;
            formas.setColor(Color.rgb(48, 48, 48));
            c.drawRect(0f, 0f, W, H, formas);
            if (marcoMapa != null)
                c.drawBitmap(marcoMapa, null, new RectF(izquierda, arriba,
                    izquierda + 1346f * escala, arriba + 1168f * escala), arte);
            // El interior se reserva al contenido regional generado al importar
            // la ROM. No usar el blanco de la plantilla como fondo del visor.
            formas.setColor(Color.rgb(48, 48, 48));
            c.drawRect(izquierda + 116f * escala, arriba + 120f * escala,
                izquierda + 1230f * escala, arriba + 1051f * escala, formas);
            int[] estadoMapa = getScouterSnapshot();
            if (++contadorMapaProbe % 40 == 1) {
                android.util.Log.i("Legacy2MapProbe",
                    estadoMapa == null || estadoMapa.length < 11
                    ? "Snapshot no disponible"
                    : "valid=" + estadoMapa[0]
                        + " region=" + estadoMapa[1]
                        + " area=" + estadoMapa[2]
                        + " actorFixed=" + estadoMapa[3] + "," + estadoMapa[4]
                        + " dimensions=" + estadoMapa[5] + "x" + estadoMapa[6]
                        + " marker=" + estadoMapa[7] + "," + estadoMapa[8]
                        + " reveal=" + estadoMapa[9]
                        + " generation=" + estadoMapa[10]);
            }
            scouter.draw(c, new RectF(izquierda + 116f * escala,
                arriba + 120f * escala, izquierda + 1230f * escala,
                arriba + 1051f * escala), estadoMapa);
            // Sustituir la flecha inferior por la misma tipografía, tamaño y
            // sombreado usados en DIARIO / ESTADO / OBJETOS.
            formas.setColor(Color.rgb(55, 64, 51));
            c.drawRect(450f, 980f, 790f, H, formas);
            dibujarBoton(c, labels()[1], W / 2f, 1050f);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (cargando) return true;

            Legacy2MenuViewport viewport =
                new Legacy2MenuViewport(getWidth(), getHeight());
            float x = viewport.x(event.getX());
            float y = viewport.y(event.getY());
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                gestoCancelado = !viewport.contains(event.getX(), event.getY());
                toqueX = x;
                toqueY = ultimoToqueY = y;
                toqueArrastrado = false;
                flechaPulsada = direccionFlecha(x, y);

                // Sonido solo en flechas habilitadas, al apoyar el dedo.
                if (!gestoCancelado &&
                        (flechaPulsada != 0 ||
                         (seccion == 1 && x >= 124f && x <= 208f &&
                          ((paginaEstado == 1 &&
                            y >= 292f && y <= 383f) ||
                           (paginaEstado == 0 && modelo.count() > 0 &&
                            y >= 677f && y <= 768f)))))
                    reproducirFlecha();

                if (!gestoCancelado && seccion == 0 &&
                        x >= 210f && x <= 650f &&
                        y >= 45f && y <= 210f)
                    reproducirCambioPestana();
                if (seccion == 3) {
                    flechaPulsada = 0;
                    if (!gestoCancelado && volverDesdeMapa(x, y))
                        reproducirCambioPestana();
                    return true;
                }

                // MAPA reproduce el sonido al apoyar el dedo.
                if (!gestoCancelado && seccion != 3 &&
                        radarDisponible &&
                        x >= 750f && x <= 990f &&
                        y >= 40f && y <= 155f)
                    reproducirCambioPestana();

                // Sonido al apoyar el dedo sobre otro botón.
                if (!gestoCancelado) {
                    int destino = seccionBotonEn(x, y);
                    if (destino >= 0 && destino != seccion)
                        reproducirCambioPestana();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL ||
                    event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                flechaPulsada = 0;
                toqueArrastrado = false;
                gestoCancelado = true;
                return true;
            }
            if (gestoCancelado) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(x - toqueX) > 14f || Math.abs(y - toqueY) > 14f)
                    toqueArrastrado = true;
                if (toqueArrastrado && seccion == 1 && paginaEstado == 1 &&
                        areaHabilidades.contains(toqueX, toqueY)) {
                    scrollHabilidades = Math.max(0f, Math.min(maxScrollHabilidades,
                        scrollHabilidades + ultimoToqueY - y));
                    invalidate();
                }
                if (toqueArrastrado && seccion == 0 &&
                        areaDiario.contains(toqueX, toqueY)) {
                    int pagina = paginaDiario;
                    scrollDiario[pagina] = Math.max(0f,
                        Math.min(maxScrollDiario[pagina],
                            scrollDiario[pagina] +
                            ultimoToqueY - y));
                    invalidate();
                }
                if (toqueArrastrado && seccion == 2 &&
                        areaObjetos.contains(toqueX, toqueY)) {
                    scrollObjetos = Math.max(0f,
                        Math.min(maxScrollObjetos,
                            scrollObjetos + ultimoToqueY - y));
                    invalidate();
                }
                ultimoToqueY = y;
                return true;
            }
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;

            int flecha = flechaPulsada;
            flechaPulsada = 0;
            if (Math.abs(x - toqueX) > 14f || Math.abs(y - toqueY) > 14f)
                toqueArrastrado = true;
            if (toqueArrastrado) return true;

            // Tocar la pestaña superior alterna MISIONES / TERMINADO.
            if (seccion == 0 &&
                    toqueX >= 210f && toqueX <= 650f &&
                    toqueY >= 45f && toqueY <= 210f &&
                    x >= 210f && x <= 650f &&
                    y >= 45f && y <= 210f) {
                paginaDiario = 1 - paginaDiario;
                performClick();
                invalidate();
                return true;
            }

            if (seccion == 3) {
                if (volverDesdeMapa(toqueX, toqueY) && volverDesdeMapa(x, y)) {
                    seccion = 1;
                    paginaEstado = 0;
                    performClick();
                    invalidate();
                }
                return true;
            }
            if (flecha != 0 && flecha == direccionFlecha(x, y)) {
                if (seccion == 0) {
                    paginaDiario = Math.max(0,
                        Math.min(1, paginaDiario + flecha));
                    invalidate();
                } else {
                    cambiarPersonaje(flecha);
                }
                performClick();
                return true;
            }
            // Abrir MAPA desde ESTADO.
            if (seccion != 3 &&
                    radarDisponible &&
                    x >= 750f && x <= 990f &&
                    y >= 40f && y <= 155f) {
                seccion = 3;
                performClick();
                invalidate();
                return true;
            }

            if (seccion == 1 && x >= 124f && x <= 208f) {
                if (paginaEstado == 1 && y >= 292f && y <= 383f) {
                    paginaEstado = 0;
                    performClick();
                    invalidate();
                    return true;
                }
                if (paginaEstado == 0 && modelo.count() > 0 && y >= 677f && y <= 768f) {
                    paginaEstado = 1;
                    performClick();
                    invalidate();
                    return true;
                }
            }
            if (seccion == 1 && paginaEstado == 1 &&
                    areaHabilidades.contains(x, y) && modelo.selectedId() >= 0) {
                for (int n = 0; n < habilidades.length; ++n) {
                    if (habilidades[n].limite.contains(toqueX, toqueY) &&
                            habilidades[n].limite.contains(x, y)) {
                        // El ataque físico y las técnicas bloqueadas solo se
                        // consultan. El motor confirma la selección energética.
                        if (n > 0 && habilidades[n].disponible == 1 &&
                                puedeSeleccionarHabilidad &&
                                habilidadActivaPersonaje == modelo.selectedId() &&
                                requestSelectSkill(modelo.selectedId(), n,
                                    habilidadesGeneracion)) {
                            performClick();
                            invalidate();
                        }
                        return true;
                    }
                }
            }

            // OBJETOS: primer toque selecciona; arrastrar nunca activa.
            if (seccion == 2 && objetosDisponibles &&
                    !toqueArrastrado &&
                    toqueX >= areaObjetos.left && toqueX < 670f &&
                    x >= areaObjetos.left && x < 670f &&
                    areaObjetos.contains(toqueX, toqueY) &&
                    areaObjetos.contains(x, y) &&
                    toqueY >= 250f && y >= 250f) {
                int indice = (int) ((y - 250f +
                    scrollObjetos) / ALTO_FILA_OBJETO);
                if (indice >= 0 && indice < objetos.length) {
                    if (objetoSeleccionado == objetos[indice].id) {
                        // Segundo toque: peticion al hilo GBA.
                        // Por ahora solo diagnostico, sin ejecutar el objeto.
                        requestUseItem(objetos[indice].id);
                    } else {
                        // Primer toque: seleccion y descripcion.
                        objetoSeleccionado = objetos[indice].id;
                    }
                    performClick();
                    invalidate();
                    return true;
                }
            }

            // Cambio de sección según el orden visible.
            int destinoInicial = seccionBotonEn(toqueX, toqueY);
            int destinoFinal = seccionBotonEn(x, y);

            if (destinoInicial >= 0 &&
                    destinoInicial == destinoFinal) {
                if (destinoFinal != seccion) {
                    if (destinoFinal == 0)
                        paginaDiario = 0;
                    seccion = destinoFinal;
                }

                performClick();
                invalidate();
                return true;
            }

            return true;
        }

        private int direccionFlecha(float x, float y) {
            if (y < 35f || y > 140f) return 0;

            if (seccion == 0) {
                if (paginaDiario == 1 &&
                        x >= 30f && x <= 140f)
                    return -1;
                if (paginaDiario == 0 &&
                        x >= 1100f && x <= 1210f)
                    return 1;
                return 0;
            }

            if (seccion != 1 || modelo.count() < 2)
                return 0;

            if (x >= 30f && x <= 140f) return -1;
            if (x >= 1100f && x <= 1210f) return 1;
            return 0;
        }

        @Override
        protected void onDetachedFromWindow() {
            sonidoPestanas.release();
            super.onDetachedFromWindow();
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
