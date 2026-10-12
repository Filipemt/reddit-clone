package com.motadev.clone_reddit.post.sort;

import java.util.Locale;

public enum PostSort {
    NEW,
    HOT,
    TOP;

    public static PostSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEW;
        }
        return PostSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
