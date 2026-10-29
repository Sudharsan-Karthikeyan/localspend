package com.localspend.logic;

import com.localspend.model.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches a merchant against a list of keywords. Matching is on whole words (with an optional
 * trailing "s"), so "bp" matches "BP Bondi" but not "Bpm Studio", and "mcdonald" matches
 * "McDonald's".
 */
public class KeywordRule implements CategorisationRule {

    private final Category category;
    private final List<String> keywords;
    private final Pattern pattern;

    public KeywordRule(Category category, String... keywords) {
        this.category = category;
        List<String> kept = new ArrayList<>();
        StringBuilder alternation = new StringBuilder();
        for (String keyword : keywords) {
            String normalised = MerchantNormaliser.normalise(keyword);
            if (normalised.isEmpty()) {
                continue;
            }
            kept.add(keyword);
            if (alternation.length() > 0) {
                alternation.append('|');
            }
            alternation.append(Pattern.quote(normalised));
        }
        this.keywords = Collections.unmodifiableList(kept);
        this.pattern = kept.isEmpty() ? null
                : Pattern.compile("\\b(?:" + alternation + ")s?\\b");
    }

    @Override
    public Category match(String merchant) {
        if (pattern == null) {
            return null;
        }
        String normalised = MerchantNormaliser.normalise(merchant);
        if (pattern.matcher(normalised).find() || endsWithCutOffKeyword(normalised)) {
            return category;
        }
        return null;
    }

    /**
     * Banks cut merchant names at about 15 characters, so "Samco Restaurant" arrives as "Samco
     * Restauran" and "Big Tree Entertainment" as "Big Tree Entert". When the last word is at least
     * five letters and is the beginning of one of this category's single-word keywords, it counts.
     */
    private boolean endsWithCutOffKeyword(String normalised) {
        int space = normalised.lastIndexOf(' ');
        String last = normalised.substring(space + 1);
        if (last.length() < 5) {
            return false;
        }
        for (String keyword : keywords) {
            String k = MerchantNormaliser.normalise(keyword);
            if (k.indexOf(' ') < 0 && k.length() > last.length() && k.startsWith(last)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Length of the longest keyword found anywhere in the text, 0 if none. Used when a whole
     * message is searched, so the most specific keyword ("coles express") beats a short one ("coles").
     */
    public int matchLength(String text) {
        if (pattern == null) {
            return 0;
        }
        java.util.regex.Matcher m = pattern.matcher(MerchantNormaliser.normalise(text));
        int longest = 0;
        while (m.find()) {
            longest = Math.max(longest, m.end() - m.start());
        }
        return longest;
    }

    public Category getCategory() {
        return category;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}
