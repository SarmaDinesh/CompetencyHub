package com.example.CompetencyHub.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link Progress}: the (student, course) pair.
 *
 * <p>No surrogate id, because the pair already identifies the row and a student can
 * have at most one progress record per course. Adding a generated id would let
 * duplicates exist and require a separate unique constraint to prevent them.
 *
 * <p>Three requirements, all mandatory for a JPA composite key class:
 * <ol>
 *   <li>{@code implements Serializable} — required by the JPA specification</li>
 *   <li>{@code equals} and {@code hashCode} — see below</li>
 *   <li>a no-arg constructor</li>
 * </ol>
 *
 * <p><b>Why equals/hashCode are not optional here.</b> Hibernate keeps its
 * persistence context as a map keyed by entity identity. With a plain Long id that
 * works automatically. With a composite key, <i>this class</i> is the map key — so
 * without value-based equality, two ProgressId(1L, 5L) instances count as different
 * keys, the same database row loads twice as two separate objects, and their updates
 * overwrite each other. No exception is thrown; the data is just wrong.
 */
@Embeddable
public class ProgressId implements Serializable {

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "course_id")
    private Long courseId;

    /** Required by JPA. */
    protected ProgressId() {
    }

    public ProgressId(Long studentId, Long courseId) {
        this.studentId = studentId;
        this.courseId = courseId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgressId other)) return false;
        // Objects.equals handles nulls — an id can be null before the referenced
        // entity has been persisted.
        return Objects.equals(studentId, other.studentId)
                && Objects.equals(courseId, other.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, courseId);
    }
}