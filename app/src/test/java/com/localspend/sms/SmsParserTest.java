package com.localspend.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.localspend.model.TransactionType;

import org.junit.Test;

public class SmsParserTest {

    private static final long NOW = 1_788_000_000_000L;
    private final SmsParser parser = SmsParserChain.defaultChain();

    private ParsedTransaction parse(String body) {
        return parser.parse(body, NOW);
    }

    @Test
    public void cardPurchaseWithBalance() {
        ParsedTransaction p = parse("Your card ending 1234 was used for $25.50 at WOOLWORTHS SYDNEY "
                + "on 28/08/2026. Avail bal $1,200.00");
        assertNotNull(p);
        assertEquals(2550, p.getAmountCents());
        assertEquals("Woolworths Sydney", p.getMerchant());
        assertEquals(TransactionType.EXPENSE, p.getType());
        // The date written in the message (28 Aug) wins over the arrival time (29 Aug).
        assertEquals("2026-08-28", com.localspend.util.Dates.isoDay(p.getDateMillis()));
    }

    @Test
    public void spentAtMerchant() {
        ParsedTransaction p = parse("CommBank: You spent $12.40 at Starbucks Central. Avail bal $500.");
        assertNotNull(p);
        assertEquals(1240, p.getAmountCents());
        assertEquals("Starbucks Central", p.getMerchant());
    }

    @Test
    public void debitedToMerchantRupees() {
        ParsedTransaction p = parse("Rs 500.00 debited from A/c XX1234 on 28-08-26 to Zomato. "
                + "Avl Bal Rs 10,000.00");
        assertNotNull(p);
        assertEquals(50000, p.getAmountCents());
        assertEquals("Zomato", p.getMerchant());
        assertEquals(TransactionType.EXPENSE, p.getType());
    }

    @Test
    public void audPrefixAndCardSuffix() {
        ParsedTransaction p = parse("Purchase of AUD 89.99 at AMAZON AU MARKETPLACE on card ending 4455.");
        assertNotNull(p);
        assertEquals(8999, p.getAmountCents());
        assertEquals("Amazon Au Marketplace", p.getMerchant());
    }

    @Test
    public void timeBeforeMerchantIsSkipped() {
        ParsedTransaction p = parse("Spent $5 at 10:45 at KFC");
        assertNotNull(p);
        assertEquals(500, p.getAmountCents());
        assertEquals("Kfc", p.getMerchant());
    }

    @Test
    public void rupeeSymbolAndMixedCaseMerchant() {
        ParsedTransaction p = parse("Spent ₹250 at Cafe Coffee Day on 28-08.");
        assertNotNull(p);
        assertEquals(25000, p.getAmountCents());
        assertEquals("Cafe Coffee Day", p.getMerchant());
    }

    @Test
    public void paymentSuccessfulToBiller() {
        ParsedTransaction p = parse("Payment of $120.00 successful to TELSTRA");
        assertNotNull(p);
        assertEquals(12000, p.getAmountCents());
        assertEquals("Telstra", p.getMerchant());
        assertEquals(TransactionType.EXPENSE, p.getType());
    }

    @Test
    public void salaryDepositIsIncome() {
        ParsedTransaction p = parse("You have received $2,500.00 from ACME PTY LTD. Thank you");
        assertNotNull(p);
        assertEquals(250000, p.getAmountCents());
        assertEquals("Acme Pty Ltd", p.getMerchant());
        assertEquals(TransactionType.INCOME, p.getType());
    }

    @Test
    public void refundIsIncome() {
        ParsedTransaction p = parse("Refund of $30.00 from AMAZON has been credited to your account.");
        assertNotNull(p);
        assertEquals(3000, p.getAmountCents());
        assertEquals("Amazon", p.getMerchant());
        assertEquals(TransactionType.INCOME, p.getType());
    }

    @Test
    public void incomeWithoutMerchantGetsDefaultLabel() {
        ParsedTransaction p = parse("Your account has been credited with $100.00.");
        assertNotNull(p);
        assertEquals(TransactionType.INCOME, p.getType());
        assertEquals("Deposit", p.getMerchant());
    }

    @Test
    public void purchaseWithoutMerchantGetsDefaultLabel() {
        ParsedTransaction p = parse("$45.00 was debited from your account.");
        assertNotNull(p);
        assertEquals("Unknown merchant", p.getMerchant());
    }

    @Test
    public void otpIsIgnored() {
        assertNull(parse("Your OTP is 482913. Do not share it. Amount $45.00 at SHOP"));
    }

    @Test
    public void billReminderIsIgnored() {
        assertNull(parse("Your electricity bill of $120.00 is due on 5 Sep"));
    }

    @Test
    public void declinedTransactionIsIgnored() {
        assertNull(parse("Transaction of $50.00 at COLES was declined"));
    }

    @Test
    public void promoWithoutDebitIsIgnored() {
        assertNull(parse("Get $50 off when you spend $200 at our store this weekend!"));
    }

    @Test
    public void messageWithoutAmountIsIgnored() {
        assertNull(parse("Your card was used at WOOLWORTHS"));
    }

    @Test
    public void nullAndEmptyAreIgnored() {
        assertNull(parse(null));
        assertNull(parse("   "));
    }

    @Test
    public void balanceIsNeverTheAmount() {
        ParsedTransaction p = parse("Avl Bal $900.00. Spent $15.00 at BP EXPRESS BONDI.");
        assertNotNull(p);
        assertEquals(1500, p.getAmountCents());
        assertEquals("Bp Express Bondi", p.getMerchant());
    }

    @Test
    public void smsKeyIsStableAndDiffersByBody() {
        String a = SmsKeys.keyFor("BANK", "Spent $5 at X", NOW);
        String b = SmsKeys.keyFor("BANK", "Spent $5 at X", NOW + 1000);
        String c = SmsKeys.keyFor("BANK", "Spent $6 at X", NOW);
        assertEquals(a, b);
        assertEquals(64, a.length());
        org.junit.Assert.assertNotEquals(a, c);
    }
}
