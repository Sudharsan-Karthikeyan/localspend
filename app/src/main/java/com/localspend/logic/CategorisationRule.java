package com.localspend.logic;

import com.localspend.model.Category;

/** One way of deciding a category. Rules are tried in order by CategorisationEngine. */
public interface CategorisationRule {

    /** Returns the category for this merchant, or null if this rule has no opinion. */
    Category match(String merchant);
}
