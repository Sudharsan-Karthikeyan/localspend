package com.localspend.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.localspend.logic.CategorisationEngine;
import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.TransactionType;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import org.junit.Test;

public class CurrencyAndUpiTest {

    private static final long NOW = 1_787_300_000_000L;   // 21 Aug 2026
    private final SmsParser parser = SmsParserChain.defaultChain();

    private static final String ICICI = "ICICI Bank Credit Card XX6005 debited for INR 1,999.00 on "
            + "19-Aug-26 for UPI-312824365410-HENNES. To dispute call 18001080/SMS BLOCK 6005 to 9215676766";

    @Test
    public void iciciUpiMessageGivesRupeesMerchantAndShopping() {
        ParsedTransaction p = parser.parse(ICICI, NOW);
        assertNotNull(p);
        assertEquals(199900, p.getAmountCents());
        assertEquals(Currency.INR, p.getCurrency());
        assertEquals("Hennes", p.getMerchant());
        assertTrue(p.isMerchantFound());
        assertEquals("2026-08-19", Dates.isoDay(p.getDateMillis()));
        assertEquals(Category.SHOPPING, CategorisationEngine.defaultEngine().categorise(p.getMerchant()));
        assertTrue(SenderDirectory.isKnownBank("AX-ICICIT-S"));
    }

    @Test
    public void currencyFollowsTheMarker() {
        assertEquals(Currency.INR, parser.parse("Rs.500.00 debited from A/c XX12 to Zomato", NOW).getCurrency());
        assertEquals(Currency.INR, parser.parse("You spent ₹250 at Cafe Coffee Day", NOW).getCurrency());
        assertEquals(Currency.AUD, parser.parse("You spent $12.40 at Starbucks", NOW).getCurrency());
        assertEquals(Currency.AUD, parser.parse("Purchase of AUD 89.99 at AMAZON AU", NOW).getCurrency());
        assertEquals(Currency.AUD, parser.parse("Purchase of 89.99 AUD at AMAZON AU", NOW).getCurrency());
    }

    @Test
    public void upiFormats() {
        assertEquals("Amazon Pay", parser.parse(
                "Rs 349.00 debited from A/c XX1234 on 20-08-26. Info: UPI/P2M/512345678901/AMAZON PAY/UPI",
                NOW).getMerchant());
        assertEquals("zomato", parser.parse(
                "Sent Rs.250.00 from Kotak Bank AC X1234 to zomato@hdfcbank on 20-08-26. UPI Ref 123456789012",
                NOW).getMerchant());
        ParsedTransaction in = parser.parse(
                "Rs.1000.00 credited to A/c XX1234 on 20-08-26 by UPI/CR/312345678901/RAVI KUMAR/SBIN", NOW);
        assertEquals(TransactionType.INCOME, in.getType());
        assertEquals("Ravi Kumar", in.getMerchant());
    }

    @Test
    public void noMerchantIsFlagged() {
        ParsedTransaction p = parser.parse("Rs.500.00 debited from your A/c XX1234 on 20-08-26.", NOW);
        assertNotNull(p);
        assertFalse(p.isMerchantFound());
        assertEquals(Category.BANK_TRANSACTIONS,
                CategorisationEngine.defaultEngine().categorise(p.getMerchant()));
    }

    @Test
    public void formattingUsesTheRightSymbolAndGrouping() {
        assertEquals("₹1,999.00", MoneyFormatter.format(199900, Currency.INR));
        assertEquals("₹1,00,000.00", MoneyFormatter.format(10000000, Currency.INR));
        assertEquals("₹12,34,567.00", MoneyFormatter.format(123456700, Currency.INR));
        assertEquals("$100,000.00", MoneyFormatter.format(10000000, Currency.AUD));
        assertEquals("₹1,250", MoneyFormatter.formatWhole(125049, Currency.INR));
        assertEquals(199900, MoneyFormatter.parseToCents("₹1,999"));
        assertEquals(199900, MoneyFormatter.parseToCents("Rs. 1,999.00"));
    }

    @Test
    public void banksFromBothCountriesAreRecognised() {
        String[] banks = {"VM-HDFCBK", "JD-SBIINB-S", "AD-KOTAKB", "BZ-AXISBK-T", "VK-PNBSMS",
                "AX-CANBNK", "TM-IDFCFB", "JM-AUBANK", "VM-PAYTMB", "AD-FEDBNK", "AX-UJJIVN",
                "VM-NEWBNK-S", "AD-XYZSFB", "CommBank", "NAB", "Westpac", "ANZ", "StGeorge",
                "BankSA", "Bankwest", "ING", "Macquarie", "Suncorp", "BOQ", "BendigoBank", "UBank",
                "HumeBank", "BankVic", "NewcastlePM", "PeoplesChoice", "GreatSouthern", "Heritagebank"};
        for (String b : banks) {
            assertTrue(b, SenderDirectory.isKnownBank(b));
        }
        assertFalse(SenderDirectory.isKnownBank("AD-SWIGGY"));
        assertFalse(SenderDirectory.isKnownBank("Everlab"));
        assertFalse(SenderDirectory.isKnownBank("0412345678"));
    }
}
