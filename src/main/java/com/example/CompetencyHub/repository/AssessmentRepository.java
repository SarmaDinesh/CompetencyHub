package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository over the whole assessment hierarchy.
 *
 * <p>Typed to the abstract parent, so queries are polymorphic: findAll() returns both
 * ObjectiveAssessment and PerformanceAssessment instances, each constructed as its
 * real subtype. Calling describeFormat() on the results dispatches correctly without
 * any type checks — the payoff for the join that JOINED inheritance costs.
 */
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
}
