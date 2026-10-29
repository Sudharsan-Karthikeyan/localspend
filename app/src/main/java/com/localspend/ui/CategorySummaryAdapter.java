package com.localspend.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.model.Currency;
import com.localspend.logic.MonthSummary;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/** One row per category with a bar showing its share of the month's spending. */
public class CategorySummaryAdapter extends RecyclerView.Adapter<CategorySummaryAdapter.Holder> {

    /** Called when the user taps a category row. */
    public interface OnCategoryClick {
        void onClick(com.localspend.model.Category category);
    }

    private final List<MonthSummary.CategoryTotal> items = new ArrayList<>();
    private OnCategoryClick listener;

    public void setOnCategoryClick(OnCategoryClick listener) {
        this.listener = listener;
    }

    private Currency currency = Currency.defaultCurrency();

    /** Currency of the amounts in the list. */
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public void setItems(List<MonthSummary.CategoryTotal> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_summary, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MonthSummary.CategoryTotal row = items.get(position);
        int color = row.getCategory().getColor();
        ColorDot.apply(h.dot, color);
        h.name.setText(row.getCategory().getLabel());
        h.percent.setText(row.getPercent() + "%");
        h.amount.setText(MoneyFormatter.format(row.getCents(), currency));
        h.progress.setProgress(row.getPercent());
        h.progress.setProgressTintList(ColorStateList.valueOf(color));
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(row.getCategory());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView percent;
        final TextView amount;
        final ProgressBar progress;

        Holder(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.dot);
            name = itemView.findViewById(R.id.tvName);
            percent = itemView.findViewById(R.id.tvPercent);
            amount = itemView.findViewById(R.id.tvAmount);
            progress = itemView.findViewById(R.id.progress);
        }
    }
}
