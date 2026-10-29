package com.localspend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.DayTotals;
import com.localspend.logic.SpendingAnalyzer;
import com.localspend.model.Currency;
import com.localspend.model.Transaction;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Month calendar: every day shows what came in and what was spent, with the month's totals above.
 * Tap a day to see (and edit) its transactions; the + button adds one on that day.
 */
public class CalendarActivity extends AppCompatActivity {

    private TransactionRepository repository;
    private AppSettings settings;

    private int year;
    private int month;
    private int selectedDay;

    private TextView tvMonth;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvNet;
    private Button btnCurrency;
    private TextView tvNotCounted;
    private MonthCalendarView calendar;

    private Currency shownCurrency = Currency.defaultCurrency();
    private List<Currency> currenciesInUse = new ArrayList<>();
    private List<Transaction> monthTransactions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        setTitle(R.string.calendar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = TransactionRepository.getInstance(this);
        settings = new AppSettings(this);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);

        tvMonth = findViewById(R.id.tvMonth);
        tvIncome = findViewById(R.id.tvIncome);
        tvExpense = findViewById(R.id.tvExpense);
        tvNet = findViewById(R.id.tvNet);
        btnCurrency = findViewById(R.id.btnCurrency);
        tvNotCounted = findViewById(R.id.tvNotCounted);
        calendar = findViewById(R.id.calendar);

        ImageButton prev = findViewById(R.id.btnPrev);
        ImageButton next = findViewById(R.id.btnNext);
        prev.setOnClickListener(v -> moveMonth(-1));
        next.setOnClickListener(v -> moveMonth(1));
        btnCurrency.setOnClickListener(v -> CurrencySwitch.show(this, settings, currenciesInUse,
                shownCurrency, this::load));
        calendar.setOnDayClickListener((y, m, day) -> {
            selectedDay = day;
            showDay(day);
        });
        findViewById(R.id.fabAdd).setOnClickListener(v -> addOn(selectedDay));
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
        Calendar now = Calendar.getInstance();
        boolean thisMonth = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH);
        selectedDay = thisMonth ? now.get(Calendar.DAY_OF_MONTH) : 0;
        load();
    }

    private void load() {
        final int y = year;
        final int m = month;
        Background.run(() -> {
            Currency currency = CurrencySwitch.resolve(settings, repository);
            List<Currency> inUse = repository.getCurrenciesInUse();
            List<Transaction> all = repository.getBetween(Dates.startOfMonth(y, m),
                    Dates.startOfMonth(y, m + 1));
            DayTotals totals = DayTotals.of(SpendingAnalyzer.inCurrency(all, currency), y, m);
            int notCounted = 0;
            for (Transaction t : all) {
                if (!t.canBeShownIn(currency)) {
                    notCounted++;
                }
            }
            final int notCountedFinal = notCounted;
            Background.main(() -> {
                if (isFinishing() || isDestroyed() || y != year || m != month) {
                    return;
                }
                shownCurrency = currency;
                currenciesInUse = inUse;
                monthTransactions = all;
                render(totals);
                tvNotCounted.setVisibility(notCountedFinal > 0 ? View.VISIBLE : View.GONE);
                tvNotCounted.setText(getResources().getQuantityString(
                        R.plurals.calendar_not_counted, notCountedFinal, notCountedFinal));
            });
        });
    }

    private void render(DayTotals totals) {
        tvMonth.setText(Dates.monthLabel(year, month));
        tvIncome.setText(plain(totals.totalIncome()));
        tvExpense.setText(plain(totals.totalExpense()));
        long net = totals.totalNet();
        tvNet.setText((net < 0 ? "-" : "") + plain(Math.abs(net)));
        btnCurrency.setText(shownCurrency.getPickerLabel());
        btnCurrency.setVisibility(currenciesInUse.size() > 1 || settings.getDisplayCurrency() != null
                ? View.VISIBLE : View.GONE);
        calendar.setData(year, month, totals);
        calendar.setSelectedDay(selectedDay);
    }

    private static String plain(long cents) {
        return MonthCalendarView.plain(cents);
    }

    /** Bottom sheet with the day's transactions. */
    private void showDay(int day) {
        List<Transaction> onDay = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        for (Transaction t : monthTransactions) {
            c.setTimeInMillis(t.getDateMillis());
            if (c.get(Calendar.DAY_OF_MONTH) == day) {
                onDay.add(t);
            }
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_day, null);
        c.clear();
        c.set(year, month, day);
        ((TextView) view.findViewById(R.id.tvSheetTitle)).setText(Dates.formatDay(c.getTimeInMillis()));

        long spent = 0;
        long received = 0;
        for (Transaction t : onDay) {
            if (!t.canBeShownIn(shownCurrency)) {
                continue;
            }
            if (t.isExpense()) {
                spent += t.getAmountIn(shownCurrency);
            } else {
                received += t.getAmountIn(shownCurrency);
            }
        }
        TextView summary = view.findViewById(R.id.tvSheetSummary);
        summary.setText(onDay.isEmpty() ? "" : getString(R.string.calendar_day_summary,
                MoneyFormatter.format(received, shownCurrency),
                MoneyFormatter.format(spent, shownCurrency)));
        summary.setVisibility(onDay.isEmpty() ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.tvSheetEmpty).setVisibility(onDay.isEmpty() ? View.VISIBLE : View.GONE);

        TransactionAdapter adapter = new TransactionAdapter(t -> {
            sheet.dismiss();
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
            startActivity(intent);
        }, false);
        adapter.setMainCurrency(shownCurrency);
        adapter.setItems(onDay);
        RecyclerView list = view.findViewById(R.id.rvSheet);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setVisibility(onDay.isEmpty() ? View.GONE : View.VISIBLE);

        view.findViewById(R.id.btnSheetAdd).setOnClickListener(v -> {
            sheet.dismiss();
            addOn(day);
        });
        sheet.setContentView(view);
        sheet.show();
    }

    /** Opens the add screen dated on the given day of the shown month (today when none is picked). */
    private void addOn(int day) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        if (day > 0) {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(year, month, day, 12, 0, 0);
            intent.putExtra(AddTransactionActivity.EXTRA_DATE, c.getTimeInMillis());
        }
        startActivity(intent);
    }
}
