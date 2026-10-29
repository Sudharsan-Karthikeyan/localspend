package com.localspend.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.BudgetStatus;
import com.localspend.logic.BudgetTracker;
import com.localspend.logic.CategoryComparison;
import com.localspend.logic.Insight;
import com.localspend.logic.InsightGenerator;
import com.localspend.logic.MonthSummary;
import com.localspend.logic.SpendingAnalyzer;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.Calendar;
import java.util.List;

/**
 * Browse any month: totals, insights, budgets, how it compares with the month before, and every
 * transaction.
 * Tap a transaction to correct its category, amount or merchant.
 */
public class HistoryActivity extends AppCompatActivity {

    /** Optional: open on this month instead of the current one (month is 0-based). */
    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_MONTH = "month";
    /** Optional: only list transactions in this category (its key). */
    public static final String EXTRA_CATEGORY = "category";

    private final SpendingAnalyzer analyzer = new SpendingAnalyzer();
    private TransactionRepository repository;
    private CategoryRepository categoryRepository;
    private AppSettings settings;

    private int year;
    private int month;
    private final int currentYear;
    private final int currentMonth;
    private Long earliestMillis;

    private TextView tvMonth;
    private TextView tvSpentLabel;
    private TextView tvTotal;
    private TextView tvChange;
    private TextView tvIncome;
    private TextView tvEmpty;
    private android.widget.Button btnCurrency;
    private Currency shownCurrency = Currency.defaultCurrency();
    private List<Currency> currenciesInUse = new java.util.ArrayList<>();
    private TextView tvBudgetsHeader;
    private LinearLayout llInsights;
    private BudgetAdapter budgetAdapter;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ColorStateList defaultChangeColor;

    private String categoryFilter;            // category key, or null for all
    private List<Transaction> monthTransactions = new java.util.ArrayList<>();
    private TextView tvFilter;
    private TextView tvTransactionsHeader;

    private CategoryComparisonAdapter comparisonAdapter;
    private TransactionAdapter transactionAdapter;

    public HistoryActivity() {
        Calendar now = Calendar.getInstance();
        currentYear = now.get(Calendar.YEAR);
        currentMonth = now.get(Calendar.MONTH);
        year = currentYear;
        month = currentMonth;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        int startYear = getIntent().getIntExtra(EXTRA_YEAR, -1);
        int startMonth = getIntent().getIntExtra(EXTRA_MONTH, -1);
        if (startYear > 0 && startMonth >= 0 && startMonth <= 11) {
            year = startYear;
            month = startMonth;
        }
        categoryFilter = getIntent().getStringExtra(EXTRA_CATEGORY);
        setTitle(R.string.history);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = TransactionRepository.getInstance(this);
        categoryRepository = CategoryRepository.getInstance(this);
        settings = new AppSettings(this);

        tvMonth = findViewById(R.id.tvMonth);
        tvSpentLabel = findViewById(R.id.tvSpentLabel);
        tvTotal = findViewById(R.id.tvTotal);
        tvChange = findViewById(R.id.tvChange);
        tvIncome = findViewById(R.id.tvIncome);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnCurrency = findViewById(R.id.btnCurrency);
        btnCurrency.setOnClickListener(v -> CurrencySwitch.show(this, settings, currenciesInUse,
                shownCurrency, this::load));
        tvBudgetsHeader = findViewById(R.id.tvBudgetsHeader);
        llInsights = findViewById(R.id.llInsights);
        budgetAdapter = new BudgetAdapter();
        RecyclerView rvBudgets = findViewById(R.id.rvBudgets);
        rvBudgets.setLayoutManager(new LinearLayoutManager(this));
        rvBudgets.setAdapter(budgetAdapter);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        defaultChangeColor = tvChange.getTextColors();

        tvFilter = findViewById(R.id.tvFilter);
        tvFilter.setOnClickListener(v -> setCategoryFilter(null));
        tvTransactionsHeader = findViewById(R.id.tvTransactionsHeader);

        comparisonAdapter = new CategoryComparisonAdapter();
        comparisonAdapter.setOnCategoryClick(c -> {
            setCategoryFilter(c.getKey());
            findViewById(R.id.rvTransactions).requestFocus();
        });
        RecyclerView rvComparison = findViewById(R.id.rvComparison);
        rvComparison.setLayoutManager(new LinearLayoutManager(this));
        rvComparison.setAdapter(comparisonAdapter);

        transactionAdapter = new TransactionAdapter(t -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
            startActivity(intent);
        });
        RecyclerView rvTransactions = findViewById(R.id.rvTransactions);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(transactionAdapter);

