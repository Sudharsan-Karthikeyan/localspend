package com.localspend.model;

/** How a transaction entered the app. */
public enum Source {
    MANUAL,
    SMS,
    /** Created automatically by a repeating payment or an instalment plan. */
    RECURRING;

    public static Source fromName(String name) {
        for (Source s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return MANUAL;
    }
}
