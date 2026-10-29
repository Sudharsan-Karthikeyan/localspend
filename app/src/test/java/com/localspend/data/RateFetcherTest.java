package com.localspend.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.localspend.model.Currency;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Calendar;

public class RateFetcherTest {

    @Test
    public void readsTheRateFromTheAnswer() {
        String json = "{\"amount\":1.0,\"base\":\"AUD\",\"date\":\"2026-05-21\",\"rates\":{\"INR\":68.17}}";
        assertEquals(0, new BigDecimal("68.17").compareTo(RateFetcher.parse(json, Currency.INR)));
    }

    @Test
    public void missingOrBadAnswersGiveNull() {
        assertNull(RateFetcher.parse("{\"rates\":{}}", Currency.INR));
        assertNull(RateFetcher.parse("{\"message\":\"not found\"}", Currency.INR));
        assertNull(RateFetcher.parse(null, Currency.INR));
    }

    @Test
    public void urlNamesOnlyTheCurrenciesAndTheDay() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.MAY, 21);
        Calendar now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.OCTOBER, 27);
        assertEquals("https://api.frankfurter.dev/v1/2026-05-21?base=AUD&symbols=INR",
                RateFetcher.urlFor(Currency.AUD, Currency.INR, c.getTimeInMillis(), now.getTimeInMillis()));
        assertEquals("https://api.frankfurter.dev/v1/latest?base=AUD&symbols=INR",
                RateFetcher.urlFor(Currency.AUD, Currency.INR, now.getTimeInMillis(), now.getTimeInMillis()));
    }
}
