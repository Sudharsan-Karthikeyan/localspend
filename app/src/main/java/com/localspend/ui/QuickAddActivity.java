package com.localspend.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.CategorisationEngine;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;
import com.localspend.notify.BudgetAlerts;
import com.localspend.util.Background;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A small pop-up, opened from the home-screen widget, for recording today's expense in a few
 * taps: an amount, an optional note and (optionally) a category. Without a category it is chosen
 * from the note automatically.
 */
public class QuickAddActivity extends AppCompatActivity {

    private TextInputLayout tilAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etNote;
    private Spinner spCurrency;
    private Spinner spCategory;

    private CategoryCatalog catalog = CategoryCatalog.defaults();
    private CategorisationEngine engine = CategorisationEngine.defaultEngine();
    private boolean loaded = false;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_add);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        tilAmount = findViewById(R.id.tilAmount);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        spCurrency = findViewById(R.id.spCurrency);
        spCategory = findViewById(R.id.spCategory);
        CurrencySpinners.setUp(spCurrency, tilAmount, Currency.defaultCurrency());

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        final AppSettings settings = new AppSettings(this);
        final TransactionRepository repository = TransactionRepository.getInstance(this);
        final CategoryRepository categories = CategoryRepository.getInstance(this);
        final boolean useBuiltIn = settings.isBuiltInKeywordsOn();
        Background.run(() -> {
            Currency currency = CurrencySwitch.resolve(settings, repository);
            CategoryCatalog loadedCatalog = categories.getCatalog();
            CategorisationEngine loadedEngine = categories.buildEngine(useBuiltIn);
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                catalog = loadedCatalog;
                engine = loadedEngine;
                List<String> labels = new ArrayList<>();
                labels.add(getString(R.string.quick_category_auto));
                labels.addAll(Arrays.asList(catalog.labels()));
                spCategory.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, labels));
                CurrencySpinners.setUp(spCurrency, tilAmount, currency);
                loaded = true;
            });
        });
    }

    private void save() {
        if (saving || !loaded) {
            return;
        }
        tilAmount.setError(null);
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

        String note = String.valueOf(etNote.getText()).trim();
        String merchant = note.isEmpty() ? getString(R.string.quick_expense_merchant) : note;
        int position = spCategory.getSelectedItemPosition();
        boolean chosen = position > 0;
        Category category;
        if (chosen) {
            List<Category> all = catalog.all();
            category = position - 1 < all.size() ? all.get(position - 1) : catalog.getOther();
        } else {
            category = note.isEmpty() ? catalog.getOther() : engine.categorise(note);
        }

        final Currency currency = CurrencySpinners.selected(spCurrency);
        final Transaction t = new Transaction(cents, merchant, System.currentTimeMillis(),
                TransactionType.EXPENSE, category, Source.MANUAL);
        t.setCurrency(currency);
        t.setCategoryChosenByUser(chosen);

        saving = true;
        final Context app = getApplicationContext();
        final TransactionRepository repository = TransactionRepository.getInstance(app);
        final long amount = cents;
        Background.run(() -> {
            repository.insert(t);
            BudgetAlerts.check(app);
            Background.main(() -> {
                Toast.makeText(app, app.getString(R.string.quick_added,
                        MoneyFormatter.format(amount, currency)), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
