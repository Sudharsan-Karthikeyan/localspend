package com.localspend.ui;

import android.Manifest;
import android.content.Context;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.DatabaseHelper;
import com.localspend.data.SmsReviewRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.BudgetStatus;
import com.localspend.logic.BudgetTracker;
import com.localspend.logic.Insight;
import com.localspend.logic.InsightGenerator;
import com.localspend.logic.MonthSummary;
import com.localspend.logic.MonthTotal;
import com.localspend.logic.SpendingAnalyzer;
import com.localspend.logic.TrendWindow;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.notify.BudgetAlerts;
import com.localspend.notify.Notifier;
import com.localspend.sms.SmsImporter;
import com.localspend.data.RecurringProcessor;
import com.localspend.recurring.RecurringScheduler;
import com.localspend.widget.QuickAddWidget;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.Calendar;
import java.util.List;

/**
 * Dashboard: this month's spending, budget progress, spending insights, the six-month trend,
 * the category breakdown and the latest transactions.
 */
public class MainActivity extends AppCompatActivity {

    /** Default for the automatic first import: as far back as the six-month chart goes. */
    private static final long IMPORT_WINDOW_MILLIS = 183L * 24 * 60 * 60 * 1000;
    private static final int RECENT_COUNT = 5;

    private final SpendingAnalyzer analyzer = new SpendingAnalyzer();
    private final BudgetTracker budgetTracker = new BudgetTracker();
    private final InsightGenerator insightGenerator = new InsightGenerator();
    private TransactionRepository repository;
    private CategoryRepository categoryRepository;
    private AppSettings settings;

    private TextView tvMonthLabel;
    private TextView tvRecentHeader;
    private Button btnBackToCurrent;
    private Button btnViewAllRef;
    private androidx.core.widget.NestedScrollView scroll;
    /** Month picked on the chart, or null to show the current month. */
    private int[] viewedMonth;
    private OnBackPressedCallback backToCurrentCallback;
    private Button btnCurrency;
    private Currency shownCurrency = Currency.defaultCurrency();
    private List<Currency> currenciesInUse = new java.util.ArrayList<>();
    private TextView tvTotalSpent;
    private TextView tvVsLast;
    private TextView tvIncome;
    private TextView tvNoData;
    private TextView tvCategoryNote;
    private Button btnCategoryMonth;
    private View smsBanner;
    private View reviewBanner;
    private TextView tvReviewBanner;
    private TextView tvNoBudgets;
    private LinearLayout llInsights;
    private MonthlyBarChartView chart;
    private TextView tvChartTitle;
    private ColorStateList defaultVsLastColor;

    private CategorySummaryAdapter categoryAdapter;
    /** Month the category list is showing (it can fall back to the latest month with spending). */
    private int categoryYear;
    private int categoryMonth;
    private TransactionAdapter recentAdapter;
    private BudgetAdapter budgetAdapter;
    private ActivityResultLauncher<String[]> smsPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = TransactionRepository.getInstance(this);
        categoryRepository = CategoryRepository.getInstance(this);
        settings = new AppSettings(this);
        Notifier.createChannels(this);

