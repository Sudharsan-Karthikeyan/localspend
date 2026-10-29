package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Frequency;
import com.localspend.model.RecurringPayment;
import com.localspend.model.TransactionType;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes repeating payments and instalment plans. Call from a background thread. */
public class RecurringRepository {

    private static RecurringRepository instance;

    private final DatabaseHelper helper;
    private final CategoryRepository categories;

    public static synchronized RecurringRepository getInstance(Context context) {
        if (instance == null) {
            instance = new RecurringRepository(DatabaseHelper.getInstance(context),
                    CategoryRepository.getInstance(context));
        }
        return instance;
    }

    private RecurringRepository(DatabaseHelper helper, CategoryRepository categories) {
        this.helper = helper;
        this.categories = categories;
    }

    public long insert(RecurringPayment p) {
        long id = helper.getWritableDatabase().insert(DatabaseHelper.TABLE_RECURRING, null, toValues(p));
        p.setId(id);
        return id;
    }

    /** Saves where the plan stands: next date, payments made and whether it is still running. */
    public void saveProgress(RecurringPayment p) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_RC_NEXT_DUE, p.getNextDueMillis());
        v.put(DatabaseHelper.COL_RC_MADE, p.getPaymentsMade());
        v.put(DatabaseHelper.COL_RC_ACTIVE, p.isActive() ? 1 : 0);
        helper.getWritableDatabase().update(DatabaseHelper.TABLE_RECURRING, v,
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(p.getId())});
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete(DatabaseHelper.TABLE_RECURRING,
                DatabaseHelper.COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /** Every plan, running ones first, then by next due date. */
    public List<RecurringPayment> getAll() {
        return query(null, DatabaseHelper.COL_RC_ACTIVE + " DESC, "
                + DatabaseHelper.COL_RC_NEXT_DUE + " ASC");
    }

    /** Plans that are still running. */
    public List<RecurringPayment> getActive() {
        return query(DatabaseHelper.COL_RC_ACTIVE + " = 1", DatabaseHelper.COL_RC_NEXT_DUE + " ASC");
    }

    private List<RecurringPayment> query(String where, String orderBy) {
        CategoryCatalog catalog = categories.getCatalog();
        List<RecurringPayment> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DatabaseHelper.TABLE_RECURRING, null,
                where, null, null, null, orderBy)) {
            while (c.moveToNext()) {
                out.add(fromCursor(c, catalog));
            }
        }
        return out;
    }

    private static ContentValues toValues(RecurringPayment p) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_RC_MERCHANT, p.getMerchant());
        v.put(DatabaseHelper.COL_RC_AMOUNT, p.getAmountCents());
        v.put(DatabaseHelper.COL_RC_CURRENCY, p.getCurrency().name());
        v.put(DatabaseHelper.COL_RC_TYPE, p.getType().name());
        v.put(DatabaseHelper.COL_RC_CATEGORY, p.getCategory().getKey());
        v.put(DatabaseHelper.COL_RC_FREQUENCY, p.getFrequency().name());
        v.put(DatabaseHelper.COL_RC_INSTALLMENT, p.isInstallment() ? 1 : 0);
        v.put(DatabaseHelper.COL_RC_TOTAL, p.getTotalPayments());
        v.put(DatabaseHelper.COL_RC_ANCHOR, p.getAnchorDay());
        v.put(DatabaseHelper.COL_RC_NEXT_DUE, p.getNextDueMillis());
        v.put(DatabaseHelper.COL_RC_MADE, p.getPaymentsMade());
        v.put(DatabaseHelper.COL_RC_ACTIVE, p.isActive() ? 1 : 0);
        return v;
    }

    private static RecurringPayment fromCursor(Cursor c, CategoryCatalog catalog) {
        RecurringPayment p = new RecurringPayment(
                c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_MERCHANT)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_AMOUNT)),
                Currency.fromCode(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_CURRENCY))),
                TransactionType.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_TYPE))),
                catalog.find(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_CATEGORY))),
                Frequency.fromName(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_FREQUENCY))),
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_INSTALLMENT)) == 1,
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_TOTAL)),
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_ANCHOR)),
                c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_NEXT_DUE)),
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_MADE)),
                c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RC_ACTIVE)) == 1);
        p.setId(c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        return p;
    }
}
