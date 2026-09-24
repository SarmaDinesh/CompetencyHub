package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    /** The student profile behind a login -- put into the JWT at login time. */
    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByEmail(String email);

    boolean existsByEmail(String email);
}
