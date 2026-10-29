package com.localspend.model;

import java.math.BigDecimal;

/**
 * A single money movement. Amounts are stored as whole cents in a long to avoid floating-point
 * rounding errors. All fields are private and validated by the setters (encapsulation).
 *
 * For transactions captured from an SMS, the complete original message (sender and text) is kept
 * so the user can always see exactly what the amount, merchant and date were extracted from.
 */
public class Transaction {

    public static final long NO_ID = -1;

    /** Merchant name used when a message gave no merchant details. */
    public static final String UNKNOWN_MERCHANT = "Unknown merchant";

    private long id = NO_ID;
    private long amountCents;
    private String merchant;
    private long dateMillis;
    private TransactionType type;
    private Category category;
    private Source source;
    private Currency currency = Currency.defaultCurrency();
    private String smsKey;        // de-duplication fingerprint, null for manual entries
    private String smsSender;     // who sent the SMS, null for manual entries
    private String smsBody;       // full original SMS text, null for manual entries
    private long smsReceivedMillis;   // when the SMS arrived, 0 for manual entries
    private boolean categoryChosenByUser;  // true once the user has picked the category

    // How this amount was converted into the app's main currency (all null/0 when it was not).
    private Currency fxTo;
    private BigDecimal fxRate;       // 1 unit of {@code currency} = fxRate units of fxTo
    private long fxRateDay;          // the day the rate is for
    private boolean fxEstimated;     // true unless the user gave the rate for this very day
    private long fxCents;            // the amount in fxTo

    public Transaction(long amountCents, String merchant, long dateMillis,
                       TransactionType type, Category category, Source source) {
        setAmountCents(amountCents);
        setMerchant(merchant);
        this.dateMillis = dateMillis;
        setType(type);
        setCategory(category);
        setSource(source);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        this.amountCents = amountCents;
        clearConversion();   // a stored conversion belongs to the old amount
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant must not be blank");
        }
        this.merchant = merchant.trim();
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public void setDateMillis(long dateMillis) {
        this.dateMillis = dateMillis;
        clearConversion();   // the rate depends on the day
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type == null ? TransactionType.EXPENSE : type;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category == null ? Category.OTHER : category;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source == null ? Source.MANUAL : source;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
        clearConversion();
    }

    /** Records how the amount was converted into another currency. */
    public void setConversion(Currency to, BigDecimal rate, long rateDay, boolean estimated,
                              long convertedCents) {
        if (to == null || rate == null || to == currency || convertedCents <= 0) {
            clearConversion();
            return;
        }
        this.fxTo = to;
        this.fxRate = rate;
        this.fxRateDay = rateDay;
        this.fxEstimated = estimated;
        this.fxCents = convertedCents;
    }

    public void clearConversion() {
        fxTo = null;
        fxRate = null;
        fxRateDay = 0;
        fxEstimated = false;
        fxCents = 0;
    }

    public boolean hasConversion() {
        return fxTo != null;
    }

    public Currency getConvertedCurrency() {
        return fxTo;
    }

    public BigDecimal getConversionRate() {
        return fxRate;
    }

    public long getConversionRateDay() {
        return fxRateDay;
    }

    public boolean isConversionEstimated() {
        return fxEstimated;
    }

    public long getConvertedCents() {
        return fxCents;
    }

    /** True when this transaction can be counted in {@code main}: it is in it, or converted to it. */
    public boolean canBeShownIn(Currency main) {
        return currency == main || (fxTo == main && fxCents > 0);
    }

    /** The amount in {@code main}; only meaningful when {@link #canBeShownIn} is true. */
    public long getAmountIn(Currency main) {
        return currency == main ? amountCents : fxCents;
    }

    /**
     * A copy whose amount and currency are those of {@code main}, for adding up. Returns this
     * transaction itself when it is already in {@code main}.
     */
    public Transaction asIn(Currency main) {
        return asIn(main, fxCents);
    }

    /**
     * Like {@link #asIn(Currency)} but with the converted amount chosen by the caller, so a list
     * can be rounded as a whole (see SpendingAnalyzer.inCurrency). The amount may be 0 when the
     * converted value is a fraction of a cent.
     */
    public Transaction asIn(Currency main, long convertedCents) {
        if (currency == main) {
            return this;
        }
        Transaction copy = new Transaction(Math.max(1, convertedCents), merchant, dateMillis, type,
                category, source);
        copy.amountCents = Math.max(0, convertedCents);
        copy.setId(id);
        copy.setCurrency(main);
        return copy;
    }

    /** True when the message did not say who was paid, so there is no merchant to categorise. */
    public boolean hasUnknownMerchant() {
        return UNKNOWN_MERCHANT.equalsIgnoreCase(merchant);
    }

    public String getSmsKey() {
        return smsKey;
    }

    public void setSmsKey(String smsKey) {
        this.smsKey = smsKey;
    }

    public String getSmsSender() {
        return smsSender;
    }

    public String getSmsBody() {
        return smsBody;
    }

    public long getSmsReceivedMillis() {
        return smsReceivedMillis;
    }

    /** Attaches the original message this transaction was read from. */
    public void setOriginalSms(String sender, String body, long receivedMillis) {
        this.smsSender = sender;
        this.smsBody = body;
        this.smsReceivedMillis = receivedMillis;
    }

    public boolean hasOriginalSms() {
        return smsBody != null && !smsBody.isEmpty();
    }

    public boolean isCategoryChosenByUser() {
        return categoryChosenByUser;
    }

    /** Marks the category as the user's own choice, so automatic re-categorising leaves it alone. */
    public void setCategoryChosenByUser(boolean chosen) {
        this.categoryChosenByUser = chosen;
    }

    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }
}
