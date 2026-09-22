package com.example.CompetencyHub.web.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope for paginated results.
 *
 * <p>Spring's own {@code Page} serialises to JSON, but its shape is an implementation
 * detail of Spring Data — it includes internal fields, and its structure has changed
 * between versions, silently breaking clients. Defining the envelope here means the API
 * contract is ours to keep stable.
 *
 * @param content items on this page, already mapped to response types
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    /** Maps a Spring Data page of entities into a page of response DTOs. */
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
