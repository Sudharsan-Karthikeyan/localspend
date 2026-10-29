package com.localspend.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.localspend.logic.BudgetTracker;
import com.localspend.model.Currency;

/**
 * The user's preferences, kept in the app's private SharedPreferences (on the device only).
 */
public class AppSettings {

    private static final String FILE = "localspend_settings";

    private static final String KEY_OVERALL_BUDGET = "overall_budget_cents";
    private static final String KEY_OVERALL_BUDGET_CURRENCY = "overall_budget_currency";
    private static final String KEY_DISPLAY_CURRENCY = "display_currency";   // "AUTO" or a code
    private static final String KEY_NOTIFY_TRANSACTIONS = "notify_transactions";
    private static final String KEY_NOTIFY_BUDGETS = "notify_budgets";
    private static final String KEY_WARN_PERCENT = "budget_warn_percent";
    private static final String KEY_BUILT_IN_KEYWORDS = "use_built_in_keywords";
    private static final String KEY_STORE_SMS = "store_sms_text";
    private static final String KEY_ONLINE_RATES = "online_exchange_rates";
    private static final String KEY_PIN_HASH = "lock_pin_hash";
    private static final String KEY_PIN_SALT = "lock_pin_salt";
    private static final String KEY_LOCK_BIOMETRIC = "lock_biometric";
    private static final String KEY_LOCK_FAILS = "lock_failed_attempts";
    private static final String KEY_LOCK_LAST_FAIL = "lock_last_fail";
    private static final String KEY_CHART_RANGE = "chart_range";
    private static final String KEY_ASKED_NOTIFICATIONS = "asked_notification_permission";
    private static final String KEY_ALERT_PREFIX = "alert_sent_";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Overall monthly spending limit in cents, or 0 for none. */
    public long getOverallBudgetCents() {
        return Math.max(0, prefs.getLong(KEY_OVERALL_BUDGET, 0));
    }

    public void setOverallBudgetCents(long cents) {
        prefs.edit().putLong(KEY_OVERALL_BUDGET, Math.max(0, cents)).apply();
    }

    /** Currency the overall budget is set in. */
    public Currency getOverallBudgetCurrency() {
        return Currency.fromCode(prefs.getString(KEY_OVERALL_BUDGET_CURRENCY, null));
    }

    public void setOverallBudgetCurrency(Currency currency) {
        prefs.edit().putString(KEY_OVERALL_BUDGET_CURRENCY, currency.name()).apply();
    }

    /**
     * Currency the dashboard and history show, or null for automatic (follow the currency of the
     * most recent transaction).
     */
    public Currency getDisplayCurrency() {
        String code = prefs.getString(KEY_DISPLAY_CURRENCY, null);
        return code == null || "AUTO".equals(code) ? null : Currency.fromCode(code);
    }

    /** null switches back to automatic. */
    public void setDisplayCurrency(Currency currency) {
        prefs.edit().putString(KEY_DISPLAY_CURRENCY, currency == null ? "AUTO" : currency.name()).apply();
    }

    /** Show a notification when a transaction is captured from an SMS. */
    public boolean isTransactionNotificationsOn() {
        return prefs.getBoolean(KEY_NOTIFY_TRANSACTIONS, true);
    }

    public void setTransactionNotificationsOn(boolean on) {
        prefs.edit().putBoolean(KEY_NOTIFY_TRANSACTIONS, on).apply();
    }

    /** Show a notification when spending nears or passes a budget. */
    public boolean isBudgetNotificationsOn() {
        return prefs.getBoolean(KEY_NOTIFY_BUDGETS, true);
    }

    public void setBudgetNotificationsOn(boolean on) {
        prefs.edit().putBoolean(KEY_NOTIFY_BUDGETS, on).apply();
    }

    /** Percentage of a budget at which to warn (for example 80). */
    public int getWarnPercent() {
        return prefs.getInt(KEY_WARN_PERCENT, BudgetTracker.DEFAULT_WARN_PERCENT);
    }

