package com.localspend.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Calendar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.model.Currency;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

/**
 * Preferences: overall monthly budget and warning point, notifications, how categorisation
 * works, and what happens to the original SMS text.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int[] WARN_OPTIONS = {50, 60, 70, 75, 80, 90, 100};

    private AppSettings settings;
    private TransactionRepository transactions;

    private TextInputLayout tilBudget;
    private TextInputEditText etBudget;
    private Currency dashboardCurrency;   // the currency every budget is in
    private Spinner spOverallCurrency;
    private TextView tvNotificationsBlocked;
    private TextView tvStoredCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.menu_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        settings = new AppSettings(this);
        transactions = TransactionRepository.getInstance(this);

        // Budget
        tilBudget = findViewById(R.id.tilBudget);
        etBudget = findViewById(R.id.etBudget);
        long budget = settings.getOverallBudgetCents();
        if (budget > 0) {
            etBudget.setText(plainAmount(budget));
        }
        spOverallCurrency = findViewById(R.id.spOverallCurrency);
        CurrencySpinners.setUp(spOverallCurrency, tilBudget, settings.getOverallBudgetCurrency());
        // The budget is in whatever currency the dashboard shows, so there is nothing to pick.
        spOverallCurrency.setVisibility(View.GONE);
        Background.run(() -> {
            Currency dashboard = CurrencySwitch.resolve(settings, transactions);
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    dashboardCurrency = dashboard;
                    spOverallCurrency.setSelection(dashboard.ordinal());
                    tilBudget.setPrefixText(dashboard.getSymbol());   // a hidden spinner reports no change
                }
            });
        });
        findViewById(R.id.btnSaveBudget).setOnClickListener(v -> saveBudget(true));
        setUpDisplayCurrency();

        Spinner spWarn = findViewById(R.id.spWarn);
        String[] labels = new String[WARN_OPTIONS.length];
        int selected = 0;
        for (int i = 0; i < WARN_OPTIONS.length; i++) {
            labels[i] = WARN_OPTIONS[i] + "%";
            if (WARN_OPTIONS[i] == settings.getWarnPercent()) {
                selected = i;
            }
        }
        spWarn.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spWarn.setSelection(selected);
        spWarn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setWarnPercent(WARN_OPTIONS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        findViewById(R.id.btnCategoryBudgets).setOnClickListener(
                v -> startActivity(new Intent(this, CategoriesActivity.class)));

        // Notifications
        bindSwitch(R.id.swNotifyTransactions, settings.isTransactionNotificationsOn(),
                settings::setTransactionNotificationsOn);
        bindSwitch(R.id.swNotifyBudgets, settings.isBudgetNotificationsOn(),
                settings::setBudgetNotificationsOn);
        tvNotificationsBlocked = findViewById(R.id.tvNotificationsBlocked);
        tvNotificationsBlocked.setOnClickListener(v -> openNotificationSettings());

        // Categorisation
        bindSwitch(R.id.swBuiltInKeywords, settings.isBuiltInKeywordsOn(), on -> {
            settings.setBuiltInKeywordsOn(on);
            offerRecategorise();
        });
        findViewById(R.id.btnManageCategories).setOnClickListener(
                v -> startActivity(new Intent(this, CategoriesActivity.class)));
        findViewById(R.id.btnRecategorise).setOnClickListener(v -> recategorise());

        // Privacy and backup
        setUpLock();
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> startExport());

        // SMS text
        bindSwitch(R.id.swStoreSms, settings.isStoreSmsTextOn(), settings::setStoreSmsTextOn);
        bindSwitch(R.id.swOnlineRates, settings.isOnlineRatesOn(), settings::setOnlineRatesOn);
        tvStoredCount = findViewById(R.id.tvStoredCount);
        findViewById(R.id.btnReviewMessages).setOnClickListener(
                v -> startActivity(new Intent(this, ReviewActivity.class)));
        findViewById(R.id.btnSenders).setOnClickListener(v -> SenderDialogs.showSenders(this));
        findViewById(R.id.btnViewMessages).setOnClickListener(
                v -> startActivity(new Intent(this, MessagesActivity.class)));
    }

    // ---- App lock -------------------------------------------------------------------------

    private boolean updatingLockSwitches = false;

    private void setUpLock() {
        SwitchCompat lock = findViewById(R.id.swAppLock);
        SwitchCompat biometric = findViewById(R.id.swLockBiometric);
        refreshLockViews();
        lock.setOnCheckedChangeListener((button, isChecked) -> {
            if (updatingLockSwitches) {
                return;
            }
            if (isChecked) {
                askNewPin(true);
            } else {
                askCurrentPinToTurnOff();
            }
        });
        biometric.setOnCheckedChangeListener((button, isChecked) -> {
            if (updatingLockSwitches) {
                return;
            }
            if (!isChecked) {
                settings.setLockBiometricOn(false);
            } else if (!biometricAvailable()) {
                showMessage(getString(R.string.biometric_unavailable));
                refreshLockViews();
            } else if (!settings.isLockOn()) {
                // Fingerprint works alongside a PIN, so choose the PIN first.
                askNewPin(true, () -> {
                    settings.setLockBiometricOn(true);
                    refreshLockViews();
                });
            } else {
                settings.setLockBiometricOn(true);
            }
        });
        findViewById(R.id.btnChangePin).setOnClickListener(v -> askNewPin(false));
    }

    /** Makes the lock switches and buttons match what is saved. */
    private void refreshLockViews() {
        updatingLockSwitches = true;
        boolean on = settings.isLockOn();
        ((SwitchCompat) findViewById(R.id.swAppLock)).setChecked(on);
        SwitchCompat biometric = findViewById(R.id.swLockBiometric);
        biometric.setVisibility(View.VISIBLE);     // always offered; it explains if the phone has none set up
        biometric.setChecked(settings.isLockBiometricOn());
        findViewById(R.id.btnChangePin).setVisibility(on ? View.VISIBLE : View.GONE);
        updatingLockSwitches = false;
    }

    private boolean biometricAvailable() {
        return androidx.biometric.BiometricManager.from(this).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void askNewPin(boolean turningOn) {
        askNewPin(turningOn, null);
    }

    /** Two fields (PIN and confirmation); saves the PIN only if both match and are 4 to 8 digits. */
    private void askNewPin(boolean turningOn, Runnable afterSaved) {
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText first = pinField(getString(R.string.lock_enter_new_pin));
        final android.widget.EditText second = pinField(getString(R.string.lock_confirm_pin));
        box.addView(first);
        box.addView(second);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lock_set_pin_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel, (d, w) -> refreshLockViews())
                .setOnCancelListener(d -> refreshLockViews())
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = first.getText().toString();
            if (!com.localspend.logic.PinHasher.isValidPin(pin)) {
                first.setError(getString(R.string.lock_pin_invalid));
            } else if (!pin.equals(second.getText().toString())) {
                second.setError(getString(R.string.lock_pin_mismatch));
            } else {
                settings.setPin(pin);
                com.localspend.lock.AppLock.markUnlocked();   // you just proved you hold the phone
                dialog.dismiss();
                refreshLockViews();
                if (afterSaved != null) {
                    afterSaved.run();
                }
            }
        });
    }

    private android.widget.EditText pinField(String hint) {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setHint(hint);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(com.localspend.logic.PinHasher.MAX_LENGTH)});
        return field;
    }

    private void askCurrentPinToTurnOff() {
        final android.widget.EditText field = pinField(getString(R.string.lock_pin_hint));
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lock_current_pin_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel, (d, w) -> refreshLockViews())
                .setOnCancelListener(d -> refreshLockViews())
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (settings.checkPin(field.getText().toString())) {
                settings.clearPin();
                dialog.dismiss();
                refreshLockViews();
            } else {
                field.setError(getString(R.string.lock_wrong_pin));
            }
        });
    }

    // ---- CSV export -----------------------------------------------------------------------

    private static final int REQUEST_EXPORT = 4711;

    /** Asks where to save the file (the system file picker, so no storage permission is needed). */
    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "localspend-" + new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        final android.content.ContentResolver resolver = getContentResolver();
        Background.run(() -> {
            java.util.List<com.localspend.model.Transaction> all = transactions.getAll();
            int count = -1;
            if (!all.isEmpty()) {
                String csv = com.localspend.logic.CsvExporter.toCsv(all, java.util.TimeZone.getDefault());
                try (java.io.OutputStream out = resolver.openOutputStream(uri)) {
                    if (out != null) {
                        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});   // so Excel reads UTF-8 (₹)
                        out.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        count = all.size();
                    }
                } catch (java.io.IOException | SecurityException e) {
                    count = -2;
                }
            }
            final int result = count;
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showMessage(result >= 0 ? getString(R.string.export_done, result)
                        : result == -1 ? getString(R.string.export_none)
                        : getString(R.string.export_failed));
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        tvNotificationsBlocked.setVisibility(enabled ? View.GONE : View.VISIBLE);
        Background.run(() -> {
            int stored = transactions.countStoredMessages();
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    tvStoredCount.setText(getResources().getQuantityString(
                            R.plurals.messages_stored, stored, stored));
                }
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveBudget(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private interface OnToggle {
        void set(boolean on);
    }

    private void bindSwitch(int id, boolean value, OnToggle onToggle) {
        SwitchCompat sw = findViewById(id);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((button, isChecked) -> onToggle.set(isChecked));
    }

    private static String plainAmount(long cents) {
        return (cents / 100) + "." + (cents % 100 < 10 ? "0" : "") + (cents % 100);
    }

    /** Saves the overall budget; an empty field removes it. */
    private void saveBudget(boolean confirm) {
        tilBudget.setError(null);
        String text = String.valueOf(etBudget.getText()).trim();
        long cents = 0;
        if (!text.isEmpty()) {
            try {
                cents = MoneyFormatter.parseToCents(text);
            } catch (NumberFormatException e) {
                tilBudget.setError(getString(R.string.error_budget));
                return;
            }
        }
        final Currency currency = dashboardCurrency != null ? dashboardCurrency
                : CurrencySpinners.selected(spOverallCurrency);
        final long newCents = cents;
        settings.setOverallBudgetCurrency(currency);
        if (newCents == settings.getOverallBudgetCents()) {
            if (confirm) {
                showMessage(getString(R.string.budget_unchanged));
            }
            return;
        }
        if (!confirm) {
            applyOverallBudget(newCents, com.localspend.data.BudgetRepository.Scope.FROM_THIS_MONTH,
                    currency, false);     // leaving the screen: never rewrite earlier months silently
            return;
        }
        BudgetScopeDialog.choose(this, newCents, Dates.monthLabel(Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH)),
                scope -> applyOverallBudget(newCents, scope, currency, true));
    }

    private void applyOverallBudget(long cents, com.localspend.data.BudgetRepository.Scope scope,
                                    Currency currency, boolean confirm) {
        final android.content.Context app = getApplicationContext();
        Background.run(() -> {
            com.localspend.data.BudgetRepository.getInstance(app).apply(
                    com.localspend.logic.BudgetHistory.OVERALL,
                    com.localspend.data.BudgetRepository.currentMonthIndex(), cents, scope);
            Background.main(() -> {
                if (confirm && !isFinishing() && !isDestroyed()) {
                    showMessage(cents > 0
                            ? getString(R.string.budget_saved, MoneyFormatter.format(cents, currency))
                            : getString(R.string.budget_removed));
                }
            });
        });
    }

    /** "Automatic" (follow the latest transaction) or one fixed currency for the dashboard. */
    private void setUpDisplayCurrency() {
        Spinner sp = findViewById(R.id.spDisplayCurrency);
        Currency[] all = Currency.values();
        String[] labels = new String[all.length + 1];
        labels[0] = getString(R.string.currency_auto_short);
        for (int i = 0; i < all.length; i++) {
            labels[i + 1] = all[i].getPickerLabel();
        }
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        Currency current = settings.getDisplayCurrency();
        sp.setSelection(current == null ? 0 : current.ordinal() + 1);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setDisplayCurrency(position == 0 ? null : all[position - 1]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    private void offerRecategorise() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.recategorise_after_toggle)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.recategorise_go, (d, w) -> recategorise())
                .show();
    }

    private void recategorise() {
        final boolean builtIn = settings.isBuiltInKeywordsOn();
        final CategoryRepository categories = CategoryRepository.getInstance(this);
        Background.run(() -> {
            int changed = transactions.recategoriseAutomatic(categories.buildEngine(builtIn));
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showMessage(getResources().getQuantityString(R.plurals.recategorised, changed, changed));
                }
            });
        });
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG).show();
    }
}
