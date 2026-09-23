package com.example.CompetencyHub;

import com.example.CompetencyHub.domain.embeddable.Address;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.Student;

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
}