    public void setWarnPercent(int percent) {
        prefs.edit().putInt(KEY_WARN_PERCENT, Math.max(1, Math.min(100, percent))).apply();
    }

    /** Whether the built-in keyword lists are used for automatic categorisation. */
    public boolean isBuiltInKeywordsOn() {
        return prefs.getBoolean(KEY_BUILT_IN_KEYWORDS, true);
    }

    public void setBuiltInKeywordsOn(boolean on) {
        prefs.edit().putBoolean(KEY_BUILT_IN_KEYWORDS, on).apply();
    }

    /** Keep the full text of each SMS a transaction was read from. */
    public boolean isStoreSmsTextOn() {
        return prefs.getBoolean(KEY_STORE_SMS, true);
    }

    public void setStoreSmsTextOn(boolean on) {
        prefs.edit().putBoolean(KEY_STORE_SMS, on).apply();
    }

    /** Look up real exchange rates online (only currency codes and a date are sent). */
    public boolean isOnlineRatesOn() {
        return prefs.getBoolean(KEY_ONLINE_RATES, true);
    }

    public void setOnlineRatesOn(boolean on) {
        prefs.edit().putBoolean(KEY_ONLINE_RATES, on).apply();
    }

    /** True when the app is protected by a PIN. */
    public boolean isLockOn() {
        return prefs.getString(KEY_PIN_HASH, null) != null;
    }

    /** Turns the lock on (or changes the PIN). Only a salted hash of the PIN is kept. */
    public void setPin(String pin) {
        String salt = com.localspend.logic.PinHasher.newSalt();
        prefs.edit().putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, com.localspend.logic.PinHasher.hash(pin, salt))
                .putInt(KEY_LOCK_FAILS, 0).apply();
    }

    public void clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).remove(KEY_LOCK_BIOMETRIC)
                .putInt(KEY_LOCK_FAILS, 0).apply();
    }

    public boolean checkPin(String pin) {
        return com.localspend.logic.PinHasher.matches(pin, prefs.getString(KEY_PIN_SALT, null),
                prefs.getString(KEY_PIN_HASH, null));
    }

    /** Fingerprint (or face) unlock as well as the PIN. */
    public boolean isLockBiometricOn() {
        return isLockOn() && prefs.getBoolean(KEY_LOCK_BIOMETRIC, false);
    }

    public void setLockBiometricOn(boolean on) {
        prefs.edit().putBoolean(KEY_LOCK_BIOMETRIC, on).apply();
    }

    public int getFailedPinAttempts() {
        return prefs.getInt(KEY_LOCK_FAILS, 0);
    }

    public long getLastFailedPinMillis() {
        return prefs.getLong(KEY_LOCK_LAST_FAIL, 0);
    }

    public void recordFailedPin() {
        prefs.edit().putInt(KEY_LOCK_FAILS, getFailedPinAttempts() + 1)
                .putLong(KEY_LOCK_LAST_FAIL, System.currentTimeMillis()).apply();
    }

    public void resetFailedPins() {
        prefs.edit().putInt(KEY_LOCK_FAILS, 0).apply();
    }

    /** Months shown on the dashboard chart. */
    public com.localspend.logic.TrendWindow.Range getChartRange() {
        return com.localspend.logic.TrendWindow.Range.fromName(prefs.getString(KEY_CHART_RANGE, null));
    }

    public void setChartRange(com.localspend.logic.TrendWindow.Range range) {
        prefs.edit().putString(KEY_CHART_RANGE, range.name()).apply();
    }

    public boolean hasAskedNotificationPermission() {
        return prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false);
    }

    public void setAskedNotificationPermission() {
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply();
    }

    /**
     * Records that a budget alert was shown, so each alert appears only once per month.
     *
     * @return true if this alert had not been shown yet (and is now marked as shown)
     */
    public synchronized boolean markAlertIfNew(String alertId) {
        String key = KEY_ALERT_PREFIX + alertId;
        if (prefs.getBoolean(key, false)) {
            return false;
        }
        prefs.edit().putBoolean(key, true).apply();
        return true;
    }
}
