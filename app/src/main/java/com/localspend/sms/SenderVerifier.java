package com.localspend.sms;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a message's sender can be trusted to send real transaction alerts.
 * The user's own choices come first, then the built-in list of bank sender IDs.
 */
public class SenderVerifier {

    /** What to do with a message from this sender. */
    public enum Verdict {
        /** A known bank, or a sender the user trusts: add transactions automatically. */
        TRUSTED,
        /** A sender the user blocked: ignore its messages. */
        BLOCKED,
        /** Anyone else: hold the transaction for the user to review. */
        UNKNOWN
    }

    private final Set<String> trusted;
    private final Set<String> blocked;

    /** @param trusted, blocked normalised sender keys chosen by the user */
    public SenderVerifier(Set<String> trusted, Set<String> blocked) {
        this.trusted = trusted == null ? Collections.<String>emptySet() : new HashSet<>(trusted);
        this.blocked = blocked == null ? Collections.<String>emptySet() : new HashSet<>(blocked);
    }

    public Verdict verdict(String sender) {
        String key = SenderDirectory.normalise(sender);
        if (blocked.contains(key)) {
            return Verdict.BLOCKED;
        }
        if (trusted.contains(key) || SenderDirectory.isKnownBank(sender)) {
            return Verdict.TRUSTED;
        }
        return Verdict.UNKNOWN;
    }
}
