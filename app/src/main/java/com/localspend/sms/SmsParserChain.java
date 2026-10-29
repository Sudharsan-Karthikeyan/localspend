package com.localspend.sms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tries each parser in turn and returns the first result, so formats can be added independently. */
public class SmsParserChain implements SmsParser {

    private final List<SmsParser> parsers;

    public SmsParserChain(List<SmsParser> parsers) {
        this.parsers = new ArrayList<>(parsers);
    }

    public static SmsParserChain defaultChain() {
        return new SmsParserChain(Arrays.<SmsParser>asList(
                new PurchaseSmsParser(), new IncomeSmsParser()));
    }

    @Override
    public ParsedTransaction parse(String body, long timestampMillis) {
        for (SmsParser parser : parsers) {
            ParsedTransaction result = parser.parse(body, timestampMillis);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
