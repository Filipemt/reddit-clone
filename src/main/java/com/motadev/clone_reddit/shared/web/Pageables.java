package com.motadev.clone_reddit.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pageables {

    public static final int FALLBACK_PAGE_SIZE = 20;

    public static final Sort NEWEST_BY_CREATED_AT = Sort.by(Sort.Direction.DESC, "createdAt");

    private Pageables() {
    }

    public static Pageable withSort(Pageable pageable, Sort sort) {
        if (!pageable.isPaged()) {
            return PageRequest.of(0, FALLBACK_PAGE_SIZE, sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    public static Pageable withoutSort(Pageable pageable) {
        if (!pageable.isPaged()) {
            return PageRequest.of(0, FALLBACK_PAGE_SIZE);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    public static Pageable newestByCreatedAt(Pageable pageable) {
        return withSort(pageable, NEWEST_BY_CREATED_AT);
    }
}
