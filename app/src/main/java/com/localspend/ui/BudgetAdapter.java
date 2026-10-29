package com.localspend.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.logic.BudgetStatus;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/** Budget progress rows: "Food and Dining  $320.00 of $400.00", a bar, and what is left. */
public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {

    private final List<BudgetStatus> items = new ArrayList<>();
    private int warnPercent = 80;

    public void setItems(List<BudgetStatus> newItems, int warnPercent) {
        items.clear();
        items.addAll(newItems);
        this.warnPercent = warnPercent;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        BudgetStatus b = items.get(position);
        Context ctx = h.itemView.getContext();

        int dotColor = b.isOverall()
                ? ContextCompat.getColor(ctx, R.color.primary) : b.getCategory().getColor();
        ColorDot.apply(h.dot, dotColor);
        h.name.setText(b.isOverall() ? ctx.getString(R.string.overall_budget) : b.getCategory().getLabel());
        h.amount.setText(ctx.getString(R.string.budget_of,
                MoneyFormatter.format(b.getSpentCents(), b.getCurrency()),
                MoneyFormatter.format(b.getBudgetCents(), b.getCurrency())));

        BudgetStatus.Level level = b.getLevel(warnPercent);
        int barColor;
        if (level == BudgetStatus.Level.EXCEEDED) {
            barColor = ContextCompat.getColor(ctx, R.color.expense);
            h.detail.setText(ctx.getString(R.string.budget_over_by,
                    MoneyFormatter.format(-b.getRemainingCents(), b.getCurrency()), b.getPercentUsed()));
        } else {
            barColor = level == BudgetStatus.Level.WARNING
                    ? ContextCompat.getColor(ctx, R.color.warning) : dotColor;
            h.detail.setText(ctx.getString(R.string.budget_left,
                    MoneyFormatter.format(b.getRemainingCents(), b.getCurrency()), b.getPercentUsed()));
        }
        h.detail.setTextColor(level == BudgetStatus.Level.OK
                ? h.name.getTextColors().getDefaultColor() : barColor);
        h.progress.setProgress(Math.min(100, b.getPercentUsed()));
        h.progress.setProgressTintList(ColorStateList.valueOf(barColor));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View dot;
        final TextView name;
        final TextView amount;
        final TextView detail;
        final ProgressBar progress;

        Holder(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.dot);
            name = itemView.findViewById(R.id.tvName);
            amount = itemView.findViewById(R.id.tvAmount);
            detail = itemView.findViewById(R.id.tvDetail);
            progress = itemView.findViewById(R.id.progress);
        }
    }
}
