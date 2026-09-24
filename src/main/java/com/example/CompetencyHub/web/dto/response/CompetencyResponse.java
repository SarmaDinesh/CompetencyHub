package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Competency;

public record CompetencyResponse(
        Long id,
        Long courseId,
        String title,
        int weight,
        int orderIndex
) {
    public static CompetencyResponse from(Competency competency) {
        return new CompetencyResponse(
                competency.getId(),
                // getCourse() is LAZY, so this may be a Hibernate proxy. Calling getId() on a
                // proxy does NOT hit the database -- the proxy already holds the id, since that
                // is how it knows which row to load later. Any other getter would trigger a
                // load, and outside a transaction that is a LazyInitializationException.
                competency.getCourse().getId(),
                competency.getTitle(),
                competency.getWeight(),
                competency.getOrderIndex()
        );
    }
}
