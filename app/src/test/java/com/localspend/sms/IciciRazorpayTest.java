package com.localspend.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.localspend.logic.CategorisationEngine;
import com.localspend.model.Category;
import com.localspend.model.Currency;

import org.junit.Test;

import java.util.Calendar;

/** An ICICI card alert where the shop name follows the date and is prefixed by the gateway. */
public class IciciRazorpayTest {

    private static final String SMS = "INR 2,131.11 spent using ICICI Bank Card XX5004 on 29-Jun-26 "
            + "on RAZ*Zomato Onli. Avl Limit: INR 11,123.77. If not you, call 1800 2662/SMS BLOCK "
            + "5004 to 9215676766.";

    private static long arrival() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.JUNE, 29, 18, 40, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void readsAmountCurrencyAndShop() {
        ParsedTransaction p = SmsParserChain.defaultChain().parse(SMS, arrival());
        assertNotNull(p);
        assertEquals(213111, p.getAmountCents());          // not the "Avl Limit" figure
        assertEquals(Currency.INR, p.getCurrency());
        assertEquals("Zomato Onli", p.getMerchant());
        assertTrue(p.isMerchantFound());
    }

    @Test
    public void dateIsTheDayInTheMessage() {
        ParsedTransaction p = SmsParserChain.defaultChain().parse(SMS, arrival());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(p.getDateMillis());
        assertEquals(29, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JUNE, c.get(Calendar.MONTH));
    }

    @Test
    public void isFoodAndTheSenderIsAKnownBank() {
        ParsedTransaction p = SmsParserChain.defaultChain().parse(SMS, arrival());
        assertEquals(Category.FOOD_AND_DINING,
                CategorisationEngine.defaultEngine().categorise(p.getMerchant(), SMS));
        assertTrue(SenderDirectory.isKnownBank("BT-ICICIT-S"));
    }

    @Test
    public void gatewayPrefixesAreRemovedFromOtherShopsToo() {
        String sms = "Rs 450.00 spent on your card XX1234 at PAYU*Swiggy. Avl Limit Rs 5000.";
        ParsedTransaction p = SmsParserChain.defaultChain().parse(sms, arrival());
        assertNotNull(p);
        assertEquals("Swiggy", p.getMerchant());
    }
}
