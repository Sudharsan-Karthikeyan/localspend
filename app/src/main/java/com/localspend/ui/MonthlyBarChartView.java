package com.localspend.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.localspend.R;
import com.localspend.logic.MonthTotal;
import com.localspend.model.Currency;
import com.localspend.util.Dates;
import com.localspend.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Bar chart of monthly spending. The selected month (the latest one unless the user picks
 * another) is drawn in full colour with a bold label. Tapping a bar reports that month through
 * {@link OnMonthClickListener}, so the dashboard can switch to it.
 */
public class MonthlyBarChartView extends View {

    /** Called when the user taps a month's bar (month is 0-based). */
    public interface OnMonthClickListener {
        void onMonthClick(int year, int month);
    }

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private List<MonthTotal> data = new ArrayList<>();
    private Currency currency = Currency.defaultCurrency();
    private int selectedIndex = -1;          // -1 means the latest month
    private OnMonthClickListener listener;
    private float downX;

    public MonthlyBarChartView(Context context) {
        this(context, null);
    }

    public MonthlyBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        int primary = ContextCompat.getColor(context, R.color.primary);
        barPaint.setColor(primary);
        barPaint.setAlpha(110);
        currentBarPaint.setColor(primary);

        int textColor = resolveSecondaryTextColor(context);
        labelPaint.setColor(textColor);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(12));
        valuePaint.setColor(textColor);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(11));
        selectedLabelPaint.setColor(primary);
        selectedLabelPaint.setTextAlign(Paint.Align.CENTER);
        selectedLabelPaint.setTextSize(sp(12));
        selectedLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        markerPaint.setColor(primary);

        setClickable(true);
        setFocusable(true);
    }

    public void setOnMonthClickListener(OnMonthClickListener listener) {
        this.listener = listener;
    }

    /** Highlights the given month (0-based); a month not in the chart highlights the latest. */
    public void setSelectedMonth(int year, int month) {
        selectedIndex = -1;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getYear() == year && data.get(i).getMonth() == month) {
                selectedIndex = i;
            }
        }
        invalidate();
    }

    private int effectiveSelection() {
        return selectedIndex >= 0 && selectedIndex < data.size() ? selectedIndex : currentIndex();
    }

    /** The bar for this month (a tax-year chart also has months still to come), else the last. */
    private int currentIndex() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getYear() == now.get(java.util.Calendar.YEAR)
                    && data.get(i).getMonth() == now.get(java.util.Calendar.MONTH)) {
                return i;
            }
        }
        return data.size() - 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (data.isEmpty() || listener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                // A tap (not a scroll) on a bar's column selects that month.
                if (Math.abs(event.getX() - downX) < dp(16)) {
                    int index = indexAt(event.getX());
                    if (index >= 0) {
                        selectedIndex = index;
                        invalidate();
                        MonthTotal m = data.get(index);
                        listener.onMonthClick(m.getYear(), m.getMonth());
                    }
                    performClick();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Which month's column contains x, or -1. */
    private int indexAt(float x) {
        float sidePad = dp(12);
        float slot = (getWidth() - 2 * sidePad) / data.size();
        int index = (int) Math.floor((x - sidePad) / slot);
        return index >= 0 && index < data.size() ? index : -1;
    }

    public void setData(List<MonthTotal> months, Currency currency) {
        this.currency = currency;
        setData(months);
    }

    public void setData(List<MonthTotal> months) {
        data = new ArrayList<>(months);
        StringBuilder description = new StringBuilder();
        for (MonthTotal m : data) {
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(Dates.shortMonth(m.getYear(), m.getMonth())).append(' ')
                    .append(MoneyFormatter.formatWhole(m.getSpentCents(), currency));
        }
        setContentDescription(getResources().getString(R.string.chart_description, description));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.isEmpty()) {
            return;
        }

        float topPad = dp(24);      // room for the value label above the tallest bar
        float bottomPad = dp(26);   // room for the month label
        float sidePad = dp(12);
        float chartHeight = getHeight() - topPad - bottomPad;
        float slot = (getWidth() - 2 * sidePad) / data.size();
        float barWidth = slot * (data.size() > 6 ? 0.6f : 0.55f);

        long max = 0;
        for (MonthTotal m : data) {
            max = Math.max(max, m.getSpentCents());
        }

        int selected = effectiveSelection();
        for (int i = 0; i < data.size(); i++) {
            MonthTotal m = data.get(i);
            boolean isSelected = i == selected;
            float centerX = sidePad + slot * i + slot / 2f;
            float barHeight = max == 0 ? 0 : chartHeight * m.getSpentCents() / (float) max;
            barHeight = Math.max(barHeight, dp(2));   // always show a sliver, even for $0
            float bottom = topPad + chartHeight;
            rect.set(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom);
            canvas.drawRoundRect(rect, dp(4), dp(4), isSelected ? currentBarPaint : barPaint);

            if (m.getSpentCents() > 0) {
                // With many bars the figures are shortened (12.5k) so they fit.
                boolean crowded = data.size() > 6;
                valuePaint.setTextSize(sp(crowded ? 9 : 11));
                String value = crowded
                        ? currency.getSymbol() + MonthCalendarView.shortAmount(m.getSpentCents())
                        : MoneyFormatter.formatWhole(m.getSpentCents(), currency);
                canvas.drawText(value, centerX, rect.top - dp(6), valuePaint);
            }
            canvas.drawText(Dates.shortMonth(m.getYear(), m.getMonth()),
                    centerX, getHeight() - dp(10), isSelected ? selectedLabelPaint : labelPaint);
            if (isSelected) {
                canvas.drawCircle(centerX, getHeight() - dp(2), dp(2), markerPaint);
            }
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    private static int resolveSecondaryTextColor(Context context) {
        TypedArray a = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            ColorStateList list = a.getColorStateList(0);
            return list != null ? list.getDefaultColor() : 0xFF757575;
        } finally {
            a.recycle();
        }
    }
}
