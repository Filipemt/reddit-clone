package com.motadev.clone_reddit.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageablesTest {

    @Test
    void newestByCreatedAtKeepsPageAndSizeAndForcesSort() {
        Pageable input = PageRequest.of(2, 10, Sort.by("title"));

        Pageable result = Pageables.newestByCreatedAt(input);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getSort()).isEqualTo(Pageables.NEWEST_BY_CREATED_AT);
    }

    @Test
    void unpagedFallsBackToFirstPageWithDefaultSize() {
        Pageable result = Pageables.withoutSort(Pageable.unpaged());

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(Pageables.FALLBACK_PAGE_SIZE);
        assertThat(result.getSort().isSorted()).isFalse();
    }

    @Test
    void withSortAppliesCustomOrder() {
        Sort hot = Sort.by(Sort.Direction.DESC, "hotScore");

        Pageable result = Pageables.withSort(PageRequest.of(1, 5), hot);

        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(5);
        assertThat(result.getSort()).isEqualTo(hot);
    }
}
