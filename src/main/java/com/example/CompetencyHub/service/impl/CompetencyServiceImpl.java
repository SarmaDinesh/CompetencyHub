package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.repository.CompetencyRepository;
import com.example.CompetencyHub.repository.CourseRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import com.example.CompetencyHub.service.CompetencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompetencyServiceImpl implements CompetencyService {

    private final CompetencyRepository competencyRepository;
    private final CourseRepository courseRepository;
    private final SubmissionRepository submissionRepository;

    public CompetencyServiceImpl(CompetencyRepository competencyRepository,
                                 CourseRepository courseRepository,
                                 SubmissionRepository submissionRepository) {
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competency> findByCourse(Long courseId) {
        // Without this check, an unknown course returns 200 with an empty list, which reads
        // as "this course has no competencies yet". 404 tells the client the truth.
        if (!courseRepository.existsById(courseId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return competencyRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Competency findById(Long id) {
        return competencyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Competency not found: " + id));
    }

    @Override
    @Transactional
    public Competency create(Long courseId, String title, int weight, Integer orderIndex) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        int position = (orderIndex != null)
                ? orderIndex
                : competencyRepository.findMaxOrderIndexByCourseId(courseId) + 1;

        Competency competency = new Competency(title, weight, position);

        // addCompetency() sets BOTH sides of the relationship: the course's list and the
        // competency's course field. Setting only one side is the classic bidirectional
        // mapping bug -- the row saves correctly, but the in-memory Course still shows the
        // old list for the rest of the transaction.
        course.addCompetency(competency);

        // Cascade would insert it at commit anyway. Saving now makes the INSERT happen
        // immediately, so the returned object already has its generated id for the
        // Location header.
        return competencyRepository.save(competency);
    }

    @Override
    @Transactional
    public Competency update(Long id, String title, int weight, int orderIndex) {
        Competency competency = findById(id);
        competency.updateDetails(title, weight, orderIndex);
        // No save(): managed entity, dirty checking writes the UPDATE at commit.
        return competency;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Competency competency = findById(id);

        // Deleting a competency cascades to its assessments in the database (V3: ON DELETE
        // CASCADE). But submission -> assessment has NO cascade, on purpose: a student's
        // graded work is not something a tidy-up should erase. So Postgres would refuse the
        // delete with a foreign-key violation, which surfaces as a 500. Checking first turns
        // it into a 409 that says why.
        if (submissionRepository.existsByAssessmentCompetencyId(id)) {
            throw new BusinessRuleException(
                    "Competency " + id + " has student submissions and cannot be deleted");
        }

        // Through the aggregate root, the mirror image of create(). orphanRemoval on
        // Course.competencies turns "removed from the list" into a DELETE.
        competency.getCourse().removeCompetency(competency);
    }
}
