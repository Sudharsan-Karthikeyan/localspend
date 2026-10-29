package com.localspend.logic;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Prepares a whole bank SMS for categorising. The message is mostly boilerplate ("To dispute call
 * 1800...", "Avl Bal...", the bank's own name, amounts, dates, reference numbers); this strips that
 * out so the words left are the ones that say what was bought and where, and a word like "fees" or
 * "bank" in the small print cannot decide the category.
 */
public final class MessageText {

    private static final int CI = Pattern.CASE_INSENSITIVE;

    /** Sentences that are instructions, balances or marketing, never the merchant. */
    private static final Pattern BOILERPLATE_SENTENCE = Pattern.compile(
            "\\b(?:dispute|not\\s+you|if\\s+not|call|sms\\s+block|block|avl|available|balance|bal"
                    + "|limit|toll[- ]?free|helpline|customer\\s+care|download|click|visit"
                    + "|never\\s+share|do\\s+not\\s+share|otp|t\\s?&\\s?cs?|terms|regards|stay\\s+safe"
                    + "|beware|fraud|report|contact|reply|unsubscribe)\\b", CI);

    private static final Pattern URL = Pattern.compile(
            "\\b(?:https?://|www\\.)\\S+|\\b[a-z0-9-]+\\.(?:com|net|org|in|co|au)(?:\\.[a-z]{2})?/?\\S*", CI);
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:\\b(?:rs\\.?|inr|aud|usd)|a\\$|us\\$|[$\u20B9])\\s?[0-9][0-9,]*(?:\\.[0-9]+)?"
                    + "|[0-9][0-9,]*\\.[0-9]{2}\\s?(?:aud|inr|usd)\\b", CI);
    private static final Pattern DATE = Pattern.compile(
            "\\b\\d{1,2}[-/ ](?:[a-z]{3,9}|\\d{1,2})[-/ ]\\d{2,4}\\b|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", CI);
    private static final Pattern ACCOUNT_MASK = Pattern.compile(
            "\\b(?:x{2,}|\\*{2,})\\s?\\d{2,}\\b|\\ba/?c\\b\\s*(?:no\\.?)?\\s*\\S*\\d\\S*", CI);
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{3,}\\b");

    /** "ICICI Bank", "Airtel Payments Bank Ltd": the bank's name says nothing about the purchase. */
    private static final Pattern BANK_NAME = Pattern.compile(
            "(?:\\b[a-z]+\\s+){0,3}\\bbank\\b(?:\\s+(?:ltd|limited))?", CI);

    /** Words that appear in many alerts and in the small print, not in what was bought. */
    private static final Pattern NOISE_WORDS = Pattern.compile(
            "\\b(?:fee|fees|charge|charges|gst|tax|interest|penalty|emi|statement|credit|debit"
                    + "|debited|credited|card|upi|txn|ref|neft|imps|rtgs|alert|info|dear|customer)\\b", CI);

    private MessageText() {
    }

    /** The message with the boilerplate removed, lower-case; empty when nothing useful is left. */
    public static String forCategorising(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "";
        }

        // Keep only the sentences that describe the payment itself.
        StringBuilder kept = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (!BOILERPLATE_SENTENCE.matcher(sentence).find()) {
                kept.append(sentence).append(' ');
            }
        }
        String s = kept.toString();
        s = URL.matcher(s).replaceAll(" ");
        s = AMOUNT.matcher(s).replaceAll(" ");
        s = DATE.matcher(s).replaceAll(" ");
        s = ACCOUNT_MASK.matcher(s).replaceAll(" ");
        s = BANK_NAME.matcher(s).replaceAll(" ");
        s = LONG_NUMBER.matcher(s).replaceAll(" ");
        s = NOISE_WORDS.matcher(s).replaceAll(" ");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
