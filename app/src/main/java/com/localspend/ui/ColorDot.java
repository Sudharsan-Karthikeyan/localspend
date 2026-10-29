package com.localspend.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Paints the small coloured circle that identifies a category. */
final class ColorDot {

    private ColorDot() {
    }

    static void apply(View dot, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
    }
}
