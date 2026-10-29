package com.localspend.logic;

import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rule-based categorisation. Rules run in order and the first match wins, so more specific rules
 * must come first (for example "Uber Eats" is Food, which is why Food is listed before Transport
 * where plain "Uber" lives).
 *
 * The full order used by the app is:
 * 1. merchants the user has corrected before ({@link LearnedMerchantRule}),
 * 2. keywords the user has added to a category ({@link UserKeywordRule}),
 * 3. the built-in keyword lists ({@link KeywordRule}), which the user can switch off.
 *
 * When the merchant name is not enough, the whole SMS is searched (see {@link MessageText}).
 * Messages that still match nothing and name no merchant go to "Bank Transactions"; a merchant
 * no rule recognises goes to "Other".
 *
 * When a catalog is given, results are resolved against it, so a renamed category comes back with
 * its new name and colour, and rules pointing at a deleted category are skipped.
 */
public class CategorisationEngine {

    private final List<CategorisationRule> rules;
    private final CategoryCatalog catalog;   // null means "no custom categories, use as-is"

    public CategorisationEngine(List<CategorisationRule> rules) {
        this(rules, null);
    }

    public CategorisationEngine(List<CategorisationRule> rules, CategoryCatalog catalog) {
        this.rules = new ArrayList<>(rules);
        this.catalog = catalog;
    }

    /** Category from the merchant name alone (typing a transaction by hand). */
    public Category categorise(String merchant) {
        return categorise(merchant, null);
    }

    /**
     * Category for a transaction read from a message. The merchant name is tried first; when no
     * rule recognises it (or the message gave no merchant) the whole message text is searched, so
     * a shop named anywhere in the SMS still decides the category. If nothing matches, a message
     * with no merchant is "Bank Transactions" and anything else is "Other".
     *
     * @param messageText the original SMS, or null when there is none
     */
    public Category categorise(String merchant, String messageText) {
        boolean hasMerchant = !(merchant == null || merchant.trim().isEmpty()
                || Transaction.UNKNOWN_MERCHANT.equalsIgnoreCase(merchant.trim()));
        if (hasMerchant) {
            Category byMerchant = matchMerchant(merchant);
            if (byMerchant != null) {
                return byMerchant;
            }
        }
        if (messageText != null && !messageText.trim().isEmpty()) {
            Category byMessage = matchMessage(messageText);
            if (byMessage != null) {
                return byMessage;
            }
        }
        // A payment to a person over UPI/IMPS/NEFT/PayID names no shop: it is a bank transfer.
        if (hasMerchant && isBankTransfer(messageText)) {
            return noMerchantCategory();
        }
        return hasMerchant ? fallback() : noMerchantCategory();
    }

    /**
     * Category for money coming in. Recognised names (a bank, an employer keyword) are matched the
     * same way; anything else is a "Bank Transactions" credit rather than "Other".
     */
    public Category categoriseIncome(String merchant, String messageText) {
        boolean hasMerchant = !(merchant == null || merchant.trim().isEmpty()
                || Transaction.UNKNOWN_MERCHANT.equalsIgnoreCase(merchant.trim()));
        if (hasMerchant) {
            Category byMerchant = matchMerchant(merchant);
            if (byMerchant != null) {
                return byMerchant;
            }
        }
        return noMerchantCategory();
    }

    private static boolean isBankTransfer(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(java.util.Locale.ROOT);
        return t.contains("upi") || t.contains("imps") || t.contains("neft") || t.contains("rtgs")
                || t.contains("transfer") || t.contains("payid") || t.contains("osko");
    }

