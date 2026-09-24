package com.example.CompetencyHub;

import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.Assessment;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.domain.model.Submission;

/**
 * Entity construction in one place.
 *
 * The problem this solves: a constructor signature change should break ONE file, not every
 * test method that happened to need a student. Tests also read better when the setup line
 * says "a student" rather than spelling out four arguments the test does not care about.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    // ---- Student ---------------------------------------------------------

    public static Student student() {
        return student("Ada", "Lovelace", "ada@example.com");
    }

    public static Student student(String firstName, String lastName, String email) {
        return new Student(firstName, lastName, email, address());
    }

    /**
     * Realistic values, not empty strings. Blanks pass today and break every test the day
     * someone adds @NotBlank to Address, for a reason unrelated to what those tests assert.
     */
    public static Address address() {
        return new Address("123 Terrace Dr", "Irving", "TX", "75061");
    }

    // ---- Course ----------------------------------------------------------

    public static Course course() {
        return course("CS544", "Enterprise Architecture", 30);
    }

    public static Course course(String code, String title, int capacity) {
        return new Course(code, title, title + " description", capacity);
    }

    /** One seat, for the concurrency test. */
    public static Course singleSeatCourse(String code) {
        return course(code, "One Seat Only", 1);
    }

    /** Standard objective quiz: scores 0-100, 20 questions, pass at 70. */
    public static ObjectiveAssessment objectiveAssessment(Competency competency, String title) {
        return objectiveAssessment(competency, title, 70);
    }

    public static ObjectiveAssessment objectiveAssessment(
            Competency competency, String title, int passingScore) {
        return new ObjectiveAssessment(
                competency, title, new ScoreRange(0, 100), 20, passingScore);
    }

    /** Essay: scores 0-100, pass at 60, no rubric link, no word limit. */
    public static PerformanceAssessment performanceAssessment(Competency competency, String title) {
        return new PerformanceAssessment(competency, title, new ScoreRange(0, 100), 60, null, null);
    }

    // ---- Mentor / Submission ------------------------------------------------

    public static Mentor mentor() {
        return mentor("grace@example.com");
    }

    public static Mentor mentor(String email) {
        return new Mentor("Grace", "Hopper", email, "Compilers");
    }

    /**
     * A submission already graded with this score. Goes through the real grade() transition,
     * same reason savedCourse() in CourseControllerTest calls reserveSeat(): a fixture should
     * not be able to build a state the domain would refuse.
     */
    public static Submission gradedSubmission(Student student, Assessment assessment, int score) {
        Submission submission = new Submission(student, assessment, 1, null);
        submission.grade(score, null, null);
        return submission;
    }

    public static Submission gradedSubmission(Student student, Assessment assessment,
                                              int attemptNumber, int score) {
        Submission submission = new Submission(student, assessment, attemptNumber, null);
        submission.grade(score, null, null);
        return submission;
    }
}
