package dev.build;

public enum PieceType {
    FOUNDATION("foundation", "Фундамент", true),
    WALL("wall", "Стіна", false),
    FLOOR("floor", "Підлога", true),
    DOORWAY("doorway", "Дверний проєм", false);

    public final String key;
    public final String title;
    private final boolean horizontal;

    PieceType(String key, String title, boolean horizontal) {
        this.key = key;
        this.title = title;
        this.horizontal = horizontal;
    }

    /** Фундамент і підлога — плоскі 3x1x3; стіна і проєм — вертикальні 3x3x1. */
    public boolean horizontal() {
        return horizontal;
    }

    public PieceType next() {
        PieceType[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static PieceType byName(String s) {
        for (PieceType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return FOUNDATION;
    }
}
