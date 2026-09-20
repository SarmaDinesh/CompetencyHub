package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.EnrollmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentAttemptRepository extends JpaRepository<EnrollmentAttempt, Long> {
    List<EnrollmentAttempt> findByCourseIdOrderByAttemptedAtDesc(Long courseId);
}
