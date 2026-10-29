package com.localspend.model;

import java.util.Locale;

/**
 * Currencies LocalSpend understands. Each transaction keeps the currency its SMS (or the user)
 * gave it, so a rupee payment is never shown or added up as dollars.
 */
public enum Currency {
    AUD("$", "A$", "Australian dollar", false),
    INR("₹", "₹", "Indian rupee", true),
    USD("US$", "US$", "US dollar", false);

    private final String symbol;
    private final String shortLabel;
    private final String displayName;
    private final boolean indianGrouping;   // 1,00,000 instead of 100,000

    Currency(String symbol, String shortLabel, String displayName, boolean indianGrouping) {
        this.symbol = symbol;
        this.shortLabel = shortLabel;
        this.displayName = displayName;
        this.indianGrouping = indianGrouping;
    }

    /** Symbol placed before amounts: "$", "₹". */
    public String getSymbol() {
        return symbol;
    }

    /** Unambiguous short label for switches and pickers: "A$", "₹". */
    public String getShortLabel() {
        return shortLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** "A$ AUD", "₹ INR". */
    public String getPickerLabel() {
        return shortLabel + " " + name();
    }

    public boolean usesIndianGrouping() {
        return indianGrouping;
    }

    /** The default for stored data written before currencies were tracked. */
    public static Currency defaultCurrency() {
        return AUD;
    }

    /** Safe lookup used when reading from the database; unknown values fall back to AUD. */
    public static Currency fromCode(String code) {
        if (code != null) {
            for (Currency c : values()) {
                if (c.name().equalsIgnoreCase(code.trim())) {
                    return c;
                }
            }
        }
        return defaultCurrency();
    }

    /** Currency for a marker found next to an amount in a message: "Rs.", "INR", "₹", "$", "AUD". */
    public static Currency fromMarker(String marker) {
        if (marker == null) {
            return defaultCurrency();
        }
        String m = marker.trim().toLowerCase(Locale.ROOT).replace(".", "");
        if (m.equals("rs") || m.equals("inr") || m.equals("₹")) {
            return INR;
        }
        if (m.equals("usd") || m.equals("us$")) {
            return USD;
        }
        return AUD;   // "$", "a$", "aud"
    }
}
