package com.localspend.sms;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.SmsReviewRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.CategorisationEngine;
import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.PendingSms;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import java.util.List;

/**
 * Connects the Android SMS inbox to the rest of the pipeline:
 * capture (SMS) -> verify sender -> extract (SmsParser) -> categorise -> store.
 *
 * Only messages from senders that can be trusted are added automatically: recognised bank sender
 * IDs, plus senders the user has chosen to trust. A transaction-looking message from anyone else
 * (a phone number, a shop, a marketing service) goes to the review list, where the user can add it
 * or ignore it, and optionally trust or block that sender. Messages from blocked senders and
 * adverts are ignored.
 *
 * Everything happens on the device. The complete original message is stored with the
 * transaction (unless switched off in Settings), and a hash of each message prevents the same
 * message being imported twice. Call from a background thread.
 */
public class SmsImporter {

    /** Outcome of an inbox scan. */
    public static final class Result {
        public final int scanned;
        public final int added;
        public final int queued;
        public final boolean permissionDenied;

        Result(int scanned, int added, int queued, boolean permissionDenied) {
            this.scanned = scanned;
            this.added = added;
            this.queued = queued;
            this.permissionDenied = permissionDenied;
        }
    }

    /** What happened to one message. */
    public static final class Outcome {
        public enum Kind {
            /** Trusted sender: the transaction was stored. */
            ADDED,
            /** Unknown sender: held for the user to review. */
            QUEUED,
            /** Not a transaction, a duplicate, or a blocked sender. */
            SKIPPED
        }

        public final Kind kind;
        public final Transaction transaction;   // set when ADDED
        public final PendingSms pending;        // set when QUEUED

        private Outcome(Kind kind, Transaction transaction, PendingSms pending) {
            this.kind = kind;
            this.transaction = transaction;
            this.pending = pending;
        }

        static final Outcome SKIP = new Outcome(Kind.SKIPPED, null, null);
    }

    private final Context context;
    private final TransactionRepository repository;
    private final SmsReviewRepository review;
    private final SmsParser parser;
    private final CategorisationEngine engine;
    private final Category incomeCategory;
    private final boolean storeText;
    private SenderVerifier verifier;

    public SmsImporter(Context context, TransactionRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.review = SmsReviewRepository.getInstance(this.context);
        this.parser = SmsParserChain.defaultChain();
        AppSettings settings = new AppSettings(this.context);
        CategoryRepository categories = CategoryRepository.getInstance(this.context);
        this.engine = categories.buildEngine(settings.isBuiltInKeywordsOn());
        this.incomeCategory = categories.getCatalog().getOther();
        this.storeText = settings.isStoreSmsTextOn();
        this.verifier = review.buildVerifier();
    }

