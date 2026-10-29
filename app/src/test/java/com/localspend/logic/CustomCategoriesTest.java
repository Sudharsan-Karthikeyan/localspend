package com.localspend.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomCategoriesTest {

    private static final Category PETS = new Category("CUSTOM_PETS", "Pets", 0xFF795548, false, 0);

    private static CategoryCatalog catalogWith(Category... extra) {
        List<Category> list = new ArrayList<>(Category.defaults());
        list.addAll(Arrays.asList(extra));
        return new CategoryCatalog(list);
    }

    @Test
    public void catalogKeepsOtherLastAndFallsBackToIt() {
        CategoryCatalog catalog = catalogWith(PETS);
        List<Category> all = catalog.all();
        assertEquals(Category.OTHER, all.get(all.size() - 1));
        assertEquals(PETS, all.get(all.size() - 2));
        assertEquals(Category.OTHER, catalog.find("CUSTOM_DELETED"));
        assertEquals(Category.OTHER, catalog.find(null));
        assertEquals(PETS, catalog.findByLabel("  pets "));
    }

    @Test
    public void renamedCategoryIsStillTheSameCategory() {
        Category renamed = Category.GROCERIES.withLabel("Supermarket").withColor(0xFF000000);
        assertEquals(Category.GROCERIES, renamed);
        assertEquals("Supermarket", renamed.getLabel());
        CategoryCatalog catalog = new CategoryCatalog(Arrays.asList(renamed, Category.OTHER));
        // The engine's built-in rule returns the default object; the catalog gives the new name.
        CategorisationEngine engine = CategorisationEngine.build(catalog, null, null, true);
        assertEquals("Supermarket", engine.categorise("Woolworths Bondi").getLabel());
    }

    @Test
    public void userKeywordsBeatBuiltInsAndLongestWins() {
        Map<String, Category> keywords = new HashMap<>();
        keywords.put("vet", PETS);
        keywords.put("petbarn", PETS);
        keywords.put("coles", Category.SHOPPING);            // move a built-in keyword
        keywords.put("coles express", Category.TRANSPORT);
        CategorisationEngine engine = CategorisationEngine.build(catalogWith(PETS), null, keywords, true);
        assertEquals(PETS, engine.categorise("PETBARN ALEXANDRIA"));
        assertEquals(PETS, engine.categorise("Bondi Vet Hospital"));       // beats built-in "hospital"
        assertEquals(Category.SHOPPING, engine.categorise("Coles Bondi"));
        assertEquals(Category.TRANSPORT, engine.categorise("Coles Express Mascot"));
    }

    @Test
    public void learnedMerchantBeatsUserKeywords() {
        Map<String, Category> keywords = new HashMap<>();
        keywords.put("vet", PETS);
        Map<String, Category> learned = new HashMap<>();
        learned.put(MerchantNormaliser.merchantKey("Bondi Vet"), Category.HEALTH);
        CategorisationEngine engine = CategorisationEngine.build(catalogWith(PETS),
                new MapCategoryLookup(learned), keywords, true);
        assertEquals(Category.HEALTH, engine.categorise("BONDI VET 123"));
        assertEquals(PETS, engine.categorise("City Vet"));
    }

    @Test
    public void builtInKeywordsCanBeSwitchedOff() {
        CategorisationEngine engine = CategorisationEngine.build(catalogWith(), null, null, false);
        assertEquals(Category.OTHER, engine.categorise("Woolworths"));
    }

    @Test
    public void rulesForDeletedCategoriesAreSkipped() {
        List<Category> list = new ArrayList<>(Category.defaults());
        list.remove(Category.FOOD_AND_DINING);
        CategorisationEngine engine = CategorisationEngine.build(new CategoryCatalog(list), null, null, true);
        // "Uber Eats" was Food; with Food deleted, the next matching rule (Transport: "uber") wins.
        assertEquals(Category.TRANSPORT, engine.categorise("UBER EATS"));
        assertEquals(Category.OTHER, engine.categorise("Starbucks"));
    }

    @Test
    public void builtInKeywordListIsAvailableForTheEditor() {
        assertTrue(CategorisationEngine.builtInKeywordsFor(Category.GROCERIES).contains("woolworths"));
        assertTrue(CategorisationEngine.builtInKeywordsFor(PETS).isEmpty());
    }

    @Test
    public void customCategoriesGetUniqueKeysAndUnusedColours() {
        Category a = Category.createCustom("Car", 0xFF000000);
        Category b = Category.createCustom("Car", 0xFF000000);
        assertNotEquals(a.getKey(), b.getKey());
        assertFalse(a.isBuiltIn());
        int suggested = catalogWith().suggestColor();
        for (Category c : Category.defaults()) {
            assertNotEquals(c.getColor(), suggested);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankNameIsRejected() {
        Category.OTHER.withLabel("  ");
    }

    @Test
    public void otherIsAlwaysPresent() {
        CategoryCatalog catalog = new CategoryCatalog(Arrays.asList(PETS));
        assertSame(Category.OTHER, catalog.getOther());
        assertEquals(2, catalog.size());
    }
}
