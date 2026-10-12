package com.motadev.clone_reddit.post.sort;

import java.time.LocalDateTime;
import java.util.Locale;

public enum TopPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL;

    public static TopPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        return TopPeriod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public LocalDateTime cutoff(LocalDateTime now) {
        return switch (this) {
            case DAY -> now.minusDays(1);
            case WEEK -> now.minusWeeks(1);
            case MONTH -> now.minusMonths(1);
            case YEAR -> now.minusYears(1);
            case ALL -> null;
        };
    }
}
