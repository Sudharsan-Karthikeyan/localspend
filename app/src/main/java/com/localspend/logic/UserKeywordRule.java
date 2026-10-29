package com.localspend.logic;

import com.localspend.model.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Keywords the user added to their categories ("officeworks" goes to Education, "jims" to a custom
 * "Car" category, and so on). Longer keywords are tried first, so "coles express" beats "coles".
 * These run before the built-in lists, so a user keyword always overrides a built-in one.
 */
public class UserKeywordRule implements CategorisationRule {

    private final List<KeywordRule> rules = new ArrayList<>();

    public UserKeywordRule(Map<String, Category> keywordToCategory) {
        List<Map.Entry<String, Category>> entries = new ArrayList<>(keywordToCategory.entrySet());
        Collections.sort(entries, (a, b) -> {
            int byLength = Integer.compare(MerchantNormaliser.normalise(b.getKey()).length(),
                    MerchantNormaliser.normalise(a.getKey()).length());
            return byLength != 0 ? byLength : a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String, Category> e : entries) {
            if (e.getValue() != null) {
                rules.add(new KeywordRule(e.getValue(), e.getKey()));
            }
        }
    }

    @Override
    public Category match(String merchant) {
        for (KeywordRule rule : rules) {
            Category c = rule.match(merchant);
            if (c != null) {
                return c;
            }
        }
        return null;
    }
}
