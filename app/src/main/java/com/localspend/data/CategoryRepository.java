package com.localspend.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localspend.logic.CategorisationEngine;
import com.localspend.logic.MapCategoryLookup;
import com.localspend.logic.MerchantNormaliser;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the user can customise about categorisation: the categories themselves (name,
 * colour, budget), keywords they add to a category, and merchants they have re-categorised.
 * Call from a background thread (see Background).
 */
public class CategoryRepository {

    private static CategoryRepository instance;

    private final DatabaseHelper helper;
    private volatile CategoryCatalog cachedCatalog;

    public static synchronized CategoryRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CategoryRepository(DatabaseHelper.getInstance(context));
        }
        return instance;
    }

    private CategoryRepository(DatabaseHelper helper) {
        this.helper = helper;
    }

    // ---- Categories ---------------------------------------------------------------------------

    /** The user's categories in display order. Cached until a category changes. */
    public CategoryCatalog getCatalog() {
        CategoryCatalog catalog = cachedCatalog;
        if (catalog == null) {
            catalog = loadCatalog();
            cachedCatalog = catalog;
        }
        return catalog;
    }

    private CategoryCatalog loadCatalog() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<Category> list = new ArrayList<>();
        try (Cursor c = db.query(DatabaseHelper.TABLE_CATEGORIES, null, null, null, null, null,
                DatabaseHelper.COL_CAT_ORDER + " ASC, " + DatabaseHelper.COL_CAT_LABEL + " ASC")) {
            int keyIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_KEY);
            int labelIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_LABEL);
            int colorIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_COLOR);
            int builtInIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_BUILT_IN);
            int budgetIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_BUDGET);
            int budgetCurIdx = c.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_BUDGET_CURRENCY);
            while (c.moveToNext()) {
                list.add(new Category(c.getString(keyIdx), c.getString(labelIdx), c.getInt(colorIdx),
                        c.getInt(builtInIdx) == 1, Math.max(0, c.getLong(budgetIdx)),
                        Currency.fromCode(c.getString(budgetCurIdx))));
            }
        }
        return new CategoryCatalog(list);
    }

    /** Adds a new category or updates an existing one (matched by key). */
    public void saveCategory(Category category) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_CAT_LABEL, category.getLabel());
        v.put(DatabaseHelper.COL_CAT_COLOR, category.getColor());
        v.put(DatabaseHelper.COL_CAT_BUDGET, category.getBudgetCents());
        v.put(DatabaseHelper.COL_CAT_BUDGET_CURRENCY, category.getBudgetCurrency().name());
        int rows = db.update(DatabaseHelper.TABLE_CATEGORIES, v,
                DatabaseHelper.COL_CAT_KEY + " = ?", new String[]{category.getKey()});
        if (rows == 0) {
            v.put(DatabaseHelper.COL_CAT_KEY, category.getKey());
            v.put(DatabaseHelper.COL_CAT_BUILT_IN, category.isBuiltIn() ? 1 : 0);
            v.put(DatabaseHelper.COL_CAT_ORDER, nextSortOrder(db));
            db.insert(DatabaseHelper.TABLE_CATEGORIES, null, v);
        }
        cachedCatalog = null;
    }

    private static int nextSortOrder(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT MAX(" + DatabaseHelper.COL_CAT_ORDER + ") FROM "
                + DatabaseHelper.TABLE_CATEGORIES + " WHERE " + DatabaseHelper.COL_CAT_ORDER
                + " < " + Integer.MAX_VALUE, null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getInt(0) + 1 : 0;
        }
    }

    /**
     * Deletes a category. Its transactions move to Other, and keywords and remembered merchants
     * that pointed to it are removed. Other itself cannot be deleted.
     *
     * @return how many transactions were moved to Other
     */
    public int deleteCategory(Category category) {
        if (category.isOther()) {
            throw new IllegalArgumentException("Other cannot be deleted");
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        String[] key = {category.getKey()};
        int moved;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put(DatabaseHelper.COL_CATEGORY, Category.OTHER_KEY);
            v.put(DatabaseHelper.COL_CATEGORY_BY_USER, 0);
            moved = db.update(DatabaseHelper.TABLE_TRANSACTIONS, v,
                    DatabaseHelper.COL_CATEGORY + " = ?", key);
            db.delete(DatabaseHelper.TABLE_KEYWORDS, DatabaseHelper.COL_KW_CATEGORY + " = ?", key);
            db.delete(DatabaseHelper.TABLE_RULES, DatabaseHelper.COL_RULE_CATEGORY + " = ?", key);
            db.delete(DatabaseHelper.TABLE_CATEGORIES, DatabaseHelper.COL_CAT_KEY + " = ?", key);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cachedCatalog = null;
        return moved;
    }

    /** Brings back any built-in category the user deleted. Renamed ones are left as they are. */
    public void restoreDefaultCategories() {
        DatabaseHelper.seedDefaultCategories(helper.getWritableDatabase());
        cachedCatalog = null;
    }

    // ---- User keywords ------------------------------------------------------------------------

    /** All keywords the user added, keyword to (current) category. */
    public Map<String, Category> getUserKeywords() {
        CategoryCatalog catalog = getCatalog();
        Map<String, Category> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : readPairs(DatabaseHelper.TABLE_KEYWORDS,
                DatabaseHelper.COL_KW_KEYWORD, DatabaseHelper.COL_KW_CATEGORY).entrySet()) {
            if (catalog.contains(e.getValue())) {
                out.put(e.getKey(), catalog.find(e.getValue()));
            }
        }
        return out;
    }

    public List<String> getKeywordsFor(Category category) {
        return keysWithValue(DatabaseHelper.TABLE_KEYWORDS, DatabaseHelper.COL_KW_KEYWORD,
                DatabaseHelper.COL_KW_CATEGORY, category.getKey());
    }

    /**
     * Files every merchant containing this word or phrase under the category. A keyword can only
     * belong to one category, so adding it again moves it.
     *
     * @return the normalised keyword that was saved, or null if it was empty
     */
    public String addKeyword(String keyword, Category category) {
        String normalised = MerchantNormaliser.normalise(keyword);
        if (normalised.isEmpty()) {
            return null;
        }
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_KW_KEYWORD, normalised);
        v.put(DatabaseHelper.COL_KW_CATEGORY, category.getKey());
        helper.getWritableDatabase().insertWithOnConflict(DatabaseHelper.TABLE_KEYWORDS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
        return normalised;
    }

    public void removeKeyword(String keyword) {
        helper.getWritableDatabase().delete(DatabaseHelper.TABLE_KEYWORDS,
                DatabaseHelper.COL_KW_KEYWORD + " = ?", new String[]{keyword});
    }

    // ---- Remembered merchants -----------------------------------------------------------------

    /** Category corrections the user has made, keyed by normalised merchant. */
    public Map<String, Category> getLearnedRules() {
        CategoryCatalog catalog = getCatalog();
        Map<String, Category> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : readPairs(DatabaseHelper.TABLE_RULES,
                DatabaseHelper.COL_RULE_KEY, DatabaseHelper.COL_RULE_CATEGORY).entrySet()) {
            if (catalog.contains(e.getValue())) {
                out.put(e.getKey(), catalog.find(e.getValue()));
            }
        }
        return out;
    }

    /** Merchant keys the user filed under this category. */
    public List<String> getLearnedMerchantsFor(Category category) {
        return keysWithValue(DatabaseHelper.TABLE_RULES, DatabaseHelper.COL_RULE_KEY,
                DatabaseHelper.COL_RULE_CATEGORY, category.getKey());
    }

    /** Remembers that this merchant should be filed under the given category from now on. */
    public void saveLearnedRule(String merchantKey, Category category) {
        if (merchantKey == null || merchantKey.isEmpty()) {
            return;
        }
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COL_RULE_KEY, merchantKey);
        v.put(DatabaseHelper.COL_RULE_CATEGORY, category.getKey());
        helper.getWritableDatabase().insertWithOnConflict(DatabaseHelper.TABLE_RULES, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteLearnedRule(String merchantKey) {
        helper.getWritableDatabase().delete(DatabaseHelper.TABLE_RULES,
                DatabaseHelper.COL_RULE_KEY + " = ?", new String[]{merchantKey});
    }

    // ---- Engine -------------------------------------------------------------------------------

    /** The categorisation engine with all of the user's customisations applied. */
    public CategorisationEngine buildEngine(boolean useBuiltInKeywords) {
        return CategorisationEngine.build(getCatalog(), new MapCategoryLookup(getLearnedRules()),
                getUserKeywords(), useBuiltInKeywords);
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private Map<String, String> readPairs(String table, String keyCol, String valueCol) {
        Map<String, String> out = new LinkedHashMap<>();
        try (Cursor c = helper.getReadableDatabase().query(table, new String[]{keyCol, valueCol},
                null, null, null, null, keyCol + " ASC")) {
            while (c.moveToNext()) {
                out.put(c.getString(0), c.getString(1));
            }
        }
        return out;
    }

    private List<String> keysWithValue(String table, String keyCol, String valueCol, String value) {
        List<String> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(table, new String[]{keyCol},
                valueCol + " = ?", new String[]{value}, null, null, keyCol + " ASC")) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return Collections.unmodifiableList(out);
    }
}
