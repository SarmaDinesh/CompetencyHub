package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);

    Optional<Mentor> findByUserId(Long userId);

    Optional<Mentor> findByEmail(String email);
}