    /** Scans inbox messages received at or after {@code sinceMillis}. Safe to run repeatedly. */
    public Result importFromInbox(long sinceMillis) {
        int scanned = 0;
        int added = 0;
        int queued = 0;
        try (Cursor c = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                Telephony.Sms.DATE + " >= ?",
                new String[]{String.valueOf(sinceMillis)},
                Telephony.Sms.DATE + " ASC")) {
            if (c == null) {
                return new Result(0, 0, 0, false);
            }
            int addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            int bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY);
            int dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE);
            while (c.moveToNext()) {
                scanned++;
                Outcome o = importMessage(c.getString(addressIdx), c.getString(bodyIdx), c.getLong(dateIdx));
                if (o.kind == Outcome.Kind.ADDED) {
                    added++;
                } else if (o.kind == Outcome.Kind.QUEUED) {
                    queued++;
                }
            }
        } catch (SecurityException e) {
            return new Result(0, 0, 0, true);
        }
        return new Result(scanned, added, queued, false);
    }

    /** Handles one message: adds it, queues it for review, or skips it. */
    public Outcome importMessage(String sender, String body, long timestampMillis) {
        ParsedTransaction parsed = parser.parse(body, timestampMillis);
        if (parsed == null) {
            return Outcome.SKIP;   // not a transaction, or an advert
        }
        String key = SmsKeys.keyFor(sender, body, timestampMillis);
        if (repository.existsSmsKey(key)) {
            // Already imported. If it was imported before messages were kept, add the text now.
            if (storeText) {
                repository.backfillOriginalSms(key, sender, body, timestampMillis);
            }
            return Outcome.SKIP;
        }
        SenderVerifier.Verdict verdict = verifier.verdict(sender);
        if (verdict == SenderVerifier.Verdict.BLOCKED || review.contains(key)) {
            return Outcome.SKIP;   // blocked, or the user has already seen this message
        }
        if (verdict == SenderVerifier.Verdict.UNKNOWN) {
            PendingSms pending = new PendingSms(0, key, sender, body, timestampMillis,
                    parsed.getAmountCents(), parsed.getMerchant(), parsed.getType(),
                    parsed.getDateMillis(), PendingSms.Status.PENDING, parsed.getCurrency());
            return review.add(pending)
                    ? new Outcome(Outcome.Kind.QUEUED, null, pending) : Outcome.SKIP;
        }
        Transaction t = store(key, sender, body, timestampMillis, parsed.getAmountCents(),
                parsed.getCurrency(), parsed.getMerchant(), parsed.getType(), parsed.getDateMillis());
        return t != null ? new Outcome(Outcome.Kind.ADDED, t, null) : Outcome.SKIP;
    }

    /** The user confirmed a message from the review list: store it as a transaction. */
    public Transaction accept(PendingSms p) {
        Transaction t = null;
        if (p.hasAmount() && !repository.existsSmsKey(p.getSmsKey())) {
            String merchant = p.getMerchant() == null || p.getMerchant().trim().isEmpty()
                    ? Transaction.UNKNOWN_MERCHANT : p.getMerchant();
            t = store(p.getSmsKey(), p.getSender(), p.getBody(), p.getReceivedMillis(),
                    p.getAmountCents(), p.getCurrency(), merchant, p.getType(), p.getDateMillis());
        }
        review.setStatus(p.getId(), PendingSms.Status.ACCEPTED);
        return t;
    }

    /** The user said this message is not a transaction. */
    public void ignore(PendingSms p) {
        review.setStatus(p.getId(), PendingSms.Status.IGNORED);
    }

    /**
     * Trusts this sender from now on and adds every message from it still waiting for review.
     *
     * @return how many waiting messages were added
     */
    public int trustSender(String sender) {
        review.trustSender(sender);
        verifier = review.buildVerifier();
        int added = 0;
        List<PendingSms> waiting = review.getPendingFrom(sender);
        for (PendingSms p : waiting) {
            if (accept(p) != null) {
                added++;
            }
        }
        return added;
    }

    /**
     * Ignores this sender from now on, along with every message from it waiting for review.
     *
     * @return how many waiting messages were ignored
     */
    public int blockSender(String sender) {
        review.blockSender(sender);
        verifier = review.buildVerifier();
        List<PendingSms> waiting = review.getPendingFrom(sender);
        for (PendingSms p : waiting) {
            ignore(p);
        }
        return waiting.size();
    }

    private Transaction store(String key, String sender, String body, long receivedMillis,
                              long amountCents, Currency currency, String merchant,
                              TransactionType type, long dateMillis) {
        // Expenses: by merchant, then by the whole message; one that names nothing recognisable and
        // has no merchant goes to "Bank Transactions".
        Category category = type == TransactionType.EXPENSE
                ? engine.categorise(merchant, body) : engine.categoriseIncome(merchant, body);
        Transaction t = new Transaction(amountCents, merchant, dateMillis, type, category, Source.SMS);
        t.setCurrency(currency);
        t.setSmsKey(key);
        if (storeText) {
            t.setOriginalSms(sender, body, receivedMillis);
        }
        return repository.insert(t) != -1 ? t : null;
    }
}
