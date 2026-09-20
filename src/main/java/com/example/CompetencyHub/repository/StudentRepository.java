package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
}
