package com.motadev.clone_reddit.post.sort;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HotScoreCalculatorTest {

    @Test
    void higherScoreRanksHigherForSameTimestamp() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);

        double low = HotScoreCalculator.compute(1, createdAt);
        double high = HotScoreCalculator.compute(100, createdAt);

        assertThat(high).isGreaterThan(low);
    }

    @Test
    void newerPostRanksHigherForSameScore() {
        double older = HotScoreCalculator.compute(10, LocalDateTime.of(2020, 1, 1, 0, 0));
        double newer = HotScoreCalculator.compute(10, LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(newer).isGreaterThan(older);
    }
}
