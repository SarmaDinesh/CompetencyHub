package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.model.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {
}
