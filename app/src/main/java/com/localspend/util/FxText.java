package com.localspend.util;

import com.localspend.model.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Wording for a currency conversion: "A$56.28 × 59.4120 = ₹3,343.97". */
public final class FxText {

    private FxText() {
    }

    /** The rate without needless zeros but with at least two decimals: 59.412, 88.00. */
    public static String rate(BigDecimal rate) {
        BigDecimal r = rate.stripTrailingZeros();
        if (r.scale() < 2) {
            r = r.setScale(2, RoundingMode.UNNECESSARY);
        }
        return r.toPlainString();
    }

    /** "A$56.28 × 59.412 = ₹3,343.97". */
    public static String calculation(long cents, Currency from, BigDecimal rate,
                                     long convertedCents, Currency to) {
        return MoneyFormatter.format(cents, from) + " × " + rate(rate) + " = "
                + MoneyFormatter.format(convertedCents, to);
    }
}
