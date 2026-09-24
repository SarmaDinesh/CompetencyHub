package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.common.exception.InvalidInputException;
import com.example.CompetencyHub.domain.enums.SubmissionStatus;
import com.example.CompetencyHub.domain.model.*;
import com.example.CompetencyHub.service.SubmissionService;
import com.example.CompetencyHub.web.SubmissionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SubmissionService submissionService;

    private Student student;
    private PerformanceAssessment essay;

    @BeforeEach
    void setUp() {
        student = student();
        ReflectionTestUtils.setField(student, "id", 1L);
        essay = performanceAssessment(new Competency("Transactions", 25, 1), "Essay");
        ReflectionTestUtils.setField(essay, "id", 200L);
    }

    @Test
    void submitReturns201WithLocationAndStatus() throws Exception {
        Submission queued = new Submission(student, essay, 1, "my answer");
        ReflectionTestUtils.setField(queued, "id", 9L);
        when(submissionService.submit(200L, 1L, null, "my answer")).thenReturn(queued);

        mockMvc.perform(post("/api/assessments/200/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "studentId": 1, "content": "my answer" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/submissions/9")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.assessmentId").value(200));
    }

    @Test
    void submitWithoutStudentIdIs400() throws Exception {
        mockMvc.perform(post("/api/assessments/200/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "my answer" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.studentId").exists());
        verifyNoInteractions(submissionService);
    }

    @Test
    void aServiceSideInputErrorIs400WithTheReason() throws Exception {
        when(submissionService.submit(anyLong(), anyLong(), any(), any()))
                .thenThrow(new InvalidInputException("A performance task is scored by a mentor; do not send a score"));

        mockMvc.perform(post("/api/assessments/200/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "studentId": 1, "score": 100, "content": "x" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("invalid-input")))
                .andExpect(jsonPath("$.detail", containsString("mentor")));
    }

    @Test
    void notEnrolledIs409() throws Exception {
        when(submissionService.submit(anyLong(), anyLong(), any(), any()))
                .thenThrow(new BusinessRuleException("Student 1 is not actively enrolled in course 10"));

        mockMvc.perform(post("/api/assessments/200/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "studentId": 1, "score": 80 }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void gradeReturnsTheGradedSubmission() throws Exception {
        Submission submission = new Submission(student, essay, 1, "my answer");
        ReflectionTestUtils.setField(submission, "id", 9L);
        Mentor mentor = mentor();
        ReflectionTestUtils.setField(mentor, "id", 3L);
        submission.grade(75, "Solid", mentor);
        when(submissionService.grade(9L, 3L, 75, "Solid")).thenReturn(submission);

        mockMvc.perform(post("/api/submissions/9/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "mentorId": 3, "score": 75, "feedback": "Solid" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.gradedByMentorId").value(3));
    }

    @Test
    void gradeWithoutScoreIs400() throws Exception {
        mockMvc.perform(post("/api/submissions/9/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "mentorId": 3 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.score").exists());
    }

    @Test
    void gradingTwiceIs409() throws Exception {
        when(submissionService.grade(9L, 3L, 90, null))
                .thenThrow(new BusinessRuleException("Submission 9 is already GRADED"));

        mockMvc.perform(post("/api/submissions/9/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "mentorId": 3, "score": 90 }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void theGradingQueueFiltersByStatus() throws Exception {
        when(submissionService.findByAssessment(200L, SubmissionStatus.SUBMITTED))
                .thenReturn(List.of(new Submission(student, essay, 1, "a")));

        mockMvc.perform(get("/api/assessments/200/submissions").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    void anUnknownStatusValueIs400NotA500() throws Exception {
        // Before this branch, a failed enum conversion fell through to the catch-all handler.
        mockMvc.perform(get("/api/assessments/200/submissions").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("invalid-parameter")));
        verifyNoInteractions(submissionService);
    }
}
