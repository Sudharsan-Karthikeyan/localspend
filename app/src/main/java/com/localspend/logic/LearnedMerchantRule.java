package com.localspend.logic;

import com.localspend.model.Category;

/** Applies corrections the user made earlier. Always tried before the built-in keyword rules. */
public class LearnedMerchantRule implements CategorisationRule {

    private final MerchantCategoryLookup lookup;

    public LearnedMerchantRule(MerchantCategoryLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public Category match(String merchant) {
        String key = MerchantNormaliser.merchantKey(merchant);
        return key.isEmpty() ? null : lookup.find(key);
    }
}
