package com.localspend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.data.RecurringProcessor;
import com.localspend.data.RecurringRepository;
import com.localspend.logic.RecurringSchedule;
import com.localspend.model.RecurringPayment;
import com.localspend.util.Background;

import java.util.List;

/**
 * Repeating payments and instalment plans. Tap one to pause, resume or delete it. New ones are
 * made on the add screen with "Repeat / Instalment".
 */
public class RecurringActivity extends AppCompatActivity {

    private RecurringRepository repository;
    private RecurringAdapter adapter;
    private View tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring);
        setTitle(R.string.recurring_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = RecurringRepository.getInstance(this);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new RecurringAdapter(this::showActions);
        RecyclerView list = findViewById(R.id.rvRecurring);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(
                v -> startActivity(new Intent(this, AddTransactionActivity.class)));
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

    private void load() {
        Background.run(() -> {
            RecurringProcessor.processDue(getApplicationContext(), false);   // show up-to-date counts
            List<RecurringPayment> plans = repository.getAll();
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                adapter.setItems(this, plans);
                tvEmpty.setVisibility(plans.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showActions(RecurringPayment plan) {
        boolean canToggle = !plan.isFinished();
        String[] items = canToggle
                ? new String[]{getString(plan.isActive() ? R.string.recurring_pause
                : R.string.recurring_resume), getString(R.string.delete)}
                : new String[]{getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(plan.getMerchant())
                .setItems(items, (d, which) -> {
                    if (canToggle && which == 0) {
                        togglePaused(plan);
                    } else {
                        confirmDelete(plan);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void togglePaused(RecurringPayment plan) {
        Background.run(() -> {
            if (plan.isActive()) {
                plan.setActive(false);
            } else {
                // Skip what was missed while paused instead of adding it all at once.
                plan.setNextDueMillis(RecurringSchedule.nextOnOrAfter(plan, System.currentTimeMillis()));
                plan.setActive(true);
            }
            repository.saveProgress(plan);
            Background.main(this::load);
        });
    }

    private void confirmDelete(RecurringPayment plan) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.recurring_delete_title)
                .setMessage(R.string.recurring_delete_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> Background.run(() -> {
                    repository.delete(plan.getId());
                    Background.main(this::load);
                }))
                .show();
    }
}
