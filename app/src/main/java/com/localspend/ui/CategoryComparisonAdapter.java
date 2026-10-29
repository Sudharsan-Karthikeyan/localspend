package com.localspend.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.model.Currency;
import com.localspend.logic.CategoryComparison;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/** Compares each category's spending this month with the previous month. */
public class CategoryComparisonAdapter
        extends RecyclerView.Adapter<CategoryComparisonAdapter.Holder> {

    /** Called when the user taps a category row. */
    public interface OnCategoryClick {
        void onClick(com.localspend.model.Category category);
    }

    private final List<CategoryComparison> items = new ArrayList<>();
    private OnCategoryClick listener;

    public void setOnCategoryClick(OnCategoryClick listener) {
        this.listener = listener;
    }

    private Currency currency = Currency.defaultCurrency();

    /** Currency of the amounts in the list. */
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public void setItems(List<CategoryComparison> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_comparison, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CategoryComparison row = items.get(position);
        android.content.Context ctx = h.itemView.getContext();

        ColorDot.apply(h.dot, row.getCategory().getColor());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(row.getCategory());
            }
        });
        h.name.setText(row.getCategory().getLabel());
        h.thisMonth.setText(MoneyFormatter.format(row.getCurrentCents(), currency));
        h.lastMonth.setText(ctx.getString(R.string.last_amount,
                MoneyFormatter.format(row.getPreviousCents(), currency)));

        long delta = row.getDeltaCents();
        Integer percent = row.getPercentChange();
        if (delta == 0) {
            h.delta.setText("0%");
            h.delta.setTextColor(h.lastMonth.getTextColors());
        } else {
            String text = percent == null
                    ? "new"
                    : (delta > 0 ? "+" : "") + percent + "%";
            h.delta.setText(text);
            // More spending is red, less spending is green.
            h.delta.setTextColor(ContextCompat.getColor(ctx,
                    delta > 0 ? R.color.expense : R.color.income));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView thisMonth;
        final TextView lastMonth;
        final TextView delta;

        Holder(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.dot);
            name = itemView.findViewById(R.id.tvName);
            thisMonth = itemView.findViewById(R.id.tvThis);
            lastMonth = itemView.findViewById(R.id.tvLast);
            delta = itemView.findViewById(R.id.tvDelta);
        }
    }
}
