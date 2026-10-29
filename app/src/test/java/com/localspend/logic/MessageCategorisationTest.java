package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.sms.ParsedTransaction;
import com.localspend.sms.PurchaseSmsParser;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Categories are read from the whole SMS, not just the extracted merchant. */
public class MessageCategorisationTest {

    private final CategorisationEngine engine = CategorisationEngine.defaultEngine();

    private static final String HENNES = "ICICI Bank Credit Card XX6005 debited for INR 1,999.00 on "
            + "19-Aug-26 for UPI-312824365410-HENNES. To dispute call 18001080/SMS BLOCK 6005 to 9215676766";

    @Test
    public void shopNamedOnlyInTheMessageDecidesTheCategory() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Unknown merchant",
                "Your card ending 1234 was used for AUD 18.50 at the Popeyes drive through"));
        assertEquals(Category.SHOPPING, engine.categorise("Unknown merchant", HENNES));
    }

    @Test
    public void merchantStillWinsWhenItIsRecognised() {
        // The message mentions Swiggy in passing, but the merchant is Woolworths.
        assertEquals(Category.GROCERIES, engine.categorise("Woolworths",
                "Spent Rs 450 at Woolworths. Order earlier via Swiggy was refunded"));
    }

    @Test
    public void unrecognisedMerchantFallsBackToTheMessage() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Pty Ltd 4412",
                "Purchase of $12.40 at Pty Ltd 4412 - kebab and chicken wings"));
    }

    @Test
    public void nothingUsefulInTheMessageStaysBankTransactionsOrOther() {
        String atm = "Your account XX1234 debited Rs 5000 on 12-Aug-26. Avl Bal Rs 20000. "
                + "To dispute call 1800 1234.";
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("Unknown merchant", atm));
        assertEquals(Category.OTHER, engine.categorise("S Karthikeyan",
                "Payment made Rs 500 To: S KARTHIKEYAN Description: Vinoop Reference: Friend"));
    }

    @Test
    public void bankNamesAndFeesInTheSmallPrintDoNotDecide() {
        // "Airtel" is a Bills keyword and "fees" an Education keyword; neither is the purchase.
        String text = "Airtel Payments Bank: Rs 250 debited from your account. Fees Rs 0.";
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("Unknown merchant", text));
    }

    @Test
    public void bankBalanceAndLimitSentencesAreIgnored() {
        String text = "Spent AUD 30 on card 1234. Available limit for petrol is high.";
        // 'petrol' sits in a limit sentence, which is dropped.
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("Unknown merchant", text));
    }

    @Test
    public void mostSpecificKeywordInTheMessageWins() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Unknown merchant",
                "Paid via UPI to Uber Eats order"));   // not Transport's "uber"
    }

    @Test
    public void userKeywordsApplyToTheWholeMessage() {
        Map<String, Category> mine = new HashMap<>();
        mine.put("vinoop", Category.EDUCATION);
        CategorisationEngine custom = CategorisationEngine.build(null, null, mine, true);
        assertEquals(Category.EDUCATION, custom.categorise("S Karthikeyan",
                "Payment made Rs 500 To: S KARTHIKEYAN Description: Vinoop Reference: Friend"));
    }

    @Test
    public void parsedPurchaseIsCategorisedFromItsMessage() {
        String sms = "Payment of $23.10 completed. Merchant ref 88231. Popeyes Louisiana Kitchen Bondi";
        ParsedTransaction parsed = new PurchaseSmsParser().parse(sms, 0L);
        assertTrue(parsed != null);
        assertEquals(Category.FOOD_AND_DINING, engine.categorise(parsed.getMerchant(), sms));
    }

    @Test
    public void cleaningRemovesAmountsDatesAndBankNames() {
        String cleaned = MessageText.forCategorising(HENNES);
        assertTrue(cleaned, cleaned.contains("hennes"));
        assertTrue(cleaned, !cleaned.contains("icici") && !cleaned.contains("1999")
                && !cleaned.contains("dispute") && !cleaned.contains("6005"));
    }
}
