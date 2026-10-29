package com.localspend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.localspend.R;
import com.localspend.data.TransactionRepository;
import com.localspend.model.Transaction;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every SMS a transaction was captured from, with the complete original text next to what was
 * extracted from it (amount, merchant, category). Searchable; tap one to correct the transaction.
 */
public class MessagesActivity extends AppCompatActivity {

    private TransactionRepository repository;
    private final Adapter adapter = new Adapter();
    private final List<Transaction> all = new ArrayList<>();
    private TextView tvEmpty;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        setTitle(R.string.sms_messages);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = TransactionRepository.getInstance(this);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);

        RecyclerView rv = findViewById(R.id.rvMessages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_messages, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_messages) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clear_messages_title)
                    .setMessage(R.string.clear_messages_body)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> Background.run(() -> {
                        int cleared = repository.clearStoredMessages();
                        Background.main(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Snackbar.make(findViewById(R.id.root), getResources().getQuantityString(
                                    R.plurals.messages_cleared, cleared, cleared),
                                    Snackbar.LENGTH_LONG).show();
                            load();
                        });
                    }))
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void load() {
        Background.run(() -> {
            List<Transaction> loaded = repository.getSmsTransactions();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                all.clear();
                all.addAll(loaded);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        String q = String.valueOf(etSearch.getText()).trim().toLowerCase(Locale.ROOT);
        List<Transaction> shown = new ArrayList<>();
        for (Transaction t : all) {
            if (q.isEmpty() || contains(t.getSmsBody(), q) || contains(t.getSmsSender(), q)
                    || contains(t.getMerchant(), q) || contains(t.getCategory().getLabel(), q)) {
                shown.add(t);
            }
        }
        adapter.set(shown);
        tvEmpty.setText(all.isEmpty() ? R.string.no_messages : R.string.no_messages_match);
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<Transaction> items = new ArrayList<>();

        void set(List<Transaction> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Transaction t = items.get(position);
            String sender = t.getSmsSender() == null || t.getSmsSender().isEmpty()
                    ? getString(R.string.unknown_sender) : t.getSmsSender();
            long received = t.getSmsReceivedMillis() > 0 ? t.getSmsReceivedMillis() : t.getDateMillis();
            h.header.setText(getString(R.string.message_header, sender, Dates.formatDateTime(received)));
            h.body.setText(t.hasOriginalSms() ? t.getSmsBody() : getString(R.string.sms_not_stored_body));
            h.body.setAlpha(t.hasOriginalSms() ? 1f : 0.6f);

            ColorDot.apply(h.dot, t.getCategory().getColor());
            String amount = MoneyFormatter.format(t.getAmountCents(), t.getCurrency());
            h.extracted.setText(getString(R.string.message_extracted,
                    (t.isExpense() ? "-" : "+") + amount, t.getMerchant(),
                    t.isExpense() ? t.getCategory().getLabel() : getString(R.string.type_income),
                    Dates.formatDay(t.getDateMillis())));
            h.extracted.setTextColor(ContextCompat.getColor(MessagesActivity.this,
                    t.isExpense() ? R.color.expense : R.color.income));

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MessagesActivity.this, AddTransactionActivity.class);
                intent.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView body;
            final View dot;
            final TextView extracted;

            Holder(@NonNull View itemView) {
                super(itemView);
                header = itemView.findViewById(R.id.tvHeader);
                body = itemView.findViewById(R.id.tvBody);
                dot = itemView.findViewById(R.id.dot);
                extracted = itemView.findViewById(R.id.tvExtracted);
            }
        }
    }
}
