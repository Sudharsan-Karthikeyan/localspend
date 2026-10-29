package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.model.Currency;
import com.localspend.model.PendingSms;
import com.localspend.model.TransactionType;
import com.localspend.sms.SenderDirectory;
import com.localspend.sms.SenderVerifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Messages waiting for the user to confirm, and the senders the user has chosen to trust or
 * block. Call from a background thread.
 */
public class SmsReviewRepository {

    /** A sender the user trusts or blocks. */
    public static final class SenderRule {
        public final String key;
        public final String label;
        public final boolean trusted;

        SenderRule(String key, String label, boolean trusted) {
            this.key = key;
            this.label = label;
            this.trusted = trusted;
        }
    }

    static final String TRUST_TRUSTED = "TRUSTED";
    static final String TRUST_BLOCKED = "BLOCKED";

    private static SmsReviewRepository instance;
    private final DatabaseHelper helper;

    public static synchronized SmsReviewRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SmsReviewRepository(DatabaseHelper.getInstance(context));
        }
        return instance;
    }

    private SmsReviewRepository(DatabaseHelper helper) {
        this.helper = helper;
    }

    // ---- Review queue -------------------------------------------------------------------------

    /** Adds a message to the queue. Returns false if this message was already queued before. */
    public boolean add(PendingSms p) {
        return insert(helper.getWritableDatabase(), p) != -1;
    }

    static long insert(SQLiteDatabase db, PendingSms p) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_RQ_SMS_KEY, p.getSmsKey());
        v.put(DatabaseHelper.COL_RQ_SENDER, p.getSender());
        v.put(DatabaseHelper.COL_RQ_BODY, p.getBody());
        v.put(DatabaseHelper.COL_RQ_RECEIVED, p.getReceivedMillis());
        v.put(DatabaseHelper.COL_RQ_AMOUNT, p.getAmountCents());
        v.put(DatabaseHelper.COL_RQ_MERCHANT, p.getMerchant());
        v.put(DatabaseHelper.COL_RQ_TYPE, p.getType().name());
        v.put(DatabaseHelper.COL_RQ_DATE, p.getDateMillis());
        v.put(DatabaseHelper.COL_RQ_STATUS, p.getStatus().name());
        v.put(DatabaseHelper.COL_RQ_CURRENCY, p.getCurrency().name());
        return db.insertWithOnConflict(DatabaseHelper.TABLE_REVIEW, null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** True if this message has been queued before (whatever the user decided). */
    public boolean contains(String smsKey) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT 1 FROM "
                + DatabaseHelper.TABLE_REVIEW + " WHERE " + DatabaseHelper.COL_RQ_SMS_KEY + " = ?",
                new String[]{smsKey})) {
            return c.moveToFirst();
        }
    }

    public List<PendingSms> getByStatus(PendingSms.Status status) {
        List<PendingSms> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DatabaseHelper.TABLE_REVIEW, null,
                DatabaseHelper.COL_RQ_STATUS + " = ?", new String[]{status.name()}, null, null,
                DatabaseHelper.COL_RQ_RECEIVED + " DESC")) {
            while (c.moveToNext()) {
                out.add(fromCursor(c));
            }
        }
        return out;
    }

    public int countPending() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "
                + DatabaseHelper.TABLE_REVIEW + " WHERE " + DatabaseHelper.COL_RQ_STATUS + " = ?",
                new String[]{PendingSms.Status.PENDING.name()})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Pending messages from the same sender as this one. */
    public List<PendingSms> getPendingFrom(String sender) {
        String key = SenderDirectory.normalise(sender);
        List<PendingSms> out = new ArrayList<>();
        for (PendingSms p : getByStatus(PendingSms.Status.PENDING)) {
            if (SenderDirectory.normalise(p.getSender()).equals(key)) {
                out.add(p);
            }
        }
        return out;
    }

    public void setStatus(long id, PendingSms.Status status) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_RQ_STATUS, status.name());
        helper.getWritableDatabase().update(DatabaseHelper.TABLE_REVIEW, v,
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    private static PendingSms fromCursor(Cursor c) {
        return new PendingSms(
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_SMS_KEY)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_SENDER)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_BODY)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_RECEIVED)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_AMOUNT)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_MERCHANT)),
                TransactionType.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_TYPE))),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_DATE)),
                PendingSms.Status.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_STATUS))),
                Currency.fromCode(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RQ_CURRENCY))));
    }

    // ---- Trusted and blocked senders ----------------------------------------------------------

    public void trustSender(String sender) {
        setSender(sender, TRUST_TRUSTED);
    }

    public void blockSender(String sender) {
        setSender(sender, TRUST_BLOCKED);
    }

    private void setSender(String sender, String trust) {
        String key = SenderDirectory.normalise(sender);
        if (key.isEmpty()) {
            return;
        }
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_SND_KEY, key);
        v.put(DatabaseHelper.COL_SND_LABEL, sender.trim());
        v.put(DatabaseHelper.COL_SND_TRUST, trust);
        helper.getWritableDatabase().insertWithOnConflict(DatabaseHelper.TABLE_SENDERS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Forget the user's choice for this sender (it goes back to the built-in rules). */
    public void removeSender(String key) {
        helper.getWritableDatabase().delete(DatabaseHelper.TABLE_SENDERS,
                DatabaseHelper.COL_SND_KEY + " = ?", new String[]{key});
    }

    public List<SenderRule> getSenderRules() {
        List<SenderRule> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DatabaseHelper.TABLE_SENDERS, null,
                null, null, null, null, DatabaseHelper.COL_SND_TRUST + " DESC, "
                        + DatabaseHelper.COL_SND_LABEL + " ASC")) {
            while (c.moveToNext()) {
                out.add(new SenderRule(
                        c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SND_KEY)),
                        c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SND_LABEL)),
                        TRUST_TRUSTED.equals(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SND_TRUST)))));
            }
        }
        return out;
    }

    /** A verifier using the user's current choices plus the built-in bank list. */
    public SenderVerifier buildVerifier() {
        Set<String> trusted = new HashSet<>();
        Set<String> blocked = new HashSet<>();
        for (SenderRule r : getSenderRules()) {
            (r.trusted ? trusted : blocked).add(r.key);
        }
        return new SenderVerifier(trusted, blocked);
    }
}
