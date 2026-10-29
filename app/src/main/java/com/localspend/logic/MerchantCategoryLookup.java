package com.localspend.logic;

import com.localspend.model.Category;

/** Source of user-taught merchant categories (backed by the database in the app). */
public interface MerchantCategoryLookup {

    /** Returns the category the user chose for this merchant key, or null if none. */
    Category find(String merchantKey);
}