        tvMonthLabel = findViewById(R.id.tvMonthLabel);
        tvRecentHeader = findViewById(R.id.tvRecentHeader);
        btnBackToCurrent = findViewById(R.id.btnBackToCurrent);
        btnBackToCurrent.setOnClickListener(v -> showCurrentMonth());
        scroll = findViewById(R.id.scroll);
        // While a past month is shown, Back returns to the current month instead of leaving.
        backToCurrentCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showCurrentMonth();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToCurrentCallback);
        btnCurrency = findViewById(R.id.btnCurrency);
        btnCurrency.setOnClickListener(v -> CurrencySwitch.show(this, settings, currenciesInUse,
                shownCurrency, this::loadDashboard));
        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvVsLast = findViewById(R.id.tvVsLast);
        tvIncome = findViewById(R.id.tvIncome);
        tvNoData = findViewById(R.id.tvNoData);
        tvCategoryNote = findViewById(R.id.tvCategoryNote);
        btnCategoryMonth = findViewById(R.id.btnCategoryMonth);
        smsBanner = findViewById(R.id.smsBanner);
        reviewBanner = findViewById(R.id.reviewBanner);
        tvReviewBanner = findViewById(R.id.tvReviewBanner);
        reviewBanner.setOnClickListener(v -> openReview());
        findViewById(R.id.btnReview).setOnClickListener(v -> openReview());
        tvNoBudgets = findViewById(R.id.tvNoBudgets);
        llInsights = findViewById(R.id.llInsights);
        chart = findViewById(R.id.chart);
        tvChartTitle = findViewById(R.id.tvChartTitle);
        setUpRangePicker();
        chart.setOnMonthClickListener(this::showMonth);
        defaultVsLastColor = tvVsLast.getTextColors();

        categoryAdapter = new CategorySummaryAdapter();
        RecyclerView rvCategories = findViewById(R.id.rvCategories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(categoryAdapter);
        // Tapping a category opens that month's transactions for just that category.
        categoryAdapter.setOnCategoryClick(c -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra(HistoryActivity.EXTRA_YEAR, categoryYear);
            intent.putExtra(HistoryActivity.EXTRA_MONTH, categoryMonth);
            intent.putExtra(HistoryActivity.EXTRA_CATEGORY, c.getKey());
            startActivity(intent);
        });

        budgetAdapter = new BudgetAdapter();
        RecyclerView rvBudgets = findViewById(R.id.rvBudgets);
        rvBudgets.setLayoutManager(new LinearLayoutManager(this));
        rvBudgets.setAdapter(budgetAdapter);
        findViewById(R.id.btnManageBudgets).setOnClickListener(
                v -> {
                    Intent intent = new Intent(this, BudgetMonthActivity.class);
                    Calendar now = Calendar.getInstance();
                    intent.putExtra(BudgetMonthActivity.EXTRA_YEAR,
                            viewedMonth == null ? now.get(Calendar.YEAR) : viewedMonth[0]);
                    intent.putExtra(BudgetMonthActivity.EXTRA_MONTH,
                            viewedMonth == null ? now.get(Calendar.MONTH) : viewedMonth[1]);
                    startActivity(intent);
                });

        recentAdapter = new TransactionAdapter(this::openTransaction);
        RecyclerView rvRecent = findViewById(R.id.rvRecent);
        rvRecent.setLayoutManager(new LinearLayoutManager(this));
        rvRecent.setAdapter(recentAdapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));

        Button viewAll = findViewById(R.id.btnViewAll);
        btnViewAllRef = viewAll;
        // Opens the full history at the month being shown.
        viewAll.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            if (viewedMonth != null) {
                intent.putExtra(HistoryActivity.EXTRA_YEAR, viewedMonth[0]);
                intent.putExtra(HistoryActivity.EXTRA_MONTH, viewedMonth[1]);
            }
            startActivity(intent);
        });

        findViewById(R.id.btnEnableSms).setOnClickListener(v -> requestSmsAccess());
        findViewById(R.id.btnCalendar).setOnClickListener(
                v -> startActivity(new Intent(this, CalendarActivity.class)));
        findViewById(R.id.btnRecurring).setOnClickListener(
                v -> startActivity(new Intent(this, RecurringActivity.class)));
        RecurringScheduler.schedule(this);   // daily wake-up that adds due repeating payments

        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    updateSmsBanner();
                    if (hasSmsPermission()) {
                        importFromSms();
                        askNotificationPermissionOnce();
                    } else {
                        showMessage(getString(R.string.import_denied));
                    }
                });
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        showMessage(getString(R.string.notifications_denied));
                    }
                });
        if (hasSmsPermission()) {
            askNotificationPermissionOnce();
        }
    }

    /**
     * Android 13+ needs permission to show notifications (new transactions, budget alerts).
     * Asked once; the user can change it later in Settings.
     */
    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || settings.hasAskedNotificationPermission()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        settings.setAskedNotificationPermission();
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** How often the inbox is checked for new bank messages while the app is open. */
    private static final long INBOX_CHECK_MILLIS = 30_000L;
    private long lastInboxCheck = 0;
    private final Runnable inboxPoll = new Runnable() {
        @Override
        public void run() {
            checkInbox();
            Background.mainDelayed(this, INBOX_CHECK_MILLIS);
        }
    };

    /** Reads messages that arrived since the last check (the receiver covers a closed app). */
    private void checkInbox() {
        if (!hasSmsPermission()) {
            return;
        }
        final long since = lastInboxCheck == 0
                ? System.currentTimeMillis() - 2 * 60 * 1000L
                : lastInboxCheck - 60 * 1000L;       // small overlap; duplicates are ignored
        lastInboxCheck = System.currentTimeMillis();
        final android.content.Context appContext = getApplicationContext();
        Background.run(() -> {
            SmsImporter.Result result = new SmsImporter(appContext, repository).importFromInbox(since);
            if (result.added > 0) {
                BudgetAlerts.check(appContext);
                Background.main(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        loadDashboard();
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Background.cancelMain(inboxPoll);
        TransactionRepository.setOnRatesUpdated(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TransactionRepository.setOnRatesUpdated(this::loadDashboard);
        updateSmsBanner();
        // Add any repeating payments that have fallen due first; the worker thread runs these in
        // order, so the dashboard below already includes them.
        final Context app = getApplicationContext();
        Background.run(() -> RecurringProcessor.processDue(app, false));
        loadDashboard();
        Background.cancelMain(inboxPoll);
        Background.mainDelayed(inboxPoll, 1_000L);   // then every 30 seconds
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_widget) {
            addWidget();
            return true;
        } else if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        } else if (id == R.id.action_calendar) {
            startActivity(new Intent(this, CalendarActivity.class));
            return true;
        } else if (id == R.id.action_recurring) {
            startActivity(new Intent(this, RecurringActivity.class));
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_categories) {
            startActivity(new Intent(this, CategoriesActivity.class));
            return true;
        } else if (id == R.id.action_review) {
            openReview();
            return true;
        } else if (id == R.id.action_messages) {
            startActivity(new Intent(this, MessagesActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_import_sms) {
            if (hasSmsPermission()) {
                askImportRange();
            } else {
                requestSmsAccess();
            }
            return true;
        } else if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_body)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Asks the launcher to pin the quick-add widget; older launchers get the manual steps. */
    private void addWidget() {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        ComponentName widget = new ComponentName(this, QuickAddWidget.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null
                && manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(widget, null, null);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.menu_add_widget)
                    .setMessage(R.string.widget_how_to)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    private void openReview() {
        startActivity(new Intent(this, ReviewActivity.class));
    }

    private void openTransaction(Transaction t) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
        startActivity(intent);
    }

    // ---- SMS access -------------------------------------------------------------------------

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateSmsBanner() {
        smsBanner.setVisibility(hasSmsPermission() ? View.GONE : View.VISIBLE);
    }

    private void requestSmsAccess() {
        smsPermissionLauncher.launch(new String[]{
                Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS});
    }

    /** Lets the user choose how far back to read the inbox, then imports. */
    private void askImportRange() {
        final long day = 24L * 60 * 60 * 1000;
        final long[] windows = {91 * day, IMPORT_WINDOW_MILLIS, 366 * day, Long.MAX_VALUE};
        String[] labels = {getString(R.string.import_range_3), getString(R.string.import_range_6),
                getString(R.string.import_range_12), getString(R.string.import_range_all)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_range_title)
                .setItems(labels, (d, which) -> importFromSms(windows[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void importFromSms() {
        importFromSms(IMPORT_WINDOW_MILLIS);
    }

    /** Reads inbox messages from the last {@code windowMillis} (Long.MAX_VALUE for all of them). */
    private void importFromSms(long windowMillis) {
        showMessage(getString(R.string.import_started));
        final android.content.Context appContext = getApplicationContext();
        Background.run(() -> {
            SmsImporter importer = new SmsImporter(appContext, repository);
            long since = windowMillis == Long.MAX_VALUE ? 0
                    : System.currentTimeMillis() - windowMillis;
            SmsImporter.Result result = importer.importFromInbox(since);
            if (result.added > 0) {
                BudgetAlerts.check(appContext);
            }
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result.permissionDenied) {
                    showMessage(getString(R.string.import_denied));
                } else {
                    showMessage(result.queued > 0
                            ? getString(R.string.import_done_review, result.scanned, result.added,
                                    result.queued)
                            : getString(R.string.import_done, result.scanned, result.added));
                }
                loadDashboard();
            });
        });
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG).show();
    }

    // ---- Dashboard --------------------------------------------------------------------------

    /** 3 months / 6 months / 12 months / tax year above the chart. */
    private void setUpRangePicker() {
        com.google.android.material.button.MaterialButtonToggleGroup group = findViewById(R.id.toggleRange);
        TrendWindow.Range range = settings.getChartRange();
        group.check(range == TrendWindow.Range.THREE_MONTHS ? R.id.btnRange3
                : range == TrendWindow.Range.TWELVE_MONTHS ? R.id.btnRange12
                : range == TrendWindow.Range.TAX_YEAR ? R.id.btnRangeTax : R.id.btnRange6);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            settings.setChartRange(checkedId == R.id.btnRange3 ? TrendWindow.Range.THREE_MONTHS
                    : checkedId == R.id.btnRange12 ? TrendWindow.Range.TWELVE_MONTHS
                    : checkedId == R.id.btnRangeTax ? TrendWindow.Range.TAX_YEAR
                    : TrendWindow.Range.SIX_MONTHS);
            loadDashboard();
        });
    }

    /** Shows a month picked on the chart as the main page. */
    private void showMonth(int year, int month) {
        Calendar now = Calendar.getInstance();
        if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)) {
            showCurrentMonth();
            return;
        }
        viewedMonth = new int[]{year, month};
        backToCurrentCallback.setEnabled(true);
        scroll.smoothScrollTo(0, 0);
        loadDashboard();
    }

    private void showCurrentMonth() {
        viewedMonth = null;
        backToCurrentCallback.setEnabled(false);
        scroll.smoothScrollTo(0, 0);
        loadDashboard();
    }

    private void loadDashboard() {
        Calendar now = Calendar.getInstance();
        final int nowYear = now.get(Calendar.YEAR);
        final int nowMonth = now.get(Calendar.MONTH);
        final boolean isCurrent = viewedMonth == null;
        // The month on the main page: the current one, or the one picked on the chart.
        final int year = isCurrent ? nowYear : viewedMonth[0];
        final int month = isCurrent ? nowMonth : viewedMonth[1];
        final int[] prev = Dates.shiftMonth(year, month, -1);
        // Days so far, for pace and projection; a finished month has none.
        final int today = isCurrent ? now.get(Calendar.DAY_OF_MONTH) : 0;
        final long overallBudget = settings.getOverallBudgetCents();
        final Currency overallCurrency = settings.getOverallBudgetCurrency();
        final int warnPercent = settings.getWarnPercent();
        final boolean useBuiltIn = settings.isBuiltInKeywordsOn();
        final android.content.Context app = getApplicationContext();

        Background.run(() -> {
            // After an upgrade that re-read stored messages, apply the user's rules once.
            if (DatabaseHelper.getInstance(app).consumeRecategoriseFlag()) {
                repository.recategoriseAutomatic(categoryRepository.buildEngine(useBuiltIn));
            }
            Currency currency = CurrencySwitch.resolve(settings, repository);
            List<Currency> inUse = repository.getCurrenciesInUse();

            // The chart shows the range chosen above it, so any of its months can be picked.
            // Data also covers the month before the viewed one, for the comparison.
            final TrendWindow window = TrendWindow.of(settings.getChartRange(), nowYear, nowMonth,
                    currency);
            long start = Math.min(Dates.startOfMonth(window.getStartYear(), window.getStartMonth()),
                    Dates.startOfMonth(prev[0], prev[1]));
            long end = Dates.startOfMonth(nowYear, nowMonth + 1);
            // Only one currency is added up at a time; the transaction list shows every currency.
            List<Transaction> all = SpendingAnalyzer.inCurrency(repository.getBetween(start, end), currency);
            List<Transaction> recent;
            if (isCurrent) {
                recent = repository.getRecent(RECENT_COUNT);
            } else {
                List<Transaction> monthList = repository.getBetween(
                        Dates.startOfMonth(year, month), Dates.startOfMonth(year, month + 1));
                recent = monthList.size() > RECENT_COUNT ? monthList.subList(0, RECENT_COUNT) : monthList;
            }
            CategoryCatalog catalog = categoryRepository.getCatalog();
            int waiting = SmsReviewRepository.getInstance(app).countPending();

            MonthSummary current = analyzer.summarise(all, year, month);
            MonthSummary previous = analyzer.summarise(all, prev[0], prev[1]);
            List<MonthTotal> trend = analyzer.monthsFrom(all, window.getStartYear(),
                    window.getStartMonth(), window.getCount());
            final String chartTitle = window.getLabel();
            // The budgets that applied in the month on screen (budgets can change over time).
            com.localspend.logic.BudgetHistory budgetHistory =
                    com.localspend.data.BudgetRepository.getInstance(app).load();
            List<BudgetStatus> budgets = budgetTracker.statuses(current, catalog, budgetHistory,
                    year, month, currency);
            long overallForInsights = budgetHistory.centsFor(com.localspend.logic.BudgetHistory.OVERALL,
                    com.localspend.logic.BudgetHistory.monthIndex(year, month));
            // Nothing spent this month yet: show the latest month that has spending instead,
            // so the category breakdown is never just empty.
            int[] shownMonth = {year, month};
            MonthSummary categorySummary = current;
            if (isCurrent && current.getCategoryTotals().isEmpty()) {
                Long latest = repository.getLatestExpenseDate(currency);
                if (latest != null && latest < Dates.startOfMonth(year, month)) {
                    shownMonth[0] = Dates.yearOf(latest);
                    shownMonth[1] = Dates.monthOf(latest);
                    List<Transaction> thatMonth = SpendingAnalyzer.inCurrency(repository.getBetween(
                            Dates.startOfMonth(shownMonth[0], shownMonth[1]),
                            Dates.startOfMonth(shownMonth[0], shownMonth[1] + 1)), currency);
                    categorySummary = analyzer.summarise(thatMonth, shownMonth[0], shownMonth[1]);
                }
            }
            final MonthSummary breakdown = categorySummary;
            List<Insight> insights = insightGenerator.generate(
                    analyzer.transactionsInMonth(all, year, month), current, previous, budgets,
                    today, Dates.daysInMonth(year, month), overallForInsights, currency);

            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                shownCurrency = currency;
                currenciesInUse = inUse;
                tvChartTitle.setText(chartTitle);
                render(year, month, current, previous, trend, recent);
                renderViewedMonth(isCurrent, year, month, prev, nowYear, nowMonth);
                renderCategories(year, month, breakdown, shownMonth[0], shownMonth[1]);
                budgetAdapter.setItems(budgets, warnPercent);
                tvNoBudgets.setVisibility(budgets.isEmpty() ? View.VISIBLE : View.GONE);
                InsightViews.render(llInsights, insights);
                reviewBanner.setVisibility(waiting > 0 ? View.VISIBLE : View.GONE);
                tvReviewBanner.setText(getResources().getQuantityString(
                        R.plurals.review_waiting, waiting, waiting));
            });
        });
    }

    /**
     * The category breakdown: this month's, or (when nothing has been spent yet this month) the
     * latest month with spending, clearly labelled, with a link to that month's details.
     */
    private void renderCategories(int year, int month, MonthSummary breakdown,
                                  int shownYear, int shownMonth) {
        categoryAdapter.setCurrency(shownCurrency);
        categoryYear = shownYear;
        categoryMonth = shownMonth;
        categoryAdapter.setItems(breakdown.getCategoryTotals());
        boolean isThisMonth = shownYear == year && shownMonth == month;
        boolean empty = breakdown.getCategoryTotals().isEmpty();
        if (empty) {
            tvCategoryNote.setText(getString(R.string.no_category_spending,
                    Dates.monthLabel(year, month)));
        } else if (!isThisMonth) {
            tvCategoryNote.setText(getString(R.string.showing_category_month,
                    Dates.monthLabel(year, month), Dates.monthLabel(shownYear, shownMonth)));
        }
        tvCategoryNote.setVisibility(empty || !isThisMonth ? View.VISIBLE : View.GONE);
        btnCategoryMonth.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnCategoryMonth.setText(getString(R.string.see_month_details, Dates.monthLabel(shownYear, shownMonth)));
        btnCategoryMonth.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra(HistoryActivity.EXTRA_YEAR, shownYear);
            intent.putExtra(HistoryActivity.EXTRA_MONTH, shownMonth);
            startActivity(intent);
        });
    }

    /** Labels and controls that differ between the current month and a month picked on the chart. */
    private void renderViewedMonth(boolean isCurrent, int year, int month, int[] prev,
                                   int nowYear, int nowMonth) {
        chart.setSelectedMonth(year, month);
        btnBackToCurrent.setVisibility(isCurrent ? View.GONE : View.VISIBLE);
        btnBackToCurrent.setText(getString(R.string.back_to_month, Dates.monthLabel(nowYear, nowMonth)));
        tvRecentHeader.setText(isCurrent ? getString(R.string.recent_transactions)
                : getString(R.string.transactions_in_month, Dates.shortMonth(year, month)));
        btnViewAllRef.setText(isCurrent ? R.string.view_all : R.string.view_month);
        if (!isCurrent) {
            tvMonthLabel.setText(getString(R.string.viewing_month, Dates.monthLabel(year, month)));
        }
    }

    private void render(int year, int month, MonthSummary current, MonthSummary previous,
                        List<MonthTotal> trend, List<Transaction> recent) {
        Currency currency = shownCurrency;
        tvMonthLabel.setText(Dates.monthLabel(year, month));
        btnCurrency.setText(currency.getPickerLabel());
        // Offer the switch once there is more than one currency, or a currency was pinned.
        btnCurrency.setVisibility(currenciesInUse.size() > 1 || settings.getDisplayCurrency() != null
                ? View.VISIBLE : View.GONE);
        tvTotalSpent.setText(MoneyFormatter.format(current.getTotalSpentCents(), currency));
        tvIncome.setText(getString(R.string.income_line,
                MoneyFormatter.format(current.getTotalIncomeCents(), currency)));

        long now = current.getTotalSpentCents();
        long before = previous.getTotalSpentCents();
        Calendar today = Calendar.getInstance();
        boolean isCurrent = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH);
        int[] prev = Dates.shiftMonth(year, month, -1);
        tvVsLast.setText(analyzer.describeChange(now, before,
                isCurrent ? "last month" : Dates.monthLabel(prev[0], prev[1]), currency));
        if (before > 0 && now != before) {
            // Spending up is red, spending down is green.
            tvVsLast.setTextColor(ContextCompat.getColor(this,
                    now > before ? R.color.expense : R.color.income));
        } else {
            tvVsLast.setTextColor(defaultVsLastColor);
        }

        chart.setData(trend, currency);
        recentAdapter.setMainCurrency(shownCurrency);
        recentAdapter.setItems(recent);
        tvNoData.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
