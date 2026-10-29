package com.localspend.logic;

/** When the app asks for the PIN again, and how long a run of wrong PINs locks out guessing. */
public final class LockPolicy {

    /** Leaving the app for less than this does not lock it (a quick look at another app). */
    public static final long GRACE_MILLIS = 15_000L;
    private static final int FREE_ATTEMPTS = 5;
    private static final long BASE_LOCKOUT_MILLIS = 30_000L;
    private static final long MAX_LOCKOUT_MILLIS = 5 * 60_000L;

    private LockPolicy() {
    }

    /** True if the app was out of sight long enough that it must be unlocked again. */
    public static boolean shouldRelock(long backgroundSinceMillis, long nowMillis) {
        return nowMillis - backgroundSinceMillis >= GRACE_MILLIS;
    }

    /**
     * Milliseconds still to wait before another PIN may be tried: nothing for the first five wrong
     * PINs, then 30 seconds, doubling with each further wrong PIN up to five minutes.
     */
    public static long lockoutRemaining(int failedAttempts, long lastFailMillis, long nowMillis) {
        if (failedAttempts < FREE_ATTEMPTS) {
            return 0;
        }
        long wait = BASE_LOCKOUT_MILLIS;
        for (int i = FREE_ATTEMPTS; i < failedAttempts && wait < MAX_LOCKOUT_MILLIS; i++) {
            wait *= 2;
        }
        wait = Math.min(wait, MAX_LOCKOUT_MILLIS);
        return Math.max(0, lastFailMillis + wait - nowMillis);
    }
}
