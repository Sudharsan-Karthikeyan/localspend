package com.localspend.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.localspend.R;
import com.localspend.data.SmsReviewRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.model.PendingSms;
import com.localspend.notify.BudgetAlerts;
import com.localspend.sms.SenderDirectory;
import com.localspend.sms.SmsImporter;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Messages that looked like transactions but did not come from a recognised bank. For each one
 * the user can add it (once, or always trust that sender) or ignore it (once, or block that
 * sender). The menu switches to messages ignored earlier, which can still be added.
 */
public class ReviewActivity extends AppCompatActivity {

    private SmsReviewRepository review;
    private final Adapter adapter = new Adapter();
    private boolean showingIgnored = false;
    private TextView tvIntro;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        setTitle(R.string.review_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        review = SmsReviewRepository.getInstance(this);
        tvIntro = findViewById(R.id.tvIntro);
        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvReview);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
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
        getMenuInflater().inflate(R.menu.menu_review, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem toggle = menu.findItem(R.id.action_toggle_ignored);
        if (toggle != null) {
            toggle.setTitle(showingIgnored ? R.string.show_waiting : R.string.show_ignored);
        }
        MenuItem addAll = menu.findItem(R.id.action_add_all);
        if (addAll != null) {
            // Only for the waiting list: the ignored list holds adverts, which should stay out.
            addAll.setVisible(!showingIgnored && adapter.getItemCount() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_ignored) {
            showingIgnored = !showingIgnored;
            invalidateOptionsMenu();
            load();
            return true;
        } else if (id == R.id.action_add_all) {
            askAddAll();
            return true;
        } else if (id == R.id.action_senders) {
            SenderDialogs.showSenders(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void load() {
        final PendingSms.Status status = showingIgnored ? PendingSms.Status.IGNORED : PendingSms.Status.PENDING;
        Background.run(() -> {
            List<PendingSms> items = review.getByStatus(status);
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                tvIntro.setText(showingIgnored ? R.string.review_intro_ignored : R.string.review_intro);
                adapter.set(items);
                tvEmpty.setText(showingIgnored ? R.string.review_empty_ignored : R.string.review_empty);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private static String senderLabel(Context ctx, PendingSms p) {
        return p.getSender() == null || p.getSender().trim().isEmpty()
                ? ctx.getString(R.string.unknown_sender) : p.getSender().trim();
    }

    /** Adds every waiting message as a transaction, after a confirmation. */
    private void askAddAll() {
        final List<PendingSms> waiting = adapter.snapshot();
        if (waiting.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_add_all_title)
                .setMessage(R.string.review_add_all_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, (d, w) -> run(importer -> {
                    for (PendingSms p : waiting) {
                        importer.accept(p);
                    }
                    return getString(R.string.review_added_many, waiting.size());
                }, true))
                .show();
    }

    private void askAdd(PendingSms p) {
        String sender = senderLabel(this, p);
        boolean canTrust = !SenderDirectory.normalise(p.getSender()).isEmpty();
        String[] options = canTrust
                ? new String[]{getString(R.string.add_once), getString(R.string.add_and_trust, sender)}
                : new String[]{getString(R.string.add_once)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_title)
                .setItems(options, (d, which) -> run(importer -> {
                    if (which == 1) {
                        int added = importer.trustSender(p.getSender());
                        return getResources().getQuantityString(R.plurals.trusted_added, added, added, sender);
                    }
                    importer.accept(p);
                    return getString(R.string.added_one);
                }, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askIgnore(PendingSms p) {
        String sender = senderLabel(this, p);
        boolean canBlock = !SenderDirectory.normalise(p.getSender()).isEmpty();
        String[] options = canBlock
                ? new String[]{getString(R.string.ignore_once), getString(R.string.ignore_and_block, sender)}
                : new String[]{getString(R.string.ignore_once)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.ignore_title)
                .setItems(options, (d, which) -> run(importer -> {
                    if (which == 1) {
                        int ignored = importer.blockSender(p.getSender());
                        return getResources().getQuantityString(R.plurals.blocked_ignored, ignored, ignored, sender);
                    }
                    importer.ignore(p);
                    return getString(R.string.ignored_one);
                }, false))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface Action {
        String apply(SmsImporter importer);
    }

    /** Runs a review action in the background, then shows the result and reloads. */
    private void run(Action action, boolean checkBudgets) {
        final Context app = getApplicationContext();
        Background.run(() -> {
            SmsImporter importer = new SmsImporter(app, TransactionRepository.getInstance(app));
            String message = action.apply(importer);
            if (checkBudgets) {
                BudgetAlerts.check(app);
            }
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG).show();
                load();
            });
        });
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<PendingSms> items = new ArrayList<>();

        void set(List<PendingSms> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
            invalidateOptionsMenu();   // the "add all" item depends on what is listed
        }

        List<PendingSms> snapshot() {
            return new ArrayList<>(items);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_review, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PendingSms p = items.get(position);
            Context ctx = h.itemView.getContext();
            h.header.setText(getString(R.string.message_header, senderLabel(ctx, p),
                    Dates.formatDateTime(p.getReceivedMillis())));
            h.body.setText(p.getBody());

            if (p.hasAmount()) {
                boolean expense = p.getType() == com.localspend.model.TransactionType.EXPENSE;
                String amount = (expense ? "-" : "+") + MoneyFormatter.format(p.getAmountCents(), p.getCurrency());
                h.extracted.setText(getString(R.string.review_extracted, amount, p.getMerchant(),
                        Dates.formatDay(p.getDateMillis())));
                h.extracted.setTextColor(ContextCompat.getColor(ctx, expense ? R.color.expense : R.color.income));
                h.add.setEnabled(true);
            } else {
                h.extracted.setText(R.string.review_no_amount);
                h.extracted.setTextColor(h.header.getTextColors().getDefaultColor());
                h.add.setEnabled(false);
            }
            h.add.setOnClickListener(v -> askAdd(p));
            h.ignore.setVisibility(showingIgnored ? View.GONE : View.VISIBLE);
            h.ignore.setOnClickListener(v -> askIgnore(p));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView body;
            final TextView extracted;
            final Button add;
            final Button ignore;

            Holder(@NonNull View itemView) {
                super(itemView);
                header = itemView.findViewById(R.id.tvHeader);
                body = itemView.findViewById(R.id.tvBody);
                extracted = itemView.findViewById(R.id.tvExtracted);
                add = itemView.findViewById(R.id.btnAdd);
                ignore = itemView.findViewById(R.id.btnIgnore);
            }
        }
    }
}
