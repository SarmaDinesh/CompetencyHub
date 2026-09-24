package com.example.CompetencyHub.security;

import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Ownership rules, called from @PreAuthorize expressions as {@code @access.xxx(...)}.
 *
 * <p>Roles answer "what kind of user are you?". Ownership answers "is this YOURS?" -- and
 * roles alone cannot: every student has ROLE_STUDENT, but student 7 must not read student 8's
 * submissions. Checking that needs the resource's owner, which is in the database.
 *
 * <p>A named bean keeps the expressions short and readable on the controller:
 * <pre>{@code @PreAuthorize("hasRole('ADMIN') or @access.ownsSubmission(#id)")}</pre>
 * and puts the lookups in one class that can be unit tested, instead of SpEL strings
 * scattered across controllers doing repository calls inline.
 *
 * <p><b>404 or 403 for someone else's resource?</b> These methods return false when the
 * resource does not exist, so a student probing ids gets 403 for "not yours" and 403 for
 * "does not exist" alike -- the response does not reveal which ids are real. Admins bypass
 * these checks and get the honest 404.
 */
@Component("access")
public class AccessRules {

    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;

    public AccessRules(EnrollmentRepository enrollmentRepository, SubmissionRepository submissionRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
    }

    /** The caller is this student. Token-only: no database access. */
    public boolean isStudent(Long studentId) {
        return studentId != null && CurrentUser.studentId().map(studentId::equals).orElse(false);
    }

    /** The caller is this mentor. */
    public boolean isMentor(Long mentorId) {
        return mentorId != null && CurrentUser.mentorId().map(mentorId::equals).orElse(false);
    }

    /** The caller's login email is this address. */
    public boolean isEmail(String email) {
        return email != null && CurrentUser.email().map(email::equalsIgnoreCase).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean ownsEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .map(e -> isStudent(e.getStudent().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean ownsSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .map(s -> isStudent(s.getStudent().getId()))
                .orElse(false);
    }
}
