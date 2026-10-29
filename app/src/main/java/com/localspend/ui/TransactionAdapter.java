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
import com.localspend.model.Source;
import com.localspend.model.Transaction;
import com.localspend.util.Dates;
import com.localspend.util.FxText;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows transactions as rows: category badge, merchant, "category - date - source", and amount.
 * Rows can be grouped under a header per day ("Today", "Yesterday", "12 May 2026").
 */
public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTransactionClick {
        void onClick(Transaction transaction);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    /** Each row is either a String (a day header) or a Transaction. */
    private final List<Object> rows = new ArrayList<>();
    private final OnTransactionClick listener;
    private final boolean groupByDay;
    private Currency mainCurrency;   // conversions are shown only into this currency

    public TransactionAdapter(OnTransactionClick listener) {
        this(listener, true);
    }

    public TransactionAdapter(OnTransactionClick listener, boolean groupByDay) {
        this.listener = listener;
        this.groupByDay = groupByDay;
    }

    /** The currency the screen totals are in; payments in it never show a conversion. */
    public void setMainCurrency(Currency main) {
        this.mainCurrency = main;
    }

    public void setItems(List<Transaction> newItems) {
        rows.clear();
        long lastDay = Long.MIN_VALUE;
        for (Transaction t : newItems) {
            if (groupByDay) {
                long day = Dates.dayStart(t.getDateMillis());
                if (day != lastDay) {
                    rows.add(Dates.dayHeader(day));
                    lastDay = day;
                }
            }
            rows.add(t);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_day_header, parent, false));
        }
        return new Holder(inflater.inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (row instanceof String) {
            ((TextView) holder.itemView).setText((String) row);
            return;
        }
        Holder h = (Holder) holder;
        Transaction t = (Transaction) row;
        android.content.Context ctx = h.itemView.getContext();

        ColorDot.apply(h.dot, t.getCategory().getColor());
        h.merchant.setText(t.getMerchant());

        int sourceRes = t.getSource() == Source.SMS ? R.string.source_sms
                : t.getSource() == Source.RECURRING ? R.string.source_recurring
                : R.string.source_manual;
        String sourceLabel = ctx.getString(sourceRes);
        String subtitle = t.getCategory().getLabel() + " · "
                + Dates.formatDay(t.getDateMillis()) + " · " + sourceLabel;
        if (t.hasConversion() && t.getCurrency() != mainCurrency
                && t.getConvertedCurrency() == mainCurrency) {
            // Other-currency payments also show what they count as in the main currency.
            subtitle += "\n" + ctx.getString(t.isConversionEstimated()
                            ? R.string.fx_list_estimated : R.string.fx_list_exact,
                    MoneyFormatter.format(t.getConvertedCents(), t.getConvertedCurrency()),
                    FxText.rate(t.getConversionRate()));
        }
        h.subtitle.setText(subtitle);

        String amount = MoneyFormatter.format(t.getAmountCents(), t.getCurrency());
        if (t.isExpense()) {
            h.amount.setText("-" + amount);
            h.amount.setTextColor(ContextCompat.getColor(ctx, R.color.expense));
        } else {
            h.amount.setText("+" + amount);
            h.amount.setTextColor(ContextCompat.getColor(ctx, R.color.income));
        }

        h.itemView.setOnClickListener(v -> listener.onClick(t));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        HeaderHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View dot;
        final TextView merchant;
        final TextView subtitle;
        final TextView amount;

        Holder(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.dot);
            merchant = itemView.findViewById(R.id.tvMerchant);
            subtitle = itemView.findViewById(R.id.tvSubtitle);
            amount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
