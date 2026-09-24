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

        entityManager.persist(new Submission(student, quiz1, 95));
        entityManager.persist(new Submission(student, quiz2, 88));
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

        entityManager.persist(new Submission(student, quiz, 50));   // below the 70 minimum
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
        entityManager.persist(new Submission(student, otherQuiz, 100));
        entityManager.flush();

        // The course filter. Without it, mastering anything anywhere would inflate every
        // course's progress -- a bug that is invisible with one course in the database.
        assertThat(submissionRepository.countMasteredCompetencies(student.getId(), enrolled.getId()))
                .isZero();
    }
}