        btnPrev.setOnClickListener(v -> moveMonth(-1));
        btnNext.setOnClickListener(v -> moveMonth(1));
    }

    @Override
    protected void onPause() {
        super.onPause();
        TransactionRepository.setOnRatesUpdated(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TransactionRepository.setOnRatesUpdated(this::load);
        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void moveMonth(int delta) {
        int[] ym = Dates.shiftMonth(year, month, delta);
        year = ym[0];
        month = ym[1];
        load();
    }

    private void load() {
        final int y = year;
        final int m = month;
        final int[] prev = Dates.shiftMonth(y, m, -1);
        final boolean isCurrentMonth = y == currentYear && m == currentMonth;
        final int daysElapsed = isCurrentMonth
                ? Calendar.getInstance().get(Calendar.DAY_OF_MONTH) : 0;
        final long overallBudget = settings.getOverallBudgetCents();
        final Currency overallCurrency = settings.getOverallBudgetCurrency();
        final int warnPercent = settings.getWarnPercent();

        Background.run(() -> {
            Currency currency = CurrencySwitch.resolve(settings, repository);
            List<Currency> inUse = repository.getCurrenciesInUse();
            long start = Dates.startOfMonth(prev[0], prev[1]);
            long end = Dates.startOfMonth(y, m + 1);
            List<Transaction> both = repository.getBetween(start, end);
            Long earliest = repository.getEarliestDate();

            // Totals, comparisons, budgets and insights use one currency; the list shows all.
            List<Transaction> sameCurrency = SpendingAnalyzer.inCurrency(both, currency);
            MonthSummary current = analyzer.summarise(sameCurrency, y, m);
            MonthSummary previous = analyzer.summarise(sameCurrency, prev[0], prev[1]);
            List<Transaction> inMonth = analyzer.transactionsInMonth(both, y, m);
            List<CategoryComparison> comparison = analyzer.compare(current, previous);
            // The budgets that applied in this month (budgets can change over time).
            com.localspend.logic.BudgetHistory budgetHistory = com.localspend.data.BudgetRepository
                    .getInstance(getApplicationContext()).load();
            List<BudgetStatus> budgets = new BudgetTracker().statuses(current,
                    categoryRepository.getCatalog(), budgetHistory, y, m, currency);
            long overallForInsights = budgetHistory.centsFor(com.localspend.logic.BudgetHistory.OVERALL,
                    com.localspend.logic.BudgetHistory.monthIndex(y, m));
            List<Insight> insights = new InsightGenerator().generate(
                    analyzer.transactionsInMonth(sameCurrency, y, m), current, previous, budgets,
                    daysElapsed, Dates.daysInMonth(y, m),
                    overallForInsights, currency);

            Background.main(() -> {
                if (isFinishing() || isDestroyed() || y != year || m != month) {
                    return;   // screen closed, or user already moved to another month
                }
                earliestMillis = earliest;
                shownCurrency = currency;
                currenciesInUse = inUse;
                render(current, previous, prev, comparison, inMonth);
                InsightViews.render(llInsights, insights);
                budgetAdapter.setItems(budgets, warnPercent);
                tvBudgetsHeader.setVisibility(budgets.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void render(MonthSummary current, MonthSummary previous, int[] prev,
                        List<CategoryComparison> comparison, List<Transaction> inMonth) {
        String monthLabel = Dates.monthLabel(year, month);
        String previousLabel = Dates.monthLabel(prev[0], prev[1]);

        tvMonth.setText(monthLabel);
        Currency currency = shownCurrency;
        tvSpentLabel.setText(getString(R.string.spent_in_month, monthLabel));
        btnCurrency.setText(currency.getPickerLabel());
        btnCurrency.setVisibility(currenciesInUse.size() > 1 || settings.getDisplayCurrency() != null
                ? View.VISIBLE : View.GONE);
        tvTotal.setText(MoneyFormatter.format(current.getTotalSpentCents(), currency));
        tvIncome.setText(getString(R.string.income_line,
                MoneyFormatter.format(current.getTotalIncomeCents(), currency)));
        comparisonAdapter.setCurrency(currency);

        long now = current.getTotalSpentCents();
        long before = previous.getTotalSpentCents();
        tvChange.setText(analyzer.describeChange(now, before, previousLabel, currency));
        if (before > 0 && now != before) {
            tvChange.setTextColor(ContextCompat.getColor(this,
                    now > before ? R.color.expense : R.color.income));
        } else {
            tvChange.setTextColor(defaultChangeColor);
        }

        comparisonAdapter.setItems(comparison);
        monthTransactions = inMonth;
        showTransactions();

        boolean canGoNext = year < currentYear || (year == currentYear && month < currentMonth);
        boolean canGoPrev = earliestMillis != null
                && earliestMillis < Dates.startOfMonth(year, month);
        setEnabled(btnNext, canGoNext);
        setEnabled(btnPrev, canGoPrev);
    }

    private void setCategoryFilter(String key) {
        categoryFilter = key;
        showTransactions();
    }

    /** Lists the month's transactions, narrowed to one category when a filter is set. */
    private void showTransactions() {
        List<Transaction> shown = new java.util.ArrayList<>();
        com.localspend.model.Category filterCategory = categoryFilter == null ? null
                : categoryRepository.getCatalog().find(categoryFilter);
        if (filterCategory == null) {
            categoryFilter = null;     // category was deleted since the screen opened
        }
        for (Transaction t : monthTransactions) {
            if (categoryFilter == null || categoryFilter.equals(t.getCategory().getKey())) {
                shown.add(t);
            }
        }
        if (filterCategory == null) {
            tvFilter.setVisibility(View.GONE);
            tvTransactionsHeader.setText(R.string.transactions);
            tvEmpty.setText(R.string.no_transactions_month);
        } else {
            tvFilter.setVisibility(View.VISIBLE);
            tvFilter.setText(getString(R.string.filter_active, filterCategory.getLabel()));
            tvTransactionsHeader.setText(getString(R.string.transactions_in_category,
                    filterCategory.getLabel()));
            tvEmpty.setText(getString(R.string.no_category_transactions, filterCategory.getLabel()));
        }
        transactionAdapter.setMainCurrency(shownCurrency);
        transactionAdapter.setItems(shown);
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static void setEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.3f);
    }
}
