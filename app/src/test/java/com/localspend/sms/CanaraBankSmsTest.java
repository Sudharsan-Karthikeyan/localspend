package com.localspend.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Currency;
import com.localspend.model.TransactionType;

import org.junit.Test;

import java.util.Calendar;

/** Canara Bank UPI alerts say "Dr." / "Cr." instead of "debited" / "credited". */
public class CanaraBankSmsTest {

    private static final String DEBIT = "Dear Customer, Acct XXXX0751 Dr. INR 1.00 on 27/10/26 to "
            + "VENKATACHALA; UPI: 317818311618; Bal INR 3.51.Not you?SMS BLOCKUPI to 9901771222-Canara Bank";
    private static final String CREDIT = "Dear Customer, Acct XXXX0751 Cr. INR 500.00 on 27/10/26 "
            + "from RAHUL KUMAR; UPI: 317818311619; Bal INR 503.51.-Canara Bank";

    private static long arrival() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 27, 23, 25, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void drMeansMoneyOut() {
        ParsedTransaction p = SmsParserChain.defaultChain().parse(DEBIT, arrival());
        assertNotNull(p);
        assertEquals(TransactionType.EXPENSE, p.getType());
        assertEquals(100, p.getAmountCents());           // not the balance
        assertEquals(Currency.INR, p.getCurrency());
        assertEquals("Venkatachala", p.getMerchant());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(p.getDateMillis());
        assertEquals(27, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.OCTOBER, c.get(Calendar.MONTH));
    }

    @Test
    public void crMeansMoneyIn() {
        ParsedTransaction p = SmsParserChain.defaultChain().parse(CREDIT, arrival());
        assertNotNull(p);
        assertEquals(TransactionType.INCOME, p.getType());
        assertEquals(50000, p.getAmountCents());
        assertEquals("Rahul Kumar", p.getMerchant());
    }

    @Test
    public void senderIsRecognised() {
        assertTrue(SenderDirectory.isKnownBank("AX-CANBNK-S"));
    }

    @Test
    public void aBalanceEnquiryIsStillNotATransaction() {
        assertEquals(null, SmsParserChain.defaultChain().parse(
                "Dear Customer, your A/c XXXX0751 balance is INR 3.51. -Canara Bank", arrival()));
    }
}
