package org.erpiopio.legacygoku1.thor;

/** Immutable snapshots published by the emulation thread; never reads live RAM on Android's UI thread. */
public final class Legacy1Native {
    private Legacy1Native() { }
    public static native int[] readState();
    public static native String[] readJournal();
    public static native String[] readInventoryNames();
    public static native int[] readMap();
    public static native void consumeInventoryCategory(int category);
    public static native void equipAttack(int attack);
}
