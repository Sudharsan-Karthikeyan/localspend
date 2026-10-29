package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class SearchExportLockTest {

    private static long day(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m, d, 12, 0);
        return c.getTimeInMillis();
    }

    private static Transaction tx(long cents, String merchant, Category cat, TransactionType type, long date) {
        return new Transaction(cents, merchant, date, type, cat, Source.MANUAL);
    }

    private final Transaction coffee = tx(550, "Cafe Roma", Category.FOOD_AND_DINING,
            TransactionType.EXPENSE, day(2026, Calendar.OCTOBER, 5));
    private final Transaction rent = tx(150000, "Rent Co", Category.BILLS_AND_UTILITIES,
            TransactionType.EXPENSE, day(2026, Calendar.OCTOBER, 20));
    private final Transaction salary = tx(500000, "Employer", Category.OTHER,
            TransactionType.INCOME, day(2026, Calendar.SEPTEMBER, 28));
    private final List<Transaction> all = Arrays.asList(coffee, rent, salary);

    // ---- search ----

    @Test
    public void emptyFilterMatchesEverything() {
        TransactionFilter f = new TransactionFilter();
        assertTrue(f.isEmpty());
        assertEquals(3, f.apply(all).size());
    }

    @Test
    public void textSearchesMerchantAndCategoryAndAllWordsMustMatch() {
        assertEquals(1, new TransactionFilter().setText("roma").apply(all).size());
        assertEquals(1, new TransactionFilter().setText("BILLS").apply(all).size());
        assertEquals(1, new TransactionFilter().setText("cafe food").apply(all).size());
        assertEquals(0, new TransactionFilter().setText("cafe rent").apply(all).size());
    }

    @Test
    public void textSearchAlsoReadsTheOriginalMessage() {
        Transaction t = tx(100, "Shop", Category.OTHER, TransactionType.EXPENSE, day(2026, 0, 1));
        t.setOriginalSms("AX-BANK", "Spent Rs.1 via UPI ref 99887", 1L);
        assertEquals(1, new TransactionFilter().setText("99887").apply(Arrays.asList(t)).size());
    }

    @Test
    public void filtersByTypeCategoryDateAndAmount() {
        assertEquals(Arrays.asList(salary),
                new TransactionFilter().setType(TransactionType.INCOME).apply(all));
        assertEquals(Arrays.asList(rent),
                new TransactionFilter().setCategoryKey(Category.BILLS_AND_UTILITIES.getKey()).apply(all));
        // October only: from 1 Oct up to (not including) 1 Nov
        List<Transaction> october = new TransactionFilter()
                .setFromMillis(day(2026, Calendar.OCTOBER, 1))
                .setToMillisExclusive(day(2026, Calendar.NOVEMBER, 1)).apply(all);
        assertEquals(Arrays.asList(coffee, rent), october);
        // 5.00 to 10.00
        assertEquals(Arrays.asList(coffee),
                new TransactionFilter().setMinCents(500L).setMaxCents(1000L).apply(all));
        // minimum only
        assertEquals(Arrays.asList(rent, salary),
                new TransactionFilter().setMinCents(100000L).apply(all));
    }

    @Test
    public void conditionsCombine() {
        List<Transaction> r = new TransactionFilter().setType(TransactionType.EXPENSE)
                .setMinCents(1000L).apply(all);
        assertEquals(Arrays.asList(rent), r);
    }

    // ---- CSV ----

    @Test
    public void csvHasHeaderAndOneRowPerTransaction() {
        String csv = CsvExporter.toCsv(all, TimeZone.getTimeZone("UTC"));
        String[] lines = csv.split("\r\n");
        assertEquals(4, lines.length);
        assertTrue(lines[0].startsWith("Date,Type,Merchant,Category,Amount,Currency"));
        assertEquals("2026-10-05 12:00,Expense,Cafe Roma,Food and Dining,5.50,"
                + coffee.getCurrency().name() + ",,,,MANUAL,,", lines[1]);
    }

    @Test
    public void csvQuotesCommasQuotesAndNewlines() {
        Transaction t = tx(100, "Smith, \"Bob\"\nCo", Category.OTHER, TransactionType.EXPENSE, day(2026, 0, 1));
        String csv = CsvExporter.toCsv(Arrays.asList(t), TimeZone.getTimeZone("UTC"));
        assertTrue(csv.contains("\"Smith, \"\"Bob\"\"\nCo\""));
    }

    @Test
    public void csvStopsSpreadsheetFormulas() {
        assertEquals("'=SUM(A1)", CsvExporter.safeText("=SUM(A1)"));
        assertEquals("'+61 400", CsvExporter.safeText("+61 400"));
        assertEquals("Coffee", CsvExporter.safeText("Coffee"));
        assertEquals("", CsvExporter.safeText(null));
    }

    @Test
    public void csvIncludesTheStoredConversion() {
        Transaction t = tx(1000, "Shop", Category.OTHER, TransactionType.EXPENSE, day(2026, 0, 1));
        t.setCurrency(Currency.AUD);
        t.setConversion(Currency.INR, new BigDecimal("67.581"), day(2026, 0, 1), false, 67581);
        String row = CsvExporter.toCsv(Arrays.asList(t), TimeZone.getTimeZone("UTC")).split("\r\n")[1];
        assertTrue(row, row.contains(",10.00,AUD,675.81,INR,67.581,"));
    }

    // ---- PIN and lock ----

    @Test
    public void pinMustBeFourToEightDigits() {
        assertTrue(PinHasher.isValidPin("1234"));
        assertTrue(PinHasher.isValidPin("12345678"));
        assertFalse(PinHasher.isValidPin("123"));
        assertFalse(PinHasher.isValidPin("123456789"));
        assertFalse(PinHasher.isValidPin("12a4"));
        assertFalse(PinHasher.isValidPin(null));
    }

    @Test
    public void hashedPinChecksOutOnlyForTheRightPin() {
        String salt = PinHasher.newSalt();
        String hash = PinHasher.hash("4821", salt);
        assertNotEquals("4821", hash);
        assertTrue(PinHasher.matches("4821", salt, hash));
        assertFalse(PinHasher.matches("4822", salt, hash));
        assertNotEquals(hash, PinHasher.hash("4821", PinHasher.newSalt()));
    }

    @Test
    public void relocksAfterTheGracePeriod() {
        assertFalse(LockPolicy.shouldRelock(1000, 1000 + LockPolicy.GRACE_MILLIS - 1));
        assertTrue(LockPolicy.shouldRelock(1000, 1000 + LockPolicy.GRACE_MILLIS));
    }

    @Test
    public void wrongPinsLockOutGuessingWithGrowingDelay() {
        assertEquals(0, LockPolicy.lockoutRemaining(4, 0, 0));
        assertEquals(30_000, LockPolicy.lockoutRemaining(5, 0, 0));
        assertEquals(10_000, LockPolicy.lockoutRemaining(5, 0, 20_000));
        assertEquals(0, LockPolicy.lockoutRemaining(5, 0, 31_000));
        assertEquals(60_000, LockPolicy.lockoutRemaining(6, 0, 0));
        assertEquals(300_000, LockPolicy.lockoutRemaining(20, 0, 0));
    }
}
