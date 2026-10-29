package com.localspend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The user's current set of categories, in display order. Looks categories up by key and always
 * has an "Other" category to fall back on, so a transaction can never end up without a category.
 */
public final class CategoryCatalog {

    private final Map<String, Category> byKey = new LinkedHashMap<>();
    private final Category other;

    public CategoryCatalog(List<Category> categories) {
        Category foundOther = null;
        for (Category c : categories) {
            if (c.isOther()) {
                foundOther = c;
            } else {
                byKey.put(c.getKey(), c);
            }
        }
        other = foundOther != null ? foundOther : Category.OTHER;
        byKey.put(other.getKey(), other);   // Other is always listed last
    }

    /** The built-in categories. */
    public static CategoryCatalog defaults() {
        return new CategoryCatalog(Category.defaults());
    }

    /** All categories, Other last. */
    public List<Category> all() {
        return Collections.unmodifiableList(new ArrayList<>(byKey.values()));
    }

    public int size() {
        return byKey.size();
    }

    /** The category with this key, or Other if it no longer exists (for example it was deleted). */
    public Category find(String key) {
        Category c = key == null ? null : byKey.get(key);
        return c != null ? c : other;
    }

    /** True if a category with this key exists. */
    public boolean contains(String key) {
        return key != null && byKey.containsKey(key);
    }

    /** The current version (name, colour, budget) of a category, or Other if it is gone. */
    public Category resolve(Category category) {
        return category == null ? other : find(category.getKey());
    }

    public Category getOther() {
        return other;
    }

    /** Position in {@link #all()}, or the position of Other if not found. */
    public int indexOf(Category category) {
        List<Category> list = all();
        int i = list.indexOf(category);
        return i >= 0 ? i : list.indexOf(other);
    }

    /** Finds a category by its name, ignoring case and extra spaces. */
    public Category findByLabel(String label) {
        if (label == null) {
            return null;
        }
        String wanted = label.trim().toLowerCase(Locale.ROOT);
        for (Category c : byKey.values()) {
            if (c.getLabel().toLowerCase(Locale.ROOT).equals(wanted)) {
                return c;
            }
        }
        return null;
    }

    /** Category names in display order, for spinners. */
    public String[] labels() {
        List<Category> list = all();
        String[] out = new String[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i).getLabel();
        }
        return out;
    }

    /** A palette colour not yet used by any category (or the first one if all are taken). */
    public int suggestColor() {
        for (int color : Category.PALETTE) {
            boolean used = false;
            for (Category c : byKey.values()) {
                if (c.getColor() == color) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return color;
            }
        }
        return Category.PALETTE[byKey.size() % Category.PALETTE.length];
    }
}
