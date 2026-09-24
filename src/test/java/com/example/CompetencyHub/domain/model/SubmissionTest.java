package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import org.junit.jupiter.api.Test;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The state machine, with no Spring and no database. */
class SubmissionTest {

    private final Competency competency = new Competency("Transactions", 25, 1);
    private final Assessment essay = performanceAssessment(competency, "Essay");   // 0-100, pass 60

    @Test
    void aNewSubmissionIsSubmittedAndUnscored() {
        Submission submission = new Submission(student(), essay, 1, "My answer");

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getScore()).isNull();
        assertThat(submission.isPassing()).isFalse();
    }

    @Test
    void gradingRecordsScoreMentorAndTime() {
        Submission submission = new Submission(student(), essay, 1, "My answer");
        Mentor mentor = mentor();

        submission.grade(72, "Good structure", mentor);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(submission.getScore()).isEqualTo(72);
        assertThat(submission.getGradedBy()).isSameAs(mentor);
        assertThat(submission.getGradedAt()).isNotNull();
        assertThat(submission.isPassing()).isTrue();
    }

    @Test
    void aSubmissionCannotBeGradedTwice() {
        Submission submission = new Submission(student(), essay, 1, "My answer");
        submission.grade(72, null, mentor());

        assertThatThrownBy(() -> submission.grade(90, null, mentor()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(submission.getScore()).isEqualTo(72);   // unchanged
    }

    @Test
    void aScoreOutsideTheRangeIsRejectedAndNothingChanges() {
        Submission submission = new Submission(student(), essay, 1, "My answer");

        assertThatThrownBy(() -> submission.grade(101, null, mentor()))
                .isInstanceOf(InvalidInputException.class);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    void theBoundaryScoresAreAccepted() {
        Submission low = new Submission(student(), essay, 1, "a");
        Submission high = new Submission(student(), essay, 2, "b");

        low.grade(0, null, null);
        high.grade(100, null, null);

        assertThat(low.getScore()).isZero();
        assertThat(high.getScore()).isEqualTo(100);
    }

    @Test
    void passingIsAtOrAboveThePassMark() {
        Submission atMark = new Submission(student(), essay, 1, "a");
        Submission below = new Submission(student(), essay, 2, "b");

        atMark.grade(60, null, null);
        below.grade(59, null, null);

        assertThat(atMark.isPassing()).isTrue();
        assertThat(below.isPassing()).isFalse();
    }

    @Test
    void anAssessmentCannotHaveAnUnreachablePassMark() {
        assertThatThrownBy(() -> new PerformanceAssessment(
                competency, "Essay", new ScoreRange(0, 100), 150, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wordLimitCountsWordsNotCharacters() {
        PerformanceAssessment limited = new PerformanceAssessment(
                competency, "Short answer", new ScoreRange(0, 10), 6, null, 3);

        assertThat(limited.fitsWordLimit("one two three")).isTrue();
        assertThat(limited.fitsWordLimit("  one   two\nthree  ")).isTrue();
        assertThat(limited.fitsWordLimit("one two three four")).isFalse();
    }
}
