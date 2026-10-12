package com.motadev.clone_reddit.post.sort;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class HotScoreCalculator {

    private HotScoreCalculator() {
    }

    public static double compute(long score, LocalDateTime createdAt) {
        double order = Math.signum(score);
        double magnitude = Math.log10(Math.max(Math.abs(score), 1));
        long seconds = createdAt.toEpochSecond(ZoneOffset.UTC);
        return order * magnitude + (seconds / 45000.0);
    }
}