    private Category matchMerchant(String merchant) {
        for (CategorisationRule rule : rules) {
            Category match = resolve(rule.match(merchant));
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * Searches the whole message. The user's own keywords win; otherwise the built-in category whose
     * keyword is the most specific (longest) match wins. Merchants the user corrected before only
     * apply to the merchant name, not to message text.
     */
    private Category matchMessage(String messageText) {
        String cleaned = MessageText.forCategorising(messageText);
        if (cleaned.isEmpty()) {
            return null;
        }
        for (CategorisationRule rule : rules) {
            if (rule instanceof UserKeywordRule) {
                Category match = resolve(rule.match(cleaned));
                if (match != null) {
                    return match;
                }
            }
        }
        Category best = null;
        int bestLength = 0;
        for (CategorisationRule rule : rules) {
            // Every bank SMS names the bank, so that rule only ever judges the merchant.
            if (rule instanceof KeywordRule
                    && !Category.BANK_TRANSACTIONS.equals(((KeywordRule) rule).getCategory())) {
                KeywordRule keywords = (KeywordRule) rule;
                int length = keywords.matchLength(cleaned);
                if (length > bestLength) {
                    Category match = resolve(keywords.getCategory());
                    if (match != null) {
                        best = match;
                        bestLength = length;
                    }
                }
            }
        }
        return best;
    }

    /** The category as the user has it now, or null if they deleted it (so the next rule decides). */
    private Category resolve(Category match) {
        if (match == null) {
            return null;
        }
        if (catalog == null) {
            return match;
        }
        return catalog.contains(match.getKey()) ? catalog.find(match.getKey()) : null;
    }

    private Category fallback() {
        return catalog == null ? Category.OTHER : catalog.getOther();
    }

    /** "Bank Transactions" if the user still has it, otherwise Other. */
    private Category noMerchantCategory() {
        if (catalog == null) {
            return Category.BANK_TRANSACTIONS;
        }
        return catalog.contains(Category.BANK_TRANSACTIONS_KEY)
                ? catalog.find(Category.BANK_TRANSACTIONS_KEY) : catalog.getOther();
    }

    /** Built-in keyword rules only. */
    public static CategorisationEngine defaultEngine() {
        return new CategorisationEngine(defaultRules());
    }

    /** User corrections first, then the built-in keyword rules. */
    public static CategorisationEngine withLearning(MerchantCategoryLookup lookup) {
        List<CategorisationRule> all = new ArrayList<>();
        all.add(new LearnedMerchantRule(lookup));
        all.addAll(defaultRules());
        return new CategorisationEngine(all);
    }

    /**
     * The engine the app uses, built from everything the user has customised.
     *
     * @param catalog             the user's categories
     * @param learned             merchants the user has re-categorised (may be null)
     * @param userKeywords        keyword to category, added by the user (may be null)
     * @param useBuiltInKeywords  false if the user switched off the built-in keyword lists
     */
    public static CategorisationEngine build(CategoryCatalog catalog, MerchantCategoryLookup learned,
                                             Map<String, Category> userKeywords,
                                             boolean useBuiltInKeywords) {
        List<CategorisationRule> all = new ArrayList<>();
        if (learned != null) {
            all.add(new LearnedMerchantRule(learned));
        }
        if (userKeywords != null && !userKeywords.isEmpty()) {
            all.add(new UserKeywordRule(userKeywords));
        }
        if (useBuiltInKeywords) {
            all.addAll(defaultRules());
        }
        return new CategorisationEngine(all, catalog);
    }

    /** Built-in keywords for one category, for showing in the category editor. */
    public static List<String> builtInKeywordsFor(Category category) {
        for (CategorisationRule rule : defaultRules()) {
            if (rule instanceof KeywordRule && ((KeywordRule) rule).getCategory().equals(category)) {
                return ((KeywordRule) rule).getKeywords();
            }
        }
        return Collections.emptyList();
    }

    public static List<CategorisationRule> defaultRules() {
        List<CategorisationRule> rules = new ArrayList<>();

        // Australian and Indian merchants. Keywords match whole words after lower-casing and
        // replacing symbols with spaces, so "H&M" is written "h m" and "McDonald's" "mcdonald".
        rules.add(new KeywordRule(Category.FOOD_AND_DINING,
                "uber eats", "ubereats", "doordash", "menulog", "deliveroo", "swiggy", "zomato",
                "eatsure", "mcdonald", "kfc", "hungry jack", "burger king", "dominos", "domino",
                "pizza", "pizza hut", "subway", "starbucks", "cafe", "coffee", "restaurant",
                "bakery", "burger", "sushi", "ramen", "noodle", "takeaway", "bistro", "diner",
                "dining", "kebab", "nandos", "grilld", "oporto", "red rooster", "guzman",
                "boost juice", "gelato", "pub", "canteen", "biryani", "dhaba", "bhavan",
                "saravana", "adyar ananda", "a2b", "haldiram", "behrouz", "faasos", "box8",
                "chaayos", "chai", "third wave", "blue tokai", "ccd", "barbeque nation",
                "wow momo", "taco bell", "dunkin", "baskin", "naturals ice", "mad over donuts",
                "freshmenu", "rebel foods", "eat club", "magicpin", "dineout", "eazydiner",
                // more chains and generic eating-out words ("Popeye's" is read as "popeye s")
                "popeyes", "popeye s", "popeye", "chicken", "fried chicken", "wings", "grill",
                "grilled", "kitchen", "eatery", "foods", "food", "sweets", "donut", "donuts",
                "pastry", "dosa", "idli", "tiffin", "tiffins", "mess", "bbq", "pizzeria",
                "gloria jean", "gloria jeans", "krispy kreme", "chatime", "gong cha", "sushi train",
                "betty s burgers", "schnitz", "mad mex", "zambrero", "chargrill charlie",
                "carl s jr", "wendy s", "papa john", "papa johns", "pizza express", "five guys",
                "shake shack", "tim hortons", "costa coffee", "mcafe", "burger singh", "wat a burger",
                "jumbo king", "goli vada pav", "sagar ratna", "udupi", "mtr", "kannur", "paradise",
                "pista house", "bikanervala", "chai point", "theobroma", "cream stone", "ibaco",
                // names the bank cuts short or spells as one word
                "bakes", "bake", "bakehouse", "gourmet", "bundl", "nahdi", "restro", "resto"));

        rules.add(new KeywordRule(Category.TRANSPORT,
                "uber", "ola", "olacabs", "didi", "13cabs", "taxi", "cab", "opal", "myki",
                "translink", "transport nsw", "metro trains", "railway", "irctc", "rapido",
                "namma yatri", "blusmart", "redbus", "abhibus", "metro", "fastag", "fuel", "petrol",
                "shell", "bp", "caltex", "ampol", "united petroleum", "coles express", "eg fuel",
                "7 eleven fuel", "linkt", "toll", "parking", "wilson parking", "secure parking",
                "train", "bus", "hpcl", "bpcl", "iocl", "indian oil", "hindustan petroleum",
                "bharat petroleum", "reliance petrol", "nayara", "indigo", "air india", "akasa",
                "spicejet", "vistara", "qantas", "jetstar", "virgin australia", "rex airlines",
                "makemytrip", "goibibo", "cleartrip", "ixigo", "yatra", "easemytrip"));

        rules.add(new KeywordRule(Category.GROCERIES,
                "woolworths", "coles", "aldi", "iga", "harris farm", "costco", "foodland",
                "drakes", "supermarket", "grocer", "grocery", "groceries", "bigbasket", "dmart",
                "d mart", "avenue supermarts", "spar", "safeway", "blinkit", "grofers", "zepto",
                "instamart", "jiomart", "reliance fresh", "reliance smart", "more retail",
                "more supermarket", "star bazaar", "spencers", "ratnadeep", "nilgiris",
                "nature s basket", "kirana", "milkbasket", "country delight", "dunzo",
                "flipkart minutes", "amazon fresh", "swiggy instamart"));

        rules.add(new KeywordRule(Category.ENTERTAINMENT,
                "netflix", "spotify", "disney", "hotstar", "jiocinema", "jiohotstar", "sonyliv",
                "zee5", "stan", "binge", "kayo", "foxtel", "youtube", "prime video", "apple music",
                "gaana", "wynk", "jiosaavn", "audible", "cinema", "cinemas", "hoyts", "event cinemas",
                "village cinemas", "pvr", "inox", "cinepolis", "steam", "playstation", "xbox",
                "nintendo", "ticketek", "ticketmaster", "bookmyshow", "paytm insider",
                "concert", "theatre", "dream11", "mpl", "entertainment", "big tree", "amusement",
                "gaming", "arcade", "bowling", "zingbus fun", "timezone"));

        rules.add(new KeywordRule(Category.SHOPPING,
                "amazon", "ebay", "kmart", "target", "big w", "myer", "david jones", "jb hi fi",
                "harvey norman", "the good guys", "officeworks", "bunnings", "ikea", "uniqlo",
                "zara", "h m", "hennes", "mauritz", "nike", "adidas", "puma", "decathlon",
                "rebel sport", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq",
                "tatacliq", "snapdeal", "firstcry", "lenskart", "aliexpress", "temu", "shein",
                "the iconic", "cotton on", "country road", "best less", "lifestyle", "westside",
                "zudio", "pantaloons", "max fashion", "reliance trends", "trends", "shoppers stop",
                "croma", "reliance digital", "vijay sales", "apple store", "samsung", "tanishq",
                "bata", "fabindia", "big bazaar", "smart bazaar", "chemist warehouse online",
                "bath body", "bath body works", "bath and body works", "zivame", "urban monkey",
                "reliance retail", "mall", "vr mall", "boutique", "apparel", "fashion",
                "jewellers", "jewelers", "footwear", "lingerie"));

        rules.add(new KeywordRule(Category.BILLS_AND_UTILITIES,
                "telstra", "optus", "vodafone", "tpg", "aussie broadband", "belong", "amaysim",
                "boost mobile", "origin energy", "agl", "energy australia", "red energy",
                "alinta", "simply energy", "electricity", "water", "gas", "internet", "broadband",
                "insurance", "rent", "council", "rates", "airtel", "jio", "vi", "vodafone idea",
                "bsnl", "mtnl", "act fibernet", "hathway", "tata play", "dish tv", "d2h",
                "bescom", "tneb", "tangedco", "msedcl", "mahadiscom", "tata power", "adani electricity",
                "bses", "cesc", "kseb", "apspdcl", "tsspdcl", "indane", "hp gas", "bharatgas",
                "lic", "recharge", "bill", "billdesk", "nbn", "medibank", "bupa", "hcf", "nib",
                "ahm", "allianz", "aami", "nrma", "racv", "budget direct", "youi", "star health",
                "hdfc ergo", "icici lombard", "policybazaar",
                // mobile and TV top-ups ("MyJio" is one word)
                "myjio", "my jio", "jio recharge", "jiofiber", "jio fiber", "jio prepaid",
                "reliance jio", "jiopay", "airtel xstream", "airtel recharge", "postpaid",
                "prepaid", "mobile recharge", "dth", "tata sky", "tatasky"));

        rules.add(new KeywordRule(Category.HEALTH,
                "pharmacy", "chemist", "chemist warehouse", "priceline", "terry white",
                "doctor", "dental", "dentist", "hospital", "clinic", "medical", "medicals",
                "physio", "optometrist", "specsavers", "apollo", "medplus", "pharmeasy",
                "netmeds", "1mg", "tata 1mg", "practo", "wellness forever", "fortis", "manipal",
                "narayana", "max healthcare", "pathology", "laverty", "douglass hanly",
                "thyrocare", "lal pathlabs", "metropolis", "gym", "fitness", "anytime fitness",
                "cult fit", "cultfit", "snap fitness", "f45", "plus fitness",
                "eye", "eyecare", "maxivision", "optical", "opticals",
                "diagnostic", "diagnostics", "healthcare", "hospitals", "ortho", "skin care clinic"));

        rules.add(new KeywordRule(Category.EDUCATION,
                "university", "uni", "tafe", "school", "tuition", "course", "coursera", "udemy",
                "udacity", "edx", "textbook", "library", "unsw", "usyd", "uts", "macquarie uni",
                "monash", "unimelb", "college", "education", "academy", "fees", "byjus", "byju",
                "unacademy", "vedantu", "physics wallah", "upgrad", "simplilearn", "great learning",
                "scaler", "testbook", "aakash", "uniofsyd", "uniofsydstudent", "uni of syd",
                "uni of sydney", "university of sydney", "sydney uni", "student services"));

        // Last, so a more specific category always wins: a merchant that is just a bank
        // ("ICICI Bank", "Canara Bank") is a bank transaction, not "Other".
        rules.add(new KeywordRule(Category.BANK_TRANSACTIONS,
                "bank", "icici", "hdfc", "sbi", "kotak", "canara", "axis", "idfc", "indusind",
                "federal", "pnb", "commbank", "westpac", "netbank", "yes bank", "union bank",
                "bank of baroda", "bank of india", "central bank", "indian bank", "iob"));

        return rules;
    }
}
