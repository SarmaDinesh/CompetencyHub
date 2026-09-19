package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CourseRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Two implementations of the same task, written to be compared.
 *
 * <p>Temporary — this exists to demonstrate and measure the N+1 problem. Delete it
 * once the numbers are recorded; it is not part of the application's behaviour.
 */
@Service
public class CatalogDiagnosticsService {

    private final CourseRepository courseRepository;

    public CatalogDiagnosticsService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * The naive version — and the one people write by accident.
     *
     * <p>findAll() issues 1 query for the courses. Competencies are LAZY, so the first
     * touch of each course's collection issues another. Ten courses means 1 + 10 = 11
     * queries to answer one question. A hundred courses means 101.
     *
     * <p>Nothing here looks wrong. That is what makes N+1 dangerous: the loop is
     * ordinary Java, the queries are invisible, and it scales linearly with your data
     * while your local test set stays small.
     *
     * <p>@Transactional is required because open-in-view is false — without an open
     * session, touching a lazy collection throws LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public int countCompetenciesNaive() {
        List<Course> courses = courseRepository.findAll();   // query 1

        int total = 0;
        for (Course course : courses) {
            total += course.getCompetencies().size();         // queries 2..N+1
        }
        return total;
    }

    /**
     * The fixed version: one query, same answer.
     *
     * <p>The fetch join tells Hibernate to bring the competencies along with the
     * courses, so the loop below touches data already in memory.
     */
    @Transactional(readOnly = true)
    public int countCompetenciesFetched() {
        List<Course> courses = courseRepository.findAllWithCompetencies();   // query 1, and only 1

        int total = 0;
        for (Course course : courses) {
            total += course.getCompetencies().size();
        }
        return total;
    }
}