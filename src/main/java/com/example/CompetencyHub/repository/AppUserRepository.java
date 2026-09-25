package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    // Spring Data derives the SQL from the name: SELECT EXISTS(... WHERE role = ?)
    boolean existsByRole(Role role);
}
