package com.localspend.sms;

import com.localspend.model.Currency;
import com.localspend.model.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared steps for reading a transaction SMS, written as a template method: subclasses decide what
 * counts as a matching message, what type it is, and how to find the merchant. The base class
 * handles noise filtering, amount extraction and merchant clean-up.
 *
 * The transaction date is read from the message text when it contains one (see
 * {@link SmsDateExtractor}); otherwise the time the SMS arrived is used.
 *
 * The parser looks for common patterns (a currency amount, words like "spent" or "credited",
 * "at X" or "from X") rather than one bank's exact layout, so it tolerates different formats.
 */
public abstract class AbstractSmsParser implements SmsParser {

    private static final int CI = Pattern.CASE_INSENSITIVE;

    /** Currency marker (group 1) then amount (group 2): "$25.50", "Rs.500", "INR 1,999.00", "₹250". */
    private static final Pattern AMOUNT_PREFIX = Pattern.compile(
            "(\\b(?:rs\\.?|inr|aud|usd|a\\$|us\\$)|[$\u20B9])\\s?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);
    /** Amount (group 1) then currency code (group 2): "25.50 AUD". */
    private static final Pattern AMOUNT_SUFFIX = Pattern.compile(
            "([0-9][0-9,]*\\.[0-9]{2})\\s?(aud|inr|usd)\\b", CI);
    private static final Pattern BALANCE_WORDS = Pattern.compile(
            "\\b(?:bal|balance|avl|avail|available|limit)\\b", CI);

    /** One-time passwords and similar: never a transaction. */
    private static final Pattern NOISE = Pattern.compile(
            "\\b(?:otp|passcode)\\s*(?:is|:)|\\b(?:code|password|pin)\\s+is\\s+\\d"
                    + "|\\bis\\s+your\\s+(?:otp|code|passcode)\\b|\\bverification\\s+code\\b", CI);

    /** Reminders, failures and future charges: money has not actually moved. */
    private static final Pattern NOT_A_TRANSACTION = Pattern.compile(
            "\\b(?:due|overdue|reminder|statement|declined|failed|unsuccessful|insufficient"
                    + "|will\\s+be\\s+(?:debited|charged|deducted)|to\\s+be\\s+(?:debited|charged))\\b", CI);

    /**
     * Adverts and offers. These often mention amounts and words like "refund", but no money has
     * moved, so they are never read as transactions, whoever sent them. Phrases that only appear
     * in marketing are enough on their own.
     */
    private static final Pattern PROMOTION = Pattern.compile(
            "\\b(?:chat\\s+with\\s+us|pick\\s+up\\s+here|get(?:ting)?\\s+started|apply\\s+now"
                    + "|limited\\s+time|unsubscribe|opt\\s*out|reply\\s+stop|t\\s?&\\s?cs?\\s+apply"
                    + "|pre-?approved|congratulations|claim\\s+(?:now|your)|refund\\s+you"
                    + "|we(?:'|\u2019)?ll\\s+refund|will\\s+refund|money[-\\s]back"
                    + "|(?:shop|book|order|buy|click)\\s+now|free\\s+(?:on\\s+us|for|trial|gift))\\b"
                    + "|\\b\\d+\\s?%\\s?off\\b", CI);

    /**
     * Words that suggest an advert but can also be part of a shop's name ("Sale St Cafe"), so they
     * only count when the message also contains a web link, as marketing messages usually do.
     */
    private static final Pattern PROMOTION_WORDS = Pattern.compile(
            "\\b(?:free|offer|offers|discount|voucher|coupon|promo|sale|deal|deals|win|prize"
                    + "|eligible|hurry|expires?|subscribe|cost|costs)\\b", CI);
    private static final Pattern LINK = Pattern.compile(
            "\\bhttps?://|\\bwww\\.|\\b[a-z0-9-]+\\.(?:com|net|org|io|ly|co|in|au)(?:\\.au)?/", CI);

    /** True if the message is an advert or offer rather than a transaction alert. */
    public static boolean isPromotion(String text) {
        if (text == null) {
            return false;
        }
        return PROMOTION.matcher(text).find()
                || (PROMOTION_WORDS.matcher(text).find() && LINK.matcher(text).find());
    }

    protected static final Pattern DEBIT_WORDS = Pattern.compile(
            "\\b(?:debited|debit\\s+of|spent|purchase|purchased|paid|withdrawn|withdrawal|charged"
                    + "|deducted|sent|used\\s+(?:for|at|to))\\b"
                    + "|\\bdr\\b\\.?(?=\\s+(?:inr|rs|aud|usd|a?\\$|\\u20B9|[0-9]))"   // "Acct XX0751 Dr. INR 1.00"
                    + "|\\bpayment\\b.{0,40}?\\b(?:successful|successfully|made|processed|completed)\\b", CI);
    protected static final Pattern REFUND_WORDS = Pattern.compile(
            "\\b(?:refund|refunded|reversal|reversed|cashback)\\b", CI);
    protected static final Pattern CREDIT_WORDS = Pattern.compile(
            "\\b(?:credited|received|deposited|deposit|salary|refund|refunded|reversal|cashback"
                    + "|credit\\s+of)\\b"
                    + "|\\bcr\\b\\.?(?=\\s+(?:inr|rs|aud|usd|a?\\$|\\u20B9|[0-9]))", CI);   // "Acct XX0751 Cr. INR 500"

    /** Where a merchant name ends: a following keyword, a date or time, punctuation, or the end. */
    private static final String STOP =
            "(?=\\s+(?:on|using|with|via|ref|reference|card|from|for|at|to|has|have|is|was|will|been"
                    + "|credited|debited|avl|avail|available|bal|balance|date|dt|txn|transaction|trf"
                    + "|upi|if|not|call|never|thank|thanks|ending|a/c|ac|acct|account"
                    + "|description|desc|receipt|amount)\\b"
                    + "|\\s+[0-9]{1,2}[/:-][0-9]{1,2}\\b|\\s*\\(|[,;]|\\.(?:\\s|$)|\\s-\\s|$)";

    /** Captures that are really account references, not merchants ("A/c XX1234", "your account"). */
    private static final Pattern INVALID_MERCHANT = Pattern.compile(
            "^(?:(?:a/c|acct|ac|account|your|card|x{2,}\\d*)\\b|\\d+$)", CI);

    /**
     * Indian UPI references with the payee's name at the end:
     * "UPI-312824365410-HENNES", "UPI/P2M/512345678901/AMAZON PAY/...", "UPI/CR/312345/JOHN".
     */
    protected static final Pattern UPI_REFERENCE = Pattern.compile(
            "\\bUPI\\s*[-/:]\\s*(?:(?:P2[AMP]|CR|DR)\\s*[-/]\\s*)?\\d{6,}\\s*[-/]\\s*"
                    + "([A-Za-z][A-Za-z0-9 .&'*]*?)"
                    + "(?=\\s*[-/.,;:(]|\\s+(?:on|ref|to|avl|bal|is|for|via|has|was)\\b|\\s*$)", CI);

    /** UPI IDs: "to zomato@hdfcbank", "to VPA john.doe@okaxis" (the part before @ is the name). */
    protected static final Pattern UPI_ID_TO = Pattern.compile(
            "\\bto\\s+(?:vpa\\s+)?([a-z0-9][a-z0-9._\\-]*)@[a-z][a-z0-9.]*", CI);
    protected static final Pattern UPI_ID_FROM = Pattern.compile(
            "\\bfrom\\s+(?:vpa\\s+)?([a-z0-9][a-z0-9._\\-]*)@[a-z][a-z0-9.]*", CI);

    /** "To dispute call...", "to report", "to block": instructions, not payees. */
    protected static final String NOT_A_PAYEE =
            "(?!(?:dispute|report|block|stop|avoid|know|check|view|unsubscribe|reverse|raise"
                    + "|register|activate|deactivate|unblock|cancel|contact|reach|call|sms)\\b)";

    /** Builds a merchant pattern: {@code prefixRegex} then the name, ending at a stop point. */
    protected static Pattern merchantPattern(String prefixRegex) {
        return Pattern.compile(prefixRegex + "(.+?)" + STOP, CI);
    }

    @Override
    public final ParsedTransaction parse(String body, long timestampMillis) {
        if (body == null) {
            return null;
        }
        String text = body.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        if (NOISE.matcher(text).find() || NOT_A_TRANSACTION.matcher(text).find()
                || isPromotion(text)) {
            return null;
        }
        if (!matchesIntent(text)) {
            return null;
        }
        Amount amount = extractAmount(text);
        if (amount == null) {
            return null;
        }
        String merchant = extractMerchant(text);
        boolean merchantFound = merchant != null;
        if (!merchantFound) {
            merchant = defaultMerchant(text.toLowerCase(Locale.ROOT));
        }
        long date = SmsDateExtractor.extract(text, timestampMillis);
        return new ParsedTransaction(amount.cents, merchant, getType(), date, amount.currency,
                merchantFound);
    }

    /** True if this parser should handle the message (for example it mentions a debit). */
    protected abstract boolean matchesIntent(String text);

    protected abstract TransactionType getType();

    /** Patterns whose first group captures the merchant, tried in order. */
    protected abstract List<Pattern> merchantPatterns();

    /** Fallback label when no merchant can be found. */
    protected abstract String defaultMerchant(String lowerText);

    private String extractMerchant(String text) {
        for (Pattern pattern : merchantPatterns()) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String cleaned = cleanMerchant(m.group(1));
                if (cleaned != null) {
                    return cleaned;
                }
            }
        }
        return null;
    }

    /** An amount and the currency written next to it. */
    private static final class Amount {
        final long cents;
        final Currency currency;

        Amount(long cents, Currency currency) {
            this.cents = cents;
            this.currency = currency;
        }
    }

    private static Amount extractAmount(String text) {
        Amount found = firstAmount(AMOUNT_PREFIX, 1, 2, text);
        return found != null ? found : firstAmount(AMOUNT_SUFFIX, 2, 1, text);
    }

    /** First amount that is not a balance or limit figure. */
    private static Amount firstAmount(Pattern pattern, int markerGroup, int numberGroup, String text) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String before = text.substring(Math.max(0, m.start() - 20), m.start());
            // Only look within the current sentence, so "Avl Bal $900. Spent $15" keeps the $15.
            int sentenceBreak = Math.max(before.lastIndexOf(". "), before.lastIndexOf(';'));
            if (sentenceBreak >= 0) {
                before = before.substring(sentenceBreak + 1);
            }
            if (BALANCE_WORDS.matcher(before).find()) {
                continue;
            }
            Long cents = toCents(m.group(numberGroup));
            if (cents != null) {
                return new Amount(cents, Currency.fromMarker(m.group(markerGroup)));
            }
        }
        return null;
    }

    private static Long toCents(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.replace(",", ""));
            long cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return cents > 0 ? cents : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static String cleanMerchant(String raw) {
        String m = raw.trim().replaceAll("\\s+", " ");
        m = m.replaceAll("^[\\s:\\-*]+|[\\s:\\-*.,;]+$", "");
        // Payment gateways put their own name in front: "RAZ*Zomato Onli", "PAYPAL *SPOTIFY".
        m = m.replaceFirst("(?i)^(?:raz|razp|payu|pyu|ccav|cf|paypal|sq|tst)\\s?\\*\\s*", "");
        if (m.isEmpty() || INVALID_MERCHANT.matcher(m).find()) {
            return null;
        }
        if (m.length() > 40) {
            m = m.substring(0, 40).trim();
        }
        return hasLowerCase(m) ? m : toTitleCase(m);
    }

    private static boolean hasLowerCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String word : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
