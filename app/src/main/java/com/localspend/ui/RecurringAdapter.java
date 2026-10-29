package com.localspend.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.localspend.R;
import com.localspend.model.RecurringPayment;
import com.localspend.model.TransactionType;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/** Rows for repeating payments and instalment plans. */
public class RecurringAdapter extends RecyclerView.Adapter<RecurringAdapter.Holder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PLAN = 1;

    public interface OnClick {
        void onClick(RecurringPayment plan);
    }

    /** Section headings (String) and plans (RecurringPayment), in display order. */
    private final List<Object> items = new ArrayList<>();
    private final OnClick listener;

    public RecurringAdapter(OnClick listener) {
        this.listener = listener;
    }

    /** Shows repeating payments first, then instalment plans, each under its own heading. */
    public void setItems(Context context, List<RecurringPayment> plans) {
        List<RecurringPayment> repeats = new ArrayList<>();
        List<RecurringPayment> instalments = new ArrayList<>();
        for (RecurringPayment p : plans) {
            (p.isInstallment() ? instalments : repeats).add(p);
        }
        items.clear();
        if (!repeats.isEmpty()) {
            items.add(context.getString(R.string.recurring_section_repeat, repeats.size()));
            items.addAll(repeats);
        }
        if (!instalments.isEmpty()) {
            items.add(context.getString(R.string.recurring_section_installment, instalments.size()));
            items.addAll(instalments);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_PLAN;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_HEADER ? R.layout.item_recurring_header : R.layout.item_recurring;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Object item = items.get(position);
        if (item instanceof String) {
            ((TextView) h.itemView).setText((String) item);
            return;
        }
        RecurringPayment p = (RecurringPayment) item;
        Context ctx = h.itemView.getContext();

        h.merchant.setText(p.getMerchant());
        String amount = MoneyFormatter.format(p.getAmountCents(), p.getCurrency());
        h.amount.setText(p.getType() == TransactionType.INCOME ? "+" + amount : amount);

        String detail = RecurrenceText.describe(ctx, p.getFrequency(), p.isInstallment(),
                p.getTotalPayments());
        String progress = p.getTotalPayments() > 0
                ? ctx.getString(R.string.recurrence_progress, p.getPaymentsMade(), p.getTotalPayments())
                : ctx.getString(R.string.recurrence_paid_count, p.getPaymentsMade());
        h.detail.setText(detail + " · " + progress);

        if (p.isFinished()) {
            h.next.setText(R.string.recurrence_finished);
        } else if (!p.isActive()) {
            h.next.setText(R.string.recurrence_paused);
        } else {
            h.next.setText(ctx.getString(R.string.recurrence_next, Dates.formatDay(p.getNextDueMillis())));
        }
        h.itemView.setAlpha(p.isActive() ? 1f : 0.6f);
        h.itemView.setOnClickListener((View v) -> listener.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView merchant;
        final TextView detail;
        final TextView next;
        final TextView amount;

        Holder(@NonNull View itemView) {
            super(itemView);
            // The heading rows are a bare TextView and have none of these.
            merchant = itemView.findViewById(R.id.tvMerchant);
            detail = itemView.findViewById(R.id.tvDetail);
            next = itemView.findViewById(R.id.tvNext);
            amount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
