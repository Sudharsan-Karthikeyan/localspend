package com.localspend.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.localspend.R;
import com.localspend.logic.Insight;

import java.util.List;

/** Fills a vertical LinearLayout with one line per insight, each with a coloured marker. */
final class InsightViews {

    private InsightViews() {
    }

    static void render(LinearLayout container, List<Insight> insights) {
        container.removeAllViews();
        Context ctx = container.getContext();
        int gap = Math.round(6 * ctx.getResources().getDisplayMetrics().density);
        for (Insight insight : insights) {
            TextView tv = new TextView(ctx);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(marker(insight.getKind())).append("  ");
            sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ctx, colorFor(insight.getKind()))),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(insight.getText());
            tv.setText(sb);
            tv.setPadding(0, gap / 2, 0, gap / 2);
            container.addView(tv);
        }
    }

    private static String marker(Insight.Kind kind) {
        switch (kind) {
            case GOOD:
                return "▲";   // up triangle
            case WARNING:
                return "●";   // filled circle
            default:
                return "○";   // open circle
        }
    }

    private static int colorFor(Insight.Kind kind) {
        switch (kind) {
            case GOOD:
                return R.color.income;
            case WARNING:
                return R.color.warning;
            default:
                return R.color.primary;
        }
    }
}
