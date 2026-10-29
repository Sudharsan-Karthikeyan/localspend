package com.localspend.sms;

import com.localspend.model.Currency;
import com.localspend.model.TransactionType;

/** What the parser pulled out of one SMS: amount and currency, merchant, type and date. */
public final class ParsedTransaction {

    private final long amountCents;
    private final String merchant;
    private final TransactionType type;
    private final long dateMillis;
    private final Currency currency;
    private final boolean merchantFound;

    public ParsedTransaction(long amountCents, String merchant, TransactionType type,
                             long dateMillis) {
        this(amountCents, merchant, type, dateMillis, Currency.defaultCurrency(), true);
    }

    public ParsedTransaction(long amountCents, String merchant, TransactionType type,
                             long dateMillis, Currency currency, boolean merchantFound) {
        this.amountCents = amountCents;
        this.merchant = merchant;
        this.type = type;
        this.dateMillis = dateMillis;
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
        this.merchantFound = merchantFound;
    }

    public Currency getCurrency() {
        return currency;
    }

    /** False when the message named no merchant and a placeholder name was used. */
    public boolean isMerchantFound() {
        return merchantFound;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getMerchant() {
        return merchant;
    }

    public TransactionType getType() {
        return type;
    }

    public long getDateMillis() {
        return dateMillis;
    }
}
