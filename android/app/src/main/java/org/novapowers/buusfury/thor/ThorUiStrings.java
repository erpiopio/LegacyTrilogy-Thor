package org.novapowers.buusfury.thor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Lower-screen copy. The cartridge identity is the only language authority. */
final class ThorUiStrings {
    private static final Map<String, String[]> COPY;
    static {
        Map<String, String[]> copy = new LinkedHashMap<>();
        add(copy, "status", "Status", "Estado", "État", "Estado");
        add(copy, "journal", "Journal", "Diario", "Journal", "Diário");
        add(copy, "inventory", "Inventory", "Inventario", "Inventaire", "Inventário");
        add(copy, "skills", "Skills", "Habilidades", "Techniques", "Habilidades");
        add(copy, "equipment", "Equipment", "Equipo", "Équipement", "Equipamento");
        add(copy, "map", "Map", "Mapa", "Carte", "Mapa");
        add(copy, "mapUnavailable", "MAP UNAVAILABLE", "MAPA NO DISPONIBLE", "CARTE INDISPONIBLE", "MAPA INDISPONÍVEL");
        add(copy, "hp", "HP", "PS", "PV", "PV");
        add(copy, "energy", "EP", "PE", "PE", "PE");
        add(copy, "level", "Level", "Nivel", "Niveau", "Nível");
        add(copy, "statLevel", "LV", "NVL", "NIV", "NÍV");
        add(copy, "experience", "EXP", "EXP", "EXP", "EXP");
        add(copy, "nextLevel", "NEXT LV", "SIG NVL", "NIV SUIV", "PRÓX NÍV");
        add(copy, "strength", "STR", "FUE", "FOR", "FOR");
        add(copy, "strengthFull", "Strength", "Fuerza", "Force", "Força");
        add(copy, "power", "POW", "POD", "PUI", "POD");
        add(copy, "endurance", "END", "RES", "END", "RES");
        add(copy, "speed", "SPD", "VEL", "VIT", "VEL");
        add(copy, "money", "ZENI", "ZENI", "ZÉNIS", "ZENI");
        add(copy, "time", "TIME", "TIEMPO", "TEMPS", "TEMPO");
        add(copy, "chest", "CHEST", "PECHO", "TORSE", "TRONCO");
        add(copy, "hands", "HANDS", "MANOS", "MAINS", "MÃOS");
        add(copy, "feet", "FEET", "PIES", "PIEDS", "PÉS");
        add(copy, "accessories", "ACCESSORIES", "ACCESORIOS", "ACCESSOIRES", "ACESSÓRIOS");
        add(copy, "unequip", "Unequip", "Desequipar", "Retirer", "Desequipar");
        add(copy, "back", "BACK", "VOLVER", "RETOUR", "VOLTAR");
        add(copy, "noEquipment", "No Equipment Available", "Sin Equipo Disponible", "Aucun équipement disponible", "Nenhum equipamento disponível");
        add(copy, "equipped", "EQUIPPED", "EQUIPADO", "ÉQUIPÉ", "EQUIPADO");
        add(copy, "otherCharacter", "ON ANOTHER CHARACTER", "EN OTRO PERSONAJE", "SUR UN AUTRE PERSONNAGE", "EM OUTRO PERSONAGEM");
        add(copy, "retryEquipment", "Tap again to retry", "Vuelve a pulsar para intentarlo", "Touchez à nouveau pour réessayer", "Toque novamente para tentar");
        add(copy, "equipmentUnavailable", "Equipment action unavailable", "Acción de equipo no disponible", "Action d'équipement indisponible", "Ação de equipamento indisponível");
        add(copy, "equipmentLevel", "Requires level %d", "Requiere nivel %d", "Niveau %d requis", "Requer nível %d");
        add(copy, "equipmentCharacter", "This character cannot equip items", "Este personaje no puede equipar piezas", "Ce personnage ne peut pas s'équiper", "Este personagem não pode se equipar");
        add(copy, "equipmentFailed", "Could not change equipment", "No se pudo cambiar el equipo", "Impossible de changer d'équipement", "Não foi possível trocar o equipamento");
        add(copy, "itemFallback", "Item %d", "Pieza %d", "Objet %d", "Item %d");
        add(copy, "skillFallback", "Skill %d", "Técnica %d", "Technique %d", "Técnica %d");
        add(copy, "levelValue", "Level %d", "Nivel %d", "Niveau %d", "Nível %d");
        add(copy, "supplies", "SUPPLIES", "SUMINISTROS", "PROVISIONS", "SUPRIMENTOS");
        add(copy, "storyItems", "STORY ITEMS", "OBJETOS DE HISTORIA", "OBJETS DE QUÊTE", "ITENS DE HISTÓRIA");
        add(copy, "activeQuests", "ACTIVE QUESTS", "MISIONES ACTIVAS", "QUÊTES EN COURS", "MISSÕES ATIVAS");
        add(copy, "completedQuests", "COMPLETED", "COMPLETADAS", "TERMINÉES", "CONCLUÍDAS");
        add(copy, "loading", "LOADING", "CARGANDO", "CHARGEMENT", "CARREGANDO");
        add(copy, "max", "MAX", "MÁX", "MAX", "MÁX");
        add(copy, "pendingData", "Waiting for game data", "Pendiente de conectar datos reales", "En attente des données du jeu", "Aguardando dados do jogo");
        add(copy, "returnStatus", "Tap STATUS to return", "Toca ESTADO para regresar", "Touchez ÉTAT pour revenir", "Toque em ESTADO para voltar");
        add(copy, "selectItem", "SELECT AN ITEM", "SELECCIONA UN OBJETO", "CHOISISSEZ UN OBJET", "SELECIONE UM ITEM");
        add(copy, "equipmentCatalog", "162 GRAPHICS — NOT EQUIPPED", "162 GRÁFICOS — NO EQUIPADOS", "162 IMAGES — NON ÉQUIPÉES", "162 IMAGENS — NÃO EQUIPADAS");
        add(copy, "equipmentCatalogTitle", "EQUIPMENT CATALOG", "CATÁLOGO DE EQUIPO", "CATALOGUE D'ÉQUIPEMENT", "CATÁLOGO DE EQUIPAMENTO");
        add(copy, "equipmentCatalogPending", "Identification, stats and ownership pending", "Identificación, estadísticas y posesión pendientes de RAM", "Identification, statistiques et possession en attente", "Identificação, atributos e posse pendentes");
        add(copy, "equipmentNumber", "EQUIPMENT %03d", "EQUIPO %03d", "ÉQUIPEMENT %03d", "EQUIPAMENTO %03d");
        COPY = Collections.unmodifiableMap(copy);
    }

    private final String tag;
    private final int column;

    ThorUiStrings(String languageTag) {
        tag = normalize(languageTag);
        column = "es".equals(tag) ? 1 : "fr".equals(tag) ? 2 : "pt-BR".equals(tag) ? 3 : 0;
    }

    static String normalize(String tag) {
        if ("es".equals(tag)) return "es";
        if ("fr".equals(tag)) return "fr";
        if ("pt-BR".equalsIgnoreCase(tag)) return "pt-BR";
        return "en";
    }

    String languageTag() { return tag; }

    String text(String key) {
        String[] row = COPY.get(key);
        if (row == null) throw new IllegalArgumentException("Unknown lower-screen text key: " + key);
        return row[column];
    }

    String format(String key, Object... args) {
        return String.format(Locale.ROOT, text(key), args);
    }

    private static void add(Map<String, String[]> copy, String key,
                            String en, String es, String fr, String pt) {
        if (copy.put(key, new String[] {en, es, fr, pt}) != null)
            throw new IllegalStateException("Duplicate lower-screen key: " + key);
    }
}
