package org.erpiopio.legacygoku2.thor;

/** Data-only presentation state. It never writes the emulated player. */
final class Legacy2EstadoModel {
    static final int CHARACTER_COUNT = 6;
    static final int HEADER_SIZE = 5;
    static final int RECORD_SIZE = 12;
    static final String[] NAMES = {
        "Gohan", "Piccolo", "Vegeta", "Trunks", "Goku", "Mr. Satan"
    };

    static final class CharacterState {
        final int id, level, hp, hpMax, energy, energyMax;
        final int strength, power, resistance, experience, nextExperience;

        CharacterState(int[] data, int offset) {
            id = data[offset + 1];
            level = data[offset + 2];
            hp = data[offset + 3];
            hpMax = data[offset + 4];
            energy = data[offset + 5];
            energyMax = data[offset + 6];
            strength = data[offset + 7];
            power = data[offset + 8];
            resistance = data[offset + 9];
            experience = data[offset + 10];
            nextExperience = data[offset + 11];
        }
    }

    static final class Snapshot {
        final int language, activeId, generation;
        final CharacterState[] characters;
        final int[] availableIds;

        private Snapshot(int language, int activeId, int generation,
                         CharacterState[] characters, int[] availableIds) {
            this.language = language;
            this.activeId = activeId;
            this.generation = generation;
            this.characters = characters;
            this.availableIds = availableIds;
        }

        static Snapshot decode(int[] data) {
            if (data == null || data.length != HEADER_SIZE +
                    CHARACTER_COUNT * RECORD_SIZE || data[0] != 1)
                return null;
            final int mask = data[3] & ((1 << CHARACTER_COUNT) - 1);
            CharacterState[] characters = new CharacterState[CHARACTER_COUNT];
            int count = 0;
            for (int id = 0; id < CHARACTER_COUNT; ++id) {
                int offset = HEADER_SIZE + id * RECORD_SIZE;
                // Both native availability and its mask must agree. Never
                // infer an unlock from nonzero, preinitialized statistics.
                if ((mask & (1 << id)) != 0 && data[offset] == 1 &&
                        data[offset + 1] == id) {
                    characters[id] = new CharacterState(data, offset);
                    ++count;
                }
            }
            int[] ids = new int[count];
            for (int id = 0, n = 0; id < CHARACTER_COUNT; ++id)
                if (characters[id] != null) ids[n++] = id;
            int active = data[2] >= 0 && data[2] < CHARACTER_COUNT ? data[2] : -1;
            int language = data[1] >= 0 && data[1] <= 4 ? data[1] : -1;
            return new Snapshot(language, active, data[4], characters, ids);
        }
    }

    private Snapshot snapshot;
    private int selectedId = -1;

    void clear() {
        snapshot = null;
        selectedId = -1;
    }

    boolean update(Snapshot next) {
        if (next == null) return false;
        snapshot = next;
        if (selectedId < 0 || next.characters[selectedId] == null) {
            selectedId = next.activeId >= 0 &&
                next.characters[next.activeId] != null ? next.activeId :
                (next.availableIds.length == 0 ? -1 : next.availableIds[0]);
        }
        return true;
    }

    int selectedId() { return selectedId; }
    int count() { return snapshot == null ? 0 : snapshot.availableIds.length; }
    Snapshot snapshot() { return snapshot; }
    CharacterState selected() {
        return snapshot == null || selectedId < 0 ? null : snapshot.characters[selectedId];
    }

    boolean move(int direction) {
        if (count() < 2 || direction == 0) return false;
        int index = indexOfSelected();
        index = (index + (direction > 0 ? 1 : -1) + count()) % count();
        selectedId = snapshot.availableIds[index];
        return true;
    }

    private int indexOfSelected() {
        if (snapshot != null)
            for (int n = 0; n < snapshot.availableIds.length; ++n)
                if (snapshot.availableIds[n] == selectedId) return n;
        return 0;
    }

    /** Back-to-front IDs; changing selection rotates the complete stack. */
    int[] layerOrder() {
        int[] order = new int[count()];
        if (order.length == 0) return order;
        int selectedIndex = indexOfSelected();
        for (int n = 0; n < order.length; ++n)
            order[n] = snapshot.availableIds[
                (selectedIndex + order.length - 1 - n) % order.length];
        return order;
    }
}
