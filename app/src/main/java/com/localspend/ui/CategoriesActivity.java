package com.localspend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.util.Background;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The user's categories. Tap one to rename it, recolour it, set a monthly budget, or add keywords;
 * use + to add a new category. The menu can re-run categorisation over past transactions and
 * bring back deleted built-in categories.
 */
public class CategoriesActivity extends AppCompatActivity {

    private CategoryRepository categories;
    private TransactionRepository transactions;
    private final Adapter adapter = new Adapter();
    private CategoryCatalog catalog = CategoryCatalog.defaults();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);
        setTitle(R.string.categories);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        categories = CategoryRepository.getInstance(this);
        transactions = TransactionRepository.getInstance(this);

        RecyclerView rv = findViewById(R.id.rvCategories);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> CategoryDialogs.askNewCategory(this, catalog,
                created -> Background.run(() -> {
                    categories.saveCategory(created);
                    Background.main(() -> openEditor(created));
                }), () -> { }));
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
        getMenuInflater().inflate(R.menu.menu_categories, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_recategorise) {
            confirmRecategorise();
            return true;
        } else if (id == R.id.action_restore_defaults) {
            Background.run(() -> {
                categories.restoreDefaultCategories();
                Background.main(() -> {
                    showMessage(getString(R.string.defaults_restored));
                    load();
                });
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmRecategorise() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.recategorise_title)
                .setMessage(R.string.recategorise_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.recategorise_go, (d, w) -> recategorise())
                .show();
    }

    private void recategorise() {
        final boolean builtIn = new AppSettings(this).isBuiltInKeywordsOn();
        Background.run(() -> {
            int changed = transactions.recategoriseAutomatic(categories.buildEngine(builtIn));
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showMessage(getResources().getQuantityString(
                            R.plurals.recategorised, changed, changed));
                }
            });
        });
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG).show();
    }

    private void openEditor(Category category) {
        Intent intent = new Intent(this, CategoryEditActivity.class);
        intent.putExtra(CategoryEditActivity.EXTRA_CATEGORY_KEY, category.getKey());
        startActivity(intent);
    }

    private void load() {
        Background.run(() -> {
            CategoryCatalog loaded = categories.getCatalog();
            Map<Category, int[]> counts = new HashMap<>();   // {keywords, merchants}
            for (Category c : loaded.all()) {
                counts.put(c, new int[2]);
            }
            for (Category c : categories.getUserKeywords().values()) {
                int[] n = counts.get(c);
                if (n != null) {
                    n[0]++;
                }
            }
            for (Category c : categories.getLearnedRules().values()) {
                int[] n = counts.get(c);
                if (n != null) {
                    n[1]++;
                }
            }
            final com.localspend.model.Currency dashboard = CurrencySwitch.resolve(
                    new com.localspend.data.AppSettings(this),
                    com.localspend.data.TransactionRepository.getInstance(this));
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                catalog = loaded;
                adapter.budgetCurrency = dashboard;
                adapter.set(loaded.all(), counts);
            });
        });
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<Category> items = new ArrayList<>();
        private Map<Category, int[]> counts = new HashMap<>();
        private com.localspend.model.Currency budgetCurrency = com.localspend.model.Currency.defaultCurrency();

        void set(List<Category> newItems, Map<Category, int[]> newCounts) {
            items.clear();
            items.addAll(newItems);
            counts = newCounts;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Category c = items.get(position);
            ColorDot.apply(h.dot, c.getColor());
            h.name.setText(c.getLabel());

            List<String> parts = new ArrayList<>();
            if (c.hasBudget()) {
                parts.add(getString(R.string.budget_per_month, MoneyFormatter.formatWhole(c.getBudgetCents(), budgetCurrency)));
            }
            int[] n = counts.get(c);
            if (n != null && n[0] > 0) {
                parts.add(getResources().getQuantityString(R.plurals.keyword_count, n[0], n[0]));
            }
            if (n != null && n[1] > 0) {
                parts.add(getResources().getQuantityString(R.plurals.merchant_count, n[1], n[1]));
            }
            if (parts.isEmpty()) {
                parts.add(getString(c.isBuiltIn() ? R.string.built_in : R.string.custom));
            }
            h.detail.setText(android.text.TextUtils.join(" · ", parts));
            h.itemView.setOnClickListener(v -> openEditor(c));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final View dot;
            final TextView name;
            final TextView detail;

            Holder(@NonNull View itemView) {
                super(itemView);
                dot = itemView.findViewById(R.id.dot);
                name = itemView.findViewById(R.id.tvName);
                detail = itemView.findViewById(R.id.tvDetail);
            }
        }
    }
}
