package com.localspend.sms;

/** Anything that can try to read a transaction out of an SMS body. */
public interface SmsParser {

    /**
     * @param body            the message text
     * @param timestampMillis when the message arrived; used as the transaction date unless the
     *                        message text contains a plausible date of its own
     * @return the transaction, or null if the message is not a recognisable transaction
     */
    ParsedTransaction parse(String body, long timestampMillis);
}
