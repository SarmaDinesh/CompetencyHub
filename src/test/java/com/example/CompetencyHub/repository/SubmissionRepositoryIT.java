package com.example.CompetencyHub.repository;

import com.example.CompetencyHub.TestcontainersConfig;
import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfig.class)
/*
 * @DataJpaTest swaps in an embedded database by default. Without this line it quietly
 * ignores the container and runs against H2 -- and the tests still pass, so you never find
 * out you were testing a database you do not deploy.
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubmissionRepositoryIT {

    @Autowired private TestEntityManager entityManager;
    @Autowired private SubmissionRepository submissionRepository;

    @Test
    void countsEachCompetencyOnceDespiteMultiplePassingSubmissions() {
        Student student = entityManager.persist(student());

        /*
         * Competencies are attached through Course.addCompetency(), not by passing the
         * course into the Competency constructor. That method sets both sides of the
         * relationship; constructing the child alone leaves Course.competencies stale in
         * the persistence context, and a later read of the parent would not see it.
         *
         * Course cascades ALL to competencies, so persisting the course persists them too.
         */
        Course course = course();
        Competency competency = new Competency("Transactions", 1, 1);
        course.addCompetency(competency);
        entityManager.persist(course);

        Assessment quiz1 = entityManager.persist(objectiveAssessment(competency, "Quiz 1"));
        Assessment quiz2 = entityManager.persist(objectiveAssessment(competency, "Quiz 2"));

        entityManager.persist(gradedSubmission(student, quiz1, 95));
        entityManager.persist(gradedSubmission(student, quiz2, 88));
        entityManager.flush();

        /*
         * Two passing submissions, ONE competency. This is the DISTINCT that stops
         * percent_complete exceeding 100 -- pinned by a test now instead of by a comment
         * that the next person can delete.
         */
        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), course.getId()))
                .isEqualTo(1);
    }

    @Test
    void submissionsBelowTheMinimumScoreDoNotCount() {
        Student student = entityManager.persist(student("Alan", "Turing", "alan@example.com"));

        Course course = course("CS545", "Web Applications", 30);
        Competency competency = new Competency("HTTP", 1, 1);
        course.addCompetency(competency);
        entityManager.persist(course);

        Assessment quiz = entityManager.persist(objectiveAssessment(competency, "Quiz"));

        entityManager.persist(gradedSubmission(student, quiz, 50));   // below the 70 minimum
        entityManager.flush();

        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), course.getId()))
                .isZero();
    }

    @Test
    void submissionsInOtherCoursesAreNotCounted() {
        Student student = entityManager.persist(student("Grace", "Hopper", "grace@example.com"));

        Course enrolled = entityManager.persist(course());

        Course other = course("CS572", "Machine Learning", 30);
        Competency otherCompetency = new Competency("Gradient Descent", 1, 1);
        other.addCompetency(otherCompetency);
        entityManager.persist(other);

        Assessment otherQuiz = entityManager.persist(
                objectiveAssessment(otherCompetency, "Quiz"));
        entityManager.persist(gradedSubmission(student, otherQuiz, 100));
        entityManager.flush();

        // The course filter. Without it, mastering anything anywhere would inflate every
        // course's progress -- a bug that is invisible with one course in the database.
        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), enrolled.getId()))
                .isZero();
    }

    // ---- V9: performance tasks count, ungraded attempts do not ------------------

    @Test
    void aPassingGradeOnAPerformanceTaskCountsTowardMastery() {
        Student student = entityManager.persist(student("Katherine", "Johnson", "kj@example.com"));

        Course course = course("CS550", "Orbital Mechanics", 30);
        Competency competency = new Competency("Trajectories", 1, 1);
        course.addCompetency(competency);
        entityManager.persist(course);

        Assessment essay = entityManager.persist(performanceAssessment(competency, "Report"));
        entityManager.persist(gradedSubmission(student, essay, 75));   // pass mark is 60
        entityManager.flush();

        // Before V9 this was 0: the query joined objective_assessment, so essays never counted.
        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), course.getId()))
                .isEqualTo(1);
    }

    @Test
    void anUngradedSubmissionDoesNotCount() {
        Student student = entityManager.persist(student("Mary", "Jackson", "mj@example.com"));

        Course course = course("CS551", "Aerodynamics", 30);
        Competency competency = new Competency("Lift", 1, 1);
        course.addCompetency(competency);
        entityManager.persist(course);

        Assessment essay = entityManager.persist(performanceAssessment(competency, "Wind tunnel"));
        entityManager.persist(new Submission(student, essay, 1, "My analysis..."));   // SUBMITTED, no score
        entityManager.flush();

        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), course.getId()))
                .isZero();
    }

    @Test
    void attemptsAreCountedPerStudentAndAssessment() {
        Student student = entityManager.persist(student("Dorothy", "Vaughan", "dv@example.com"));

        Course course = course("CS552", "Fortran", 30);
        Competency competency = new Competency("Loops", 1, 1);
        course.addCompetency(competency);
        entityManager.persist(course);

        Assessment quiz = entityManager.persist(objectiveAssessment(competency, "Quiz"));
        entityManager.persist(gradedSubmission(student, quiz, 1, 40));
        entityManager.persist(gradedSubmission(student, quiz, 2, 80));
        entityManager.flush();

        // The service numbers the next attempt as count + 1.
        assertThat(submissionRepository.countByStudentIdAndAssessmentId(student.getId(), quiz.getId()))
                .isEqualTo(2);
    }
}
