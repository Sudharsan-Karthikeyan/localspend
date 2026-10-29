package com.localspend.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns messy merchant text ("McDonald's #1234 BONDI") into stable comparable forms. */
public final class MerchantNormaliser {

    private MerchantNormaliser() {
    }

    /** Lower-case, apostrophes dropped, every other symbol collapsed to a single space. */
    public static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[’'`]", "");
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Key used to remember user corrections: the first two words that contain no digits, so store
     * numbers and card suffixes do not stop "Woolworths 1234" matching "Woolworths 5678".
     */
    public static String merchantKey(String text) {
        String normalised = normalise(text);
        if (normalised.isEmpty()) {
            return "";
        }
        List<String> words = new ArrayList<>();
        for (String token : normalised.split(" ")) {
            if (!token.matches(".*\\d.*")) {
                words.add(token);
                if (words.size() == 2) {
                    break;
                }
            }
        }
        return words.isEmpty() ? normalised : String.join(" ", words);
    }
}
