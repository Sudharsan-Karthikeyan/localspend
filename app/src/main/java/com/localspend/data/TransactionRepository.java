package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.logic.CategorisationEngine;
import com.localspend.logic.MerchantNormaliser;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;
import com.localspend.widget.QuickAddWidget;

import com.localspend.logic.ExchangeRates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * All database reads and writes go through here. The rest of the app only sees Transaction
 * objects, never SQL. Call from a background thread (see Background).
 */
public class TransactionRepository {

    private static TransactionRepository instance;

    private final Context appContext;
    private final DatabaseHelper helper;
    private final CategoryRepository categories;

    public static synchronized TransactionRepository getInstance(Context context) {
        if (instance == null) {
            instance = new TransactionRepository(context.getApplicationContext(),
                    DatabaseHelper.getInstance(context), CategoryRepository.getInstance(context));
        }
        return instance;
    }

    private TransactionRepository(Context appContext, DatabaseHelper helper,
                                  CategoryRepository categories) {
        this.appContext = appContext;
        this.helper = helper;
        this.categories = categories;
    }

    /**
     * Inserts the transaction. Returns the new row id, or -1 if an SMS with the same fingerprint
     * was already stored (so re-importing the inbox never creates duplicates).
     */
    public long insert(Transaction t) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = db.insertWithOnConflict(DatabaseHelper.TABLE_TRANSACTIONS, null,
                toValues(t), SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) {
            t.setId(id);
            QuickAddWidget.requestRefresh(appContext);
        }
        return id;
    }

    public boolean update(Transaction t) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int rows = db.update(DatabaseHelper.TABLE_TRANSACTIONS, toValues(t),
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(t.getId())});
        QuickAddWidget.requestRefresh(appContext);
        return rows > 0;
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_TRANSACTIONS,
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(id)});
        QuickAddWidget.requestRefresh(appContext);
    }

    public Transaction getById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DatabaseHelper.TABLE_TRANSACTIONS, null,
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null)) {
            return c.moveToFirst() ? fromCursor(c, categories.getCatalog()) : null;
        }
    }

    /** Transactions with startMillis <= date < endMillis, newest first. */
    public List<Transaction> getBetween(long startMillis, long endMillis) {
        return query(DatabaseHelper.COL_DATE + " >= ? AND " + DatabaseHelper.COL_DATE + " < ?",
                new String[]{String.valueOf(startMillis), String.valueOf(endMillis)}, null);
    }

    public List<Transaction> getRecent(int limit) {
        return query(null, null, String.valueOf(limit));
    }

    /** Date of the oldest stored transaction, or null when the database is empty. */
    public Long getEarliestDate() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT MIN(" + DatabaseHelper.COL_DATE + ") FROM "
                + DatabaseHelper.TABLE_TRANSACTIONS, null)) {
            if (c.moveToFirst() && !c.isNull(0)) {
                return c.getLong(0);
            }
            return null;
        }
    }

    /** Every transaction, newest first. */
    public List<Transaction> getAll() {
        return query(null, null, null);
    }

    /** Transactions that came from an SMS, newest first, with their original message. */
    public List<Transaction> getSmsTransactions() {
        return query(DatabaseHelper.COL_SOURCE + " = ?", new String[]{Source.SMS.name()}, null);
    }

    /** True if a transaction was already imported from this message. */
    public boolean existsSmsKey(String smsKey) {
        if (smsKey == null) {
            return false;
        }
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT 1 FROM "
                + DatabaseHelper.TABLE_TRANSACTIONS + " WHERE " + DatabaseHelper.COL_SMS_KEY + " = ?",
                new String[]{smsKey})) {
            return c.moveToFirst();
        }
    }

    /**
     * The currency everything is shown in: the one the user pinned, otherwise the one most
     * transactions are in (the latest transaction breaks a tie).
     */
    public Currency resolveMainCurrency(AppSettings settings) {
        Currency pinned = settings.getDisplayCurrency();
        if (pinned != null) {
            return pinned;
        }
        List<Currency> inUse = getCurrenciesInUse();
        if (inUse.isEmpty()) {
            return Currency.defaultCurrency();
        }
        return inUse.get(0);
    }

    /** Every exchange rate the user has saved. */
    public ExchangeRates loadRates() {
        List<ExchangeRates.Saved> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DatabaseHelper.TABLE_FX_RATES, null,
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                try {
                    list.add(new ExchangeRates.Saved(
                            Currency.fromCode(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FXR_FROM))),
                            Currency.fromCode(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FXR_TO))),
                            c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_FXR_DAY)),
                            new BigDecimal(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FXR_RATE)))));
                } catch (NumberFormatException ignored) {
                    // a damaged row is skipped
                }
            }
        }
        return new ExchangeRates(list);
    }

    /** Remembers a rate the user entered for one day (replacing an earlier one for that day). */
    public void saveRate(Currency from, Currency to, long dateMillis, BigDecimal rate) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_FXR_FROM, from.name());
        v.put(DatabaseHelper.COL_FXR_TO, to.name());
        v.put(DatabaseHelper.COL_FXR_DAY, ExchangeRates.dayStart(dateMillis));
        v.put(DatabaseHelper.COL_FXR_RATE, rate.toPlainString());
        helper.getWritableDatabase().insertWithOnConflict(DatabaseHelper.TABLE_FX_RATES, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Works out, and stores, how every transaction in another currency converts into {@code main}
     * on the day it happened, fetching the real rate for each day when the setting allows.
     * Transactions whose rate is exact (fetched or typed) are kept; estimated ones are
     * recalculated, so a rate saved later improves nearby days.
     * Safe to call often: rows that are already correct are not rewritten.
     */
    public void ensureConverted(Currency main) {
        SQLiteDatabase db = helper.getWritableDatabase();
        // Payments already in the main currency need no conversion: drop any left over from when
        // another currency was the main one.
        ContentValues clear = new ContentValues();
        clear.putNull(DatabaseHelper.COL_FX_TO);
        clear.putNull(DatabaseHelper.COL_FX_RATE);
        clear.put(DatabaseHelper.COL_FX_DAY, 0);
        clear.put(DatabaseHelper.COL_FX_ESTIMATED, 0);
        clear.put(DatabaseHelper.COL_FX_CENTS, 0);
        db.update(DatabaseHelper.TABLE_TRANSACTIONS, clear, DatabaseHelper.COL_CURRENCY + " = ? AND "
                + DatabaseHelper.COL_FX_TO + " IS NOT NULL", new String[]{main.name()});
        List<Transaction> todo = new ArrayList<>();
        try (Cursor c = db.query(DatabaseHelper.TABLE_TRANSACTIONS, null,
                DatabaseHelper.COL_CURRENCY + " != ? AND (" + DatabaseHelper.COL_FX_TO
                        + " IS NULL OR " + DatabaseHelper.COL_FX_TO + " != ? OR "
                        + DatabaseHelper.COL_FX_ESTIMATED + " = 1)",
                new String[]{main.name(), main.name()}, null, null, null)) {
            CategoryCatalog catalog = categories.getCatalog();
            while (c.moveToNext()) {
                todo.add(fromCursor(c, catalog));
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        // Days with no exact rate yet are fetched in the background (never here: the single
        // database worker must stay free, or new messages would wait behind the network).
        ExchangeRates known = loadRates();
        for (Transaction t : todo) {
            ExchangeRates.Rate r = known.find(t.getCurrency(), main, t.getDateMillis());
            if (r == null || r.isEstimated()) {
                fetchMissingRatesAsync(main);
                break;
            }
        }
        ExchangeRates rates = loadRates();
        db.beginTransaction();
        try {
            for (Transaction t : todo) {
                ExchangeRates.Rate rate = rates.find(t.getCurrency(), main, t.getDateMillis());
                if (rate == null) {
                    continue;   // no rate yet: not counted until one is fetched or typed
                }
                long cents = ExchangeRates.convert(t.getAmountCents(), rate.getValue());
                boolean same = t.hasConversion() && t.getConvertedCurrency() == main
                        && t.getConversionRate().compareTo(rate.getValue()) == 0
                        && t.getConvertedCents() == cents
                        && t.isConversionEstimated() == rate.isEstimated()
                        && t.getConversionRateDay() == rate.getDayStart();
                if (same) {
                    continue;
                }
                ContentValues v = new ContentValues();
                v.put(DatabaseHelper.COL_FX_TO, main.name());
                v.put(DatabaseHelper.COL_FX_RATE, rate.getValue().toPlainString());
                v.put(DatabaseHelper.COL_FX_DAY, rate.getDayStart());
                v.put(DatabaseHelper.COL_FX_ESTIMATED, rate.isEstimated() ? 1 : 0);
                v.put(DatabaseHelper.COL_FX_CENTS, cents);
                db.update(DatabaseHelper.TABLE_TRANSACTIONS, v, DatabaseHelper.COL_ID + " = ?",
                        new String[]{String.valueOf(t.getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static final int MAX_FETCHES_PER_RUN = 40;
    private static final int STOP_AFTER_FAILURES = 3;

    /** Network work runs here, separate from the database worker. */
    private static final java.util.concurrent.ExecutorService NETWORK =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean FETCHING =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile Runnable onRatesUpdated;

    /** Runs {@code work} on the network thread. */
    public static void runOnNetwork(Runnable work) {
        NETWORK.execute(work);
    }

    /** Called (on the main thread) after new rates changed some totals; null to stop. */
    public static void setOnRatesUpdated(Runnable listener) {
        onRatesUpdated = listener;
    }

    /**
     * Fetches, in the background, the real rate for each day that still needs one, then
     * converts those payments and tells the open screen to refresh. Gives up for now after a few
     * failures in a row (offline), and one run at a time.
     */
    public void fetchMissingRatesAsync(Currency main) {
        if (!new AppSettings(appContext).isOnlineRatesOn() || !FETCHING.compareAndSet(false, true)) {
            return;
        }
        NETWORK.execute(() -> {
            try {
                ExchangeRates known = loadRates();
                int budget = MAX_FETCHES_PER_RUN;
                int failures = 0;
                boolean gotAny = false;
                for (Transaction t : foreignTransactionsNeedingRates(main)) {
                    ExchangeRates.Rate r = known.find(t.getCurrency(), main, t.getDateMillis());
                    if (r != null && !r.isEstimated()) {
                        continue;
                    }
                    if (budget-- <= 0 || failures >= STOP_AFTER_FAILURES) {
                        break;
                    }
                    if (fetchRate(t.getCurrency(), main, t.getDateMillis())) {
                        gotAny = true;
                        failures = 0;
                        known = loadRates();
                    } else {
                        failures++;
                    }
                }
                if (gotAny) {
                    ensureConverted(main);
                    Runnable listener = onRatesUpdated;
                    if (listener != null) {
                        com.localspend.util.Background.main(listener);
                    }
                }
            } finally {
                FETCHING.set(false);
            }
        });
    }

    private List<Transaction> foreignTransactionsNeedingRates(Currency main) {
        List<Transaction> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DatabaseHelper.TABLE_TRANSACTIONS, null,
                DatabaseHelper.COL_CURRENCY + " != ? AND (" + DatabaseHelper.COL_FX_TO
                        + " IS NULL OR " + DatabaseHelper.COL_FX_TO + " != ? OR "
                        + DatabaseHelper.COL_FX_ESTIMATED + " = 1)",
                new String[]{main.name(), main.name()}, null, null,
                DatabaseHelper.COL_DATE + " DESC")) {
            CategoryCatalog catalog = categories.getCatalog();
            while (c.moveToNext()) {
                out.add(fromCursor(c, catalog));
            }
        }
        return out;
    }
    private static final long RETRY_AFTER_MS = 10 * 60 * 1000;
    private final java.util.Map<String, Long> failedFetches = new java.util.HashMap<>();

    /**
     * Fetches the real rate for one day and saves it. Returns false when it could not be fetched
     * (offline, or looked up unsuccessfully a few minutes ago). Blocks on the network, so call it
     * only from {@link #runOnNetwork}, never from the database worker.
     */
    public boolean fetchRate(Currency from, Currency to, long dateMillis) {
        long day = ExchangeRates.dayStart(dateMillis);
        String key = from.name() + ">" + to.name() + "@" + day;
        synchronized (failedFetches) {
            Long failedAt = failedFetches.get(key);
            if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) {
                return false;
            }
        }
        if (!new AppSettings(appContext).isOnlineRatesOn()) {
            return false;
        }
        BigDecimal rate = RateFetcher.fetch(from, to, day);
        if (rate == null) {
            synchronized (failedFetches) {
                failedFetches.put(key, System.currentTimeMillis());
            }
            return false;
        }
        saveRate(from, to, day, rate);
        return true;
    }

    /** Currencies that have at least one transaction, most used first. */
    public List<Currency> getCurrenciesInUse() {
        List<Currency> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT " + DatabaseHelper.COL_CURRENCY
                + ", COUNT(*) AS n FROM " + DatabaseHelper.TABLE_TRANSACTIONS + " GROUP BY "
                + DatabaseHelper.COL_CURRENCY + " ORDER BY n DESC, MAX(" + DatabaseHelper.COL_DATE + ") DESC", null)) {
            while (c.moveToNext()) {
                Currency currency = Currency.fromCode(c.getString(0));
                if (!out.contains(currency)) {
                    out.add(currency);
                }
            }
        }
        return out;
    }

    /** Date of the most recent expense in this currency, or null if there is none. */
    public Long getLatestExpenseDate(Currency currency) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT MAX(" + DatabaseHelper.COL_DATE
                + ") FROM " + DatabaseHelper.TABLE_TRANSACTIONS + " WHERE " + DatabaseHelper.COL_TYPE
                + " = ? AND " + DatabaseHelper.COL_CURRENCY + " = ?",
                new String[]{TransactionType.EXPENSE.name(), currency.name()})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : null;
        }
    }

    /** Currency of the most recent transaction, or null if there are none. */
    public Currency getLatestCurrency() {
        List<Transaction> latest = getRecent(1);
        return latest.isEmpty() ? null : latest.get(0).getCurrency();
    }

    /** Number of transactions that still hold their original SMS text. */
    public int countStoredMessages() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "
                + DatabaseHelper.TABLE_TRANSACTIONS + " WHERE " + DatabaseHelper.COL_SMS_BODY
                + " IS NOT NULL", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * Adds the original message to a transaction imported before messages were stored (or after
     * they were cleared). Only fills in a missing message; never overwrites one.
     */
    public void backfillOriginalSms(String smsKey, String sender, String body, long receivedMillis) {
        if (smsKey == null || body == null) {
            return;
        }
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_SMS_SENDER, sender);
        v.put(DatabaseHelper.COL_SMS_BODY, body);
        v.put(DatabaseHelper.COL_SMS_RECEIVED, receivedMillis);
        helper.getWritableDatabase().update(DatabaseHelper.TABLE_TRANSACTIONS, v,
                DatabaseHelper.COL_SMS_KEY + " = ? AND " + DatabaseHelper.COL_SMS_BODY + " IS NULL",
                new String[]{smsKey});
    }

    /** Removes every stored SMS text (the transactions themselves are kept). */
    public int clearStoredMessages() {
        ContentValues v = new ContentValues();
        v.putNull(DatabaseHelper.COL_SMS_BODY);
        v.putNull(DatabaseHelper.COL_SMS_SENDER);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_TRANSACTIONS, v,
                DatabaseHelper.COL_SMS_BODY + " IS NOT NULL", null);
    }

    /** Expense transactions from the same merchant (same merchant key), excluding one id. */
    public List<Transaction> getByMerchant(String merchantKey, long excludeId) {
        List<Transaction> out = new ArrayList<>();
        if (merchantKey == null || merchantKey.isEmpty()) {
            return out;
        }
        for (Transaction t : getAll()) {
            if (t.getId() != excludeId && t.isExpense()
                    && merchantKey.equals(MerchantNormaliser.merchantKey(t.getMerchant()))) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Files every expense from this merchant under the category, as if the user had chosen it
     * for each one.
     *
     * @return how many transactions changed
     */
    public int applyCategoryToMerchant(String merchantKey, Category category) {
        int changed = 0;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Transaction t : getByMerchant(merchantKey, Transaction.NO_ID)) {
                if (!t.getCategory().equals(category) || !t.isCategoryChosenByUser()) {
                    t.setCategory(category);
                    t.setCategoryChosenByUser(true);
                    update(t);
                    changed++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    /**
     * Runs the categorisation rules again over every expense whose category was chosen
     * automatically, for example after the user adds keywords or a new category. Categories the
     * user picked by hand are never changed.
     *
     * @return how many transactions changed category
     */
    public int recategoriseAutomatic(CategorisationEngine engine) {
        int changed = 0;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Transaction t : getAll()) {
                if (t.isCategoryChosenByUser()) {
                    continue;
                }
                Category suggested = t.isExpense()
                        ? engine.categorise(t.getMerchant(), t.getSmsBody())
                        : engine.categoriseIncome(t.getMerchant(), t.getSmsBody());
                if (!suggested.equals(t.getCategory())) {
                    t.setCategory(suggested);
                    update(t);
                    changed++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    private List<Transaction> query(String selection, String[] args, String limit) {
        SQLiteDatabase db = helper.getReadableDatabase();
        CategoryCatalog catalog = categories.getCatalog();
        List<Transaction> out = new ArrayList<>();
        try (Cursor c = db.query(DatabaseHelper.TABLE_TRANSACTIONS, null, selection, args, null, null,
                DatabaseHelper.COL_DATE + " DESC, " + DatabaseHelper.COL_ID + " DESC", limit)) {
            while (c.moveToNext()) {
                out.add(fromCursor(c, catalog));
            }
        }
        return out;
    }

    private static ContentValues toValues(Transaction t) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_AMOUNT, t.getAmountCents());
        v.put(DatabaseHelper.COL_MERCHANT, t.getMerchant());
        v.put(DatabaseHelper.COL_DATE, t.getDateMillis());
        v.put(DatabaseHelper.COL_TYPE, t.getType().name());
        v.put(DatabaseHelper.COL_CATEGORY, t.getCategory().getKey());
        v.put(DatabaseHelper.COL_SOURCE, t.getSource().name());
        v.put(DatabaseHelper.COL_SMS_KEY, t.getSmsKey());
        v.put(DatabaseHelper.COL_SMS_SENDER, t.getSmsSender());
        v.put(DatabaseHelper.COL_SMS_BODY, t.getSmsBody());
        v.put(DatabaseHelper.COL_SMS_RECEIVED, t.getSmsReceivedMillis());
        v.put(DatabaseHelper.COL_CATEGORY_BY_USER, t.isCategoryChosenByUser() ? 1 : 0);
        v.put(DatabaseHelper.COL_CURRENCY, t.getCurrency().name());
        v.put(DatabaseHelper.COL_FX_TO, t.hasConversion() ? t.getConvertedCurrency().name() : null);
        v.put(DatabaseHelper.COL_FX_RATE, t.hasConversion() ? t.getConversionRate().toPlainString() : null);
        v.put(DatabaseHelper.COL_FX_DAY, t.hasConversion() ? t.getConversionRateDay() : 0);
        v.put(DatabaseHelper.COL_FX_ESTIMATED, t.hasConversion() && t.isConversionEstimated() ? 1 : 0);
        v.put(DatabaseHelper.COL_FX_CENTS, t.hasConversion() ? t.getConvertedCents() : 0);
        return v;
    }

    private static Transaction fromCursor(Cursor c, CategoryCatalog catalog) {
        Transaction t = new Transaction(
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_AMOUNT)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_MERCHANT)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_DATE)),
                TransactionType.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_TYPE))),
                catalog.find(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORY))),
                Source.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE))));
        t.setId(c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        t.setSmsKey(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SMS_KEY)));
        t.setOriginalSms(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SMS_SENDER)),
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_SMS_BODY)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_SMS_RECEIVED)));
        t.setCategoryChosenByUser(
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORY_BY_USER)) == 1);
        t.setCurrency(Currency.fromCode(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENCY))));
        // Last, because changing the currency above clears any conversion.
        String fxTo = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FX_TO));
        String fxRate = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FX_RATE));
        if (fxTo != null && fxRate != null) {
            t.setConversion(Currency.fromCode(fxTo), new BigDecimal(fxRate),
                    c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_FX_DAY)),
                    c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_FX_ESTIMATED)) == 1,
                    c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_FX_CENTS)));
        }
        return t;
    }
}
