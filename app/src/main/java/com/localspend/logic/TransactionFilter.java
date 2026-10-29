package com.localspend.logic;

import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The conditions of a search: words, category, income or expense, a range of dates and a range of
 * amounts. Every condition left empty is ignored; the ones that are set must all match.
 */
public final class TransactionFilter {

    private String text = "";
    private String categoryKey;          // null = any
    private TransactionType type;        // null = both
    private Long fromMillis;             // inclusive, null = no start
    private Long toMillisExclusive;      // exclusive, null = no end
    private Long minCents;               // inclusive, null = no minimum
    private Long maxCents;               // inclusive, null = no maximum
    private Currency amountCurrency;     // the currency the amount range is in; null = each own

    /** The amount range is in this (dashboard) currency; others are converted with their stored rate. */
    public TransactionFilter setAmountCurrency(Currency amountCurrency) {
        this.amountCurrency = amountCurrency;
        return this;
    }

    public TransactionFilter setText(String text) {
        this.text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return this;
    }

    public TransactionFilter setCategoryKey(String categoryKey) {
        this.categoryKey = categoryKey;
        return this;
    }

    public TransactionFilter setType(TransactionType type) {
        this.type = type;
        return this;
    }

    public TransactionFilter setFromMillis(Long fromMillis) {
        this.fromMillis = fromMillis;
        return this;
    }

    public TransactionFilter setToMillisExclusive(Long toMillisExclusive) {
        this.toMillisExclusive = toMillisExclusive;
        return this;
    }

    public TransactionFilter setMinCents(Long minCents) {
        this.minCents = minCents;
        return this;
    }

    public TransactionFilter setMaxCents(Long maxCents) {
        this.maxCents = maxCents;
        return this;
    }

    /** True when no condition is set at all. */
    public boolean isEmpty() {
        return text.isEmpty() && categoryKey == null && type == null && fromMillis == null
                && toMillisExclusive == null && minCents == null && maxCents == null;
    }

    public boolean matches(Transaction t) {
        if (type != null && t.getType() != type) {
            return false;
        }
        if (categoryKey != null && !categoryKey.equals(t.getCategory().getKey())) {
            return false;
        }
        if (fromMillis != null && t.getDateMillis() < fromMillis) {
            return false;
        }
        if (toMillisExclusive != null && t.getDateMillis() >= toMillisExclusive) {
            return false;
        }
        if (minCents != null || maxCents != null) {
            long amount;
            if (amountCurrency == null) {
                amount = Math.abs(t.getAmountCents());
            } else if (t.canBeShownIn(amountCurrency)) {
                amount = Math.abs(t.getAmountIn(amountCurrency));
            } else {
                return false;   // cannot be converted, so it cannot be said to be in range
            }
            if (minCents != null && amount < minCents) {
                return false;
            }
            if (maxCents != null && amount > maxCents) {
                return false;
            }
        }
        return text.isEmpty() || textMatches(t);
    }

    /** Every word typed must appear in the merchant, the category or the original message. */
    private boolean textMatches(Transaction t) {
        StringBuilder haystack = new StringBuilder();
        haystack.append(t.getMerchant()).append(' ').append(t.getCategory().getLabel());
        if (t.getSmsBody() != null) {
            haystack.append(' ').append(t.getSmsBody());
        }
        String hay = haystack.toString().toLowerCase(Locale.ROOT);
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty() && !hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** The matching transactions, in the order given. */
    public List<Transaction> apply(List<Transaction> all) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : all) {
            if (matches(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
