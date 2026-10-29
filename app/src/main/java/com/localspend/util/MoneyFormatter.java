package com.localspend.util;

import com.localspend.model.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between user-visible amounts and the whole-cent (or whole-paise) longs used
 * internally. Every amount is shown with its own currency: "$25.50", "₹1,999.00". Rupee amounts
 * use Indian digit grouping ("₹1,00,000.00").
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    /** 255000 becomes "$2,550.00" (Australian dollars). */
    public static String format(long cents) {
        return format(cents, Currency.defaultCurrency());
    }

    /** 199900 in INR becomes "₹1,999.00". */
    public static String format(long cents, Currency currency) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return sign + currency.getSymbol() + group(abs / 100, currency) + "." + twoDigits(abs % 100);
    }

    /** Rounds to the nearest whole unit: 125049 becomes "$1,250". Used where space is tight. */
    public static String formatWhole(long cents) {
        return formatWhole(cents, Currency.defaultCurrency());
    }

    public static String formatWhole(long cents, Currency currency) {
        long whole = Math.round(Math.abs(cents) / 100.0);
        return (cents < 0 ? "-" : "") + currency.getSymbol() + group(whole, currency);
    }

    /**
     * Parses text typed by the user ("25.5", "$1,250.00", "₹1,999") into cents.
     *
     * @throws NumberFormatException if the text is not a non-negative amount
     */
    public static long parseToCents(String text) {
        if (text == null) {
            throw new NumberFormatException("empty");
        }
        String cleaned = text.trim().replaceAll("(?i)(us\\$|a\\$|rs\\.?|inr|aud|usd)", "")
                .replace("$", "").replace("₹", "").replace(",", "").replace(" ", "");
        if (cleaned.isEmpty()) {
            throw new NumberFormatException("empty");
        }
        BigDecimal value = new BigDecimal(cleaned);
        if (value.signum() < 0) {
            throw new NumberFormatException("negative");
        }
        try {
            return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            throw new NumberFormatException("too large");
        }
    }

    private static String twoDigits(long n) {
        return n < 10 ? "0" + n : Long.toString(n);
    }

    private static String group(long n, Currency currency) {
        String digits = Long.toString(n);
        if (!currency.usesIndianGrouping() || digits.length() <= 3) {
            return groupBy3(digits);
        }
        // Indian system: last three digits, then groups of two (12,34,567).
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = rest.length() - 1; i >= 0; i--) {
            sb.append(rest.charAt(i));
            if (++count % 2 == 0 && i > 0) {
                sb.append(',');
            }
        }
        return sb.reverse() + "," + last3;
    }

    private static String groupBy3(String digits) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                sb.append(',');
            }
        }
        return sb.reverse().toString();
    }
}
