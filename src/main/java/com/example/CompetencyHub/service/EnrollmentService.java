package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Enrollment;

public interface EnrollmentService {

    Enrollment enroll(Long studentId, Long courseId);

    void withdraw(Long enrollmentId);
}