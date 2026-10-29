package com.localspend.logic;

import com.localspend.model.RecurringPayment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out which payments of a repeating plan have fallen due. Pure logic with no database, so it
 * can be tested directly. If the phone was off for a while, every missed payment is returned so
 * none is lost.
 */
public final class RecurringSchedule {

    /** Safety limit so a daily plan left for years cannot flood the database in one go. */
    public static final int MAX_CATCH_UP = 400;

    private RecurringSchedule() {
    }

    /** The due dates up to now, and where the plan stands afterwards. */
    public static final class Outcome {
        private final List<Long> dueDates;
        private final long nextDueMillis;
        private final int paymentsMade;
        private final boolean active;

        Outcome(List<Long> dueDates, long nextDueMillis, int paymentsMade, boolean active) {
            this.dueDates = Collections.unmodifiableList(dueDates);
            this.nextDueMillis = nextDueMillis;
            this.paymentsMade = paymentsMade;
            this.active = active;
        }

        /** Dates (oldest first) that need a transaction created. */
        public List<Long> getDueDates() {
            return dueDates;
        }

        public long getNextDueMillis() {
            return nextDueMillis;
        }

        public int getPaymentsMade() {
            return paymentsMade;
        }

        public boolean isActive() {
            return active;
        }
    }

    /**
     * The first date on or after {@code nowMillis} that the plan would fall on, skipping any it
     * missed. Used when a paused plan is resumed, so the paused weeks are not charged afterwards.
     */
    public static long nextOnOrAfter(RecurringPayment plan, long nowMillis) {
        long next = plan.getNextDueMillis();
        int guard = 0;
        while (next < nowMillis && guard++ < 5000) {
            next = plan.getFrequency().next(next, plan.getAnchorDay());
        }
        return next;
    }

    /** Payments of {@code plan} due on or before {@code nowMillis}. Does not change the plan. */
    public static Outcome catchUp(RecurringPayment plan, long nowMillis) {
        List<Long> due = new ArrayList<>();
        long next = plan.getNextDueMillis();
        int made = plan.getPaymentsMade();
        boolean active = plan.isActive() && !plan.isFinished();
        int total = plan.getTotalPayments();

        while (active && next <= nowMillis && due.size() < MAX_CATCH_UP) {
            due.add(next);
            made++;
            next = plan.getFrequency().next(next, plan.getAnchorDay());
            if (total > 0 && made >= total) {
                active = false;
            }
        }
        return new Outcome(due, next, made, active);
    }
}
