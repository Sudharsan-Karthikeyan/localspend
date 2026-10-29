package com.localspend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.BudgetRepository;
import com.localspend.data.BudgetRepository.Scope;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.BudgetHistory;
import com.localspend.model.Category;
import com.localspend.model.Currency;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The budgets a month had: the overall one and one per category. The current month asks whether a
 * new budget applies from now or to earlier months too; a past month changes only itself. This is
 * also the only place to remove a budget from an earlier month.
 */
public class BudgetMonthActivity extends AppCompatActivity {

    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_MONTH = "month";

    private static final int MAX_MONTHS_BACK = 60;

    private BudgetRepository budgets;
    private CategoryRepository categories;
    private AppSettings settings;
    private TransactionRepository transactions;

    private int year;
    private int month;
    private final int nowYear = Calendar.getInstance().get(Calendar.YEAR);
    private final int nowMonth = Calendar.getInstance().get(Calendar.MONTH);

    private TextView tvMonth;
    private TextView tvNote;
    private LinearLayout rows;
    private Button btnPrev;
    private Button btnNext;
    private Currency currency = Currency.defaultCurrency();

    /** One editable row: which budget it is, its field, and what the month had when it was shown. */
    private static final class Row {
        final String key;
        final TextInputEditText field;
        final long shownCents;

        Row(String key, TextInputEditText field, long shownCents) {
            this.key = key;
            this.field = field;
            this.shownCents = shownCents;
        }
    }

    private final List<Row> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_month);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        budgets = BudgetRepository.getInstance(this);
        categories = CategoryRepository.getInstance(this);
        settings = new AppSettings(this);
        transactions = TransactionRepository.getInstance(this);

        year = getIntent().getIntExtra(EXTRA_YEAR, nowYear);
        month = getIntent().getIntExtra(EXTRA_MONTH, nowMonth);

        tvMonth = findViewById(R.id.tvBudgetMonth);
        tvNote = findViewById(R.id.tvBudgetNote);
        rows = findViewById(R.id.llBudgetRows);
        btnPrev = findViewById(R.id.btnBudgetPrev);
        btnNext = findViewById(R.id.btnBudgetNext);
        btnPrev.setOnClickListener(v -> move(-1));
        btnNext.setOnClickListener(v -> move(1));
        findViewById(R.id.btnBudgetSave).setOnClickListener(v -> save());
        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private boolean isCurrentMonth() {
        return year == nowYear && month == nowMonth;
    }

    private void move(int delta) {
        int[] ym = Dates.shiftMonth(year, month, delta);
        int away = (nowYear * 12 + nowMonth) - (ym[0] * 12 + ym[1]);
        if (away < 0 || away > MAX_MONTHS_BACK) {
            return;
        }
        year = ym[0];
        month = ym[1];
        load();
    }

    private void load() {
        final int y = year;
        final int m = month;
        Background.run(() -> {
            Currency dashboard = CurrencySwitch.resolve(settings, transactions);
            BudgetHistory history = budgets.load();
            List<Category> all = categories.getCatalog().all();
            Background.main(() -> {
                if (isFinishing() || isDestroyed() || y != year || m != month) {
                    return;
                }
                currency = dashboard;
                show(history, all);
            });
        });
    }

    private void show(BudgetHistory history, List<Category> all) {
        String label = Dates.monthLabel(year, month);
        setTitle(getString(R.string.budgets_for_month, label));
        tvMonth.setText(label);
        tvNote.setText(isCurrentMonth() ? getString(R.string.budgets_for_month_help)
                : getString(R.string.budgets_past_note));
        btnNext.setEnabled(!isCurrentMonth());
        btnNext.setAlpha(isCurrentMonth() ? 0.3f : 1f);
        rows.removeAllViews();
        shown.clear();
        int index = BudgetHistory.monthIndex(year, month);
        addRow(BudgetHistory.OVERALL, getString(R.string.budgets_overall),
                history.centsFor(BudgetHistory.OVERALL, index));
        for (Category c : all) {
            addRow(c.getKey(), c.getLabel(), history.centsFor(c.getKey(), index));
        }
        Button manage = new Button(this);
        manage.setText(R.string.budgets_manage_categories);
        manage.setOnClickListener(v -> startActivity(new Intent(this, CategoriesActivity.class)));
        rows.addView(manage);
    }

    private void addRow(String key, String label, long cents) {
        View row = getLayoutInflater().inflate(R.layout.row_budget_edit, rows, false);
        TextInputLayout til = row.findViewById(R.id.tilBudgetRow);
        TextInputEditText field = row.findViewById(R.id.etBudgetRow);
        til.setHint(label);
        til.setPrefixText(currency.getSymbol());
        if (cents > 0) {
            field.setText((cents / 100) + "." + (cents % 100 < 10 ? "0" : "") + (cents % 100));
        }
        rows.addView(row);
        shown.add(new Row(key, field, cents));
    }

    private void save() {
        final List<Row> changed = new ArrayList<>();
        final List<Long> newCents = new ArrayList<>();
        boolean anySet = false;
        for (Row r : shown) {
            String text = String.valueOf(r.field.getText()).trim();
            long cents = 0;
            if (!text.isEmpty()) {
                try {
                    cents = MoneyFormatter.parseToCents(text);
                } catch (NumberFormatException e) {
                    r.field.setError(getString(R.string.error_budget));
                    return;
                }
            }
            if (cents != r.shownCents) {
                changed.add(r);
                newCents.add(cents);
                anySet |= cents > 0;
            }
        }
        if (changed.isEmpty()) {
            finish();
            return;
        }
        if (isCurrentMonth()) {
            // Setting a budget for the current month asks once how far back it reaches.
            BudgetScopeDialog.choose(this, anySet ? 1 : 0, Dates.monthLabel(year, month),
                    scope -> apply(changed, newCents, scope));
        } else {
            apply(changed, newCents, Scope.THIS_MONTH_ONLY);
        }
    }

    private void apply(List<Row> changed, List<Long> newCents, Scope chosen) {
        final int index = BudgetHistory.monthIndex(year, month);
        final android.content.Context app = getApplicationContext();
        Background.run(() -> {
            for (int i = 0; i < changed.size(); i++) {
                long cents = newCents.get(i);
                // Removing a budget in the current month never reaches earlier months.
                Scope scope = chosen == Scope.ALL_MONTHS && cents == 0 ? Scope.FROM_THIS_MONTH : chosen;
                BudgetRepository.getInstance(app).apply(changed.get(i).key, index, cents, scope);
            }
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    android.widget.Toast.makeText(this, R.string.budgets_saved,
                            android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }
}
