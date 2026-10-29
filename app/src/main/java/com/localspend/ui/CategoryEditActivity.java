package com.localspend.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.data.CategoryRepository;
import com.localspend.data.TransactionRepository;
import com.localspend.logic.CategorisationEngine;
import com.localspend.logic.MerchantNormaliser;
import com.localspend.model.Category;
import com.localspend.model.CategoryCatalog;
import com.localspend.model.Currency;
import com.localspend.util.Background;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Customise one category:
 * - name and colour,
 * - an optional monthly budget,
 * - keywords: any merchant containing one of them is filed here automatically,
 * - merchants the user has filed here before (remove one to forget it),
 * - delete the category (its transactions move to Other).
 *
 * Keyword and merchant changes are saved straight away; name, colour and budget are saved with
 * the Save button. If the rules changed, past automatically-categorised transactions are
 * re-categorised so the dashboard reflects the change at once.
 */
public class CategoryEditActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_KEY = "category_key";

    private CategoryRepository categories;
    private TransactionRepository transactions;
    private AppSettings settings;

    private Category category;
    private CategoryCatalog catalog;
    private int selectedColor;
    private volatile boolean rulesChanged = false;   // set from the background thread

    private TextInputLayout tilName;
    private TextInputEditText etName;
    private TextInputLayout tilBudget;
    private TextInputEditText etBudget;
    private Spinner spBudgetCurrency;
    private Currency dashboardCurrency;   // the currency every budget is in
    private GridLayout gridColors;
    private ChipGroup chipsKeywords;
    private EditText etKeyword;
    private TextView tvNoKeywords;
    private ChipGroup chipsMerchants;
    private TextView tvNoMerchants;
    private View sectionBuiltIn;
    private TextView tvBuiltIn;
    private Button btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_edit);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        categories = CategoryRepository.getInstance(this);
        transactions = TransactionRepository.getInstance(this);
        settings = new AppSettings(this);

        tilName = findViewById(R.id.tilName);
        etName = findViewById(R.id.etName);
        tilBudget = findViewById(R.id.tilBudget);
        etBudget = findViewById(R.id.etBudget);
        spBudgetCurrency = findViewById(R.id.spBudgetCurrency);
        gridColors = findViewById(R.id.gridColors);
        chipsKeywords = findViewById(R.id.chipsKeywords);
        etKeyword = findViewById(R.id.etKeyword);
        tvNoKeywords = findViewById(R.id.tvNoKeywords);
        chipsMerchants = findViewById(R.id.chipsMerchants);
        tvNoMerchants = findViewById(R.id.tvNoMerchants);
        sectionBuiltIn = findViewById(R.id.sectionBuiltIn);
        tvBuiltIn = findViewById(R.id.tvBuiltIn);
        btnDelete = findViewById(R.id.btnDelete);

        findViewById(R.id.btnAddKeyword).setOnClickListener(v -> addKeyword());
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                addKeyword();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        String key = getIntent().getStringExtra(EXTRA_CATEGORY_KEY);
        Background.run(() -> {
            CategoryCatalog loaded = categories.getCatalog();
            Category found = loaded.contains(key) ? loaded.find(key) : null;
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (found == null) {
                    finish();
                    return;
                }
                catalog = loaded;
                category = found;
                populate();
                loadRules();
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        // Keyword or merchant changes are already saved; bring past transactions in line.
        if (rulesChanged && isFinishing()) {
            recategoriseInBackground();
        }
        super.onDestroy();
    }

    private void populate() {
        setTitle(category.getLabel());
        etName.setText(category.getLabel());
        if (category.hasBudget()) {
            etBudget.setText(plainAmount(category.getBudgetCents()));
        }
        CurrencySpinners.setUp(spBudgetCurrency, tilBudget, category.getBudgetCurrency());
        // The budget is in whatever currency the dashboard shows, so there is nothing to pick.
        spBudgetCurrency.setVisibility(View.GONE);
        Background.run(() -> {
            Currency dashboard = CurrencySwitch.resolve(settings, TransactionRepository.getInstance(this));
            Background.main(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    dashboardCurrency = dashboard;
                    spBudgetCurrency.setSelection(dashboard.ordinal());
                    tilBudget.setPrefixText(dashboard.getSymbol());   // a hidden spinner reports no change
                }
            });
        });
        selectedColor = category.getColor();
        buildColorGrid();
        btnDelete.setVisibility(category.isOther() ? View.GONE : View.VISIBLE);

        List<String> builtIn = CategorisationEngine.builtInKeywordsFor(category);
        if (builtIn.isEmpty()) {
            sectionBuiltIn.setVisibility(View.GONE);
        } else {
            sectionBuiltIn.setVisibility(View.VISIBLE);
            String list = TextUtils.join(", ", builtIn);
            tvBuiltIn.setText(settings.isBuiltInKeywordsOn()
                    ? getString(R.string.built_in_keywords_on, list)
                    : getString(R.string.built_in_keywords_off, list));
        }
    }

    private static String plainAmount(long cents) {
        return (cents / 100) + "." + (cents % 100 < 10 ? "0" : "") + (cents % 100);
    }

    // ---- Colour ---------------------------------------------------------------------------------

    private void buildColorGrid() {
        gridColors.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(36 * density);
        int margin = Math.round(5 * density);
        boolean currentInPalette = false;
        for (int color : Category.PALETTE) {
            currentInPalette |= color == selectedColor;
        }
        int[] colors = Category.PALETTE;
        if (!currentInPalette) {
            colors = new int[Category.PALETTE.length + 1];
            colors[0] = selectedColor;
            System.arraycopy(Category.PALETTE, 0, colors, 1, Category.PALETTE.length);
        }
        for (int color : colors) {
            View swatch = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            if (color == selectedColor) {
                shape.setStroke(Math.round(3 * density),
                        ContextCompat.getColor(this, R.color.swatch_ring));
            }
            swatch.setBackground(shape);
            swatch.setContentDescription(getString(color == selectedColor
                    ? R.string.colour_selected : R.string.colour_option));
            swatch.setOnClickListener(v -> {
                selectedColor = color;
                buildColorGrid();
            });
            gridColors.addView(swatch);
        }
    }

    // ---- Keywords and remembered merchants -------------------------------------------------------

    private void loadRules() {
        Background.run(() -> {
            List<String> keywords = categories.getKeywordsFor(category);
            List<String> merchants = categories.getLearnedMerchantsFor(category);
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showKeywords(keywords);
                showMerchants(merchants);
            });
        });
    }

    private void showKeywords(List<String> keywords) {
        chipsKeywords.removeAllViews();
        for (String keyword : keywords) {
            Chip chip = new Chip(this);
            chip.setText(keyword);
            chip.setCloseIconVisible(true);
            chip.setCloseIconContentDescription(getString(R.string.remove_item, keyword));
            chip.setOnCloseIconClickListener(v -> Background.run(() -> {
                categories.removeKeyword(keyword);
                rulesChanged = true;
                Background.main(this::loadRules);
            }));
            chipsKeywords.addView(chip);
        }
        tvNoKeywords.setVisibility(keywords.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showMerchants(List<String> merchants) {
        chipsMerchants.removeAllViews();
        for (String merchant : merchants) {
            Chip chip = new Chip(this);
            chip.setText(merchant);
            chip.setCloseIconVisible(true);
            chip.setCloseIconContentDescription(getString(R.string.remove_item, merchant));
            chip.setOnCloseIconClickListener(v -> Background.run(() -> {
                categories.deleteLearnedRule(merchant);
                rulesChanged = true;
                Background.main(this::loadRules);
            }));
            chipsMerchants.addView(chip);
        }
        tvNoMerchants.setVisibility(merchants.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addKeyword() {
        if (category == null) {
            return;
        }
        String typed = String.valueOf(etKeyword.getText()).trim();
        if (MerchantNormaliser.normalise(typed).isEmpty()) {
            etKeyword.setError(getString(R.string.error_keyword));
            return;
        }
        etKeyword.setText("");
        Background.run(() -> {
            // Tell the user if the keyword was moved here from another category.
            Map<String, Category> existing = categories.getUserKeywords();
            Category previous = existing.get(MerchantNormaliser.normalise(typed));
            categories.addKeyword(typed, category);
            rulesChanged = true;
            Background.main(() -> {
                if (previous != null && !previous.equals(category)) {
                    Toast.makeText(this, getString(R.string.keyword_moved, previous.getLabel()),
                            Toast.LENGTH_SHORT).show();
                }
                loadRules();
            });
        });
    }

    // ---- Save and delete -----------------------------------------------------------------------

    private void save() {
        if (category == null) {
            return;
        }
        tilName.setError(null);
        tilBudget.setError(null);
        String name = String.valueOf(etName.getText()).trim();
        String error = CategoryDialogs.validateName(this, catalog, name, category);
        if (error != null) {
            tilName.setError(error);
            return;
        }
        long budget = 0;
        String budgetText = String.valueOf(etBudget.getText()).trim();
        if (!budgetText.isEmpty()) {
            try {
                budget = MoneyFormatter.parseToCents(budgetText);
            } catch (NumberFormatException e) {
                tilBudget.setError(getString(R.string.error_budget));
                return;
            }
        }
        final long newBudget = budget;
        final Currency budgetCurrency = dashboardCurrency != null ? dashboardCurrency
                : CurrencySpinners.selected(spBudgetCurrency);
        // The name and colour are saved as before; the budget goes through the budget history.
        final Category updated = category.withLabel(name).withColor(selectedColor)
                .withBudget(category.getBudgetCents(), budgetCurrency);
        if (newBudget == category.getBudgetCents()) {
            saveCategoryAndBudget(updated, newBudget, null);
            return;
        }
        Calendar now = Calendar.getInstance();
        BudgetScopeDialog.choose(this, newBudget,
                Dates.monthLabel(now.get(Calendar.YEAR), now.get(Calendar.MONTH)),
                scope -> saveCategoryAndBudget(updated, newBudget, scope));
    }

    private void saveCategoryAndBudget(Category updated, long newBudget,
                                       com.localspend.data.BudgetRepository.Scope scope) {
        final android.content.Context app = getApplicationContext();
        Background.run(() -> {
            categories.saveCategory(updated);
            if (scope != null) {
                com.localspend.data.BudgetRepository.getInstance(app).apply(updated.getKey(),
                        com.localspend.data.BudgetRepository.currentMonthIndex(), newBudget, scope);
            }
            Background.main(this::finish);
        });
    }

    private void recategoriseInBackground() {
        final boolean builtIn = settings.isBuiltInKeywordsOn();
        final android.content.Context app = getApplicationContext();
        Background.run(() -> {
            int changed = transactions.recategoriseAutomatic(categories.buildEngine(builtIn));
            if (changed > 0) {
                Background.main(() -> Toast.makeText(app, app.getResources().getQuantityString(
                        R.plurals.recategorised, changed, changed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_category_title, category.getLabel()))
                .setMessage(category.isBuiltIn()
                        ? R.string.delete_category_body_builtin : R.string.delete_category_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    final android.content.Context app = getApplicationContext();
                    Background.run(() -> {
                        int moved = categories.deleteCategory(category);
                        Background.main(() -> {
                            Toast.makeText(app, app.getResources().getQuantityString(
                                    R.plurals.moved_to_other, moved, moved), Toast.LENGTH_SHORT).show();
                            rulesChanged = true;   // moved transactions may match other rules
                            finish();
                        });
                    });
                })
                .show();
    }
}
