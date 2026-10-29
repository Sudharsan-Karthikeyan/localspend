package com.localspend.logic;

import com.localspend.model.Category;

import java.util.HashMap;
import java.util.Map;

/** In-memory MerchantCategoryLookup, loaded once so typing in the UI never touches the database. */
public class MapCategoryLookup implements MerchantCategoryLookup {

    private final Map<String, Category> rules;

    public MapCategoryLookup(Map<String, Category> rules) {
        this.rules = new HashMap<>(rules);
    }

    @Override
    public Category find(String merchantKey) {
        return rules.get(merchantKey);
    }
}
