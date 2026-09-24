package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.model.Competency;
import com.example.CompetencyHub.domain.model.Course;
import com.example.CompetencyHub.service.CompetencyService;
import com.example.CompetencyHub.web.CompetencyController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.example.CompetencyHub.TestFixtures.course;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompetencyController.class)
class CompetencyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CompetencyService competencyService;

    private Competency savedCompetency(Long id, String title, int orderIndex) {
        Course course = course();
        ReflectionTestUtils.setField(course, "id", 1L);
        Competency competency = new Competency(title, 25, orderIndex);
        course.addCompetency(competency);
        ReflectionTestUtils.setField(competency, "id", id);
        return competency;
    }

    @Test
    void createReturns201WithLocationAndCourseId() throws Exception {
        when(competencyService.create(1L, "Transactions", 25, null))
                .thenReturn(savedCompetency(7L, "Transactions", 1));

        mockMvc.perform(post("/api/courses/1/competencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Transactions", "weight": 25 }
                                """))
                .andExpect(status().isCreated())
                // Flat URL for the new resource, not nested under the course.
                .andExpect(header().string("Location", containsString("/api/competencies/7")))
                .andExpect(jsonPath("$.courseId").value(1))
                .andExpect(jsonPath("$.orderIndex").value(1));
    }

    @Test
    void weightOutOfRangeIs400() throws Exception {
        mockMvc.perform(post("/api/courses/1/competencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Transactions", "weight": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weight").exists());

        verifyNoInteractions(competencyService);
    }

    @Test
    void listReturnsCompetenciesInOrder() throws Exception {
        when(competencyService.findByCourse(1L)).thenReturn(List.of(
                savedCompetency(7L, "Transactions", 1),
                savedCompetency(8L, "Messaging", 2)));

        mockMvc.perform(get("/api/courses/1/competencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Transactions"))
                .andExpect(jsonPath("$[1].title").value("Messaging"));
    }

    @Test
    void listForUnknownCourseIs404() throws Exception {
        when(competencyService.findByCourse(99L)).thenThrow(new NotFoundException("Course not found: 99"));

        mockMvc.perform(get("/api/courses/99/competencies"))
                .andExpect(status().isNotFound());
    }

    @Test
    void putRequiresEveryField() throws Exception {
        // PUT replaces the resource, so orderIndex is required here even though it is
        // optional on create.
        mockMvc.perform(put("/api/competencies/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Transactions", "weight": 25 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderIndex").exists());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/competencies/7"))
                .andExpect(status().isNoContent());

        verify(competencyService).delete(7L);
    }
}
