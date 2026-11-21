package org.erpiopio.legacygoku2.thor;

/** One generation copied by JNI; no component reads mutable guest RAM. */
final class Legacy2MenuSnapshot {
    final Legacy2EstadoModel.Snapshot state;
    final int selectedId;
    final int[] portrait;
    final String[] skills, items, journal;
    final int[] skillSelection;
    final int[] skillIconIds;

    private Legacy2MenuSnapshot(Legacy2EstadoModel.Snapshot state, int selectedId,
            int[] portrait, String[] skills, String[] items, String[] journal,
            int[] skillSelection, int[] skillIconIds) {
        this.state = state;
        this.selectedId = selectedId;
        this.portrait = portrait;
        this.skills = skills;
        this.items = items;
        this.journal = journal;
        this.skillSelection = skillSelection;
        this.skillIconIds = skillIconIds;
    }

    static Legacy2MenuSnapshot decode(Object[] data) {
        if (data == null || data.length != 8 || !(data[0] instanceof int[]) ||
                !(data[1] instanceof int[]) || ((int[]) data[1]).length != 1)
            return null;
        Legacy2EstadoModel.Snapshot state =
            Legacy2EstadoModel.Snapshot.decode((int[]) data[0]);
        int selected = ((int[]) data[1])[0];
        if (state == null || selected < -1 || selected >= 6 ||
                (selected < 0 && state.availableIds.length != 0) ||
                (selected >= 0 && state.characters[selected] == null))
            return null;
        if ((data[2] != null && !(data[2] instanceof int[])) ||
                (data[3] != null && !(data[3] instanceof String[])) ||
                (data[4] != null && !(data[4] instanceof String[])) ||
                (data[5] != null && !(data[5] instanceof String[])) ||
                !(data[6] instanceof int[]) || !(data[7] instanceof int[]))
            return null;
        String[] skills = (String[]) data[3];
        String[] items = (String[]) data[4];
        String[] journal = (String[]) data[5];
        int[] selection = ((int[]) data[6]).clone();
        int[] icons = ((int[]) data[7]).clone();
        if ((selected >= 0 && (skills == null || skills.length == 0 || skills.length % 3 != 0)) ||
                (items != null && items.length != 46 * 4) ||
                (journal != null && journal.length != 43 * 4) ||
                selection.length != 4 || selection[0] != state.activeId ||
                selection[1] < -1 || selection[1] > 2 ||
                (selection[2] != 0 && selection[2] != 1) ||
                selection[3] != state.generation ||
                (selection[0] < 0 && (selection[1] != -1 || selection[2] != 0)) ||
                icons.length != (skills == null ? 3 : skills.length / 3))
            return null;
        for (int n = 0; n < icons.length; ++n)
            if (icons[n] < (n == 0 ? -22 : -1) || icons[n] >= 14) return null;
        return new Legacy2MenuSnapshot(state, selected, (int[]) data[2],
            skills, items, journal, selection, icons);
    }
}
