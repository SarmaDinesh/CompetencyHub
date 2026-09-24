package com.example.CompetencyHub.domain.enums;

/**
 * What a logged-in account may do. Stored without the "ROLE_" prefix; Spring Security adds
 * it when turning the JWT's roles claim into authorities, which is what hasRole('ADMIN')
 * checks for.
 */
public enum Role {
    ADMIN, MENTOR, STUDENT
}
