package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.common.exception.DuplicateEmailException;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.service.MentorService;
import com.example.CompetencyHub.web.MentorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MentorController.class)
class MentorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MentorService mentorService;

    @Test
    void createReturns201() throws Exception {
        Mentor mentor = new Mentor("Grace", "Hopper", "grace@example.com", "Compilers");
        ReflectionTestUtils.setField(mentor, "id", 3L);
        when(mentorService.create("Grace", "Hopper", "grace@example.com", "Compilers")).thenReturn(mentor);

        mockMvc.perform(post("/api/mentors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Grace", "lastName": "Hopper",
                                  "email": "grace@example.com", "specialization": "Compilers" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/mentors/3")));
    }

    @Test
    void invalidEmailIs400() throws Exception {
        mockMvc.perform(post("/api/mentors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Grace", "lastName": "Hopper", "email": "not-an-email" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        verifyNoInteractions(mentorService);
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(mentorService.create(any(), any(), eq("grace@example.com"), any()))
                .thenThrow(new DuplicateEmailException("A mentor with email grace@example.com already exists"));

        mockMvc.perform(post("/api/mentors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Grace", "lastName": "Hopper", "email": "grace@example.com" }
                                """))
                .andExpect(status().isConflict());
    }
}
