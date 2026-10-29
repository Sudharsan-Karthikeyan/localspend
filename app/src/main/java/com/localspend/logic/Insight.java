package com.localspend.logic;

/** One short observation about the user's spending, shown on the dashboard and history screens. */
public final class Insight {

    /** Whether the observation is good news, a warning, or simply information. */
    public enum Kind {
        GOOD, WARNING, INFO
    }

    private final Kind kind;
    private final String text;

    public Insight(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return kind + ": " + text;
    }
}
