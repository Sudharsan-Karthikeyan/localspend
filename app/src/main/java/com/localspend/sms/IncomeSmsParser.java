package com.localspend.sms;

import com.localspend.model.TransactionType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Reads money-in messages: deposits, salary, refunds and transfers received. */
public class IncomeSmsParser extends AbstractSmsParser {

    private static final List<Pattern> MERCHANT_PATTERNS = Arrays.asList(
            // "UPI/CR/312345678/JOHN SMITH"
            UPI_REFERENCE,
            // "from john@okaxis"
            UPI_ID_FROM,
            // "received $2,500 from ACME PTY LTD", "refund from AMAZON"
            merchantPattern("\\bfrom\\s+(?![0-9])"),
            merchantPattern("\\b(?:merchant|payee|sender|info)\\s*[:\\-]\\s*"));

    @Override
    protected boolean matchesIntent(String text) {
        // A message that also mentions a debit is treated as money out, not in.
        return CREDIT_WORDS.matcher(text).find() && !DEBIT_WORDS.matcher(text).find();
    }

    @Override
    protected TransactionType getType() {
        return TransactionType.INCOME;
    }

    @Override
    protected List<Pattern> merchantPatterns() {
        return MERCHANT_PATTERNS;
    }

    @Override
    protected String defaultMerchant(String lowerText) {
        if (lowerText.contains("salary")) {
            return "Salary";
        }
        if (lowerText.contains("refund")) {
            return "Refund";
        }
        return "Deposit";
    }
}
