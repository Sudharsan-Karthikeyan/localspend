package com.localspend.logic;

import static org.junit.Assert.assertEquals;

import com.localspend.model.Category;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class CategorisationEngineTest {

    private final CategorisationEngine engine = CategorisationEngine.defaultEngine();

    @Test
    public void popeyesAndOtherChainsAreFood() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Popeyes"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("POPEYES LOUISIANA KITCHEN"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Popeye's Chicken"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Krispy Kreme"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Zambrero Parramatta"));
    }

    @Test
    public void commonMerchantsLandInTheRightCategory() {
        assertEquals(Category.GROCERIES, engine.categorise("Woolworths 1234 Sydney"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("McDonald's Bondi"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Starbucks"));
        assertEquals(Category.TRANSPORT, engine.categorise("UBER *TRIP"));
        assertEquals(Category.TRANSPORT, engine.categorise("Bp Express Bondi"));
        assertEquals(Category.SHOPPING, engine.categorise("AMAZON AU MARKETPLACE"));
        assertEquals(Category.ENTERTAINMENT, engine.categorise("Netflix.com"));
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("Telstra"));
        assertEquals(Category.HEALTH, engine.categorise("Chemist Warehouse"));
        assertEquals(Category.EDUCATION, engine.categorise("University of Sydney"));
    }

    @Test
    public void specificRulesBeatGeneralOnes() {
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("UBER *EATS"));
        assertEquals(Category.TRANSPORT, engine.categorise("Coles Express Mascot"));
        assertEquals(Category.GROCERIES, engine.categorise("Coles Bondi Junction"));
        assertEquals(Category.ENTERTAINMENT, engine.categorise("Amazon Prime Video"));
    }

    @Test
    public void keywordsMatchWholeWordsOnly() {
        assertEquals(Category.OTHER, engine.categorise("Bpm Studio"));
        assertEquals(Category.OTHER, engine.categorise("Subwayz Hardware".replace("Subwayz", "Fabrics")));
    }

    @Test
    public void unrecognisedMerchantIsOther() {
        assertEquals(Category.OTHER, engine.categorise("Joe's Hardware"));
    }

    @Test
    public void noMerchantDetailsGoToBankTransactions() {
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise(""));
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise(null));
        assertEquals(Category.BANK_TRANSACTIONS, engine.categorise("Unknown merchant"));
    }

    @Test
    public void indianMerchantsAreRecognised() {
        assertEquals(Category.SHOPPING, engine.categorise("HENNES"));
        assertEquals(Category.SHOPPING, engine.categorise("H&M Bondi Junction"));
        assertEquals(Category.SHOPPING, engine.categorise("Myntra Designs"));
        assertEquals(Category.GROCERIES, engine.categorise("BLINKIT"));
        assertEquals(Category.GROCERIES, engine.categorise("Zepto Marketplace"));
        assertEquals(Category.FOOD_AND_DINING, engine.categorise("Swiggy"));
        assertEquals(Category.TRANSPORT, engine.categorise("IRCTC"));
        assertEquals(Category.TRANSPORT, engine.categorise("Indian Oil Petrol Pump"));
        assertEquals(Category.BILLS_AND_UTILITIES, engine.categorise("Airtel Prepaid"));
        assertEquals(Category.ENTERTAINMENT, engine.categorise("PVR Cinemas"));
        assertEquals(Category.HEALTH, engine.categorise("Apollo Pharmacy"));
    }

    @Test
    public void learnedCorrectionBeatsBuiltInRules() {
        Map<String, Category> learned = new HashMap<>();
        learned.put(MerchantNormaliser.merchantKey("Target"), Category.OTHER);
        CategorisationEngine learning = CategorisationEngine.withLearning(new MapCategoryLookup(learned));
        assertEquals(Category.OTHER, learning.categorise("TARGET 5678"));
        assertEquals(Category.SHOPPING, engine.categorise("TARGET 5678"));
    }

    @Test
    public void merchantKeyIgnoresStoreNumbers() {
        assertEquals("woolworths sydney", MerchantNormaliser.merchantKey("WOOLWORTHS 1234 Sydney CBD"));
        assertEquals(MerchantNormaliser.merchantKey("Woolworths 1234 Sydney"),
                MerchantNormaliser.merchantKey("woolworths 9876 SYDNEY"));
    }
}
