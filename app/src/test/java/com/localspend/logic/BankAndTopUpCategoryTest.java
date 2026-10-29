package com.localspend.logic;

import static org.junit.Assert.assertEquals;

import com.localspend.model.Category;

import org.junit.Test;

public class BankAndTopUpCategoryTest {

    private final CategorisationEngine engine = CategorisationEngine.defaultEngine();

    @Test
    public void myJioIsBillsAndUtilities() {
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("Myjio"));
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("MyJio Recharge"));
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("Jio"));
    }

    @Test
    public void bankNamedMerchantsAreBankTransactions() {
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("ICICI Bank"));
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("Canara Bank"));
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("HDFC"));
    }

    @Test
    public void specificCategoriesStillBeatTheBankRule() {
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("HDFC Ergo"));
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("ICICI Lombard"));
    }

    @Test
    public void incomeFromABankIsABankTransactionNotOther() {
        assertEquals(Category.BANK_TRANSACTIONS, engine.categoriseIncome("ICICI Bank", "credited Rs 1.00"));
        assertEquals(Category.BANK_TRANSACTIONS, engine.categoriseIncome("Unknown", null));
    }

    @Test
    public void paymentToAPersonOverUpiIsABankTransfer() {
        assertEquals(Category.BANK_TRANSACTIONS,
                engine.categorise("Sudharsan K", "Sent Rs.1.00 via UPI to Sudharsan K"));
        assertEquals(Category.OTHER, engine.categorise("Sudharsan K", "Paid Rs.1.00 to Sudharsan K"));
    }

    @Test
    public void namesTheBankCutOffAreStillRecognised() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Samco Restauran"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Gourmet Bakes S"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Bundl Technolog"));
        assertEquals(Category.ENTERTAINMENT, engine.categorise("Big Tree Entert"));
    }

    @Test
    public void shopsAndServicesFromTheScreenshots() {
        assertEquals(Category.EDUCATION, engine.categorise("UniofSydStudent"));
        assertEquals(Category.HEALTH, engine.categorise("Maxivision Eye"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Nahdi Private L"));
        assertEquals(Category.SHOPPING, engine.categorise("Bath Body Work"));
        assertEquals(Category.SHOPPING, engine.categorise("Zivame VR Mall"));
        assertEquals(Category.SHOPPING, engine.categorise("Urban Monkey Pr"));
        assertEquals(Category.SHOPPING, engine.categorise("Reliance Retail"));
    }

    @Test
    public void cutOffMatchingNeedsFiveLettersAndAKeywordStart() {
        assertEquals(Category.OTHER, engine.categorise("Rest"));        // too short a stub
        assertEquals(Category.OTHER, engine.categorise("Xyzzy Foo"));
    }

    @Test
    public void bankNameInTheMessageDoesNotCaptureAnUnknownShopExpense() {
        assertEquals(Category.OTHER, engine.categorise("Qwerty Pvt",
                "Spent Rs.100 at Qwerty Pvt on card. -Canara Bank"));
    }
}
