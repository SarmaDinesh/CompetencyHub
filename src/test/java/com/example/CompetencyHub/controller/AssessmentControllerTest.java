package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.TestFixtures;
import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.domain.embeddable.ScoreRange;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.ObjectiveAssessment;
import com.example.CompetencyHub.domain.model.PerformanceAssessment;
import com.example.CompetencyHub.service.AssessmentService;
import com.example.CompetencyHub.web.AssessmentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.example.CompetencyHub.security.WebSecurityTestConfig;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static com.example.CompetencyHub.security.SecurityTestSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Most of these tests are about the "type" field: that Jackson picks the right record,
 * that the right service method is called, and that a bad or missing type is a 400 rather
 * than a 500 or, worse, a silently wrong assessment.
 */
@WebMvcTest(AssessmentController.class)
@Import(WebSecurityTestConfig.class)
class AssessmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AssessmentService assessmentService;

    private Competency competency;

    @BeforeEach
    void setUp() {
        competency = new Competency("Transactions", 25, 1);
        ReflectionTestUtils.setField(competency, "id", 7L);
    }

    // ---- polymorphic request ------------------------------------------------

    @Test
    void objectiveTypeCallsCreateObjectiveAndEchoesTheType() throws Exception {
        ObjectiveAssessment saved = TestFixtures.objectiveAssessment(competency, "Quiz 1");
        ReflectionTestUtils.setField(saved, "id", 3L);
        when(assessmentService.createObjective(7L, "Quiz 1", 0, 100, 20, 70)).thenReturn(saved);

        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OBJECTIVE", "title": "Quiz 1",
                                  "minScore": 0, "maxScore": 100,
                                  "questionCount": 20, "passingScore": 70 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/assessments/3")))
                // The response carries "type" too, written by the same annotations that
                // read it on the way in.
                .andExpect(jsonPath("$.type").value("OBJECTIVE"))
                .andExpect(jsonPath("$.questionCount").value(20))
                .andExpect(jsonPath("$.competencyId").value(7))
                // Subtype fields do not leak across: an objective test has no rubric.
                .andExpect(jsonPath("$.rubricUrl").doesNotExist());

        verify(assessmentService, never()).createPerformance(any(), any(), anyInt(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void performanceTypeCallsCreatePerformance() throws Exception {
        PerformanceAssessment saved = new PerformanceAssessment(
                competency, "Essay", new ScoreRange(0, 100), 60, "https://example.com/rubric", 1500);
        ReflectionTestUtils.setField(saved, "id", 4L);
        when(assessmentService.createPerformance(7L, "Essay", 0, 100, 60, "https://example.com/rubric", 1500))
                .thenReturn(saved);

        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERFORMANCE", "title": "Essay",
                                  "minScore": 0, "maxScore": 100, "passingScore": 60,
                                  "rubricUrl": "https://example.com/rubric", "wordLimit": 1500 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PERFORMANCE"))
                .andExpect(jsonPath("$.wordLimit").value(1500))
                .andExpect(jsonPath("$.passingScore").value(60))
                .andExpect(jsonPath("$.questionCount").doesNotExist());
    }

    @Test
    void missingTypeIs400() throws Exception {
        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Quiz 1", "minScore": 0, "maxScore": 100,
                                  "questionCount": 20, "passingScore": 70 }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void unknownTypeIs400() throws Exception {
        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "ORAL_EXAM", "title": "Viva", "minScore": 0, "maxScore": 100 }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(assessmentService);
    }

    // ---- validation of the chosen subtype -----------------------------------

    @Test
    void subtypeConstraintsRunEvenThoughTheParameterIsTheInterface() throws Exception {
        // questionCount is only declared on the OBJECTIVE record. If @Valid only looked at
        // the declared parameter type, this would slip through.
        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OBJECTIVE", "title": "Quiz 1",
                                  "minScore": 0, "maxScore": 100, "passingScore": 70 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.questionCount").exists());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void invertedScoreRangeIs400NotA500() throws Exception {
        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "PERFORMANCE", "title": "Essay",
                                  "minScore": 100, "maxScore": 0, "passingScore": 50 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.scoreRangeValid").exists());

        verifyNoInteractions(assessmentService);
    }

    @Test
    void passingScoreOutsideTheRangeIs400() throws Exception {
        mockMvc.perform(post("/api/competencies/7/assessments").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "OBJECTIVE", "title": "Quiz 1",
                                  "minScore": 0, "maxScore": 100,
                                  "questionCount": 20, "passingScore": 150 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passingScoreInRange").exists());
    }

    // ---- reads and delete ---------------------------------------------------

    @Test
    void listReturnsEachAssessmentWithItsOwnType() throws Exception {
        ObjectiveAssessment quiz = TestFixtures.objectiveAssessment(competency, "Quiz");
        PerformanceAssessment essay = new PerformanceAssessment(
                competency, "Essay", new ScoreRange(0, 100), 60, null, null);
        when(assessmentService.findByCompetency(7L)).thenReturn(List.of(quiz, essay));

        // A List<AssessmentResponse>: each element must still carry its own "type".
        mockMvc.perform(get("/api/competencies/7/assessments").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("OBJECTIVE"))
                .andExpect(jsonPath("$[1].type").value("PERFORMANCE"));
    }

    @Test
    void deletingAnAssessmentWithSubmissionsIs409() throws Exception {
        doThrow(new BusinessRuleException("Assessment 3 has student submissions and cannot be deleted"))
                .when(assessmentService).delete(3L);

        mockMvc.perform(delete("/api/assessments/3").with(asAdmin()))
                .andExpect(status().isConflict());
    }
}
