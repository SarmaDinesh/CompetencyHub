package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.common.exception.BusinessRuleException;
import com.example.CompetencyHub.service.EnrollmentService;
import com.example.CompetencyHub.web.EnrollmentController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer only. The service is a mock, so these tests are about HTTP: which status code,
 * which error shape, and whether the request was stopped before reaching the service.
 */
@WebMvcTest(EnrollmentController.class)
class EnrollmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private EnrollmentService enrollmentService;

    // ---- Bug 3: missing @Valid ----------------------------------------------

    @Test
    void enrollWithoutStudentIdIs400NotA500() throws Exception {
        mockMvc.perform(post("/api/courses/1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.studentId").exists());

        // The point of @Valid: bad input is turned away at the door. If this fails, the
        // request reached the service, which means validation is not running.
        verifyNoInteractions(enrollmentService);
    }

    @Test
    void enrollWithExplicitNullStudentIdIs400() throws Exception {
        mockMvc.perform(post("/api/courses/1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "studentId": null }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.studentId").exists());

        verifyNoInteractions(enrollmentService);
    }

    // ---- Bug 1, as the client sees it ---------------------------------------

    @Test
    void withdrawingAnAlreadyWithdrawnEnrollmentIs409() throws Exception {
        doThrow(new BusinessRuleException("Enrollment 5 cannot be withdrawn because it is WITHDRAWN"))
                .when(enrollmentService).withdraw(5L);

        // 409, not 204. A second DELETE that "succeeds" tells the client nothing went wrong,
        // which is exactly how the extra seat went unnoticed.
        mockMvc.perform(delete("/api/enrollments/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Enrollment 5 cannot be withdrawn because it is WITHDRAWN"));
    }

    @Test
    void withdrawingAnActiveEnrollmentIs204() throws Exception {
        mockMvc.perform(delete("/api/enrollments/5"))
                .andExpect(status().isNoContent());

        verify(enrollmentService).withdraw(5L);
    }
}
