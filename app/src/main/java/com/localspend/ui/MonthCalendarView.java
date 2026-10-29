package com.localspend.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.localspend.R;
import com.localspend.logic.DayTotals;
import com.localspend.model.Currency;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * A month calendar that fills its space. Days are soft rounded tiles coloured by the day's result:
 * green when more came in than went out, red otherwise, grey when nothing happened (the bigger
 * the difference, the deeper the colour). The tile colour follows the day's net, which is what adds
 * up to the Total above the calendar. Every day with activity shows both what came in (+) and
 * what went out (-); tapping a day lists its transactions. Today is a filled
 * circle, the tapped day has an outline, and days from the neighbouring months are faint.
 */
public class MonthCalendarView extends View {

    public interface OnDayClickListener {
        /** month is 0-based. */
        void onDayClick(int year, int month, int day);
    }

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug",
            "Sep", "Oct", "Nov", "Dec"};
    private static final String[] WEEKDAYS = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    private final float density;
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int quietTile;
    private final int textPrimary;
    private final int textSecondary;
    private final int primary;
    private final int incomeColor;
    private final int expenseColor;

    private int year;
    private int month;
    private DayTotals totals;
    private int selectedDay = 0;
    private OnDayClickListener listener;
    private float downX;
    private float downY;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);

        textPrimary = resolveColor(context, android.R.attr.textColorPrimary, 0xFF212121);
        textSecondary = resolveColor(context, android.R.attr.textColorSecondary, 0xFF757575);
        quietTile = ContextCompat.getColor(context, R.color.calendar_quiet);
        primary = ContextCompat.getColor(context, R.color.primary);
        incomeColor = ContextCompat.getColor(context, R.color.calendar_income);
        expenseColor = ContextCompat.getColor(context, R.color.calendar_expense);

        tilePaint.setStyle(Paint.Style.FILL);
        outlinePaint.setColor(primary);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(density * 2f);
        todayPaint.setColor(primary);
        todayPaint.setStyle(Paint.Style.FILL);
        dayPaint.setTextSize(sp(13));
        dayPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTextSize(sp(10.5f));
        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
        headerPaint.setColor(withAlpha(textSecondary, 200));
        amountPaint.setTextAlign(Paint.Align.CENTER);
        amountPaint.setTypeface(Typeface.DEFAULT_BOLD);

        setClickable(true);
        setFocusable(true);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    /** Shows this month (0-based) with these daily totals. */
    public void setData(int year, int month, DayTotals totals) {
        this.year = year;
        this.month = month;
        this.totals = totals;
        invalidate();
    }

    /** Outlines this day of the shown month; 0 clears it. */
    public void setSelectedDay(int day) {
        selectedDay = day;
        invalidate();
    }

    // ---- geometry ----------------------------------------------------------------------------

    private int firstColumn() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
    }

    private int daysInMonth() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int daysInPreviousMonth() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        c.add(Calendar.MONTH, -1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int rowCount() {
        return (firstColumn() + daysInMonth() + 6) / 7;
    }

    private float headerHeight() {
        return dp(28);
    }

    // ---- drawing -----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float gridLeft = getPaddingLeft();
        float gridW = getWidth() - getPaddingLeft() - getPaddingRight();
        float headerH = headerHeight();
        float gridTop = getPaddingTop() + headerH;
        int rows = rowCount();
        float cellW = gridW / 7f;
        float cellH = Math.min((getHeight() - getPaddingBottom() - gridTop) / rows, cellW * 1.5f);
        float gap = dp(2.5f);
        float radius = dp(12);

        for (int col = 0; col < 7; col++) {
            canvas.drawText(WEEKDAYS[col], gridLeft + cellW * col + cellW / 2f,
                    getPaddingTop() + headerH / 2f + headerPaint.getTextSize() / 3f, headerPaint);
        }

        long maxNet = 0;
        if (totals != null) {
            for (int d = 1; d <= daysInMonth(); d++) {
                maxNet = Math.max(maxNet, Math.abs(totals.netOn(d)));
            }
        }

        int offset = firstColumn();
        int inMonth = daysInMonth();
        int previousDays = daysInPreviousMonth();
        Calendar now = Calendar.getInstance();
        boolean thisMonth = now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month;
        int today = thisMonth ? now.get(Calendar.DAY_OF_MONTH) : -1;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                int day = index - offset + 1;
                boolean current = day >= 1 && day <= inMonth;
                int shown = current ? day : day < 1 ? previousDays + day : day - inMonth;
                int shownMonth = current ? month : day < 1 ? month - 1 : month + 1;   // 0-based, may wrap

                float left = gridLeft + col * cellW + gap;
                float top = gridTop + row * cellH + gap;
                float right = gridLeft + (col + 1) * cellW - gap;
                float bottom = gridTop + (row + 1) * cellH - gap;
                rect.set(left, top, right, bottom);

                long expense = current && totals != null ? totals.expenseOn(day) : 0;
                long income = current && totals != null ? totals.incomeOn(day) : 0;

                boolean active = income > 0 || expense > 0;
                long net = income - expense;
                if (current) {
                    // Green when more came in than went out, red otherwise, grey when nothing
                    // happened. A bigger difference gives a deeper colour.
                    int base = quietTile;
                    int alpha = 255;
                    if (active) {
                        base = net > 0 ? incomeColor : expenseColor;
                        double ratio = maxNet > 0 ? Math.sqrt((double) Math.abs(net) / maxNet) : 0;
                        ratio = Math.max(0.25, ratio);
                        alpha = (int) ((0.25 + ratio * 0.55) * 255);
                    }
                    tilePaint.setColor(withAlpha(base, alpha));
                    canvas.drawRoundRect(rect, radius, radius, tilePaint);
                }

                float centreX = (left + right) / 2f;
                // Number and figures form one block, centred in the tile so nothing touches.
                int lines = (expense > 0 ? 1 : 0) + (income > 0 ? 1 : 0);
                float numberSize = dayPaint.getTextSize();
                float amountSize = sp(10f);
                float lineH = amountSize + dp(4);
                float blockH = numberSize + (lines > 0 ? dp(7) + lines * lineH - dp(4) : 0);
                float offset0 = Math.max(dp(5), ((bottom - top) - blockH) / 2f);
                float numberBaseline = top + offset0 + numberSize;
                drawDayNumber(canvas, centreX, numberBaseline, shown, shownMonth, current,
                        current && day == today);
                if (lines > 0) {
                    drawAmounts(canvas, centreX, numberBaseline + dp(7) + amountSize,
                            lineH, right - left, income, expense);
                }
                if (current && day == selectedDay) {
                    float inset = outlinePaint.getStrokeWidth() / 2f;
                    rect.set(left + inset, top + inset, right - inset, bottom - inset);
                    canvas.drawRoundRect(rect, radius, radius, outlinePaint);
                }
            }
        }
    }

    private void drawDayNumber(Canvas canvas, float centreX, float baseline, int shown,
                               int shownMonth, boolean current, boolean isToday) {
        // The first of a month says which month it is ("1 Jul"), so it is not mistaken for an amount.
        boolean first = shown == 1;
        String label = first ? shown + " " + MONTHS[Math.floorMod(shownMonth, 12)]
                : String.valueOf(shown);
        float size = first ? sp(11) : sp(13);
        dayPaint.setTextSize(size);
        if (isToday) {
            canvas.drawCircle(centreX, baseline - size * 0.36f, size * 0.95f, todayPaint);
            dayPaint.setColor(0xFFFFFFFF);
            dayPaint.setTypeface(Typeface.DEFAULT_BOLD);
        } else if (current) {
            dayPaint.setColor(textPrimary);
            dayPaint.setTypeface(first ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        } else {
            dayPaint.setColor(withAlpha(textSecondary, 110));
            dayPaint.setTypeface(Typeface.DEFAULT);
        }
        canvas.drawText(label, centreX, baseline, dayPaint);
        dayPaint.setTextSize(sp(13));
    }

    /** Income (green, "+") above spending (red, "-"), centred under the day number. */
    private void drawAmounts(Canvas canvas, float centreX, float firstBaseline, float lineH,
                             float tileWidth, long income, long expense) {
        float baseline = firstBaseline;
        if (income > 0) {
            amountPaint.setColor(incomeColor);
            drawFitted(canvas, "+" + shortAmount(income), centreX, baseline, tileWidth - dp(4));
            baseline += lineH;
        }
        if (expense > 0) {
            amountPaint.setColor(expenseColor);
            drawFitted(canvas, "-" + shortAmount(expense), centreX, baseline, tileWidth - dp(4));
        }
    }

    /** Draws the text as large as fits (down to a minimum). */
    private void drawFitted(Canvas canvas, String text, float centreX, float baseline, float maxWidth) {
        float size = sp(10f);
        float min = sp(7f);
        amountPaint.setTextSize(size);
        while (amountPaint.measureText(text) > maxWidth && size > min) {
            size -= sp(0.5f);
            amountPaint.setTextSize(size);
        }
        canvas.drawText(text, centreX, baseline, amountPaint);
    }

    /** Whole units for a tile: 5628 becomes "56"; 123456 becomes "1.2k". */
    static String shortAmount(long cents) {
        double units = cents / 100.0;
        if (units >= 1000) {
            return compact(String.valueOf(units));
        }
        return String.valueOf(Math.round(units));
    }

    /** "1234567.80" becomes "1.2M"; "12500.00" becomes "12.5k". */
    static String compact(String digits) {
        double value;
        try {
            value = Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return digits;
        }
        if (value >= 1_000_000) {
            return trim(value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return trim(value / 1_000) + "k";
        }
        return trim(value);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** "1,250.00": the amount without a currency symbol, as the calendar shows it. */
    static String plain(long cents) {
        return String.format(Locale.US, "%,d.%02d", cents / 100, cents % 100);
    }

    // ---- touch -------------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - downX) <= dp(16) && Math.abs(event.getY() - downY) <= dp(16)) {
                    handleTap(event.getX(), event.getY());
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleTap(float x, float y) {
        float headerH = headerHeight();
        float gridW = getWidth() - getPaddingLeft() - getPaddingRight();
        float gridTop = getPaddingTop() + headerH;
        if (y < gridTop || gridW <= 0) {
            return;
        }
        int rows = rowCount();
        float cellW = gridW / 7f;
        float cellH = Math.min((getHeight() - getPaddingBottom() - gridTop) / rows, cellW * 1.5f);
        if (x < getPaddingLeft()) {
            return;
        }
        int col = Math.min(6, (int) ((x - getPaddingLeft()) / cellW));
        int row = Math.min(rows - 1, (int) ((y - gridTop) / cellH));
        int day = row * 7 + col - firstColumn() + 1;
        if (day < 1 || day > daysInMonth()) {
            return;
        }
        performClick();
        selectedDay = day;
        invalidate();
        if (listener != null) {
            listener.onDayClick(year, month, day);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private float dp(float v) {
        return v * density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int resolveColor(Context context, int attr, int fallback) {
        TypedArray a = context.obtainStyledAttributes(new int[]{attr});
        try {
            ColorStateList list = a.getColorStateList(0);
            return list != null ? list.getDefaultColor() : fallback;
        } finally {
            a.recycle();
        }
    }
}
