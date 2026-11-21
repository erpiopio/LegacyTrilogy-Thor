package org.novapowers.buusfury.thor;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import org.gbarecomp.GbaNative;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Touch-only second-screen panel; no guest-memory access from the UI thread. */
final class ThorMenuPresentation extends Presentation {
    private final ThorGameActivity host;

    ThorMenuPresentation(Context context, Display display, ThorGameActivity host) {
        super(context, display);
        this.host = host;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(createContent(getContext(), host));
        Window window = getWindow();
        if (window != null) {
            // A focusable dialog would pause SDL when the lower screen is touched.
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.setLayout(-1, -1);
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private static final class ControlButton extends Button {
        private final ThorGameActivity host;
        private final int key;

        ControlButton(Context context, ThorGameActivity host, int key) {
            super(context);
            this.host = host;
            this.key = key;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            // Accessibility clicks arrive without a held physical touch.
            // The touch path calls this before clearing its pressed state.
            if (!isPressed()) host.pulseKey(key);
            return true;
        }
    }



    private static final class EstadoArtView extends View {
        // Umbrales reales de experiencia, niveles 50 a 200.
        private static final int[] XP_UMBRALES = {
            0, 10, 40, 100, 200, 350, 560, 840,
            1200, 1650, 2200, 2875, 3700, 4700, 5900, 7325,
            9000, 10950, 13200, 15775, 18700, 22025, 25800, 30075,
            34900, 40325, 46400, 53175, 60700, 69025, 78200, 88300,
            99400, 111575, 124900, 139450, 155300, 172525, 191200, 211400,
            233200, 256700, 282000, 309200, 338400, 369700, 403200, 439000,
            477200, 517900, 561200, 607225, 656100, 707950, 762900, 821075,
            882600, 947600, 1016200, 1088525, 1164700, 1244875, 1329200, 1417825,
            1510900, 1608575, 1711000, 1818352, 1930727, 2048302, 2171227, 2299677,
            2433827, 2573852, 2719927, 2872227, 3030927, 3196202, 3368227, 3547177,
            3733227, 3926577, 4127427, 4335977, 4552427, 4776977, 5009827, 5251177,
            5501227, 5760177, 6028227, 6305627, 6592627, 6889477, 7196427, 7513727,
            7841627, 8180377, 8530227, 8891427, 9264227, 9648927, 10045827, 10455227,
            10877427, 11312727, 11761427, 12223827, 12700227, 13190927, 13696227, 14216477,
            14752027, 15303227, 15870427, 16453977, 17054227, 17671527, 18306227, 18958677,
            19629227, 20318277, 21026227, 21753477, 22500427, 23267477, 24055027, 24863477,
            25693227, 26544677, 27418227, 28314377, 29233627, 30176477, 31143427, 32134977,
            33151627, 34193877, 35262227, 36357177, 37479227, 38629377, 39808627, 41017977,
            42258427, 43530977, 44836627, 46176377, 47551227, 48962177, 50410227
        };

        private final Bitmap plantilla;
        private final ThorUiStrings ui;
        private final ThorContentText content;
        private final ThorLabelPainter labels;
        private final ThorMenuLabels menuLabels;
        private final Bitmap[] iconosTecnicas = new Bitmap[24];
        private final String[] nombresTecnicas = new String[24];
        private final boolean[] tecnicasConNivel = new boolean[24];

        private final Bitmap[] retratos = new Bitmap[9];
        private final Bitmap[] carteles = new Bitmap[9];
        private final Paint pixel = new Paint();
        private final Paint texto = new Paint();
        private final Paint fondo = new Paint();
        private int[] stats;
        private boolean probeLogged = false;
        private String ultimaFirmaHabilidades = "";
        private String ultimaFirmaInventario = "";
        private Bitmap plantillaMapa;
        private Bitmap mapaRegional;

        // THOR_SCOUTER_BG2_LIVE
        private Bitmap mapaRegionalCapturado;
        private android.graphics.Rect mapaRecorteCapturado;
        private long firmaScouter = Long.MIN_VALUE;

        private int seccion = 0;
        private android.media.SoundPool sonidoCambioMenu;
        private int idSonidoMenu;
        private boolean sonidoMenuListo = false;
        private int seccionInicial = -1;
        private boolean sonidoMenuAnticipado = false;
        private int diarioInicial;
        private int inventarioInicial;
        private int equipoInicial;


        // Inventario táctil: cinco columnas, con scroll vertical.
        private static final int INV_COLS = 5;
        private static final float INV_OBJ_ANCHO = 174f;
        private static final float INV_OBJ_ALTO = 116f;
        private static final float INV_X = 155f;
        private static final float INV_Y = 335f;
        private static final float INV_ANCHO = 140f;
        private static final float INV_ALTO = 96f;
        private static final float INV_SEP_X = 15f;
        private static final float INV_SEP_Y = 12f;
        private static final float INV_FINAL = 875f;

        private int inventarioPestana = 0;
        private int inventarioSeleccionado = -1;
        private float desplazamientoInventario = 0f;
        private int inventarioTocado = -1;
        private float inicioInventarioX;
        private float inicioInventarioY;
        private float ultimoInventarioY;
        private boolean tocandoInventario = false;
        private boolean arrastrandoInventario = false;

        // Se rellenarán con los datos originales del juego.
        private final String[][] inventarioNombres = {
            new String[0], new String[0]
        };
        private final String[][] inventarioDescripciones = {
            new String[0], new String[0]
        };
        private final int[][] inventarioCantidades = {
            new int[0], new int[0]
        };
        private final Bitmap[][] inventarioIconos = {
            new Bitmap[0], new Bitmap[0]
        };


        // Catálogo gráfico de inventario (no equivale a objetos poseídos).
        // Se mantiene separado de Equipo; las cantidades reales están pendientes de RAM.
        private void cargarCatalogoInventario(android.content.Context context) {
            try (java.io.InputStream entrada = context.getAssets().open("buusfury/" + "thor_inventory_catalog.json")) {
                java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1)
                    salida.write(buffer, 0, leidos);
                org.json.JSONObject catalogo = new org.json.JSONObject(
                    new String(salida.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                org.json.JSONArray pestanas = catalogo.getJSONArray("pestanas");
                if (pestanas.length() != 2)
                    throw new IllegalStateException("Se esperaban dos pestañas de inventario");
                for (int pestana = 0; pestana < 2; pestana++) {
                    org.json.JSONArray objetos = pestanas.getJSONArray(pestana);
                    int n = objetos.length();
                    inventarioNombres[pestana] = new String[n];
                    inventarioDescripciones[pestana] = new String[n];
                    inventarioCantidades[pestana] = new int[n];
                    inventarioIconos[pestana] = new Bitmap[n];
                    for (int i = 0; i < n; i++) {
                        org.json.JSONObject objeto = objetos.getJSONObject(i);
                        int id = objeto.getInt("id");
                        inventarioNombres[pestana][i] = content.localize(
                                "inventoryName", id, objeto.getString("nombre"));
                        inventarioDescripciones[pestana][i] = content.localize(
                                "inventoryDescription", id, objeto.optString("descripcion", ""));
                        inventarioCantidades[pestana][i] = objeto.optInt("cantidad", 0);
                        android.graphics.BitmapFactory.Options opciones =
                            new android.graphics.BitmapFactory.Options();
                        opciones.inScaled = false; // pixel art 16x16 sin suavizado
                        try (java.io.InputStream grafico = ThorInventoryExtractor.open(
                                context, ThorRomIdentity.activeSha1(),
                                objeto.getString("icono"))) {
                            Bitmap bitmap = BitmapFactory.decodeStream(grafico, null, opciones);
                            if (bitmap == null)
                                throw new IllegalStateException("PNG no válido: " + objeto.getString("icono"));
                            inventarioIconos[pestana][i] = bitmap;
                        }
                    }
                }
                desplazamientoInventario = 0f;
                inventarioSeleccionado = -1;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo cargar catálogo visual de Inventario", e);
            }
        }


        // THOR-EQUIPO-BEGIN: sección independiente del inventario.
        // Catálogo gráfico, NO es lectura de los objetos equipados de la RAM.
        // THOR-EQUIPO-HORIZONTAL-V1: elementos realmente poseídos.
        private final String[] nombresEquipo = new String[162];
        private final String[] descripcionesEquipo = new String[162];
        private final TextPaint textoDescripcionEquipo = new TextPaint();
        private static final int EQUIPO_REGISTROS = 250;
        private static final int EQUIPO_BASE = 457;
        private static final int EQUIPO_ATRIBUTOS = EQUIPO_BASE + 4 * EQUIPO_REGISTROS;
        private static final int EQUIPO_EPOCA = EQUIPO_ATRIBUTOS + 4;
        private static final int EQUIPO_RANURAS = EQUIPO_EPOCA + 1;
        private static final int EQUIPO_RESPUESTA = EQUIPO_RANURAS + 4;
        private static final int EQUIPO_SNAPSHOT = EQUIPO_RESPUESTA + 2;
        private static final float EQUIPO_LISTA_Y0 = 646f;
        private static final float EQUIPO_LISTA_Y1 = 925f;
        private static final float EQUIPO_PASO = 280f;
        private int categoriaEquipo = 0;
        private int seleccionadoRegistroEquipo = -1;
        private int seleccionadoIdEquipo = -1;
        private int idParaDesequipar = -1;
        private int solicitudEquipo = 0;
        private String avisoEquipo = "";
        private int registroEquipoTocado = -1;
        private int idEquipoTocado = -1;
        private int propietarioEquipoTocado = -1;
        private int personajeEquipoTocado = -1;
        private int epocaEquipoTocada;
        private float desplazamientoEquipoX = 0f;
        private float inicioEquipoX = 0f;
        private float inicioEquipoY = 0f;
        private float ultimoEquipoX = 0f;
        private boolean tocandoListaEquipo = false;
        private boolean arrastrandoListaEquipo = false;

        private final Bitmap[] equipoIconos = new Bitmap[162];
        private boolean equipoAbierto = false;
        private int equipoSeleccionado = -1;
        private float equipoDesplazamiento = 0f;
        private float equipoUltimoY;
        private boolean equipoArrastrando = false;
        private boolean equipoPulsandoBoton = false;

        private void cargarCatalogoEquipo(android.content.Context context) {
            for (int i = 0; i < equipoIconos.length; i++) {
                String nombre = String.format(java.util.Locale.US, "thor_equipment_%03d.png", i);
                try (java.io.InputStream entrada =
                        ThorEquipmentExtractor.applies(ThorRomIdentity.activeSha1())
                        ? new java.io.FileInputStream(
                            ThorEquipmentExtractor.icon(
                                context, ThorRomIdentity.activeSha1(), nombre))
                        : context.getAssets().open("buusfury/" + nombre)) {
                    android.graphics.BitmapFactory.Options opciones =
                        new android.graphics.BitmapFactory.Options();
                    opciones.inScaled = false;
                    Bitmap bitmap = BitmapFactory.decodeStream(entrada, null, opciones);
                    if (bitmap == null || bitmap.getWidth() != 16 || bitmap.getHeight() != 16)
                        throw new IllegalStateException("Icono de equipo incorrecto: " + nombre);
                    equipoIconos[i] = bitmap;
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Error al cargar " + nombre, e);
                }
            }
        }

        private void cargarTextosEquipo(Context context, String archivo, String[] destino) {
            try (java.io.InputStream entrada = context.getAssets().open("buusfury/" + archivo)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = entrada.read(buffer)) != -1)
                    bytes.write(buffer, 0, n);
                org.json.JSONArray lista = new org.json.JSONArray(
                    new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                if (lista.length() != destino.length)
                    throw new IllegalStateException("El catálogo de EQUIPO no tiene 162 entradas");
                for (int i = 0; i < destino.length; i++)
                    destino[i] = content.localize(destino == nombresEquipo
                            ? "equipmentName" : "equipmentDescription", i, lista.getString(i));
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo cargar " + archivo, e);
            }
        }

        private int cantidadEquipoRAM() {
            if (stats == null || stats.length < EQUIPO_SNAPSHOT) return 0;
            return Math.min(EQUIPO_REGISTROS, Math.max(0, stats[456] & 0xFF));
        }

        private int idEnRegistroEquipo(int registro) {
            return stats[EQUIPO_BASE + registro * 4] & 0xFF;
        }

        private int propietarioRegistroEquipo(int registro) {
            // Formato guardado en GBA: ID 00 00 X0; X=1 corresponde a Goku.
            return (stats[EQUIPO_BASE + registro * 4 + 3] >>> 4) & 0x0F;
        }

        private int categoriaDeIdEquipo(int id) {
            if (id > 0 && id < 44) return 0;
            if (id > 44 && id < 77) return 1;
            if (id > 77 && id < 112) return 2;
            if (id > 112 && id < 162) return 3;
            return -1;
        }

        private int[] registrosEquipoDisponibles(int categoria) {
            int cantidad = cantidadEquipoRAM();
            int[] temporal = new int[cantidad];
            int usados = 0;
            for (int r = 0; r < cantidad; ++r) {
                if (categoriaDeIdEquipo(idEnRegistroEquipo(r)) == categoria)
                    temporal[usados++] = r;
            }
            return java.util.Arrays.copyOf(temporal, usados);
        }

        private int idEquipado(int categoria) {
            if (stats == null || stats.length < EQUIPO_SNAPSHOT) return -1;
            int id = stats[EQUIPO_RANURAS + categoria];
            return categoriaDeIdEquipo(id) == categoria ? id : -1;
        }

        private String nombreEquipo(int id) {
            if (id > 0 && id < nombresEquipo.length
                    && nombresEquipo[id] != null && !nombresEquipo[id].isEmpty())
                return nombresEquipo[id];
            return ui.format("itemFallback", id);
        }

        private String textoEquipoAjustado(String valor, float anchoMaximo, float tamano,
                                          float tamanoMinimo) {
            texto.setTextSize(tamano);
            while (texto.measureText(valor) > anchoMaximo && texto.getTextSize() > tamanoMinimo)
                texto.setTextSize(Math.max(tamanoMinimo, texto.getTextSize() - 2f));
            while (valor.length() > 2 && texto.measureText(valor) > anchoMaximo)
                valor = valor.substring(0, valor.length() - 2) + "…";
            return valor;
        }

        private float maxDesplazamientoListaEquipo() {
            int n = registrosEquipoDisponibles(categoriaEquipo).length;
            return Math.max(0f, 90f + (n - 1) * EQUIPO_PASO + 260f - 1176f);
        }

        private int registroEnListaEquipo(float x, float y) {
            if (x < 64f || x > 1176f || y < EQUIPO_LISTA_Y0 || y >= EQUIPO_LISTA_Y1)
                return -1;
            int[] registros = registrosEquipoDisponibles(categoriaEquipo);
            float local = x - (registros.length == 1 ? 490f : 90f - desplazamientoEquipoX);
            if (local < 0f || local % EQUIPO_PASO >= 260f) return -1;
            int indice = (int) (local / EQUIPO_PASO);
            return indice < registros.length ? registros[indice] : -1;
        }

        private void solicitarCambioEquipo(int registro, int id, int propietario) {
            if (stats == null || stats.length < EQUIPO_SNAPSHOT || solicitudEquipo != 0) return;
            try {
                solicitudEquipo = GbaNative.requestEquipmentChange(stats[72], categoriaEquipo,
                        registro, id, propietario, stats[EQUIPO_EPOCA]);
                avisoEquipo = solicitudEquipo != 0 ? "" : ui.text("retryEquipment");
            } catch (UnsatisfiedLinkError error) {
                avisoEquipo = ui.text("equipmentUnavailable");
            }
        }

        private void dibujarDetalleEquipo(Canvas canvas) {
            String descripcion = seleccionadoIdEquipo >= 0
                    ? descripcionesEquipo[seleccionadoIdEquipo] : "";
            if (descripcion.isEmpty() && avisoEquipo.isEmpty()) return;
            textoDescripcionEquipo.setTypeface(texto.getTypeface());
            textoDescripcionEquipo.setColor(Color.rgb(231, 238, 216));
            textoDescripcionEquipo.setTextSize(27f);
            textoDescripcionEquipo.setAntiAlias(false);
            textoDescripcionEquipo.setSubpixelText(false);
            textoDescripcionEquipo.clearShadowLayer();
            StaticLayout detalle = StaticLayout.Builder
                    .obtain(descripcion, 0, descripcion.length(), textoDescripcionEquipo, 1072)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
            canvas.save();
            canvas.translate(87f, 937f);
            if (!descripcion.isEmpty()) {
                textoDescripcionEquipo.setColor(Color.rgb(9, 14, 10));
                detalle.draw(canvas);
                canvas.translate(-3f, -4f);
                textoDescripcionEquipo.setColor(Color.rgb(231, 238, 216));
                detalle.draw(canvas);
            }
            canvas.restore();
            if (!avisoEquipo.isEmpty()) {
                labels.draw(canvas, avisoEquipo, 620f, 1004f, 22f, 1072f,
                        Paint.Align.CENTER, Color.rgb(249, 219, 132), false);
            }
        }

        private boolean enBotonDesequipar(float x, float y) {
            return idParaDesequipar >= 0 && x >= 904f && x <= 1176f
                    && y >= 577f && y <= 631f;
        }

        private void dibujarBotonDesequipar(Canvas canvas) {
            if (idParaDesequipar < 0) return;
            fondo.setColor(Color.rgb(64, 87, 66));
            canvas.drawRoundRect(904f, 577f, 1176f, 631f, 8f, 8f, fondo);
            labels.draw(canvas, ui.text("unequip"), 1040f, 614f, 45f, 258f,
                    Paint.Align.CENTER, ThorLabelPainter.VALUE, true);
        }

        private void dibujarListaEquipoHorizontal(Canvas canvas) {
            int[] registros = registrosEquipoDisponibles(categoriaEquipo);
            canvas.save();
            canvas.clipRect(64f, EQUIPO_LISTA_Y0, 1176f, EQUIPO_LISTA_Y1);
            Paint.Align alignAnterior = texto.getTextAlign();
            int colorAnterior = texto.getColor();
            float tamanoAnterior = texto.getTextSize();
            boolean negritaAnterior = texto.isFakeBoldText();
            texto.setAntiAlias(false);
            texto.setFakeBoldText(true);
            texto.setTextAlign(Paint.Align.CENTER);
            if (registros.length == 0) {
                labels.draw(canvas, ui.text("noEquipment"), 620f, 793f, 55f, 1060f,
                        Paint.Align.CENTER, Color.rgb(228, 237, 211), true);
            } else {
                for (int i = 0; i < registros.length; ++i) {
                    int r = registros[i];
                    int id = idEnRegistroEquipo(r);
                    boolean unico = registros.length == 1;
                    float x = unico ? 490f : 90f + i * EQUIPO_PASO - desplazamientoEquipoX;
                    if (x + 260f < 64f || x > 1176f) continue;
                    float tam = unico ? 196f : 174f;
                    float iconX = x + (260f - tam) / 2f;
                    float iconY = unico ? 650f : 661f;
                    Bitmap icono = equipoIconos[id];
                    if (icono != null) {
                        canvas.drawBitmap(icono, null,
                            new android.graphics.RectF(
                                iconX, iconY, iconX + tam, iconY + tam), pixel);
                    }
                    String nombre = textoEquipoAjustado(
                        nombreEquipo(id), 256f, unico ? 43f : 39f, 27f);
                    texto.setColor(Color.rgb(8, 18, 10));
                    canvas.drawText(nombre, x + 133f, 880f, texto);
                    texto.setColor(r == seleccionadoRegistroEquipo
                        ? Color.rgb(249, 219, 132) : Color.rgb(231, 238, 216));
                    canvas.drawText(nombre, x + 130f, 876f, texto);
                    int propietario = propietarioRegistroEquipo(r);
                    if (propietario > 0) {
                        texto.setTextSize(24f);
                        texto.setColor(Color.rgb(225, 211, 157));
                        String etiqueta = (stats != null && stats.length > 72
                            && propietario == stats[72] + 1)
                                ? ui.text("equipped") : ui.text("otherCharacter");
                        labels.draw(canvas, etiqueta, x + 130f, 909f, 24f, 256f,
                                Paint.Align.CENTER, Color.rgb(225, 211, 157), false);
                    }
                }
            }
            texto.setTextAlign(alignAnterior);
            texto.setTextSize(tamanoAnterior);
            texto.setColor(colorAnterior);
            texto.setFakeBoldText(negritaAnterior);
            canvas.restore();
        }

        private float maxDesplazamientoEquipo() {
            int filas = (equipoIconos.length + 5) / 6;
            return Math.max(0f, (filas - 5) * (INV_ALTO + INV_SEP_Y));
        }

        private void dibujarBotonEquipo(Canvas canvas) {
            fondo.setStyle(Paint.Style.FILL);
            fondo.setColor(equipoAbierto ? Color.rgb(57, 95, 82) : Color.rgb(98, 62, 108));
            canvas.drawRoundRect(898f, 963f, 1082f, 1007f, 7f, 7f, fondo);
            fondo.setStyle(Paint.Style.STROKE);
            fondo.setStrokeWidth(2f);
            fondo.setColor(Color.rgb(217, 226, 205));
            canvas.drawRoundRect(899f, 964f, 1081f, 1006f, 7f, 7f, fondo);
            fondo.setStyle(Paint.Style.FILL);
            texto.setAntiAlias(false);
            texto.setColor(Color.WHITE);
            texto.setFakeBoldText(true);
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setTextSize(26f);
            canvas.drawText(ui.text(equipoAbierto ? "back" : "equipment"), 990f, 994f, texto);
            texto.setFakeBoldText(false);
            texto.setTextAlign(Paint.Align.LEFT);
        }

        private void dibujarRanurasVaciasEquipo(Canvas canvas) {
            final int[] vacios = {0, 44, 77, 112};
            final String[] categorias = {ui.text("chest"), ui.text("hands"),
                    ui.text("feet"), ui.text("accessories")};
            pixel.setFilterBitmap(false);
            pixel.setAntiAlias(false);
            Paint.Align alignAnterior = texto.getTextAlign();
            int colorAnterior = texto.getColor();
            float tamanoAnterior = texto.getTextSize();
            boolean negritaAnterior = texto.isFakeBoldText();
            texto.setTextAlign(Paint.Align.LEFT);
            texto.setFakeBoldText(true);
            texto.setAntiAlias(false);
            for (int i = 0; i < 4; ++i) {
                float y = 205f + i * 102f;
                int equipado = idEquipado(i);
                int idIcono = equipado >= 0 ? equipado : vacios[i];
                Bitmap icono = equipoIconos[idIcono];
                if (icono != null)
                    canvas.drawBitmap(icono, null,
                        new android.graphics.RectF(220f, y, 316f, y + 96f), pixel);
                String nombre = equipado >= 0 ? nombreEquipo(equipado) : categorias[i];
                labels.draw(canvas, nombre, 354f, y + 69f, 60f, 400f,
                        Paint.Align.LEFT, i == categoriaEquipo
                            ? Color.rgb(244, 227, 156) : ThorLabelPainter.PALE, true);
            }
            texto.setTextAlign(alignAnterior);
            texto.setTextSize(tamanoAnterior);
            texto.setColor(colorAnterior);
            texto.setFakeBoldText(negritaAnterior);
        }

        private void dibujarEquipo(Canvas canvas) {
            fondo.setStyle(Paint.Style.FILL);
            fondo.setAntiAlias(false);
            fondo.setColor(Color.rgb(67, 92, 76));
            canvas.drawRect(145f, 185f, 1095f, 1015f, fondo);
            texto.setAntiAlias(false);
            texto.setTextAlign(Paint.Align.LEFT);
            texto.setFakeBoldText(true);
            texto.setTextSize(55f);
            texto.setColor(Color.rgb(240, 235, 202));
            canvas.drawText(ui.text("equipment"), 162f, 265f, texto);
            texto.setFakeBoldText(false);
            texto.setTextSize(26f);
            texto.setColor(Color.rgb(212, 222, 201));
            canvas.drawText(ui.text("equipmentCatalog"), 465f, 265f, texto);
            canvas.save();
            canvas.clipRect(145f, 327f, 1093f, 875f);
            pixel.setFilterBitmap(false);
            pixel.setAntiAlias(false);
            for (int i = 0; i < equipoIconos.length; i++) {
                int col = i % 6;
                int fila = i / 6;
                float x = INV_X + col * (INV_ANCHO + INV_SEP_X);
                float y = INV_Y + fila * (INV_ALTO + INV_SEP_Y) - equipoDesplazamiento;
                if (y + INV_ALTO < 327f || y > 875f) continue;
                fondo.setStyle(Paint.Style.FILL);
                fondo.setColor(i == equipoSeleccionado
                    ? Color.rgb(126, 103, 57) : Color.rgb(39, 60, 48));
                canvas.drawRoundRect(x, y, x + INV_ANCHO, y + INV_ALTO, 6f, 6f, fondo);
                fondo.setStyle(Paint.Style.STROKE);
                fondo.setStrokeWidth(i == equipoSeleccionado ? 4f : 2f);
                fondo.setColor(i == equipoSeleccionado ? Color.WHITE : Color.rgb(121, 151, 121));
                canvas.drawRoundRect(x + 1f, y + 1f, x + INV_ANCHO - 1f, y + INV_ALTO - 1f, 6f, 6f, fondo);
                fondo.setStyle(Paint.Style.FILL);
                Bitmap icono = equipoIconos[i];
                if (icono != null)
                    canvas.drawBitmap(icono, null,
                        new android.graphics.RectF(x + 32f, y + 8f, x + 108f, y + 84f), pixel);
                texto.setTextSize(20f);
                texto.setTextAlign(Paint.Align.RIGHT);
                texto.setColor(Color.rgb(220, 225, 201));
                canvas.drawText(String.valueOf(i), x + 132f, y + 25f, texto);
            }
            canvas.restore();
            fondo.setStyle(Paint.Style.FILL);
            fondo.setColor(Color.rgb(37, 60, 48));
            canvas.drawRoundRect(155f, 900f, 880f, 1007f, 8f, 8f, fondo);
            texto.setTextAlign(Paint.Align.LEFT);
            texto.setTextSize(33f);
            texto.setFakeBoldText(true);
            texto.setColor(Color.rgb(244, 224, 175));
            canvas.drawText(equipoSeleccionado >= 0
                ? ui.format("equipmentNumber", equipoSeleccionado)
                : ui.text("selectItem"), 170f, 947f, texto);
            texto.setFakeBoldText(false);
            texto.setTextSize(23f);
            texto.setColor(Color.rgb(213, 226, 205));
            canvas.drawText(ui.text("equipmentCatalogPending"), 170f, 982f, texto);
            dibujarBotonEquipo(canvas);
        }

        // Se ejecuta al principio del manejador táctil; deja libres las otras secciones.
        private boolean manejarToqueEquipo(MotionEvent evento) {
            if (seccion != 2) {
                equipoAbierto = false;
                return false;
            }
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return false;
            float x = evento.getX() * 1200f / w;
            float y = evento.getY() * 1080f / h;
            int accion = evento.getActionMasked();
            boolean boton = x >= 898f && x <= 1082f && y >= 963f && y <= 1007f;
            if (accion == MotionEvent.ACTION_DOWN && boton) {
                equipoPulsandoBoton = true;
                return true;
            }
            if (equipoPulsandoBoton) {
                if (accion == MotionEvent.ACTION_UP) {
                    if (boton) {
                        equipoAbierto = !equipoAbierto;
                        equipoSeleccionado = -1;
                        equipoDesplazamiento = 0f;
                        invalidate();
                    }
                    equipoPulsandoBoton = false;
                } else if (accion == MotionEvent.ACTION_CANCEL) {
                    equipoPulsandoBoton = false;
                }
                return true;
            }
            if (!equipoAbierto) return false;
            if (y < 185f || x < 145f || x > 1095f || y > 1015f) return false;
            if (accion == MotionEvent.ACTION_DOWN) {
                equipoUltimoY = y;
                equipoArrastrando = false;
                return true;
            }
            if (accion == MotionEvent.ACTION_MOVE) {
                float delta = y - equipoUltimoY;
                if (Math.abs(delta) > 2f) equipoArrastrando = true;
                equipoDesplazamiento = Math.max(0f,
                    Math.min(maxDesplazamientoEquipo(), equipoDesplazamiento - delta));
                equipoUltimoY = y;
                invalidate();
                return true;
            }
            if (accion == MotionEvent.ACTION_UP) {
                if (!equipoArrastrando && y >= INV_Y && y < 875f) {
                    int columna = (int)((x - INV_X) / (INV_ANCHO + INV_SEP_X));
                    int fila = (int)((y - INV_Y + equipoDesplazamiento) /
                        (INV_ALTO + INV_SEP_Y));
                    if (columna >= 0 && columna < 6 && fila >= 0) {
                        int indice = fila * 6 + columna;
                        float localX = (x - INV_X) % (INV_ANCHO + INV_SEP_X);
                        float localY = (y - INV_Y + equipoDesplazamiento) %
                            (INV_ALTO + INV_SEP_Y);
                        if (indice < equipoIconos.length && localX < INV_ANCHO && localY < INV_ALTO)
                            equipoSeleccionado = indice;
                    }
                }
                invalidate();
                return true;
            }
            return accion == MotionEvent.ACTION_CANCEL;
        }
        // THOR-EQUIPO-END

        private float maxDesplazamientoInventario() {
            int cantidad = inventarioNombres[inventarioPestana].length;
            int total = Math.max(30, cantidad);
            int filas = (total + INV_COLS - 1) / INV_COLS;
            float altoContenido = filas * (INV_OBJ_ALTO + INV_SEP_Y) - INV_SEP_Y;
            return Math.max(0f, altoContenido - (INV_FINAL - INV_Y));
        }
        // Diario: 0 = submenús, 1 = activas, 2 = completadas.
        private int diarioVista = 0;

        // Área de las misiones, recortada dentro del marco.
        private static final float DIARIO_X0 = 150f;
        private static final float DIARIO_X1 = 1090f;
        private static final float DIARIO_Y0 = 390f;
        private static final float DIARIO_Y1 = 940f;
        private static final float DIARIO_FILA = 145f;

        private String[] misionesActivas = new String[0];
        private String[] misionesCompletadas = new String[0];
        private org.json.JSONArray datosMisiones;
        private int[] estrellasActivas = new int[0];
        private int[] estrellasCompletadas = new int[0];


        private float desplazamientoDiario = 0f;
        private float inicioDiarioY;
        private float ultimoDiarioY;
        private boolean tocandoDiario = false;
        private boolean arrastrandoDiario = false;


        private void cargarMisiones(Context context) {
            try (java.io.InputStream entrada =
                    context.getAssets().open("buusfury/" + "thor_quests.json")) {
                java.io.ByteArrayOutputStream bytes =
                    new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1)
                    bytes.write(buffer, 0, leidos);

                String json = new String(bytes.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8);
                datosMisiones = new org.json.JSONObject(json)
                    .getJSONArray("misiones");

                if (datosMisiones.length() != 40)
                    throw new IllegalStateException(
                        "El Diario debe contener 40 misiones");
            } catch (Exception error) {
                throw new IllegalStateException(
                    "No se pudieron cargar las misiones españolas", error);
            }
        }

        private boolean evaluarMision(Object condicion, int[] valores)
                throws org.json.JSONException {
            if (condicion instanceof Boolean)
                return (Boolean) condicion;

            if (condicion instanceof Number)
                return ((Number) condicion).intValue() != 0;

            if (!(condicion instanceof org.json.JSONObject))
                throw new org.json.JSONException(
                    "Condición de misión desconocida");

            org.json.JSONObject objeto = (org.json.JSONObject) condicion;

            if (objeto.has("flag")) {
                int flag = objeto.getInt("flag");
                if (flag < 0 || flag >= 143 * 8)
                    throw new org.json.JSONException(
                        "Indicador fuera de rango: " + flag);

                int byteIndice = 221 + flag / 8;
                return ((valores[byteIndice] >> (flag % 8)) & 1) != 0;
            }

            if (objeto.has("not"))
                return !evaluarMision(objeto.get("not"), valores);

            if (objeto.has("all")) {
                org.json.JSONArray elementos = objeto.getJSONArray("all");
                for (int i = 0; i < elementos.length(); i++)
                    if (!evaluarMision(elementos.get(i), valores))
                        return false;
                return true;
            }

            if (objeto.has("any")) {
                org.json.JSONArray elementos = objeto.getJSONArray("any");
                for (int i = 0; i < elementos.length(); i++)
                    if (evaluarMision(elementos.get(i), valores))
                        return true;
                return false;
            }

            throw new org.json.JSONException(
                "Operación de misión desconocida");
        }

        private void actualizarMisiones(int[] valores) {
            if (datosMisiones == null || valores == null
                    || valores.length < 364) {
                misionesActivas = new String[0];
                misionesCompletadas = new String[0];
                estrellasActivas = new int[0];
                estrellasCompletadas = new int[0];
                return;
            }

            java.util.ArrayList<String> activas =
                new java.util.ArrayList<>();
            java.util.ArrayList<String> completadas =
                new java.util.ArrayList<>();
            java.util.ArrayList<Integer> oroActivas =
                new java.util.ArrayList<>();
            java.util.ArrayList<Integer> oroCompletadas =
                new java.util.ArrayList<>();

            try {
                for (int i = 0; i < datosMisiones.length(); i++) {
                    org.json.JSONObject m = datosMisiones.getJSONObject(i);

                    if (evaluarMision(m.get("oculta"), valores))
                        continue;

                    boolean terminada =
                        evaluarMision(m.get("completada"), valores);
                    boolean activa =
                        evaluarMision(m.get("activa"), valores);

                    if (!terminada && !activa)
                        continue;

                    String nombre = content.localize("quest", m.getInt("id"), m.getString("texto"));
                    String tipo = m.getString("estrella");
                    int estrella;
                    if ("oro".equals(tipo))
                        estrella = 0;
                    else if ("plata".equals(tipo))
                        estrella = 1;
                    else if ("bronce".equals(tipo))
                        estrella = 2;
                    else
                        throw new org.json.JSONException(
                            "Tipo de estrella desconocido: " + tipo);

                    if (terminada) {
                        completadas.add(nombre);
                        oroCompletadas.add(estrella);
                    } else {
                        activas.add(nombre);
                        oroActivas.add(estrella);
                    }
                }
            } catch (org.json.JSONException error) {
                throw new IllegalStateException(
                    "Error interpretando las misiones", error);
            }

            misionesActivas = activas.toArray(new String[0]);
            misionesCompletadas = completadas.toArray(new String[0]);

            estrellasActivas = new int[oroActivas.size()];
            for (int i = 0; i < oroActivas.size(); i++)
                estrellasActivas[i] = oroActivas.get(i);

            estrellasCompletadas = new int[oroCompletadas.size()];
            for (int i = 0; i < oroCompletadas.size(); i++)
                estrellasCompletadas[i] = oroCompletadas.get(i);

            desplazamientoDiario = Math.min(
                desplazamientoDiario, maxDesplazamientoDiario());
        }

        private float maxDesplazamientoDiario() {
            int cantidad = diarioVista == 1
                ? misionesActivas.length : misionesCompletadas.length;
            return Math.max(0f,
                cantidad * DIARIO_FILA - (DIARIO_Y1 - DIARIO_Y0));
        }



        private static final float HAB_INICIO = 190f;
        private static final float HAB_FILA = 285f;
        private static final float HAB_FINAL = 1000f;

        // Los IDs de habilidades se leen del snapshot nativo.
        // El mapeo a nombres e iconos se realizará después de validarlos.
        private String[] habilidadesActuales() {
            if (stats == null || stats.length < 221)
                return new String[0];

            int personaje = stats[72];
            if (personaje < 0 || personaje >= 9)
                return new String[0];

            int base = 95 + personaje * 14;
            int cantidad = Math.min(6, Math.max(0, stats[base] & 0xFF));

            String[] resultado = new String[cantidad];

            for (int i = 0; i < cantidad; i++) {
                int id = stats[base + 1 + i] & 0xFF;

                if (id > 0 && id < nombresTecnicas.length)
                    resultado[i] = nombresTecnicas[id];
            }

            return resultado;
        }


        private int habilidadSeleccionada = -1;
        private float desplazamientoHabilidades = 0f;
        private float inicioToqueY;
        private float ultimoToqueY;
        private boolean tocandoHabilidades = false;
        private boolean arrastrandoHabilidades = false;

        private float maxDesplazamientoHabilidades() {
            // Seis posiciones caben completas en la pantalla.
            return 0f;
        }

        private void dibujarHabilidades(Canvas canvas) {
            if (stats == null || stats.length < 221)
                return;

            int personaje = stats[72];

            if (personaje < 0 || personaje >= 9)
                return;

            int base = 95 + personaje * 14;
            int cantidad = Math.min(6, Math.max(0, stats[base] & 0xFF));
            int seleccionada = stats[base + 13] & 0xFF;

            canvas.save();
            canvas.clipRect(52f, 175f, 1190f, HAB_FINAL);

            texto.setFakeBoldText(true);
            texto.setAntiAlias(false);

            for (int i = 0; i < cantidad; i++) {
                int id = stats[base + 1 + i] & 0xFF;

                if (id <= 0 || id >= 24 ||
                        iconosTecnicas[id] == null)
                    continue;

                int columna = i % 2;
                int fila = i / 2;

                float izquierda = columna == 0 ? 75f : 635f;
                float superior = HAB_INICIO + fila * HAB_FILA;
                float ancho = 515f;
                float centro = izquierda + ancho / 2f;

                boolean activa = i == seleccionada;

                if (activa) {
                    fondo.setColor(Color.argb(55, 165, 235, 165));
                    canvas.drawRect(
                        izquierda, superior,
                        izquierda + ancho, superior + 240f,
                        fondo);
                }

                // 1. Nombre encima del icono.
                String nombre = nombresTecnicas[id];

                if (nombre == null || nombre.isEmpty())
                    nombre = ui.format("skillFallback", id);

                float yNombre = superior + 66f;
                labels.draw(canvas, nombre, centro, yNombre, 54f, 490f,
                        Paint.Align.CENTER, activa ? Color.rgb(245, 220, 155)
                            : Color.rgb(205, 214, 201), true);

                // 2. Icono original, ampliado sin suavizado.
                canvas.drawBitmap(
                    iconosTecnicas[id],
                    null,
                    new android.graphics.RectF(
                        centro - 84f, superior + 95f,
                        centro + 84f, superior + 183f),
                    pixel);

                // 3. Nivel únicamente para técnicas mejorables.
                if (tecnicasConNivel[id]) {
                    int nivelBruto = stats[base + 7 + i] & 0xFF;

                    // La ROM almacena niveles 0, 1 y 2;
                    // el jugador ve Nivel 1, 2 y 3.
                    if (nivelBruto <= 2) {
                        String nivel = ui.format("levelValue", nivelBruto + 1);
                        float yNivel = superior + 226f;
                        labels.draw(canvas, nivel, centro, yNivel, 38f, 490f,
                                Paint.Align.CENTER, Color.rgb(205, 214, 201), true);
                    }
                }
            }

            canvas.restore();
        }

        private final Bitmap cargaImagen;
        private boolean cargando = true;

        EstadoArtView(Context context) {
            this(context, ThorRomIdentity.activeLanguage(context));
        }

        // Also used by the on-device layout test; the app always uses the identity constructor.
        EstadoArtView(Context context, String languageTag) {
            super(context);
            ui = new ThorUiStrings(languageTag);
            content = new ThorContentText(context, ui.languageTag());
            labels = new ThorLabelPainter(context);
            menuLabels = new ThorMenuLabels(ui, labels);
            try (java.io.InputStream in = context.getAssets().open("buusfury/" + "loading.png")) {
                cargaImagen = BitmapFactory.decodeStream(in);
                if (cargaImagen == null)
                    throw new IllegalStateException("loading.png no válido");
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Error cargando loading.png", e);
            }
            try {
                try (java.io.InputStream mapaInput =
                        context.getAssets().open("buusfury/" + "plantilla_mapa.png")) {
                    plantillaMapa = BitmapFactory.decodeStream(mapaInput);
                    if (plantillaMapa == null)
                        throw new IllegalStateException("Plantilla MAPA inválida");

                    // THOR_MAPA_SIN_BLANCO:
                    // Hacer transparente solo el blanco neutro del
                    // interior, conservando marco verde y botones.
                    Bitmap limpia = plantillaMapa.copy(
                        Bitmap.Config.ARGB_8888, true);

                    for (int y = 0; y < limpia.getHeight(); y++) {
                        for (int x = 0; x < limpia.getWidth(); x++) {
                            int c = limpia.getPixel(x, y);
                            int r = Color.red(c);
                            int g = Color.green(c);
                            int b = Color.blue(c);

                            if (Color.alpha(c) > 0 &&
                                r >= 225 && g >= 225 && b >= 225 &&
                                Math.max(r, Math.max(g, b)) -
                                Math.min(r, Math.min(g, b)) <= 18) {
                                limpia.setPixel(x, y, Color.TRANSPARENT);
                            }
                        }
                    }
                    plantillaMapa = limpia;
                }
                try (java.io.InputStream regionalInput =
                        context.getAssets().open("buusfury/" + "mapa_regional_original.png")) {
                    mapaRegional = BitmapFactory.decodeStream(regionalInput);
                    if (mapaRegional == null)
                        throw new IllegalStateException("Mapa regional inválido");
                }
                try (java.io.InputStream in = context.getAssets().open("buusfury/" + "plantilla.png")) {
                    plantilla = BitmapFactory.decodeStream(in);
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Error cargando los gráficos de Estado", e);
            }
            if (plantilla == null || plantilla.getWidth() != 1240 || plantilla.getHeight() != 1080)
                throw new IllegalStateException("Gráficos de Estado inválidos");
            cargarMisiones(context);

            for (int i = 0; i < 9; i++) {
                try (java.io.InputStream in =
                        context.getAssets().open("buusfury/" + "thor_nameplate_" + i + ".png")) {
                    carteles[i] = BitmapFactory.decodeStream(in);
                } catch (java.io.IOException ignored) {
                }

                try (java.io.InputStream in =
                        ThorPortraitExtractor.open(
                            context, ThorRomIdentity.activeSha1(),
                            "thor_portrait_" + i + ".png")) {
                    retratos[i] = BitmapFactory.decodeStream(in);
                } catch (java.io.IOException ignored) {
                    // Gogeta todavía no tiene retrato identificado.
                }
            }

            pixel.setFilterBitmap(false);
            pixel.setAntiAlias(false);
            texto.setTypeface(Typeface.createFromAsset(context.getAssets(), "buusfury/PixelOperator.ttf"));
            texto.setAntiAlias(false);
            setClickable(true);
            cargarCatalogoInventario(context);
            cargarCatalogoEquipo(context);
            cargarTextosEquipo(context, "thor_equipment_names.json", nombresEquipo);
            cargarTextosEquipo(context, "thor_equipment_descriptions.json", descripcionesEquipo);

            // Catálogo e iconos originales de técnicas extraídos de la ROM.
            try (java.io.InputStream in =
                    context.getAssets().open("buusfury/" + "thor_skills.json")) {

                java.io.ByteArrayOutputStream buffer =
                    new java.io.ByteArrayOutputStream();
                byte[] bloque = new byte[4096];
                int leidos;

                while ((leidos = in.read(bloque)) != -1)
                    buffer.write(bloque, 0, leidos);

                org.json.JSONArray catalogo = new org.json.JSONArray(
                    new String(buffer.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8));

                for (int i = 0; i < catalogo.length(); i++) {
                    org.json.JSONObject tecnica = catalogo.getJSONObject(i);
                    int id = tecnica.getInt("id");

                    if (id < 0 || id >= 24)
                        continue;

                    nombresTecnicas[id] = content.localize("skill", id,
                        tecnica.optString("nombre", ui.format("skillFallback", id)));
                    tecnicasConNivel[id] =
                        tecnica.optBoolean("tieneNivel", false);
                }

                for (int id = 1; id < 24; id++) {
                    try (java.io.InputStream imagen =
                            ThorSkillsExtractor.open(context, "thor_skill_" + id + ".png")) {
                        iconosTecnicas[id] =
                            BitmapFactory.decodeStream(imagen);
                    }

                    if (iconosTecnicas[id] == null)
                        throw new IllegalStateException(
                            "Icono inválido: " + id);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Error cargando las técnicas originales", e);
            }

        }

        private int ultimoPersonajeRegistrado = -1;

        private int[] mapaDatos;
        // THOR_AUTO_MAP_V1: escenarios originales, sin abrir Scouter.
        private ThorAutomaticMap mapaAutomatico;
        private Bitmap mapaScouterOriginal;
        private int revisionScouterOriginal = -1;

        void setOriginalScouterMap(int[] data) {
            if (data == null || data.length < 3) return;
            final int width = data[1], height = data[2];
            if (width == 0 && height == 0) {
                revisionScouterOriginal = data[0];
                if (mapaScouterOriginal != null) mapaScouterOriginal.recycle();
                mapaScouterOriginal = null;
            } else {
                if (width != 240 || height != 160 ||
                        data.length != 3 + width * height) return;
                if (mapaScouterOriginal == null) {
                    mapaScouterOriginal = Bitmap.createBitmap(
                        width, height, Bitmap.Config.ARGB_8888);
                }
                mapaScouterOriginal.setPixels(data, 3, width, 0, 0, width, height);
                revisionScouterOriginal = data[0];
            }
            if (seccion == 5) invalidate();
        }



        // THOR_SCOUTER_BG2_LIVE: reconstruir el fondo original de GBA.
        // No escribe VRAM ni altera el juego superior.
        void setScouterGraphics(byte[] datos) {
            if (datos == null || datos.length != 100352) return;

            long firma = 0xcbf29ce484222325L;
            // Tiles + tablas de fondos y paleta.
            for (int i = 0; i < 65536; i++) {
                firma ^= datos[i] & 255;
                firma *= 0x100000001b3L;
            }
            // THOR_BG2_MAP_SIGNATURE: incluir la tabla original
            // de BG2. Distintos mapas pueden compartir los mismos tiles.
            for (int i = 0xF000; i < 0xF800; i++) {
                firma ^= datos[i] & 255;
                firma *= 0x100000001b3L;
            }
            for (int i = 98304; i < 99328; i++) {
                firma ^= datos[i] & 255;
                firma *= 0x100000001b3L;
            }
            // THOR_SCOUTER_OAM_JAVA: localizar el sprite original 1020.
            // Solo diagnóstico: todavía no sabemos si representa a Goku.
            for (int i = 0; i < 128; i++) {
                int off = 99328 + i * 8;
                int a0 = (datos[off] & 255)
                    | ((datos[off + 1] & 255) << 8);
                int a1 = (datos[off + 2] & 255)
                    | ((datos[off + 3] & 255) << 8);
                int a2 = (datos[off + 4] & 255)
                    | ((datos[off + 5] & 255) << 8);

                if ((a2 & 1023) == 1020
                        && (a0 & 0x0300) != 0x0200) {
                    int x = a1 & 511;
                    int y = a0 & 255;
                    android.util.Log.d("THOR_SCOUTER_OAM",
                        "Sprite1020 X=" + x + " Y=" + y);
                    break;
                }
            }

            if (firma == firmaScouter && mapaRegionalCapturado != null)
                return;

            int[] colores = new int[256 * 256];
            int minX = 256, minY = 256, maxX = -1, maxY = -1;

            // BG2: pantalla de texto 32x32, 8bpp, charbase 0,
            // screenbase 0xF000. Configuración medida en el Scouter.
            for (int ty = 0; ty < 32; ty++) {
                for (int tx = 0; tx < 32; tx++) {
                    int entrada = 0xF000 + (ty * 32 + tx) * 2;
                    int valor = (datos[entrada] & 255)
                        | ((datos[entrada + 1] & 255) << 8);
                    int tile = valor & 1023;
                    boolean flipX = (valor & 0x400) != 0;
                    boolean flipY = (valor & 0x800) != 0;

                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int sx = flipX ? 7 - x : x;
                            int sy = flipY ? 7 - y : y;
                            int direccion = tile * 64 + sy * 8 + sx;
                            if (direccion >= 98304) continue;

                            int indice = datos[direccion] & 255;
                            if (indice == 0) continue;

                            int paleta = 98304 + indice * 2;
                            int c = (datos[paleta] & 255)
                                | ((datos[paleta + 1] & 255) << 8);

                            int r = ((c & 31) * 255) / 31;
                            int g = (((c >> 5) & 31) * 255) / 31;
                            int b = (((c >> 10) & 31) * 255) / 31;

                            int px = tx * 8 + x;
                            int py = ty * 8 + y;
                            colores[py * 256 + px] =
                                Color.rgb(r, g, b);

                            minX = Math.min(minX, px);
                            minY = Math.min(minY, py);
                            maxX = Math.max(maxX, px);
                            maxY = Math.max(maxY, py);
                        }
                    }
                }
            }

            if (maxX < minX || maxY < minY) return;

            Bitmap nuevo = Bitmap.createBitmap(
                colores, 256, 256, Bitmap.Config.ARGB_8888);

            Bitmap anterior = mapaRegionalCapturado;
            mapaRegionalCapturado = nuevo;
            mapaRecorteCapturado = new android.graphics.Rect(
                minX, minY, maxX + 1, maxY + 1);
            firmaScouter = firma;

            if (anterior != null) anterior.recycle();

            if (seccion == 5) invalidate();
        }

private String ultimaZonaMapa = "";

        void setMapSnapshot(int[] valores) {
            mapaDatos = valores != null && valores.length >= 5
                ? valores.clone() : null;

            if (mapaDatos != null && mapaDatos.length >= 11
                    && mapaDatos[0] == 1) {
                String zona = String.format(
                    java.util.Locale.ROOT,
                    "AREA=%02X:%02X RAW=%08X ENTRY=%08X DIM=%d,%d",
                    mapaDatos[5], mapaDatos[6],
                    mapaDatos[7], mapaDatos[8],
                    mapaDatos[9], mapaDatos[10]);

                if (!zona.equals(ultimaZonaMapa)) {
                    ultimaZonaMapa = zona;
                    android.util.Log.i("THOR_MAP_ZONE", zona);
                }
            }

            // THOR_AUTO_MAP_V1: sincronización de escenario.

            if (mapaAutomatico == null)

                mapaAutomatico = new ThorAutomaticMap(getContext());

            mapaAutomatico.update(mapaDatos);
            if (seccion == 5) invalidate();
        }

        private String ultimaFirmaDiagnosticoArea = "";

        void setStats(int[] valores) {

            // THOR_AREA_DIAG: lectura de punteros globales, solo diagnóstico.
            if (valores != null && valores.length >= 372) {
                StringBuilder firma = new StringBuilder();
                for (int i = 364; i < 372; i++) {
                    firma.append(String.format(
                        java.util.Locale.ROOT,
                        "%02X", valores[i] & 0xFF));
                }

                String actual = firma.toString();

                if (!actual.equals(ultimaFirmaDiagnosticoArea)) {
                    ultimaFirmaDiagnosticoArea = actual;

                    long puntero1 = 0;
                    long puntero2 = 0;

                    for (int i = 0; i < 4; i++) {
                        puntero1 |= ((long)(valores[364 + i] & 0xFF))
                            << (i * 8);
                        puntero2 |= ((long)(valores[368 + i] & 0xFF))
                            << (i * 8);
                    }

                    android.util.Log.i(
                        "THOR_AREA_DIAG",
                        String.format(
                            java.util.Locale.ROOT,
                            "RAM[0300197C]=%08X RAM[03001980]=%08X RAW=%s",
                            puntero1, puntero2, actual));
                }
            }

            // Retirar la pantalla de carga al detectar partida activa.
            if (cargando && valores != null
                    && valores.length > 1468 && valores[1468] == 1) {
                cargando = false;
                android.util.Log.i("ThorLoading",
                    "Partida detectada: mostrando menú inferior");
                invalidate();
            }

            int[] anteriores = stats;
            actualizarMisiones(valores);
            stats = valores != null && valores.length >= 7 && valores[1] > 0
                    ? valores.clone() : null;

            if (stats == null || stats.length < EQUIPO_SNAPSHOT
                    || anteriores == null || anteriores.length < EQUIPO_SNAPSHOT
                    || anteriores[72] != stats[72]
                    || anteriores[EQUIPO_EPOCA] != stats[EQUIPO_EPOCA]) {
                seleccionadoRegistroEquipo = seleccionadoIdEquipo = idParaDesequipar = -1;
                solicitudEquipo = 0;
                avisoEquipo = "";
            } else {
                if (seleccionadoRegistroEquipo >= 0
                        && (seleccionadoRegistroEquipo >= cantidadEquipoRAM()
                            || idEnRegistroEquipo(seleccionadoRegistroEquipo) != seleccionadoIdEquipo)) {
                    seleccionadoRegistroEquipo = seleccionadoIdEquipo = -1;
                }
                if (idParaDesequipar != idEquipado(categoriaEquipo)) idParaDesequipar = -1;
                if (solicitudEquipo != 0 && stats[EQUIPO_RESPUESTA] == solicitudEquipo) {
                    int resultado = stats[EQUIPO_RESPUESTA + 1];
                    avisoEquipo = resultado >= 50 ? ui.format("equipmentLevel", resultado)
                            : resultado == 2 ? ui.text("equipmentCharacter")
                            : resultado != 0 ? ui.text("equipmentFailed") : "";
                    solicitudEquipo = 0;
                }
                desplazamientoEquipoX = Math.min(desplazamientoEquipoX,
                        maxDesplazamientoListaEquipo());
            }

            // Inventario: prueba de correspondencia entre los 83 IDs
            // originales y RAM 0x0300106A..0x030010BC.
            // Snapshot 373..455. Nunca escribe en la RAM del juego.
            int idInventario = 0;
            for (int pestana = 0; pestana < inventarioCantidades.length; pestana++) {
                int[] cantidades = inventarioCantidades[pestana];
                if (cantidades == null) continue;

                for (int i = 0; i < cantidades.length; i++, idInventario++) {
                    int posicion = 373 + idInventario;
                    cantidades[i] = stats != null && stats.length > posicion
                        ? (stats[posicion] & 0xFF) : 0;
                }
            }
            // Diagnóstico del inventario real; no modifica objetos.
            if (stats != null && stats.length >= 456) {
                String firma = java.util.Arrays.toString(
                    java.util.Arrays.copyOfRange(stats, 372, 456));
                if (!firma.equals(ultimaFirmaInventario)) {
                    ultimaFirmaInventario = firma;
                    android.util.Log.i("ThorInvProbe",
                        "RAM_03001069_030010BC=" + firma);
                }
            }

            // Registrar IDs y bytes de niveles solo cuando cambien.
            if (stats != null && stats.length >= 221) {
                int personaje = stats[72];

                if (personaje >= 0 && personaje < 9) {
                    int base = 95 + personaje * 14;
                    String firma = personaje + ":" +
                        java.util.Arrays.toString(
                            java.util.Arrays.copyOfRange(
                                stats, base, base + 14));

                    if (!firma.equals(ultimaFirmaHabilidades)) {
                        ultimaFirmaHabilidades = firma;
                        android.util.Log.i(
                            "ThorSkillsProbe",
                            "PERSONAJE=" + personaje +
                            " DATOS=" + firma);
                    }
                }
            }

            if (stats != null && stats.length >= 95 &&
                    stats[72] != ultimoPersonajeRegistrado) {
                ultimoPersonajeRegistrado = stats[72];
                android.util.Log.i("ThorPersonaje", "PERSONAJE=" + stats[72]);
            }
            if (!probeLogged && stats != null && stats.length >= 95) {
                probeLogged = true;
                for (int start = 0; start < 64; start += 16) {
                    StringBuilder line = new StringBuilder(
                        String.format(java.util.Locale.US,
                            "0x%08X:", 0x03001560 + start * 2));
                    for (int i = start; i < start + 16; i++)
                        line.append(String.format(java.util.Locale.US,
                            " %04X", stats[7 + i] & 0xFFFF));
                    android.util.Log.i("ThorStatProbe", line.toString());
                }
                android.util.Log.i("ThorStatProbe",
                    "RELOJ_RAW=" + Integer.toUnsignedLong(stats[71])
                    + " PERSONAJE=" + stats[72]);
                for (int tipo = 0; tipo < 2; ++tipo) {
                    int total = stats[73 + tipo];
                    StringBuilder encontrados = new StringBuilder(
                        (tipo == 0 ? "NIVEL_60" : "SIGUIENTE_675")
                        + " coincidencias=" + total + " direcciones:");
                    for (int k = 0; k < Math.min(total, 10); ++k) {
                        encontrados.append(String.format(
                            java.util.Locale.US, " 0x%08X",
                            stats[75 + tipo * 10 + k]));
                    }
                    android.util.Log.i("ThorStatProbe",
                        encontrados.toString());
                }
            }
            if (seccion == 0 || seccion == 1 || seccion == 2 || seccion == 3 || seccion == 4 || seccion == 5) invalidate();
        }

        private void textoDiario(Canvas canvas, String valor,
                                 float centroX, float y, float tamano,
                                 float anchoMaximo) {
            labels.draw(canvas, valor, centroX, y, tamano, anchoMaximo,
                    Paint.Align.CENTER, ThorLabelPainter.PALE, true);
        }

        private void dibujarInventario(Canvas canvas) {


            fondo.setAntiAlias(false);
            fondo.setStyle(Paint.Style.FILL);

            // Fondo original del Inventario visible.

            // The common code-drawn chrome supplies the title and navigation.

            // Pestañas táctiles.
            for (int pestaña = 0; pestaña < 2; pestaña++) {
                float izquierda = pestaña == 0 ? 165f : 640f;
                float derecha = pestaña == 0 ? 600f : 1075f;

                fondo.setColor(pestaña == 0
                    ? Color.rgb(69, 157, 104)
                    : Color.rgb(205, 69, 145));

                canvas.drawRoundRect(
                    izquierda, 215f, derecha, 300f,
                    8f, 8f, fondo);

                fondo.setStyle(Paint.Style.STROKE);
                fondo.setStrokeWidth(
                    inventarioPestana == pestaña ? 5f : 2f);
                fondo.setColor(
                    inventarioPestana == pestaña
                        ? Color.WHITE
                        : Color.rgb(160, 181, 164));

                canvas.drawRoundRect(
                    izquierda + 2f, 217f,
                    derecha - 2f, 298f,
                    7f, 7f, fondo);

                fondo.setStyle(Paint.Style.FILL);

                textoDiario(canvas,
                    pestaña == 0
                        ? ui.text("supplies")
                        : ui.text("storyItems"),
                    (izquierda + derecha) / 2f,
                    270f, 40f, 415f);
            }

            String[] nombres = inventarioNombres[inventarioPestana];
            int[] cantidades = inventarioCantidades[inventarioPestana];
            Bitmap[] iconos = inventarioIconos[inventarioPestana];

            int total = Math.max(30, nombres.length);
            int filas = (total + INV_COLS - 1) / INV_COLS;

            canvas.save();
            canvas.clipRect(
                INV_X - 5f, INV_Y - 4f,
                1085f, INV_FINAL);

            for (int fila = 0; fila < filas; fila++) {
                float arriba = INV_Y
                    + fila * (INV_OBJ_ALTO + INV_SEP_Y)
                    - desplazamientoInventario;

                if (arriba + INV_OBJ_ALTO < INV_Y
                        || arriba > INV_FINAL)
                    continue;

                for (int columna = 0; columna < INV_COLS; columna++) {
                    int indice = fila * INV_COLS + columna;
                    if (indice >= total) break;

                    float izquierda = INV_X
                        + columna * (INV_OBJ_ANCHO + INV_SEP_X);

                    fondo.setStyle(Paint.Style.FILL);
                    fondo.setColor(Color.rgb(39, 80, 61));
                    canvas.drawRect(
                        izquierda, arriba,
                        izquierda + INV_OBJ_ANCHO,
                        arriba + INV_OBJ_ALTO, fondo);

                    fondo.setStyle(Paint.Style.STROKE);
                    fondo.setStrokeWidth(
                        indice == inventarioSeleccionado ? 5f : 2f);
                    fondo.setColor(
                        indice == inventarioSeleccionado
                            ? Color.rgb(250, 220, 112)
                            : Color.rgb(157, 196, 161));

                    canvas.drawRect(
                        izquierda + 2f, arriba + 2f,
                        izquierda + INV_OBJ_ANCHO - 2f,
                        arriba + INV_OBJ_ALTO - 2f, fondo);

                    fondo.setStyle(Paint.Style.FILL);

                    // Iconos auténticos, cuando estén conectados.
                    if (indice < iconos.length
                            && indice < cantidades.length
                            && cantidades[indice] > 0
                            && iconos[indice] != null) {
                        canvas.drawBitmap(
                            iconos[indice], null,
                            new android.graphics.RectF(
                                izquierda + 20f, arriba + 8f,
                                izquierda + 154f, arriba + 108f),
                            pixel);
                    }

                    // Cantidad del objeto.
                    if (indice < nombres.length
                            && indice < cantidades.length
                            && cantidades[indice] > 1) {
                        textoDiario(canvas,
                            Integer.toString(cantidades[indice]),
                            izquierda + 146f,
                            arriba + 105f, 30f, 55f);
                    }
                }
            }

            canvas.restore();

            // Información al tocar un objeto real, sin boton provisional.

            if (inventarioSeleccionado >= 0
                    && inventarioSeleccionado < nombres.length) {
                int indice = inventarioSeleccionado;

                textoDiario(canvas, nombres[indice],
                    620f, 930f, 48f, 860f);

                String descripcion = indice
                        < inventarioDescripciones[inventarioPestana].length
                    ? inventarioDescripciones[inventarioPestana][indice]
                    : "";

                labels.drawWrapped(canvas, descripcion, 190f, 950f, 860, 60,
                        36f, 2, ThorLabelPainter.PALE);
            }


}

        private void dibujarDiario(Canvas canvas) {
            fondo.setAntiAlias(false);
            fondo.setStyle(Paint.Style.FILL);

            if (diarioVista == 0) {

                for (int opcion = 0; opcion < 2; opcion++) {
                    float izquierda = opcion == 0 ? 120f : 640f;
                    float centro = izquierda + 240f;

                    fondo.setColor(Color.rgb(31, 54, 37));
                    canvas.drawRoundRect(izquierda, 230f,
                        izquierda + 480f, 980f, 18f, 18f, fondo);

                    fondo.setColor(Color.rgb(128, 160, 107));
                    fondo.setStyle(Paint.Style.STROKE);
                    fondo.setStrokeWidth(5f);
                    canvas.drawRoundRect(izquierda + 3f, 233f,
                        izquierda + 477f, 977f, 16f, 16f, fondo);
                    fondo.setStyle(Paint.Style.FILL);

                    if (opcion == 0) {
                        // Icono de pergamino pixelado.
                        fondo.setColor(Color.rgb(201, 177, 117));
                        canvas.drawRect(centro - 75f, 432f,
                            centro + 75f, 605f, fondo);

                        fondo.setColor(Color.rgb(235, 213, 155));
                        canvas.drawRect(centro - 60f, 448f,
                            centro + 60f, 590f, fondo);

                        fondo.setColor(Color.rgb(93, 70, 43));
                        for (int linea = 0; linea < 4; linea++) {
                            float y = 478f + linea * 27f;
                            canvas.drawRect(centro - 39f, y,
                                centro + 39f, y + 7f, fondo);
                        }

                        fondo.setColor(Color.rgb(156, 112, 64));
                        canvas.drawRect(centro - 85f, 425f,
                            centro + 85f, 446f, fondo);
                        canvas.drawRect(centro - 85f, 590f,
                            centro + 85f, 611f, fondo);
                    } else {
                        // Estrella de misiones completadas.
                        android.graphics.Path estrella =
                            new android.graphics.Path();

                        for (int punto = 0; punto < 10; punto++) {
                            double angulo =
                                -Math.PI / 2.0 + punto * Math.PI / 5.0;
                            float radio = punto % 2 == 0 ? 95f : 43f;
                            float x = centro +
                                (float) Math.cos(angulo) * radio;
                            float y = 520f +
                                (float) Math.sin(angulo) * radio;

                            if (punto == 0)
                                estrella.moveTo(x, y);
                            else
                                estrella.lineTo(x, y);
                        }

                        estrella.close();
                        fondo.setColor(Color.rgb(240, 185, 59));
                        canvas.drawPath(estrella, fondo);
                    }

                    textoDiario(canvas,
                        ui.text(opcion == 0 ? "activeQuests" : "completedQuests"),
                        centro, 740f, 57f, 445f);
                }
            } else {
                // Pantalla de lista; los datos reales se añadirán después.
                String titulo = diarioVista == 1
                    ? ui.text("activeQuests") : ui.text("completedQuests");

                textoDiario(canvas, titulo, 620f, 335f, 72f, 1060f);

                String[] lista = diarioVista == 1
                    ? misionesActivas : misionesCompletadas;

                canvas.save();
                canvas.clipRect(
                    DIARIO_X0, DIARIO_Y0, DIARIO_X1, DIARIO_Y1);

                for (int i = 0; i < lista.length; i++) {
                    float arriba = DIARIO_Y0
                        + i * DIARIO_FILA - desplazamientoDiario;

                    if (arriba + DIARIO_FILA < DIARIO_Y0
                            || arriba > DIARIO_Y1)
                        continue;

                    fondo.setColor(Color.rgb(31, 54, 37));
                    canvas.drawRoundRect(
                        170f, arriba + 8f,
                        1070f, arriba + DIARIO_FILA - 12f,
                        10f, 10f, fondo);

                    int[] estrellas = diarioVista == 1
                        ? estrellasActivas : estrellasCompletadas;
                    int tipo = estrellas[i];

                    android.graphics.Path estrella =
                        new android.graphics.Path();
                    for (int punto = 0; punto < 10; punto++) {
                        double angulo = -Math.PI / 2.0
                            + punto * Math.PI / 5.0;
                        float radio = punto % 2 == 0 ? 31f : 14f;
                        float xx = 245f
                            + (float) Math.cos(angulo) * radio;
                        float yy = arriba + 70f
                            + (float) Math.sin(angulo) * radio;

                        if (punto == 0)
                            estrella.moveTo(xx, yy);
                        else
                            estrella.lineTo(xx, yy);
                    }
                    estrella.close();

                    fondo.setStyle(Paint.Style.FILL);
                    fondo.setColor(tipo == 0
                        ? Color.rgb(243, 195, 69)
                        : tipo == 1
                            ? Color.rgb(190, 204, 215)
                            : Color.rgb(194, 132, 79));
                    canvas.drawPath(estrella, fondo);

                    labels.drawWrapped(canvas, lista[i], 298f, arriba + 30f,
                            735, 92, 44f, 2, ThorLabelPainter.PALE);
                }

                canvas.restore();

            }
        }

        private void escribir(Canvas canvas, String valor, float x, float y, float tamano) {
            labels.draw(canvas, valor, x, y, tamano * 1.40f, 1200f - x,
                    Paint.Align.LEFT, ThorLabelPainter.VALUE, true);
        }

        private void dibujarAtributos(Canvas canvas, float x, float y) {
            if (stats == null || stats.length < 29) return;
            for (int i = 0; i < 4; i++) {
                int valor = stats.length >= EQUIPO_SNAPSHOT ? stats[EQUIPO_ATRIBUTOS + i]
                        : (stats[27 + i / 2] >>> (8 * (i % 2))) & 0xFF;
                escribir(canvas, Integer.toString(valor), x, y + i * 90f, 46f);
            }
        }


        private void dibujarRejillaRadar(Canvas canvas,
                                         float left, float top,
                                         float right, float bottom) {
            Paint rejilla = new Paint();
            rejilla.setAntiAlias(false);
            rejilla.setStyle(Paint.Style.STROKE);
            rejilla.setStrokeWidth(2f);
            rejilla.setColor(Color.argb(78, 0, 0, 0));

            final float paso = 54f;
            final float cx = (left + right) * 0.5f;
            final float cy = (top + bottom) * 0.5f;

            for (float x = cx; x <= right; x += paso)
                canvas.drawLine(x, top, x, bottom, rejilla);
            for (float x = cx - paso; x >= left; x -= paso)
                canvas.drawLine(x, top, x, bottom, rejilla);

            for (float y = cy; y <= bottom; y += paso)
                canvas.drawLine(left, y, right, y, rejilla);
            for (float y = cy - paso; y >= top; y -= paso)
                canvas.drawLine(left, y, right, y, rejilla);

            rejilla.setStrokeWidth(3f);
            rejilla.setColor(Color.argb(108, 0, 0, 0));
            canvas.drawLine(cx, top, cx, bottom, rejilla);
            canvas.drawLine(left, cy, right, cy, rejilla);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() == 0 || getHeight() == 0) return;

            canvas.save();

            // Ajustar ambas capas a la superficie completa, sin recortes.
            canvas.scale(getWidth() / 1240f, getHeight() / 1080f);
            if (cargando) {
                canvas.drawBitmap(cargaImagen, null,
                    new android.graphics.RectF(0f, 0f, 1240f, 1080f), pixel);

                long tiempo = android.os.SystemClock.uptimeMillis();
                String puntos = new String(new char[1 + (int)((tiempo / 500L) % 3)])
                        .replace('\0', '.');
                labels.draw(canvas, ui.text("loading") + " " + puntos,
                        620f, 980f, 64f, 1000f, Paint.Align.CENTER, Color.WHITE, true);

                canvas.restore();
                postInvalidateDelayed(500L - tiempo % 500L);
                return;
            }

            // MAPA: ocupar toda la pantalla inferior por debajo del bezel.
            // THOR_AUTO_MAP_V2: render detallado a pantalla completa + rejilla radar.
            if (seccion == 5 && mapaScouterOriginal != null) {
                fondo.setStyle(Paint.Style.FILL);
                fondo.setColor(Color.BLACK);
                canvas.drawRect(0f, 0f, 1240f, 1080f, fondo);
                pixel.setFilterBitmap(false);
                // Original 240x160 map viewport, integer scale, centered.
                final int scale = Math.max(1, Math.min(1240 / 240, 1080 / 160));
                final float w = 240f * scale, h = 160f * scale;
                canvas.drawBitmap(mapaScouterOriginal, null,
                    new android.graphics.RectF((1240f - w) / 2f,
                        (1080f - h) / 2f, (1240f + w) / 2f,
                        (1080f + h) / 2f), pixel);
            }
            if (seccion == 5 && mapaScouterOriginal == null &&
                    mapaAutomatico != null) {
                mapaAutomatico.draw(canvas,
                    new android.graphics.RectF(0f, 0f, 1240f, 1080f));
                dibujarRejillaRadar(canvas, 0f, 0f, 1240f, 1080f);
            }
            Bitmap mapaVisible = mapaRegionalCapturado != null
                    ? mapaRegionalCapturado : mapaRegional;
            if (seccion == 5 && mapaScouterOriginal == null && mapaAutomatico == null && mapaVisible != null) {
                canvas.save();

                final float panelL = 0f;
                final float panelT = 0f;
                final float panelR = 1240f;
                final float panelB = 1080f;

                canvas.clipRect(panelL, panelT, panelR, panelB);

                fondo.setStyle(Paint.Style.FILL);
                fondo.setColor(Color.rgb(9, 14, 22));
                canvas.drawRect(panelL, panelT, panelR, panelB, fondo);

                pixel.setFilterBitmap(false);

                android.graphics.Rect recorteMapa =
                    mapaRecorteCapturado != null
                        ? new android.graphics.Rect(
                            mapaRecorteCapturado.left,
                            mapaRecorteCapturado.top,
                            mapaRecorteCapturado.right,
                            mapaRecorteCapturado.bottom)
                        : new android.graphics.Rect(43, 67, 165, 133);

                if (recorteMapa.width() < 16 || recorteMapa.height() < 16) {
                    recorteMapa = new android.graphics.Rect(43, 67, 165, 133);
                }

                final float panelW = panelR - panelL;
                final float panelH = panelB - panelT;

                final float escala = Math.max(
                    panelW / recorteMapa.width(),
                    panelH / recorteMapa.height());

                final float drawW = recorteMapa.width() * escala;
                final float drawH = recorteMapa.height() * escala;
                final float drawL = panelL + (panelW - drawW) / 2f;
                final float drawT = panelT + (panelH - drawH) / 2f;

                android.graphics.RectF destinoMapa =
                    new android.graphics.RectF(
                        drawL,
                        drawT,
                        drawL + drawW,
                        drawT + drawH);

                canvas.drawBitmap(
                    mapaVisible, recorteMapa, destinoMapa, pixel);

                dibujarRejillaRadar(canvas, panelL, panelT, panelR, panelB);

                // THOR_MAP_AREA_CALIBRATION:
                // La transformación actual solo está verificada
                // para el área 0x00010A01.
                // THOR_MAP_MARKER_PENDING:
                // No dibujar un marcador calculado con coordenadas
                // locales de Goku. La relación correcta con BG2
                // todavía está pendiente de identificar.


                canvas.restore();
            }

            canvas.drawBitmap(seccion == 5 ? plantillaMapa : plantilla, 0f, 0f, pixel);
            menuLabels.draw(canvas, seccion);

            if (seccion == 5 && mapaScouterOriginal == null && mapaAutomatico != null
                    && !mapaAutomatico.hasMap()) {
                // Localize the fallback's empty-state caption without altering its renderer.
                fondo.setStyle(Paint.Style.FILL);
                fondo.setColor(Color.rgb(9, 14, 22));
                canvas.drawRect(190f, 485f, 1050f, 590f, fondo);
                labels.draw(canvas, ui.text("mapUnavailable"), 620f, 555f, 48f,
                        830f, Paint.Align.CENTER, ThorLabelPainter.PALE, true);
            }

            if (seccion == 0) {
                // Former status-template separator, now drawn over the common blank background.
                fondo.setStyle(Paint.Style.FILL);
                fondo.setColor(Color.rgb(64, 83, 57));
                canvas.drawRect(713f, 481f, 727f, 789f, fondo);
                fondo.setColor(Color.rgb(85, 108, 75));
                canvas.drawRect(719f, 481f, 725f, 789f, fondo);
            }

            if (seccion == 0) {

                // Retrato y cartel del personaje activo.
                if (stats != null && stats.length > 72) {
                    int personaje = stats[72];
                    if (personaje >= 0 && personaje < retratos.length) {
                        if (retratos[personaje] != null) {
                            canvas.drawBitmap(
                                retratos[personaje], null,
                                new android.graphics.RectF(182f, 405f, 582f, 805f),
                                pixel);
                        }

                        if (carteles[personaje] != null) {
                            Bitmap cartel = carteles[personaje];
                            float ancho = cartel.getWidth() * 6f;
                            canvas.drawBitmap(
                                cartel, null,
                                new android.graphics.RectF(
                                    64f, 205f, 64f + ancho, 265f),
                                pixel);
                        }
                    }
                }

                if (stats != null) {
                // Nivel y experiencia restante calculados con la tabla de la ROM.
                long experiencia = Integer.toUnsignedLong(stats[4]);
                int indiceNivel = 0;

                while (indiceNivel + 1 < XP_UMBRALES.length
                        && experiencia >= XP_UMBRALES[indiceNivel + 1]) {
                    indiceNivel++;
                }

                escribir(canvas, Integer.toString(50 + indiceNivel),
                        895, 229, 46);

                if (indiceNivel + 1 < XP_UMBRALES.length) {
                    long restante = XP_UMBRALES[indiceNivel + 1] - experiencia;
                    escribir(canvas, Long.toString(restante),
                            1010, 415, 46);
                } else {
                    escribir(canvas, ui.text("max"), 1010, 415, 46);
                }

                    escribir(canvas, stats[0] + "/" + stats[1], 470, 229, 46);
                    escribir(canvas, stats[2] + "/" + stats[3], 470, 324, 46);
                    escribir(canvas, Long.toString(Integer.toUnsignedLong(stats[4])),
                            893, 324, 46);
                    escribir(canvas, Long.toString(Integer.toUnsignedLong(stats[6])),
                            255, 905, 46);
                    // Tiempo de partida: comprobar conversión a 60 Hz.
                    if (stats.length >= 72) {
                        long ticks = Integer.toUnsignedLong(stats[71]);
                        long minutos = ticks / 3600L;
                        String tiempo = String.format(java.util.Locale.US,
                                "%d:%02d", minutos / 60, minutos % 60);
                        escribir(canvas, tiempo, 298, 998, 46);
                    }
                    dibujarAtributos(canvas, 910f, 555f);
                }
            } else if (seccion == 1) {
                dibujarDiario(canvas);
            } else if (seccion == 2) {
                dibujarInventario(canvas);
            } else if (seccion == 4) {
                // EQUIPO: estadísticas originales, compartidas con ESTADO.
                dibujarRanurasVaciasEquipo(canvas);
                dibujarListaEquipoHorizontal(canvas);
                dibujarAtributos(canvas, 940f, 285f);
                dibujarBotonDesequipar(canvas);
                dibujarDetalleEquipo(canvas);
            } else if (seccion == 3) {
                dibujarHabilidades(canvas);
            } else if (seccion == 5) {
                // MAPA: gráficos regionales pendientes de conexión.
            } else {
                // Panel provisional: nunca inventar datos del juego.
                fondo.setColor(Color.rgb(29, 49, 31));
                canvas.drawRect(75, 345, 1160, 830, fondo);

                String titulo = ui.text(seccion == 1 ? "journal"
                        : seccion == 2 ? "inventory" : "skills");
                escribir(canvas, titulo, 140, 465, 65);
                escribir(canvas, ui.text("pendingData"), 140, 545, 31);
                escribir(canvas, ui.text("returnStatus"), 140, 615, 31);
            }

            canvas.restore();
        }

        private int objetoEnInventario(float x, float y) {
            float anchoTotal = INV_COLS * INV_OBJ_ANCHO
                + (INV_COLS - 1) * INV_SEP_X;
            if (x < INV_X || x >= INV_X + anchoTotal
                    || y < INV_Y || y >= INV_FINAL) return -1;
            float localX = x - INV_X;
            float localY = y - INV_Y + desplazamientoInventario;
            float pasoX = INV_OBJ_ANCHO + INV_SEP_X;
            float pasoY = INV_OBJ_ALTO + INV_SEP_Y;
            int columna = (int) (localX / pasoX);
            if (columna < 0 || columna >= INV_COLS
                    || localX % pasoX >= INV_OBJ_ANCHO
                    || localY % pasoY >= INV_OBJ_ALTO) return -1;
            int indice = (int) (localY / pasoY) * INV_COLS + columna;
            return indice < Math.max(30, inventarioNombres[inventarioPestana].length)
                ? indice : -1;
        }


        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            sonidoMenuListo = false;
            android.media.AudioAttributes atributos =
                new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_GAME)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            sonidoCambioMenu = new android.media.SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(atributos)
                .build();
            sonidoCambioMenu.setOnLoadCompleteListener((pool, id, estado) -> {
                if (id == idSonidoMenu && estado == 0)
                    sonidoMenuListo = true;
            });
            int recurso = getResources().getIdentifier(
                "menu_change", "raw", getContext().getPackageName());
            if (recurso != 0)
                idSonidoMenu = sonidoCambioMenu.load(getContext(), recurso, 1);
            else
                android.util.Log.e("ThorMenu", "No se encuentra menu_change.mp3");
        }

        @Override
        protected void onDetachedFromWindow() {
            sonidoMenuListo = false;
            if (sonidoCambioMenu != null) {
                sonidoCambioMenu.release();
                sonidoCambioMenu = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        public boolean performClick() {
            boolean cambio = seccionInicial >= 0 &&
                (seccion != seccionInicial ||
                 (seccion == 1 && diarioVista != diarioInicial) ||
                 (seccion == 2 && inventarioPestana != inventarioInicial) ||
                 (seccion == 4 && categoriaEquipo != equipoInicial));
            boolean equipoYaVibro = seccionInicial == 4 && seccion == 4
                && categoriaEquipo != equipoInicial;
            seccionInicial = -1;
            if (cambio && !sonidoMenuAnticipado) {
                if (!equipoYaVibro)
                    performHapticFeedback(
                        android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (sonidoMenuListo && sonidoCambioMenu != null)
                    sonidoCambioMenu.play(idSonidoMenu, 0.5f, 0.5f, 1, 0, 1.0f);
            }
            sonidoMenuAnticipado = false;
            return super.performClick();
        }


        private boolean cambioMenuAlTocar(float x, float y) {
            if (seccion == 1) {
                if (y < 160f)
                    return x > 850f || (x < 310f && diarioVista != 0);
                return diarioVista == 0 && y >= 230f && y <= 980f
                    && ((x >= 120f && x <= 600f) ||
                        (x >= 640f && x <= 1120f));
            }
            if (seccion == 2) {
                if (y < 160f) return x < 310f || x > 850f;
                return y >= 215f && y <= 300f
                    && ((x >= 165f && x <= 600f && inventarioPestana != 0)
                     || (x >= 640f && x <= 1075f && inventarioPestana != 1));
            }
            if (seccion == 4) {
                if (y < 160f && x < 350f) return true;
                if (x >= 185f && x <= 758f && y >= 205f && y < 613f) {
                    int categoria = (int)((y - 205f) / 102f);
                    return categoria >= 0 && categoria < 4
                        && categoria != categoriaEquipo;
                }
                return false;
            }
            int nueva = seccion;
            if (y < 160f && x < 310f) nueva = 1;
            else if (y < 160f && x > 850f) nueva = 2;
            else if (y > 850f && x > 770f) nueva = 3;
            else if (y < 160f && x >= 365f && x <= 850f) nueva = 0;
            return nueva != seccion;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (cargando) return true;
            if (getWidth() == 0 || getHeight() == 0) return true;

            float x = event.getX() * 1240f / getWidth();
            float y = event.getY() * 1080f / getHeight();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sonidoMenuAnticipado = false;
                    seccionInicial = seccion;
                    diarioInicial = diarioVista;
                    inventarioInicial = inventarioPestana;
                    equipoInicial = categoriaEquipo;
                    inicioToqueY = y;
                    ultimoToqueY = y;
                    arrastrandoHabilidades = false;

                    inicioInventarioX = x;
                    inicioInventarioY = y;
                    ultimoInventarioY = y;
                    arrastrandoInventario = false;
                    tocandoInventario = seccion == 2
                        && x >= INV_X && x <= 1085f
                        && y >= INV_Y && y <= INV_FINAL;
                    inventarioTocado = tocandoInventario ? objetoEnInventario(x, y) : -1;

                    tocandoDiario = seccion == 1 && diarioVista != 0
                        && x >= DIARIO_X0 && x <= DIARIO_X1
                        && y >= DIARIO_Y0 && y <= DIARIO_Y1;
                    arrastrandoDiario = false;
                    inicioDiarioY = y;
                    ultimoDiarioY = y;

                    tocandoHabilidades = seccion == 3
                        && x >= 52f && x <= 1190f
                        && y >= 175f && y <= HAB_FINAL;
                    tocandoListaEquipo = seccion == 4
                        && x >= 64f && x <= 1176f
                        && y >= EQUIPO_LISTA_Y0 && y <= EQUIPO_LISTA_Y1;
                    arrastrandoListaEquipo = false;
                    inicioEquipoX = ultimoEquipoX = x;
                    inicioEquipoY = y;
                    registroEquipoTocado = tocandoListaEquipo ? registroEnListaEquipo(x, y) : -1;
                    idEquipoTocado = registroEquipoTocado >= 0 ? idEnRegistroEquipo(registroEquipoTocado) : -1;
                    propietarioEquipoTocado = registroEquipoTocado >= 0
                            ? propietarioRegistroEquipo(registroEquipoTocado) : -1;
                    personajeEquipoTocado = stats != null && stats.length >= EQUIPO_SNAPSHOT
                            ? stats[72] : -1;
                    epocaEquipoTocada = personajeEquipoTocado >= 0 ? stats[EQUIPO_EPOCA] : 0;
                    if (!tocandoListaEquipo && !tocandoInventario
                            && !tocandoDiario && !tocandoHabilidades
                            && cambioMenuAlTocar(x, y)) {
                        sonidoMenuAnticipado = true;
                        performHapticFeedback(
                            android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        if (sonidoMenuListo && sonidoCambioMenu != null)
                            sonidoCambioMenu.play(idSonidoMenu, 0.5f, 0.5f, 1, 0, 1.0f);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (tocandoListaEquipo) {
                        if (Math.abs(x - inicioEquipoX) > 14f)
                            arrastrandoListaEquipo = true;
                        if (arrastrandoListaEquipo) {
                            desplazamientoEquipoX = Math.max(0f,
                                Math.min(maxDesplazamientoListaEquipo(),
                                    desplazamientoEquipoX + ultimoEquipoX - x));
                            invalidate();
                        }
                        ultimoEquipoX = x;
                        return true;
                    }
                    if (tocandoInventario) {
                        if (Math.abs(y - inicioInventarioY) > 14f
                                || Math.abs(x - inicioInventarioX) > 14f)
                            arrastrandoInventario = true;

                        if (arrastrandoInventario) {
                            inventarioSeleccionado = -1;
                            desplazamientoInventario = Math.max(0f,
                                Math.min(maxDesplazamientoInventario(),
                                    desplazamientoInventario
                                        + ultimoInventarioY - y));
                            invalidate();
                        }

                        ultimoInventarioY = y;
                        return true;
                    }

                    if (tocandoDiario) {
                        if (Math.abs(y - inicioDiarioY) > 14f)
                            arrastrandoDiario = true;

                        if (arrastrandoDiario) {
                            desplazamientoDiario = Math.max(0f,
                                Math.min(maxDesplazamientoDiario(),
                                    desplazamientoDiario
                                        + ultimoDiarioY - y));
                            invalidate();
                        }

                        ultimoDiarioY = y;
                        return true;
                    }

                    if (tocandoHabilidades) {
                        if (Math.abs(y - inicioToqueY) > 14f)
                            arrastrandoHabilidades = true;

                        if (arrastrandoHabilidades) {
                            float delta = ultimoToqueY - y;
                            desplazamientoHabilidades = Math.max(0f,
                                Math.min(maxDesplazamientoHabilidades(),
                                    desplazamientoHabilidades + delta));
                            invalidate();
                        }
                    }

                    ultimoToqueY = y;
                    return true;

                case MotionEvent.ACTION_UP:
                    if (tocandoListaEquipo) {
                        int registro = registroEnListaEquipo(x, y);
                        if (!arrastrandoListaEquipo
                                && Math.abs(x - inicioEquipoX) <= 14f
                                && Math.abs(y - inicioEquipoY) <= 20f
                                && registro >= 0 && registro == registroEquipoTocado
                                && idEnRegistroEquipo(registro) == idEquipoTocado) {
                            seleccionadoRegistroEquipo = registro;
                            seleccionadoIdEquipo = idEquipoTocado;
                            idParaDesequipar = -1;
                            avisoEquipo = "";
                            int propietario = propietarioRegistroEquipo(registro);
                            if (stats.length >= EQUIPO_SNAPSHOT
                                    && stats[72] == personajeEquipoTocado
                                    && stats[EQUIPO_EPOCA] == epocaEquipoTocada
                                    && propietario == propietarioEquipoTocado
                                    && propietario != stats[72] + 1) {
                                solicitarCambioEquipo(registro, idEquipoTocado, propietario);
                            }
                            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        }
                        tocandoListaEquipo = false;
                        arrastrandoListaEquipo = false;
                        invalidate();
                        performClick();
                        return true;
                    }
                    if (tocandoInventario) {
                        int indice = objetoEnInventario(x, y);
                        if (!arrastrandoInventario
                                && Math.abs(x - inicioInventarioX) <= 14f
                                && Math.abs(y - inicioInventarioY) <= 14f
                                && indice >= 0 && indice == inventarioTocado
                                && indice < inventarioCantidades[inventarioPestana].length
                                && inventarioCantidades[inventarioPestana][indice] > 0) {
                            performHapticFeedback(
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            if (inventarioSeleccionado == indice) {
                                int idObjeto = indice;
                                for (int p = 0; p < inventarioPestana; p++) {
                                    idObjeto += inventarioNombres[p].length;
                                }
                                GbaNative.requestUseItem(idObjeto);
                                inventarioSeleccionado = -1;
                            } else {
                                inventarioSeleccionado = indice;
                            }
                            invalidate();
                        }

                        tocandoInventario = false;
                        arrastrandoInventario = false;
                        performClick();
                        return true;
                    }

                    if (tocandoDiario) {
                        tocandoDiario = false;
                        arrastrandoDiario = false;
                        performClick();
                        return true;
                    }

                    if (tocandoHabilidades) {
                        if (!arrastrandoHabilidades) {
                            float yy = y + desplazamientoHabilidades;
                            int fila = (int) ((yy - HAB_INICIO) / HAB_FILA);
                            int columna = x >= 625f ? 1 : 0;
                            int indice = fila * 2 + columna;

                            float inicioFila = HAB_INICIO + fila * HAB_FILA;

                            if (yy >= HAB_INICIO
                                    && yy <= inicioFila + 240f
                                    && indice >= 0
                                    && indice < habilidadesActuales().length
                                    && habilidadesActuales()[indice] != null) {
                                // Seleccionar mediante el botón L del juego.
                                if (stats != null && stats.length >= 221) {
                                    int personaje = stats[72];

                                    if (personaje >= 0 && personaje < 9) {
                                        int base = 95 + personaje * 14;
                                        int total = Math.min(
                                            8, Math.max(0, stats[base] & 0xFF));
                                        int actual = stats[base + 13] & 0xFF;

                                        if (total > 0 && actual < total
                                                && indice < total) {
                                            int ciclos =
                                                (indice - actual + total) % total;

                                            if (ciclos > 0)
                                                GbaNative.requestSkillCycles(ciclos);
                                        }
                                    }
                                }
                                invalidate();
                            }
                        }

                        tocandoHabilidades = false;
                        performClick();
                        return true;
                    }

                    if (seccion == 1 && y < 160f) {
                        if (x > 850f) {
                            seccion = 0;
                            diarioVista = 0;
                        } else {
                            diarioVista = 0;
                        }
                        invalidate();
                        performClick();
                        return true;
                    }

                    // Diario tiene navegación táctil propia.
                    if (seccion == 1) {
                        if (y < 160f) {
                            if (x < 310f) {
                                diarioVista = 0;
                                desplazamientoDiario = 0f;
                            } else if (x > 850f) {
                                seccion = 0;
                                diarioVista = 0;
                                desplazamientoDiario = 0f;
                            }
                        } else if (diarioVista == 0
                                && y >= 230f && y <= 980f) {
                            if (x >= 120f && x <= 600f) {
                                diarioVista = 1;
                                desplazamientoDiario = 0f;
                            } else if (x >= 640f && x <= 1120f) {
                                diarioVista = 2;
                                desplazamientoDiario = 0f;
                            }
                        }

                        invalidate();
                        performClick();
                        return true;
                    }

                    // Inventario: navegación independiente.
                    if (seccion == 2) {
                        if (y < 160f) {
                            if (x < 310f) {
                                seccion = 0; // ESTADO
                            }
                            if (x > 850f) {
                                seccion = 4; // EQUIPO
                                inventarioSeleccionado = -1;
                            }
                        } else if (y >= 215f && y <= 300f) {
                            int nuevaPestana = -1;
                            if (x >= 165f && x <= 600f)
                                nuevaPestana = 0;
                            else if (x >= 640f && x <= 1075f)
                                nuevaPestana = 1;

                            if (nuevaPestana >= 0
                                    && nuevaPestana != inventarioPestana) {
                                inventarioPestana = nuevaPestana;
                                inventarioSeleccionado = -1;
                                desplazamientoInventario = 0f;
                            }
                        }

                        invalidate();
                        performClick();
                        return true;
                    }

                    if (seccion == 5) {
                        if (y < 160f) seccion = 0;       // ESTADO
                        else if (y >= 920f) seccion = 2; // INVENTARIO
                        invalidate();
                        performClick();
                        return true;
                    }

                    if (seccion == 4) {
                        if (Math.abs(x - inicioEquipoX) > 14f
                                || Math.abs(y - inicioEquipoY) > 20f) return true;
                        if (y < 160f && x < 350f) {
                            seccion = 2; // Volver a INVENTARIO
                            idParaDesequipar = -1;
                        } else if (y < 160f && x > 850f) {
                            seccion = 5; // MAPA
                            idParaDesequipar = -1;
                        } else if (enBotonDesequipar(x, y)
                                && enBotonDesequipar(inicioEquipoX, inicioEquipoY)
                                && stats != null && stats.length >= EQUIPO_SNAPSHOT
                                && stats[72] == personajeEquipoTocado
                                && stats[EQUIPO_EPOCA] == epocaEquipoTocada) {
                            solicitarCambioEquipo(-1, idParaDesequipar, stats[72] + 1);
                        } else if (x >= 185f && x <= 758f
                                && y >= 205f && y < 613f) {
                            int nueva = (int)((y - 205f) / 102f);
                            if (nueva >= 0 && nueva < 4) {
                                if (categoriaEquipo != nueva) {
                                    desplazamientoEquipoX = 0f;
                                    seleccionadoRegistroEquipo = seleccionadoIdEquipo = -1;
                                    avisoEquipo = "";
                                }
                                categoriaEquipo = nueva;
                                idParaDesequipar = idEquipado(nueva);
                                performHapticFeedback(
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            }
                        }
                        invalidate();
                        performClick();
                        return true;
                    }

                    int nueva = seccion;

                    if (y < 160 && x < 310) {
                        nueva = 1; // Diario
                    } else if (y < 160 && x > 850) {
                        nueva = seccion == 1 ? 0 : 2; // Estado desde Diario
                    } else if (y > 850 && x > 770) {
                        nueva = 3; // Habilidades
                    } else if (y < 160 && x >= 365 && x <= 850) {
                        nueva = 0; // Estado
                    }

                    if (nueva != seccion) {
                        seccion = nueva;
                        diarioVista = 0;
                        invalidate();
                    } else if (seccion == 1) {
                        if (y < 160f && x < 310f) {
                            // Pulsar Diario de nuevo vuelve al inicio.
                            diarioVista = 0;
                            invalidate();
                        } else if (diarioVista == 0
                                && y >= 230f && y <= 980f) {
                            if (x >= 120f && x <= 600f) {
                                diarioVista = 1;
                                invalidate();
                            } else if (x >= 640f && x <= 1120f) {
                                diarioVista = 2;
                                invalidate();
                            }
                        }
                    }

                    performClick();
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (tocandoListaEquipo) arrastrandoListaEquipo = true;
                    if (tocandoInventario) arrastrandoInventario = true;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    sonidoMenuAnticipado = false;
                    seccionInicial = -1;
                    tocandoListaEquipo = false;
                    arrastrandoListaEquipo = false;
                    tocandoInventario = false;
                    arrastrandoInventario = false;
                    tocandoDiario = false;
                    arrastrandoDiario = false;
                    tocandoHabilidades = false;
                    arrastrandoHabilidades = false;
                    return true;

                default:
                    return true;
            }
        }

    }

    static View createContent(Context context, ThorGameActivity host) {
        EstadoArtView estado = new EstadoArtView(context);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable actualizar = new Runnable() {
            @Override
            public void run() {
                if (!estado.isAttachedToWindow()) return;
                try {
                    estado.setStats(GbaNative.getStatusSnapshot());
                } catch (UnsatisfiedLinkError error) {
                    estado.setStats(null);
                }
                try {
                    estado.setMapSnapshot(GbaNative.getMapSnapshot());
                } catch (UnsatisfiedLinkError error) {
                    estado.setMapSnapshot(null);
                }
                try {
                    estado.setScouterGraphics(
                        GbaNative.getScouterGraphics());
                } catch (UnsatisfiedLinkError error) {
                    // Compatible con APK nativa anterior.
                }

                handler.postDelayed(this, 400);
            }
        };

        Runnable actualizarMapaOriginal = new Runnable() {
            @Override
            public void run() {
                if (!estado.isAttachedToWindow()) return;
                if (estado.seccion == 5) {
                    try {
                        estado.setOriginalScouterMap(GbaNative.getOriginalScouterMap(
                            estado.revisionScouterOriginal));
                    } catch (UnsatisfiedLinkError error) {
                        // Older native library keeps its existing map provider.
                    }
                }
                handler.postDelayed(this, 50);
            }
        };

        estado.addOnAttachStateChangeListener(
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    handler.removeCallbacks(actualizar);
                    handler.post(actualizar);
                    handler.removeCallbacks(actualizarMapaOriginal);
                    handler.post(actualizarMapaOriginal);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    handler.removeCallbacks(actualizar);
                    handler.removeCallbacks(actualizarMapaOriginal);
                }
            });

        return estado;
    }

    private static void addControl(Context context, LinearLayout row,
            ThorGameActivity host, String label, int key) {
        ControlButton control = new ControlButton(context, host, key);
        control.setText(label);
        control.setAllCaps(false);
        control.setFocusable(false);
        control.setOnTouchListener(new View.OnTouchListener() {
            private int pointer = -1;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pointer = event.getPointerId(0);
                        view.setPressed(true);
                        host.keyDown(key);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (pointer >= 0) view.performClick();
                        // fall through
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_OUTSIDE:
                        pointer = -1;
                        view.setPressed(false);
                        host.keyUp(key);
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        if (pointer == event.getPointerId(event.getActionIndex())) {
                            pointer = -1;
                            view.setPressed(false);
                            host.keyUp(key);
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        row.addView(control, new LinearLayout.LayoutParams(0, dp(context, 56), 1));
    }

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setFocusable(false);
        return button;
    }

    private static TextView text(Context context, String value, int size) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(0, dp(context, 6), 0, dp(context, 6));
        return text;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
