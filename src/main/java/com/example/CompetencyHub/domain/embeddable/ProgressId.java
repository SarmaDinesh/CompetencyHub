package com.example.CompetencyHub.domain.embeddable;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProgressId implements Serializable {

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "course_id")
    private Long courseId;

    protected ProgressId() { }

    public ProgressId(Long studentId, Long courseId) {
        this.studentId = studentId;
        this.courseId = courseId;
    }

    public Long getStudentId() { return studentId; }
    public Long getCourseId() { return courseId; }

    // equals/hashCode are mandatory on a composite key class. JPA uses them to decide whether
    // two rows are the same entity; without them the persistence context treats every lookup
    // as a new object and you get duplicate inserts instead of updates.
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgressId that)) return false;
        return Objects.equals(studentId, that.studentId) && Objects.equals(courseId, that.courseId);
    }
    @Override public int hashCode() { return Objects.hash(studentId, courseId); }
}
