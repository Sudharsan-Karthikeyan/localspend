package com.localspend.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.localspend.model.TransactionType;
import com.localspend.util.Dates;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class SenderAndPromotionTest {

    private static final long NOW = 1_789_000_000_000L;   // mid September 2026
    private final SmsParser parser = SmsParserChain.defaultChain();

    private static final String EVERLAB = "Hi Sudharsan, it's the Everlab Team. You started customising "
            + "your baseline plan but didn't finish, it's saved and ready when you are.\n\n"
            + "We've also included ApoB, a $100 heart risk marker that standard blood panels miss. "
            + "It's free on us for the next 24 hours.\n\nGetting started costs $299, and if it's not "
            + "for you, we'll refund you completely.\n\nPick up here:\n\nhttps://go.everlab.com.au/pick-up"
            + "\n\nOr chat with us first:\n\nhttps://go.everlab.com.au/chat-first";

    private static final String PAYMENT = "Payment made\nAmount: $200.00\nReceipt#: N890962345318\n"
            + "To: S KARTHIKEYAN (+61-402377715)\nDescription: Vinoop\nReference: Friend\n\n"
            + "Date: 09 Sep 2026 05:53 PM";

    @Test
    public void advertIsNotATransaction() {
        assertTrue(AbstractSmsParser.isPromotion(EVERLAB));
        assertNull(parser.parse(EVERLAB, NOW));
    }

    @Test
    public void paymentMadeReadsPayeeAmountAndDate() {
        ParsedTransaction p = parser.parse(PAYMENT, NOW);
        assertNotNull(p);
        assertEquals(20000, p.getAmountCents());
        assertEquals("S Karthikeyan", p.getMerchant());
        assertEquals(TransactionType.EXPENSE, p.getType());
        assertEquals("2026-09-09", Dates.isoDay(p.getDateMillis()));
    }

    @Test
    public void shopNamesThatLookLikeAdvertWordsStillWork() {
        assertFalse(AbstractSmsParser.isPromotion("You spent $12.00 at Sale St Cafe. Avail bal $100"));
        assertNotNull(parser.parse("You spent $12.00 at Free Range Butcher on 10/09/2026", NOW));
        assertTrue(AbstractSmsParser.isPromotion("Big sale! 50% off everything at www.shop.com/x"));
        assertTrue(AbstractSmsParser.isPromotion("Mega deal today only https://x.co/abc spend $50"));
    }

    @Test
    public void merchantStopsAtBracketsAndLabels() {
        ParsedTransaction p = parser.parse("Paid to: JOHN SMITH (BSB 062-000) Amount: $45.00", NOW);
        assertNotNull(p);
        assertEquals("John Smith", p.getMerchant());
    }

    @Test
    public void bankSenderIdsAreRecognised() {
        assertTrue(SenderDirectory.isKnownBank("CommBank"));
        assertTrue(SenderDirectory.isKnownBank("NAB"));
        assertTrue(SenderDirectory.isKnownBank("VM-HDFCBK"));
        assertTrue(SenderDirectory.isKnownBank("AD-SBIINB-S"));
        assertTrue(SenderDirectory.isKnownBank("Westpac"));
        assertFalse(SenderDirectory.isKnownBank("Everlab"));
        assertFalse(SenderDirectory.isKnownBank("+61402377715"));
        assertFalse(SenderDirectory.isKnownBank(""));
        assertFalse(SenderDirectory.isKnownBank(null));
        assertTrue(SenderDirectory.isPhoneNumber("+61 402 377 715"));
        assertEquals("HDFCBK", SenderDirectory.normalise("vm-hdfcbk"));
    }

    @Test
    public void userChoicesOverrideTheBuiltInList() {
        SenderVerifier v = new SenderVerifier(
                new HashSet<>(Collections.singletonList(SenderDirectory.normalise("+61 400 000 000"))),
                new HashSet<>(Arrays.asList("NAB")));
        assertEquals(SenderVerifier.Verdict.TRUSTED, v.verdict("+61400000000"));
        assertEquals(SenderVerifier.Verdict.BLOCKED, v.verdict("NAB"));
        assertEquals(SenderVerifier.Verdict.TRUSTED, v.verdict("CommBank"));
        assertEquals(SenderVerifier.Verdict.UNKNOWN, v.verdict("Everlab"));
    }
}
