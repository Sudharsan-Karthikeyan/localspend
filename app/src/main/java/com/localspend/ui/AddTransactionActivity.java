package com.localspend.ui;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.RecurringRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.CategorisationEngine;
import com.localspend.logic.MerchantNormaliser;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Frequency;
import com.localspend.model.RecurringPayment;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;
import com.localspend.notify.BudgetAlerts;
import com.localspend.util.Background;
import com.localspend.logic.ExchangeRates;
import com.localspend.util.Dates;
import com.localspend.util.FxText;
import com.localspend.util.MoneyFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Add a transaction by hand, or edit an existing one (pass EXTRA_TRANSACTION_ID).
 *
 * While typing the merchant, the category is suggested automatically. The user can pick any of
 * their categories or create a new one straight from the list. When the category of an existing
 * transaction is changed, the user chooses whether the change applies to just this transaction,
 * to future ones from the same merchant, or to every transaction from that merchant.
 *
 * Transactions captured from an SMS show the complete original message they were read from.
 */
public class AddTransactionActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    /** Optional: start with this date (milliseconds) instead of today. */
    public static final String EXTRA_DATE = "date_millis";

    private TransactionRepository repository;
    private CategoryRepository categoryRepository;
    private CategorisationEngine engine = CategorisationEngine.defaultEngine();
    private CategoryCatalog catalog = CategoryCatalog.defaults();
    private final List<Category> spinnerCategories = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private MaterialButtonToggleGroup toggleType;
    private TextInputLayout tilAmount;
    private TextInputLayout tilMerchant;
    private TextInputEditText etAmount;
    private TextInputEditText etMerchant;
    private Spinner spCategory;
    private Spinner spCurrency;
    private Button btnDate;
    private Button btnDelete;
    private View cardSms;
    private TextView tvSmsSender;
    private TextView tvSmsReceived;
    private TextView tvSmsBody;
    private View btnCopySms;

    private final Calendar selectedDate = Calendar.getInstance();
    private Transaction editing;            // null when adding a new transaction
    private Category originalCategory;
    private int autoPosition = -1;          // spinner position last chosen by the app, not the user
    private int lastRealPosition = 0;       // last position that was a category (not "New...")
    private boolean userPickedCategory = false;
    private boolean saving = false;

    // Conversion into the main currency (shown only for a payment in another currency)
    private View cardFx;
    private TextView tvFxTitle;
    private TextView tvFxCalc;
    private TextView tvFxNote;
    private TextInputLayout tilFxRate;
    private TextInputEditText etFxRate;
    private Currency mainCurrency;               // null until loaded
    private ExchangeRates fxRates = new ExchangeRates(null);
    private boolean fxRateTyped = false;         // the user entered the rate themselves
    private boolean fxSettingText = false;       // the app, not the user, is changing the rate field
    private BigDecimal fxRateToSave;             // remembered for the day when the user typed it

    // Repeat / instalment chosen on this screen (new transactions only)
    private Button btnRecurrence;
    private boolean recurrenceSet = false;
    private Frequency recFrequency = Frequency.MONTHLY;
    private boolean recInstallment = false;
    private int recTotal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = TransactionRepository.getInstance(this);
        categoryRepository = CategoryRepository.getInstance(this);

        toggleType = findViewById(R.id.toggleType);
        tilAmount = findViewById(R.id.tilAmount);
        tilMerchant = findViewById(R.id.tilMerchant);
        etAmount = findViewById(R.id.etAmount);
        etMerchant = findViewById(R.id.etMerchant);
        spCategory = findViewById(R.id.spCategory);
        spCurrency = findViewById(R.id.spCurrency);
        cardFx = findViewById(R.id.cardFx);
        tvFxTitle = findViewById(R.id.tvFxTitle);
        tvFxCalc = findViewById(R.id.tvFxCalc);
        tvFxNote = findViewById(R.id.tvFxNote);
        tilFxRate = findViewById(R.id.tilFxRate);
        etFxRate = findViewById(R.id.etFxRate);
        setUpCurrencyPicker();
        btnDate = findViewById(R.id.btnDate);
        btnDelete = findViewById(R.id.btnDelete);
        cardSms = findViewById(R.id.cardSms);
        tvSmsSender = findViewById(R.id.tvSmsSender);
        tvSmsReceived = findViewById(R.id.tvSmsReceived);
        tvSmsBody = findViewById(R.id.tvSmsBody);
        btnCopySms = findViewById(R.id.btnCopySms);

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>());
        spCategory.setAdapter(spinnerAdapter);
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == spinnerCategories.size()) {
                    // The last row is "+ New category..."
                    askNewCategory();
                    return;
                }
                lastRealPosition = position;
                if (position != autoPosition) {
                    userPickedCategory = true;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        toggleType.check(R.id.btnExpense);
        long startDate = getIntent().getLongExtra(EXTRA_DATE, 0);
        if (startDate > 0) {
            selectedDate.setTimeInMillis(startDate);
        }
        updateDateButton();
        btnDate.setOnClickListener(v -> pickDate());
        btnRecurrence = findViewById(R.id.btnRecurrence);
        btnRecurrence.setOnClickListener(v -> showRecurrenceMenu());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());
        btnCopySms.setOnClickListener(v -> copySms());

        long id = getIntent().getLongExtra(EXTRA_TRANSACTION_ID, Transaction.NO_ID);
        setTitle(id != Transaction.NO_ID ? R.string.edit_transaction : R.string.add_transaction);

        // Load the user's categories and categorisation rules (and the transaction when editing)
        // once, so suggestions while typing never touch the database.
        final AppSettings settings = new AppSettings(this);
        final boolean useBuiltIn = settings.isBuiltInKeywordsOn();
        Background.run(() -> {
            Currency defaultCurrency = CurrencySwitch.resolve(settings, repository);
            CategoryCatalog loadedCatalog = categoryRepository.getCatalog();
            CategorisationEngine loadedEngine = categoryRepository.buildEngine(useBuiltIn);
            Transaction t = id != Transaction.NO_ID ? repository.getById(id) : null;
            ExchangeRates loadedRates = repository.loadRates();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                catalog = loadedCatalog;
                engine = loadedEngine;
                mainCurrency = defaultCurrency;     // the main currency (see CurrencySwitch.resolve)
                fxRates = loadedRates;
                fillSpinner();
                if (id != Transaction.NO_ID) {
                    if (t == null) {
                        finish();
                        return;
                    }
                    populate(t);
                } else {
                    selectCategory(catalog.getOther());
                    selectCurrency(defaultCurrency);
                }
                watchMerchant();
                watchFx();
                updateFx();
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void fillSpinner() {
        spinnerCategories.clear();
        spinnerCategories.addAll(catalog.all());
        List<String> labels = new ArrayList<>(Arrays.asList(catalog.labels()));
        labels.add(getString(R.string.new_category_option));
        spinnerAdapter.clear();
        spinnerAdapter.addAll(labels);
        spinnerAdapter.notifyDataSetChanged();
    }

    private void populate(Transaction t) {
        editing = t;
        originalCategory = t.getCategory();
        toggleType.check(t.isExpense() ? R.id.btnExpense : R.id.btnIncome);
        etAmount.setText(plainAmount(t.getAmountCents()));
        etMerchant.setText(t.getMerchant());
        selectedDate.setTimeInMillis(t.getDateMillis());
        updateDateButton();
        selectCategory(t.getCategory());
        selectCurrency(t.getCurrency());
        fxRateTyped = false;
        userPickedCategory = true;   // never auto-change a category that is already decided
        btnDelete.setVisibility(View.VISIBLE);
        btnRecurrence.setVisibility(View.GONE);   // repeating is set up when a payment is first added
        showOriginalSms(t);
    }

    /** Shows the complete message the transaction was read from. */
    private void showOriginalSms(Transaction t) {
        if (t.getSource() != Source.SMS) {
            cardSms.setVisibility(View.GONE);
            return;
        }
        cardSms.setVisibility(View.VISIBLE);
        if (t.hasOriginalSms()) {
            String sender = t.getSmsSender() == null || t.getSmsSender().isEmpty()
                    ? getString(R.string.unknown_sender) : t.getSmsSender();
            tvSmsSender.setText(getString(R.string.sms_from, sender));
            tvSmsReceived.setText(getString(R.string.sms_received_at,
                    Dates.formatDateTime(t.getSmsReceivedMillis())));
            tvSmsReceived.setVisibility(t.getSmsReceivedMillis() > 0 ? View.VISIBLE : View.GONE);
            tvSmsBody.setText(t.getSmsBody());
            btnCopySms.setVisibility(View.VISIBLE);
        } else {
            tvSmsSender.setText(R.string.sms_not_stored_title);
            tvSmsReceived.setVisibility(View.GONE);
            tvSmsBody.setText(R.string.sms_not_stored_body);
            btnCopySms.setVisibility(View.GONE);
        }
    }

    private void copySms() {
        if (editing == null || !editing.hasOriginalSms()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.original_sms),
                    editing.getSmsBody()));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Currency picker beside the amount; the amount field's prefix follows it ("$", "₹"). */
    private void setUpCurrencyPicker() {
        Currency[] all = Currency.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = all[i].getPickerLabel();
        }
        spCurrency.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                tilAmount.setPrefixText(all[position].getSymbol());
                updateFx();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        selectCurrency(Currency.defaultCurrency());
    }

    private void selectCurrency(Currency currency) {
        spCurrency.setSelection(currency.ordinal());
        tilAmount.setPrefixText(currency.getSymbol());
    }

    private Currency selectedCurrency() {
        int position = spCurrency.getSelectedItemPosition();
        Currency[] all = Currency.values();
        return position >= 0 && position < all.length ? all[position] : Currency.defaultCurrency();
    }

    private static String plainAmount(long cents) {
        return (cents / 100) + "." + (cents % 100 < 10 ? "0" : "") + (cents % 100);
    }

    /** Suggest a category as the merchant name is typed, unless the user has chosen one. */
    private void watchMerchant() {
        etMerchant.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                tilMerchant.setError(null);
                if (!userPickedCategory) {
                    selectCategory(engine.categorise(s.toString()));
                }
            }
        });
    }

    private void watchFx() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFx();
            }
        };
        etAmount.addTextChangedListener(watcher);
        etFxRate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!fxSettingText) {
                    fxRateTyped = true;
                    tilFxRate.setError(null);
                    updateFx();
                }
            }
        });
    }

    /** The rate typed by the user, or null when the field is empty or not a positive number. */
    private BigDecimal typedRate() {
        try {
            BigDecimal r = new BigDecimal(String.valueOf(etFxRate.getText()).trim().replace(",", ""));
            return r.signum() > 0 ? r : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Shows how a payment in another currency converts into the main one: the rate for the
     * payment's day (the user's own, or an estimate they can correct), and the sum.
     */
    private void updateFx() {
        Currency from = selectedCurrency();
        if (mainCurrency == null || from == mainCurrency) {
            cardFx.setVisibility(View.GONE);
            return;
        }
        cardFx.setVisibility(View.VISIBLE);
        long date = selectedDate.getTimeInMillis();
        ExchangeRates.Rate found = fxRates.find(from, mainCurrency, date);
        tvFxTitle.setText(getString(R.string.fx_title, mainCurrency.getDisplayName()));
        tilFxRate.setHint(getString(R.string.fx_hint_rate, mainCurrency.getSymbol(), from.getShortLabel()));
        if (found == null || found.isEstimated()) {
            fetchRateInBackground(from, date);   // the real rate for the day, when online
        }
        if (!fxRateTyped) {
            fxSettingText = true;
            etFxRate.setText(found == null ? "" : FxText.rate(found.getValue()));
            fxSettingText = false;
        }
        BigDecimal rate = fxRateTyped ? typedRate() : (found == null ? null : found.getValue());
        long cents;
        try {
            cents = MoneyFormatter.parseToCents(String.valueOf(etAmount.getText()));
        } catch (NumberFormatException e) {
            cents = 0;
        }
        if (rate == null || cents <= 0) {
            tvFxCalc.setText("");
        } else {
            tvFxCalc.setText(FxText.calculation(cents, from, rate,
                    ExchangeRates.convert(cents, rate), mainCurrency));
        }
        String day = Dates.formatDay(ExchangeRates.dayStart(date));
        if (fxRateTyped || (found != null && !found.isEstimated())) {
            tvFxNote.setText(getString(R.string.fx_note_exact, day));
        } else if (found != null) {
            tvFxNote.setText(getString(R.string.fx_note_nearby, Dates.formatDay(found.getDayStart())));
        } else {
            tvFxNote.setText(R.string.fx_note_none);
        }
    }

    private final java.util.Set<String> fetchedKeys = new java.util.HashSet<>();

    /** Asks for the real rate of the chosen day once, then redraws the card with it. */
    private void fetchRateInBackground(Currency from, long date) {
        final Currency to = mainCurrency;
        final String key = from.name() + ">" + to.name() + "@" + ExchangeRates.dayStart(date);
        if (!fetchedKeys.add(key)) {
            return;
        }
        TransactionRepository.runOnNetwork(() -> {
            if (repository.fetchRate(from, to, date)) {
                ExchangeRates reloaded = repository.loadRates();
                Background.main(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        fxRates = reloaded;
                        updateFx();
                    }
                });
            }
        });
    }

    /** Moves the spinner as the app (not the user) so it is not mistaken for a user choice. */
    private void selectCategory(Category category) {
        int position = spinnerCategories.indexOf(catalog.resolve(category));
        if (position < 0) {
            position = spinnerCategories.indexOf(catalog.getOther());
        }
        autoPosition = position;
        lastRealPosition = position;
        if (spCategory.getSelectedItemPosition() != position) {
            spCategory.setSelection(position);
        }
    }

    /** Lets the user create a category without leaving the screen. */
    private void askNewCategory() {
        CategoryDialogs.askNewCategory(this, catalog, created -> Background.run(() -> {
            categoryRepository.saveCategory(created);
            CategoryCatalog updated = categoryRepository.getCatalog();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                catalog = updated;
                fillSpinner();
                int position = spinnerCategories.indexOf(created);
                lastRealPosition = Math.max(position, 0);
                userPickedCategory = true;
                spCategory.setSelection(lastRealPosition);
            });
        }), () -> {
            if (!userPickedCategory) {
                autoPosition = lastRealPosition;   // reverting is not a user choice
            }
            spCategory.setSelection(lastRealPosition);
        });
    }

    // ---- Repeat / Instalment -----------------------------------------------------------------

    private void showRecurrenceMenu() {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.recurrence_repeat));
        items.add(getString(R.string.recurrence_installment));
        if (recurrenceSet) {
            items.add(getString(R.string.recurrence_none));
        }
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        askRecurrence(false);
                    } else if (which == 1) {
                        askRecurrence(true);
                    } else {
                        recurrenceSet = false;
                        updateRecurrenceButton();
                    }
                })
                .show();
    }

    /** Asks how often it happens and, for an instalment plan, how many payments there are. */
    private void askRecurrence(boolean installment) {
        final float density = getResources().getDisplayMetrics().density;
        final int pad = (int) (20 * density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView label = new TextView(this);
        label.setText(R.string.recurrence_how_often);
        box.addView(label);

        Frequency[] all = Frequency.values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].getLabel();
        }
        Spinner spFrequency = new Spinner(this);
        spFrequency.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        spFrequency.setSelection(recurrenceSet ? recFrequency.ordinal() : Frequency.MONTHLY.ordinal());
        box.addView(spFrequency);

        EditText etCount = new EditText(this);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setHint(installment ? R.string.recurrence_installment_hint
                : R.string.recurrence_stop_hint);
        if (recurrenceSet && recInstallment == installment && recTotal > 0) {
            etCount.setText(String.valueOf(recTotal));
        }
        box.addView(etCount);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(installment ? R.string.recurrence_installment_title
                        : R.string.recurrence_repeat_title)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.recurrence_set, null)
                .create();
        dialog.show();
        // Set the click listener after show() so a bad entry keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = etCount.getText().toString().trim();
            int total = 0;
            if (!text.isEmpty()) {
                try {
                    total = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    total = -1;
                }
            }
            boolean valid = text.isEmpty() ? !installment : total >= 2 && total <= 360;
            if (!valid) {
                etCount.setError(getString(installment ? R.string.recurrence_error_installments
                        : R.string.recurrence_error_stop));
                return;
            }
            recurrenceSet = true;
            recInstallment = installment;
            recFrequency = all[Math.max(spFrequency.getSelectedItemPosition(), 0)];
            recTotal = total;
            updateRecurrenceButton();
            dialog.dismiss();
        });
    }

    private void updateRecurrenceButton() {
        btnRecurrence.setText(recurrenceSet
                ? RecurrenceText.describe(this, recFrequency, recInstallment, recTotal)
                : getString(R.string.recurrence_button));
    }

    private void pickDate() {
        new DatePickerDialog(this, (picker, year, month, day) -> {
            selectedDate.set(year, month, day);
            updateDateButton();
            updateFx();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButton() {
        btnDate.setText(Dates.formatDay(selectedDate.getTimeInMillis()));
    }

    private void save() {
        if (saving) {
            return;
        }
        tilAmount.setError(null);
        tilMerchant.setError(null);

        long cents;
        try {
            cents = MoneyFormatter.parseToCents(String.valueOf(etAmount.getText()));
        } catch (NumberFormatException e) {
            cents = 0;
        }
        if (cents <= 0) {
            tilAmount.setError(getString(R.string.error_amount));
            return;
        }
        String merchant = String.valueOf(etMerchant.getText()).trim();
        if (merchant.isEmpty()) {
            tilMerchant.setError(getString(R.string.error_merchant));
            return;
        }
        if (spinnerCategories.isEmpty()) {
            return;   // categories still loading
        }

        // A payment in another currency is stored with its conversion into the main currency.
        final Currency from = selectedCurrency();
        final boolean convert = mainCurrency != null && from != mainCurrency;
        BigDecimal chosenRate = null;
        boolean estimated = true;
        long rateDay = ExchangeRates.dayStart(selectedDate.getTimeInMillis());
        if (convert) {
            if (fxRateTyped) {
                chosenRate = typedRate();
                if (chosenRate == null) {
                    tilFxRate.setError(getString(R.string.fx_error_rate));
                    return;
                }
                estimated = false;
                fxRateToSave = chosenRate;
            } else {
                ExchangeRates.Rate found = fxRates.find(from, mainCurrency,
                        selectedDate.getTimeInMillis());
                if (found != null) {
                    chosenRate = found.getValue();
                    estimated = found.isEstimated();
                    rateDay = found.getDayStart();
                }   // otherwise it is converted later, once a rate has been fetched
                fxRateToSave = null;
            }
        } else {
            fxRateToSave = null;
        }

        TransactionType type = toggleType.getCheckedButtonId() == R.id.btnIncome
                ? TransactionType.INCOME : TransactionType.EXPENSE;
        int position = spCategory.getSelectedItemPosition();
        Category category = position >= 0 && position < spinnerCategories.size()
                ? spinnerCategories.get(position) : catalog.getOther();

        final Transaction t;
        boolean categoryChanged;
        if (editing != null) {
            t = editing;
            categoryChanged = !category.equals(originalCategory);
            t.setAmountCents(cents);
            t.setMerchant(merchant);
            t.setType(type);
            t.setCategory(category);
            t.setDateMillis(selectedDate.getTimeInMillis());
            t.setCurrency(selectedCurrency());
            if (categoryChanged) {
                t.setCategoryChosenByUser(true);
            }
        } else {
            t = new Transaction(cents, merchant, withCurrentTime(selectedDate),
                    type, category, Source.MANUAL);
            t.setCurrency(selectedCurrency());
            t.setCategoryChosenByUser(userPickedCategory);
            categoryChanged = userPickedCategory;
        }

        if (convert && chosenRate != null) {
            t.setConversion(mainCurrency, chosenRate, rateDay, estimated,
                    ExchangeRates.convert(cents, chosenRate));
        }

        if (type == TransactionType.EXPENSE && editing != null && categoryChanged) {
            askHowFarToApply(t);
        } else {
            boolean learn = type == TransactionType.EXPENSE && categoryChanged;
            persist(t, learn ? ApplyScope.FUTURE : ApplyScope.ONLY_THIS);
        }
    }

    /** How far a category change should reach. */
    private enum ApplyScope {
        ONLY_THIS, FUTURE, ALL
    }

    /**
     * When the category of a transaction changes, ask whether to remember it for the merchant and
     * whether to move the merchant's earlier transactions too.
     */
    private void askHowFarToApply(Transaction t) {
        saving = true;
        final String key = MerchantNormaliser.merchantKey(t.getMerchant());
        Background.run(() -> {
            int others = repository.getByMerchant(key, t.getId()).size();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                saving = false;
                String[] options = others > 0
                        ? new String[]{
                        getString(R.string.apply_only_this),
                        getString(R.string.apply_future, t.getMerchant()),
                        getResources().getQuantityString(R.plurals.apply_all, others, others,
                                t.getMerchant())}
                        : new String[]{
                        getString(R.string.apply_only_this),
                        getString(R.string.apply_future, t.getMerchant())};
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.apply_title, t.getCategory().getLabel()))
                        .setItems(options, (d, which) -> persist(t,
                                which == 0 ? ApplyScope.ONLY_THIS
                                        : which == 1 ? ApplyScope.FUTURE : ApplyScope.ALL))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    private void persist(Transaction t, ApplyScope scope) {
        saving = true;
        final Context appContext = getApplicationContext();
        final boolean isNew = t.getId() == Transaction.NO_ID;
        final RecurringPayment plan = isNew && recurrenceSet
                ? RecurringPayment.startingAfter(t, recFrequency, recInstallment, recTotal) : null;
        final BigDecimal rateToSave = fxRateToSave;
        Background.run(() -> {
            if (rateToSave != null && t.hasConversion()) {
                // Remember the rate the user typed for this day, so other payments that day use it.
                repository.saveRate(t.getCurrency(), t.getConvertedCurrency(), t.getDateMillis(),
                        rateToSave);
            }
            if (isNew) {
                if (repository.insert(t) != -1 && plan != null) {
                    // The payment above is the first one; the plan adds the later ones.
                    RecurringRepository.getInstance(appContext).insert(plan);
                }
            } else {
                repository.update(t);
            }
            String key = MerchantNormaliser.merchantKey(t.getMerchant());
            int moved = 0;
            if (scope != ApplyScope.ONLY_THIS) {
                categoryRepository.saveLearnedRule(key, t.getCategory());
            }
            if (scope == ApplyScope.ALL) {
                moved = repository.applyCategoryToMerchant(key, t.getCategory());
            }
            BudgetAlerts.check(appContext);
            final int movedFinal = moved;
            Background.main(() -> {
                if (movedFinal > 0) {
                    Toast.makeText(appContext, appContext.getResources().getQuantityString(
                            R.plurals.moved_transactions, movedFinal, movedFinal,
                            t.getCategory().getLabel()), Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        });
    }

    /** A new entry dated today keeps the current time so it sorts above older entries. */
    private static long withCurrentTime(Calendar picked) {
        Calendar now = Calendar.getInstance();
        Calendar result = (Calendar) picked.clone();
        result.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        result.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
        result.set(Calendar.SECOND, now.get(Calendar.SECOND));
        return result.getTimeInMillis();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    final long id = editing.getId();
                    Background.run(() -> {
                        repository.delete(id);
                        Background.main(this::finish);
                    });
                })
                .show();
    }
}
