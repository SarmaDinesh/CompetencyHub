package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.Submission;

import java.util.List;

public interface SubmissionService {

    /**
     * Records an attempt. An objective test must come with its score and is graded at once;
     * a performance task must come with content and no score, and waits for a mentor.
     */
    Submission submit(Long assessmentId, Long studentId, Integer score, String content);

    Submission findById(Long id);

    List<Submission> findByStudent(Long studentId);

    /** {@code status} null means every submission; SUBMITTED gives the grading queue. */
    List<Submission> findByAssessment(Long assessmentId, SubmissionStatus status);

    Submission grade(Long submissionId, Long mentorId, int score, String feedback);
}
