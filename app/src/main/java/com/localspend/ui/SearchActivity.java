package com.localspend.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.TransactionFilter;
import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.model.TransactionType;
import com.localspend.util.Background;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Searches every transaction: words in the merchant, category or original message, plus an
 * optional category, income or expense, date range and amount range. Results update as you type.
 * Tapping a result opens it for editing, as in History.
 */
public class SearchActivity extends AppCompatActivity {

    private TransactionRepository repository;
    private CategoryRepository categoryRepository;
    private AppSettings settings;
    private TransactionAdapter adapter;

    private TextInputEditText etSearch;
    private TextInputEditText etMin;
    private TextInputEditText etMax;
    private Button btnCategory;
    private Button btnType;
    private Button btnFrom;
    private Button btnTo;
    private TextView tvCount;
    private Currency dashboardCurrency;
    private TextView tvEmpty;

    private List<Transaction> everything = new ArrayList<>();
    private String categoryKey;
    private TransactionType type;
    private Long fromMillis;
    private Long toDayMillis;       // start of the chosen last day; the search runs to the day's end

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        setTitle(R.string.search_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = TransactionRepository.getInstance(this);
        categoryRepository = CategoryRepository.getInstance(this);
        settings = new AppSettings(this);

        etSearch = findViewById(R.id.etSearch);
        etMin = findViewById(R.id.etSearchMin);
        etMax = findViewById(R.id.etSearchMax);
        btnCategory = findViewById(R.id.btnSearchCategory);
        btnType = findViewById(R.id.btnSearchType);
        btnFrom = findViewById(R.id.btnSearchFrom);
        btnTo = findViewById(R.id.btnSearchTo);
        tvCount = findViewById(R.id.tvSearchCount);
        tvEmpty = findViewById(R.id.tvSearchEmpty);

        adapter = new TransactionAdapter(t -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
            startActivity(intent);
        });
        RecyclerView rv = findViewById(R.id.rvSearch);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        TextWatcher refresh = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                runSearch();
            }
        };
        etSearch.addTextChangedListener(refresh);
        etMin.addTextChangedListener(refresh);
        etMax.addTextChangedListener(refresh);

        btnCategory.setOnClickListener(v -> chooseCategory());
        btnType.setOnClickListener(v -> chooseType());
        btnFrom.setOnClickListener(v -> pickDate(true));
        btnTo.setOnClickListener(v -> pickDate(false));
        findViewById(R.id.btnSearchClear).setOnClickListener(v -> clearFilters());
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /** Reads every transaction once (again after editing one), then filters in memory. */
    private void load() {
        Background.run(() -> {
            Currency currency = CurrencySwitch.resolve(settings, repository);
            List<Transaction> all = repository.getAll();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                everything = all;
                adapter.setMainCurrency(currency);
                dashboardCurrency = currency;
                ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.tilSearchMin)).setPrefixText(currency.getSymbol());
                ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.tilSearchMax)).setPrefixText(currency.getSymbol());
                ((TextView) findViewById(R.id.tvSearchNote)).setText(getString(R.string.search_amounts_note, currency.name()));
                runSearch();
            });
        });
    }

    private void runSearch() {
        TransactionFilter filter = new TransactionFilter()
                .setText(etSearch.getText() == null ? "" : etSearch.getText().toString())
                .setCategoryKey(categoryKey)
                .setType(type)
                .setFromMillis(fromMillis)
                .setToMillisExclusive(toDayMillis == null ? null : nextDay(toDayMillis))
                .setMinCents(toCents(etMin))
                .setMaxCents(toCents(etMax))
                .setAmountCurrency(dashboardCurrency);
        List<Transaction> found = filter.apply(everything);
        adapter.setItems(found);
        tvCount.setText(getString(R.string.search_count, found.size()));
        tvEmpty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static Long toCents(TextInputEditText field) {
        String text = field.getText() == null ? "" : field.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text).movePointRight(2).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long nextDay(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    private void chooseCategory() {
        final List<Category> categories = categoryRepository.getCatalog().all();
        String[] labels = new String[categories.size() + 1];
        labels[0] = getString(R.string.search_any_category);
        for (int i = 0; i < categories.size(); i++) {
            labels[i + 1] = categories.get(i).getLabel();
        }
        new AlertDialog.Builder(this)
                .setItems(labels, (d, which) -> {
                    categoryKey = which == 0 ? null : categories.get(which - 1).getKey();
                    updateButtons();
                    runSearch();
                })
                .show();
    }

    private void chooseType() {
        String[] labels = {getString(R.string.search_type_any), getString(R.string.search_type_expense),
                getString(R.string.search_type_income)};
        new AlertDialog.Builder(this)
                .setItems(labels, (d, which) -> {
                    type = which == 0 ? null : which == 1 ? TransactionType.EXPENSE : TransactionType.INCOME;
                    updateButtons();
                    runSearch();
                })
                .show();
    }

    private void pickDate(boolean isFrom) {
        Calendar c = Calendar.getInstance();
        Long current = isFrom ? fromMillis : toDayMillis;
        if (current != null) {
            c.setTimeInMillis(current);
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar day = Calendar.getInstance();
            day.clear();
            day.set(y, m, d);
            if (isFrom) {
                fromMillis = day.getTimeInMillis();
            } else {
                toDayMillis = day.getTimeInMillis();
            }
            updateButtons();
            runSearch();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void clearFilters() {
        etSearch.setText("");
        etMin.setText("");
        etMax.setText("");
        categoryKey = null;
        type = null;
        fromMillis = null;
        toDayMillis = null;
        updateButtons();
        runSearch();
    }

    private void updateButtons() {
        Category category = categoryKey == null ? null : categoryRepository.getCatalog().find(categoryKey);
        if (category == null) {
            categoryKey = null;
        }
        btnCategory.setText(category == null ? getString(R.string.search_any_category) : category.getLabel());
        btnType.setText(type == null ? R.string.search_type_any
                : type == TransactionType.EXPENSE ? R.string.search_type_expense
                : R.string.search_type_income);
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);
        btnFrom.setText(fromMillis == null ? getString(R.string.search_from)
                : getString(R.string.search_from_value, format.format(new Date(fromMillis))));
        btnTo.setText(toDayMillis == null ? getString(R.string.search_to)
                : getString(R.string.search_to_value, format.format(new Date(toDayMillis))));
    }
}
