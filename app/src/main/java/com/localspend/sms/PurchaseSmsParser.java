package com.localspend.sms;

import com.localspend.model.TransactionType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Reads money-out messages: card purchases, debits, payments and transfers sent. */
public class PurchaseSmsParser extends AbstractSmsParser {

    private static final List<Pattern> MERCHANT_PATTERNS = Arrays.asList(
            // "... for UPI-312824365410-HENNES", "Info: UPI/P2M/5123456/AMAZON PAY/..."
            UPI_REFERENCE,
            // "... to zomato@hdfcbank", "to VPA john@okaxis"
            UPI_ID_TO,
            // "... on 29-Jun-26 on RAZ*Zomato Onli." (ICICI card alerts name the shop after the date)
            merchantPattern("\\bon\\s+\\d{1,2}[-/ ][A-Za-z]{3,9}[-/ ]\\d{2,4}\\s+(?:on|at)\\s+(?![0-9])"),
            // "... at WOOLWORTHS SYDNEY on ..." (but not a time such as "at 10:45")
            merchantPattern("(?:\\bat\\s+(?![0-9])|@\\s*)"),
            // "... debited from A/c XX1234 to ZOMATO", "paid $20 to John" (not "To dispute call")
            merchantPattern("\\b(?:debited|dr|paid|sent|transferred|transfer|payment|trf)\\b.*?\\bto\\s+(?![0-9])"
                    + NOT_A_PAYEE),
            // "Merchant: X", "Info: X", "To: S KARTHIKEYAN (+61...)", "Paid to: X"
            merchantPattern("\\b(?:merchant|payee|beneficiary|info|to|paid\\s+to|recipient)\\s*:\\s*(?![0-9])"
                    + NOT_A_PAYEE),
            merchantPattern("\\b(?:merchant|payee|beneficiary|info)\\s*-\\s*"));

    @Override
    protected boolean matchesIntent(String text) {
        return DEBIT_WORDS.matcher(text).find() && !REFUND_WORDS.matcher(text).find();
    }

    @Override
    protected TransactionType getType() {
        return TransactionType.EXPENSE;
    }

    @Override
    protected List<Pattern> merchantPatterns() {
        return MERCHANT_PATTERNS;
    }

    @Override
    protected String defaultMerchant(String lowerText) {
        return com.localspend.model.Transaction.UNKNOWN_MERCHANT;
    }
}
