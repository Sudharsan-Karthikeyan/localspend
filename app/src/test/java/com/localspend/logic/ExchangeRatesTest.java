package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;
import com.localspend.util.FxText;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class ExchangeRatesTest {

    private static long day(int y, int m, int d, int hour) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d, hour, 30, 0);
        return c.getTimeInMillis();
    }

    private static ExchangeRates.Saved saved(Currency f, Currency t, long when, String rate) {
        return new ExchangeRates.Saved(f, t, when, new BigDecimal(rate));
    }

    @Test
    public void exactDayRateWinsAndIsNotAnEstimate() {
        ExchangeRates r = new ExchangeRates(Arrays.asList(
                saved(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 21, 0), "59.40"),
                saved(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 25, 0), "60.10")));
        ExchangeRates.Rate rate = r.find(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 21, 22));
        assertEquals(0, new BigDecimal("59.40").compareTo(rate.getValue()));
        assertFalse(rate.isEstimated());
    }

    @Test
    public void nearestSavedDayIsUsedAsAnEstimate() {
        ExchangeRates r = new ExchangeRates(Arrays.asList(
                saved(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 21, 0), "59.40"),
                saved(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 30, 0), "60.10")));
        ExchangeRates.Rate rate = r.find(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 23, 9));
        assertEquals(0, new BigDecimal("59.40").compareTo(rate.getValue()));
        assertTrue(rate.isEstimated());
        assertTrue(rate.isFromSaved());
    }

    @Test
    public void aSavedRateAlsoWorksBackwards() {
        ExchangeRates r = new ExchangeRates(Arrays.asList(
                saved(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 21, 0), "50")));
        ExchangeRates.Rate rate = r.find(Currency.INR, Currency.AUD, day(2026, Calendar.MAY, 21, 5));
        assertEquals(0, new BigDecimal("0.02").compareTo(rate.getValue()));
        assertFalse(rate.isEstimated());
    }

    @Test
    public void withNothingSavedThereIsNoRate() {
        assertEquals(null, new ExchangeRates(null)
                .find(Currency.AUD, Currency.INR, day(2026, Calendar.MAY, 21, 5)));
    }

    @Test
    public void sameCurrencyIsRateOne() {
        ExchangeRates.Rate rate = new ExchangeRates(null)
                .find(Currency.INR, Currency.INR, day(2026, Calendar.MAY, 21, 5));
        assertEquals(0, BigDecimal.ONE.compareTo(rate.getValue()));
        assertFalse(rate.isEstimated());
    }

    @Test
    public void conversionRoundsToTheNearestCentAndNeverToZero() {
        assertEquals(334405, ExchangeRates.convert(5628, new BigDecimal("59.4180")));   // 56.28 x 59.418
        assertEquals(1, ExchangeRates.convert(1, new BigDecimal("0.01")));
    }

    @Test
    public void calculationReadsLikeASum() {
        String text = FxText.calculation(5628, Currency.AUD, new BigDecimal("59.4180"), 334405,
                Currency.INR);
        assertEquals("$56.28 × 59.418 = ₹3,344.05", text);
        assertEquals("88.00", FxText.rate(new BigDecimal("88.000000")));
    }

    @Test
    public void totalsCountConvertedPaymentsInTheMainCurrency() {
        Transaction inr = new Transaction(100000, "Shop", 1L, TransactionType.EXPENSE,
                Category.OTHER, Source.SMS);
        inr.setCurrency(Currency.INR);
        Transaction aud = new Transaction(5628, "Doordash", 2L, TransactionType.EXPENSE,
                Category.OTHER, Source.SMS);
        aud.setCurrency(Currency.AUD);
        aud.setConversion(Currency.INR, new BigDecimal("59.418"), 2L, true, 334405);
        Transaction unconverted = new Transaction(900, "Cafe", 3L, TransactionType.EXPENSE,
                Category.OTHER, Source.SMS);
        unconverted.setCurrency(Currency.USD);

        List<Transaction> all = new ArrayList<>(Arrays.asList(inr, aud, unconverted));
        List<Transaction> inRupees = SpendingAnalyzer.inCurrency(all, Currency.INR);
        assertEquals(2, inRupees.size());           // the unconverted dollars are not mixed in
        long sum = 0;
        for (Transaction t : inRupees) {
            assertEquals(Currency.INR, t.getCurrency());
            sum += t.getAmountCents();
        }
        assertEquals(434405, sum);
        // the original keeps its own amount and currency
        assertEquals(5628, aud.getAmountCents());
        assertEquals(Currency.AUD, aud.getCurrency());
    }

    @Test
    public void editingTheAmountDropsTheOldConversion() {
        Transaction aud = new Transaction(5628, "Doordash", 2L, TransactionType.EXPENSE,
                Category.OTHER, Source.SMS);
        aud.setCurrency(Currency.AUD);
        aud.setConversion(Currency.INR, new BigDecimal("59.418"), 2L, true, 334405);
        assertTrue(aud.hasConversion());
        aud.setAmountCents(6000);
        assertFalse(aud.hasConversion());
    }

    @Test
    public void smallConvertedAmountsAddUpWithoutRoundingDrift() {
        // INR 1.00 twice at 0.0147 is 2.94 cents in total: 3 cents, not 1 + 1.
        List<Transaction> all = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Transaction t = new Transaction(100, "Test", 10L + i, TransactionType.EXPENSE,
                    Category.OTHER, Source.SMS);
            t.setCurrency(Currency.INR);
            t.setConversion(Currency.AUD, new BigDecimal("0.0147"), 10L, false, 1);
            all.add(t);
        }
        long sum = 0;
        for (Transaction t : SpendingAnalyzer.inCurrency(all, Currency.AUD)) {
            sum += t.getAmountCents();
        }
        assertEquals(3, sum);

        List<Transaction> usd = new ArrayList<>();
        for (Transaction t : all) {
            t.setConversion(Currency.USD, new BigDecimal("0.0114"), 10L, false, 1);
            usd.add(t);
        }
        sum = 0;
        for (Transaction t : SpendingAnalyzer.inCurrency(usd, Currency.USD)) {
            sum += t.getAmountCents();
        }
        assertEquals(2, sum);   // 2.28 cents
    }

    @Test
    public void largeAmountsAreStillConvertedNormally() {
        Transaction t = new Transaction(5628, "Doordash", 2L, TransactionType.EXPENSE,
                Category.OTHER, Source.SMS);
        t.setCurrency(Currency.AUD);
        t.setConversion(Currency.INR, new BigDecimal("68.17"), 2L, false, 383661);
        List<Transaction> out = SpendingAnalyzer.inCurrency(java.util.Collections.singletonList(t),
                Currency.INR);
        assertEquals(383661, out.get(0).getAmountCents());   // 56.28 x 68.17 = 3,836.6076
    }
}
