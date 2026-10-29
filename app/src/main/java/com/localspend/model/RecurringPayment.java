package com.localspend.model;

import java.util.Calendar;

/**
 * A payment that repeats on a schedule, or an instalment plan (a fixed number of payments).
 *
 * The first payment is recorded as an ordinary transaction when the plan is created; the plan then
 * holds the date of the next one. {@code totalPayments == 0} means it repeats until stopped;
 * otherwise it ends after that many payments in all (counting the first).
 */
public class RecurringPayment {

    public static final long NO_ID = -1;

    private long id = NO_ID;
    private final String merchant;
    private final long amountCents;
    private final Currency currency;
    private final TransactionType type;
    private final Category category;
    private final Frequency frequency;
    private final boolean installment;
    private final int totalPayments;
    private final int anchorDay;
    private long nextDueMillis;
    private int paymentsMade;
    private boolean active;

    public RecurringPayment(String merchant, long amountCents, Currency currency,
                            TransactionType type, Category category, Frequency frequency,
                            boolean installment, int totalPayments, int anchorDay,
                            long nextDueMillis, int paymentsMade, boolean active) {
        this.merchant = merchant;
        this.amountCents = amountCents;
        this.currency = currency == null ? Currency.defaultCurrency() : currency;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.category = category == null ? Category.OTHER : category;
        this.frequency = frequency == null ? Frequency.MONTHLY : frequency;
        this.installment = installment;
        this.totalPayments = Math.max(totalPayments, 0);
        this.anchorDay = anchorDay;
        this.nextDueMillis = nextDueMillis;
        this.paymentsMade = paymentsMade;
        this.active = active;
    }

    /**
     * Plan for a payment that has just been recorded as its first occurrence, so the next one is
     * one step later.
     */
    public static RecurringPayment startingAfter(Transaction first, Frequency frequency,
                                                 boolean installment, int totalPayments) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(first.getDateMillis());
        int anchor = c.get(Calendar.DAY_OF_MONTH);
        long next = frequency.next(first.getDateMillis(), anchor);
        boolean finished = totalPayments > 0 && totalPayments <= 1;
        return new RecurringPayment(first.getMerchant(), first.getAmountCents(),
                first.getCurrency(), first.getType(), first.getCategory(), frequency, installment,
                totalPayments, anchor, next, 1, !finished);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMerchant() {
        return merchant;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TransactionType getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public boolean isInstallment() {
        return installment;
    }

    /** Payments in the whole plan, or 0 when it repeats until stopped. */
    public int getTotalPayments() {
        return totalPayments;
    }

    public int getAnchorDay() {
        return anchorDay;
    }

    public long getNextDueMillis() {
        return nextDueMillis;
    }

    public void setNextDueMillis(long nextDueMillis) {
        this.nextDueMillis = nextDueMillis;
    }

    public int getPaymentsMade() {
        return paymentsMade;
    }

    public void setPaymentsMade(int paymentsMade) {
        this.paymentsMade = paymentsMade;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** True when the plan has a fixed length and every payment has been made. */
    public boolean isFinished() {
        return totalPayments > 0 && paymentsMade >= totalPayments;
    }
}
