package com.motadev.clone_reddit.shared.dtos.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PagedResponseDTO<T> from(Page<T> page) {
        return from(page, Function.identity());
    }

    public static <T, R> PagedResponseDTO<R> from(Page<T> page, Function<T, R> mapper) {
        return new PagedResponseDTO<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
