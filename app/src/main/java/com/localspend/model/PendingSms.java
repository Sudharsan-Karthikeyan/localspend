package com.localspend.model;

/**
 * A transaction message that was not added automatically, because it came from a sender that is
 * not a recognised bank (or it looked like an advert). The user decides whether to add it.
 */
public final class PendingSms {

    /** Where the message is in the review process. */
    public enum Status {
        PENDING, ACCEPTED, IGNORED;

        public static Status fromName(String name) {
            for (Status s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return PENDING;
        }
    }

    private final long id;
    private final String smsKey;
    private final String sender;
    private final String body;
    private final long receivedMillis;
    private final long amountCents;
    private final String merchant;
    private final TransactionType type;
    private final long dateMillis;
    private final Status status;
    private final Currency currency;

    public PendingSms(long id, String smsKey, String sender, String body, long receivedMillis,
                      long amountCents, String merchant, TransactionType type, long dateMillis,
                      Status status, Currency currency) {
        this.id = id;
        this.smsKey = smsKey;
        this.sender = sender;
        this.body = body;
        this.receivedMillis = receivedMillis;
        this.amountCents = amountCents;
        this.merchant = merchant;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.dateMillis = dateMillis;
        this.status = status == null ? Status.PENDING : status;
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getId() {
        return id;
    }

    public String getSmsKey() {
        return smsKey;
    }

    public String getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public long getReceivedMillis() {
        return receivedMillis;
    }

    /** Amount the parser found, or 0 if the message did not look like a transaction. */
    public long getAmountCents() {
        return amountCents;
    }

    public boolean hasAmount() {
        return amountCents > 0;
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

    public Status getStatus() {
        return status;
    }
}
