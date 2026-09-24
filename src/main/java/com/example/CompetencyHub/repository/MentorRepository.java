package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
}
