package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.PendingSms;
import com.localspend.model.TransactionType;
import com.localspend.sms.ParsedTransaction;
import com.localspend.sms.SenderDirectory;
import com.localspend.sms.SmsParser;
import com.localspend.sms.SmsParserChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the on-device SQLite database. The file lives in the app's private storage, so no other
 * app can read it and nothing is sent anywhere (local-first).
 *
 * Schema history:
 * 1. transactions and learned merchant rules.
 * 2. the original SMS (sender, text, arrival time) on each transaction; a flag for categories the
 *    user chose; user-editable categories with budgets; user keywords.
 * 3. a review queue for messages from senders that are not recognised banks, and the senders
 *    the user trusts or blocks. Existing SMS transactions are re-checked once (see
 *    {@link #recheckSmsTransactions}).
 * 4. a currency on every transaction, review item and category budget; the built-in "Bank
 *    Transactions" category. Stored messages are re-read once to pick up their currency and
 *    better merchant names (see {@link #refreshFromStoredMessages}).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "localspend.db";
    private static final int DB_VERSION = 12;

    public static final String TABLE_TRANSACTIONS = "transactions";
    public static final String COL_ID = "id";
    public static final String COL_AMOUNT = "amount_cents";
    public static final String COL_MERCHANT = "merchant";
    public static final String COL_DATE = "date_millis";
    public static final String COL_TYPE = "type";
    public static final String COL_CATEGORY = "category";
    public static final String COL_SOURCE = "source";
    public static final String COL_SMS_KEY = "sms_key";
    public static final String COL_SMS_SENDER = "sms_sender";
    public static final String COL_SMS_BODY = "sms_body";
    public static final String COL_SMS_RECEIVED = "sms_received_millis";
    public static final String COL_CATEGORY_BY_USER = "category_by_user";
    public static final String COL_CURRENCY = "currency";
    // How the amount was converted into the main currency
    public static final String COL_FX_TO = "fx_to";
    public static final String COL_FX_RATE = "fx_rate";
    public static final String COL_FX_DAY = "fx_day";
    public static final String COL_FX_ESTIMATED = "fx_estimated";
    public static final String COL_FX_CENTS = "fx_cents";

    /** Exchange rates the user entered: 1 from = rate to, for one day. */
    public static final String TABLE_FX_RATES = "fx_rates";
    public static final String COL_FXR_FROM = "from_currency";
    public static final String COL_FXR_TO = "to_currency";
    public static final String COL_FXR_DAY = "day_millis";
    public static final String COL_FXR_RATE = "rate";

    /** Merchants the user re-categorised: merchant key to category key. */
    public static final String TABLE_RULES = "merchant_rules";
    public static final String COL_RULE_KEY = "merchant_key";
    public static final String COL_RULE_CATEGORY = "category";

    public static final String TABLE_CATEGORIES = "categories";
    public static final String COL_CAT_KEY = "key";
    public static final String COL_CAT_LABEL = "label";
    public static final String COL_CAT_COLOR = "color";
    public static final String COL_CAT_BUILT_IN = "built_in";
    public static final String COL_CAT_BUDGET = "budget_cents";
    public static final String COL_CAT_ORDER = "sort_order";
    public static final String COL_CAT_BUDGET_CURRENCY = "budget_currency";

    /** Keywords the user added to a category: keyword to category key. */
    public static final String TABLE_KEYWORDS = "category_keywords";
    public static final String COL_KW_KEYWORD = "keyword";
    public static final String COL_KW_CATEGORY = "category";

    /** Messages waiting for the user to confirm (or already accepted / ignored). */
    public static final String TABLE_REVIEW = "sms_review";
    public static final String COL_RQ_SMS_KEY = "sms_key";
    public static final String COL_RQ_SENDER = "sender";
    public static final String COL_RQ_BODY = "body";
    public static final String COL_RQ_RECEIVED = "received_millis";
    public static final String COL_RQ_AMOUNT = "amount_cents";
    public static final String COL_RQ_MERCHANT = "merchant";
    public static final String COL_RQ_TYPE = "type";
    public static final String COL_RQ_DATE = "date_millis";
    public static final String COL_RQ_STATUS = "status";
    public static final String COL_RQ_CURRENCY = "currency";

    /** Senders the user trusts or blocks, keyed by normalised sender. */
    public static final String TABLE_SENDERS = "sms_senders";
    public static final String COL_SND_KEY = "sender_key";
    public static final String COL_SND_LABEL = "label";
    public static final String COL_SND_TRUST = "trust";

    /** Repeating payments and instalment plans. */
    public static final String TABLE_RECURRING = "recurring";
    public static final String COL_RC_MERCHANT = "merchant";
    public static final String COL_RC_AMOUNT = "amount_cents";
    public static final String COL_RC_CURRENCY = "currency";
    public static final String COL_RC_TYPE = "type";
    public static final String COL_RC_CATEGORY = "category";
    public static final String COL_RC_FREQUENCY = "frequency";
    public static final String COL_RC_INSTALLMENT = "installment";
    public static final String COL_RC_TOTAL = "total_payments";
    public static final String COL_RC_ANCHOR = "anchor_day";
    public static final String COL_RC_NEXT_DUE = "next_due_millis";
    public static final String COL_RC_MADE = "payments_made";
    public static final String COL_RC_ACTIVE = "active";

    private static DatabaseHelper instance;

    /** Set when an upgrade re-read stored messages, so the app re-runs categorisation once. */
    private volatile boolean needsRecategorise = false;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_TRANSACTIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_AMOUNT + " INTEGER NOT NULL, "
                + COL_MERCHANT + " TEXT NOT NULL, "
                + COL_DATE + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_CATEGORY + " TEXT NOT NULL, "
                + COL_SOURCE + " TEXT NOT NULL, "
                + COL_SMS_KEY + " TEXT UNIQUE, "
                + COL_SMS_SENDER + " TEXT, "
                + COL_SMS_BODY + " TEXT, "
                + COL_SMS_RECEIVED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CATEGORY_BY_USER + " INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_transactions_date ON " + TABLE_TRANSACTIONS
                + " (" + COL_DATE + ")");
        db.execSQL("CREATE TABLE " + TABLE_RULES + " ("
                + COL_RULE_KEY + " TEXT PRIMARY KEY, "
                + COL_RULE_CATEGORY + " TEXT NOT NULL)");
        createVersion2Tables(db);
        createVersion3Tables(db);
        addVersion4Columns(db);
        createVersion6Tables(db);
        addVersion8(db);
        createBudgetHistory(db);
    }

    public static final String TABLE_BUDGET_HISTORY = "budget_history";

    private static void createBudgetHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BUDGET_HISTORY + " ("
                + "budget_key TEXT NOT NULL, "
                + "from_month INTEGER NOT NULL, "
                + "cents INTEGER NOT NULL, "
                + "PRIMARY KEY (budget_key, from_month))");
    }

    /** v8: the exchange behind each converted amount, and the user's saved rates. */
    private static void addVersion8(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_FX_TO + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_FX_RATE + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_FX_DAY
                + " INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_FX_ESTIMATED
                + " INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_FX_CENTS
                + " INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FX_RATES + " ("
                + COL_FXR_FROM + " TEXT NOT NULL, "
                + COL_FXR_TO + " TEXT NOT NULL, "
                + COL_FXR_DAY + " INTEGER NOT NULL, "
                + COL_FXR_RATE + " TEXT NOT NULL, "
                + "PRIMARY KEY (" + COL_FXR_FROM + ", " + COL_FXR_TO + ", " + COL_FXR_DAY + "))");
    }

    private void createVersion6Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_RECURRING + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_RC_MERCHANT + " TEXT NOT NULL, "
                + COL_RC_AMOUNT + " INTEGER NOT NULL, "
                + COL_RC_CURRENCY + " TEXT NOT NULL, "
                + COL_RC_TYPE + " TEXT NOT NULL, "
                + COL_RC_CATEGORY + " TEXT NOT NULL, "
                + COL_RC_FREQUENCY + " TEXT NOT NULL, "
                + COL_RC_INSTALLMENT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RC_TOTAL + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RC_ANCHOR + " INTEGER NOT NULL DEFAULT 1, "
                + COL_RC_NEXT_DUE + " INTEGER NOT NULL, "
                + COL_RC_MADE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RC_ACTIVE + " INTEGER NOT NULL DEFAULT 1)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migrations only add things, so existing transactions and corrections are kept.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_SMS_SENDER + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_SMS_BODY + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_SMS_RECEIVED
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_CATEGORY_BY_USER
                    + " INTEGER NOT NULL DEFAULT 0");
            createVersion2Tables(db);
        }
        // Schema first (every version), then data fixes, so the fixes can use every column.
        if (oldVersion < 3) {
            createVersion3Tables(db);
        }
        if (oldVersion < 4) {
            addVersion4Columns(db);
            seedDefaultCategories(db);   // adds "Bank Transactions"
        }
        if (oldVersion < 3) {
            recheckSmsTransactions(db);
        }
        if (oldVersion < 4) {
            refreshFromStoredMessages(db);
        }
        // v5: more built-in merchants (Popeyes and other chains). No schema change; transactions
        // the user has not categorised themselves are re-checked against the larger list.
        if (oldVersion < 5) {
            needsRecategorise = true;
        }
        // v6: repeating payments and instalment plans (a new table only).
        if (oldVersion < 6) {
            createVersion6Tables(db);
        }
        // v7: categories are now also read from the whole stored SMS, so re-check them once.
        if (oldVersion < 7) {
            needsRecategorise = true;
        }
        // v8: currency conversion (new columns and a table of saved rates).
        if (oldVersion < 8) {
            addVersion8(db);
        }
        // v9: more built-in keywords (mobile top-ups, bank names), so re-check categories once.
        if (oldVersion < 11) {
            needsRecategorise = true;
        }
        // v12: budgets that change over time.
        if (oldVersion < 12) {
            createBudgetHistory(db);
        }
    }

    /**
     * True once after an upgrade that re-read stored messages; the caller should then re-run
     * categorisation with the user's rules. Opens (and if needed upgrades) the database.
     */
    public boolean consumeRecategoriseFlag() {
        getWritableDatabase();
        boolean flag = needsRecategorise;
        needsRecategorise = false;
        return flag;
    }

    private static void addVersion4Columns(SQLiteDatabase db) {
        String aud = "'" + Currency.defaultCurrency().name() + "'";
        db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_CURRENCY
                + " TEXT NOT NULL DEFAULT " + aud);
        db.execSQL("ALTER TABLE " + TABLE_REVIEW + " ADD COLUMN " + COL_RQ_CURRENCY
                + " TEXT NOT NULL DEFAULT " + aud);
        db.execSQL("ALTER TABLE " + TABLE_CATEGORIES + " ADD COLUMN " + COL_CAT_BUDGET_CURRENCY
                + " TEXT NOT NULL DEFAULT " + aud);
    }

    /**
     * Re-reads every stored SMS with the current parser so older data gets the right currency
     * ("INR 1,999.00" is rupees, not dollars) and better merchant names ("UPI-...-HENNES").
     * The currency is always corrected; merchant, amount, date and type only where the user has
     * not chosen the category themselves. Review items are refreshed the same way.
     */
    private static void refreshFromStoredMessages(SQLiteDatabase db) {
        SmsParser parser = SmsParserChain.defaultChain();

        List<long[]> txIds = new ArrayList<>();         // {id, receivedMillis, byUser}
        List<String> txBodies = new ArrayList<>();
        try (Cursor c = db.query(TABLE_TRANSACTIONS, new String[]{COL_ID, COL_SMS_BODY,
                        COL_SMS_RECEIVED, COL_DATE, COL_CATEGORY_BY_USER},
                COL_SMS_BODY + " IS NOT NULL", null, null, null, null)) {
            while (c.moveToNext()) {
                long received = c.getLong(2) > 0 ? c.getLong(2) : c.getLong(3);
                txIds.add(new long[]{c.getLong(0), received, c.getInt(4)});
                txBodies.add(c.getString(1));
            }
        }
        for (int i = 0; i < txIds.size(); i++) {
            long[] row = txIds.get(i);
            ParsedTransaction parsed = parser.parse(txBodies.get(i), row[1]);
            if (parsed == null) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put(COL_CURRENCY, parsed.getCurrency().name());
            if (row[2] == 0) {
                v.put(COL_AMOUNT, parsed.getAmountCents());
                v.put(COL_MERCHANT, parsed.getMerchant());
                v.put(COL_DATE, parsed.getDateMillis());
                v.put(COL_TYPE, parsed.getType().name());
            }
            db.update(TABLE_TRANSACTIONS, v, COL_ID + " = ?", new String[]{String.valueOf(row[0])});
        }

        List<long[]> rqIds = new ArrayList<>();
        List<String> rqBodies = new ArrayList<>();
        try (Cursor c = db.query(TABLE_REVIEW, new String[]{COL_ID, COL_RQ_BODY, COL_RQ_RECEIVED},
                COL_RQ_BODY + " IS NOT NULL", null, null, null, null)) {
            while (c.moveToNext()) {
                rqIds.add(new long[]{c.getLong(0), c.getLong(2)});
                rqBodies.add(c.getString(1));
            }
        }
        for (int i = 0; i < rqIds.size(); i++) {
            ParsedTransaction parsed = parser.parse(rqBodies.get(i), rqIds.get(i)[1]);
            if (parsed == null) {
                continue;
            }
            ContentValues v = new ContentValues();
            v.put(COL_RQ_CURRENCY, parsed.getCurrency().name());
            v.put(COL_RQ_AMOUNT, parsed.getAmountCents());
            v.put(COL_RQ_MERCHANT, parsed.getMerchant());
            v.put(COL_RQ_DATE, parsed.getDateMillis());
            v.put(COL_RQ_TYPE, parsed.getType().name());
            db.update(TABLE_REVIEW, v, COL_ID + " = ?", new String[]{String.valueOf(rqIds.get(i)[0])});
        }
    }

    private static void createVersion3Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REVIEW + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_RQ_SMS_KEY + " TEXT UNIQUE NOT NULL, "
                + COL_RQ_SENDER + " TEXT, "
                + COL_RQ_BODY + " TEXT, "
                + COL_RQ_RECEIVED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RQ_AMOUNT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RQ_MERCHANT + " TEXT, "
                + COL_RQ_TYPE + " TEXT NOT NULL, "
                + COL_RQ_DATE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_RQ_STATUS + " TEXT NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_SENDERS + " ("
                + COL_SND_KEY + " TEXT PRIMARY KEY, "
                + COL_SND_LABEL + " TEXT, "
                + COL_SND_TRUST + " TEXT NOT NULL)");
    }

    /**
     * Earlier versions accepted transaction-looking messages from any sender, including adverts.
     * Re-reads every SMS transaction whose original text is stored and whose category the user
     * has not confirmed:
     * - adverts and non-transactions are removed (kept in the review list as "ignored", so they
     *   can be added back),
     * - messages from senders that are not recognised banks move to the review list,
     * - the rest are kept, with merchant, amount and date re-read by the improved parser.
     */
    private static void recheckSmsTransactions(SQLiteDatabase db) {
        // Read everything first: changing the table while a cursor is open on it is unsafe.
        List<PendingSms> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db.query(TABLE_TRANSACTIONS, null,
                COL_SOURCE + " = 'SMS' AND " + COL_SMS_BODY + " IS NOT NULL AND "
                        + COL_CATEGORY_BY_USER + " = 0 AND " + COL_SMS_KEY + " IS NOT NULL",
                null, null, null, null)) {
            while (c.moveToNext()) {
                long received = c.getLong(c.getColumnIndexOrThrow(COL_SMS_RECEIVED));
                if (received <= 0) {
                    received = c.getLong(c.getColumnIndexOrThrow(COL_DATE));
                }
                ids.add(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
                rows.add(new PendingSms(0,
                        c.getString(c.getColumnIndexOrThrow(COL_SMS_KEY)),
                        c.getString(c.getColumnIndexOrThrow(COL_SMS_SENDER)),
                        c.getString(c.getColumnIndexOrThrow(COL_SMS_BODY)),
                        received,
                        c.getLong(c.getColumnIndexOrThrow(COL_AMOUNT)),
                        c.getString(c.getColumnIndexOrThrow(COL_MERCHANT)),
                        TransactionType.fromName(c.getString(c.getColumnIndexOrThrow(COL_TYPE))),
                        c.getLong(c.getColumnIndexOrThrow(COL_DATE)),
                        PendingSms.Status.PENDING, Currency.defaultCurrency()));
            }
        }

        SmsParser parser = SmsParserChain.defaultChain();
        for (int i = 0; i < rows.size(); i++) {
            PendingSms row = rows.get(i);
            String[] idArg = {String.valueOf(ids.get(i))};
            ParsedTransaction parsed = parser.parse(row.getBody(), row.getReceivedMillis());
            if (parsed == null) {
                // An advert or other non-transaction: keep it only as an ignored review item.
                SmsReviewRepository.insert(db, new PendingSms(0, row.getSmsKey(), row.getSender(),
                        row.getBody(), row.getReceivedMillis(), row.getAmountCents(),
                        row.getMerchant(), row.getType(), row.getDateMillis(),
                        PendingSms.Status.IGNORED, row.getCurrency()));
                db.delete(TABLE_TRANSACTIONS, COL_ID + " = ?", idArg);
            } else if (!SenderDirectory.isKnownBank(row.getSender())) {
                SmsReviewRepository.insert(db, new PendingSms(0, row.getSmsKey(), row.getSender(),
                        row.getBody(), row.getReceivedMillis(), parsed.getAmountCents(),
                        parsed.getMerchant(), parsed.getType(), parsed.getDateMillis(),
                        PendingSms.Status.PENDING, parsed.getCurrency()));
                db.delete(TABLE_TRANSACTIONS, COL_ID + " = ?", idArg);
            } else {
                ContentValues v = new ContentValues();
                v.put(COL_AMOUNT, parsed.getAmountCents());
                v.put(COL_MERCHANT, parsed.getMerchant());
                v.put(COL_DATE, parsed.getDateMillis());
                v.put(COL_TYPE, parsed.getType().name());
                v.put(COL_CURRENCY, parsed.getCurrency().name());
                db.update(TABLE_TRANSACTIONS, v, COL_ID + " = ?", idArg);
            }
        }
    }

    private static void createVersion2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CATEGORIES + " ("
                + COL_CAT_KEY + " TEXT PRIMARY KEY, "
                + COL_CAT_LABEL + " TEXT NOT NULL, "
                + COL_CAT_COLOR + " INTEGER NOT NULL, "
                + COL_CAT_BUILT_IN + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CAT_BUDGET + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CAT_ORDER + " INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE " + TABLE_KEYWORDS + " ("
                + COL_KW_KEYWORD + " TEXT PRIMARY KEY, "
                + COL_KW_CATEGORY + " TEXT NOT NULL)");
        seedDefaultCategories(db);
    }

    /** Inserts any built-in category that is missing. Existing (possibly renamed) ones are kept. */
    static void seedDefaultCategories(SQLiteDatabase db) {
        List<Category> defaults = Category.defaults();
        for (int i = 0; i < defaults.size(); i++) {
            Category c = defaults.get(i);
            ContentValues v = new ContentValues();
            v.put(COL_CAT_KEY, c.getKey());
            v.put(COL_CAT_LABEL, c.getLabel());
            v.put(COL_CAT_COLOR, c.getColor());
            v.put(COL_CAT_BUILT_IN, 1);
            v.put(COL_CAT_BUDGET, 0);
            v.put(COL_CAT_ORDER, c.isOther() ? Integer.MAX_VALUE : i);
            db.insertWithOnConflict(TABLE_CATEGORIES, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }
}
