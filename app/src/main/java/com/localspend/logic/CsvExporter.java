package com.localspend.logic;

import com.localspend.model.Transaction;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Turns transactions into CSV text a spreadsheet can open (RFC 4180 quoting). Text that a
 * spreadsheet would run as a formula ("=...", "+...", "-...", "@...") is prefixed with an apostrophe.
 */
public final class CsvExporter {

    public static final String[] HEADER = {
            "Date", "Type", "Merchant", "Category", "Amount", "Currency",
            "Converted amount", "Converted currency", "Exchange rate", "Source",
            "SMS sender", "SMS message"};

    private CsvExporter() {
    }

    public static String toCsv(List<Transaction> transactions, TimeZone zone) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        dateFormat.setTimeZone(zone);
        StringBuilder out = new StringBuilder();
        appendRow(out, HEADER);
        for (Transaction t : transactions) {
            String converted = "";
            String convertedCurrency = "";
            String rate = "";
            if (t.hasConversion()) {
                converted = money(t.getConvertedCents());
                convertedCurrency = t.getConvertedCurrency().name();
                BigDecimal r = t.getConversionRate();
                rate = r == null ? "" : r.toPlainString();
            }
            appendRow(out, new String[]{
                    dateFormat.format(new Date(t.getDateMillis())),
                    t.getType().getLabel(),
                    safeText(t.getMerchant()),
                    safeText(t.getCategory().getLabel()),
                    money(t.getAmountCents()),
                    t.getCurrency().name(),
                    converted, convertedCurrency, rate,
                    t.getSource().name(),
                    safeText(t.getSmsSender()),
                    safeText(t.getSmsBody())});
        }
        return out.toString();
    }

    static String money(long cents) {
        return BigDecimal.valueOf(Math.abs(cents), 2).toPlainString();
    }

    /** Stops a spreadsheet treating text as a formula. */
    static String safeText(String text) {
        if (text == null) {
            return "";
        }
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private static void appendRow(StringBuilder out, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(cells[i]));
        }
        out.append("\r\n");
    }

    static String quote(String cell) {
        if (cell == null) {
            return "";
        }
        boolean needsQuotes = cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }
}
