package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
}
