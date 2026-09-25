package com.example.CompetencyHub.controller;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.security.WebSecurityTestConfig;
import com.example.CompetencyHub.service.AuthService;
import com.example.CompetencyHub.web.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static com.example.CompetencyHub.security.SecurityTestSupport.asStudent;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthService authService;

    @Test
    void registerNeedsNoTokenAndReturns201() throws Exception {
        when(authService.register("Ada", "Lovelace", "ada@example.com", "correct-horse"))
                .thenReturn(new AuthService.AuthResult("tkn", Instant.now(), 3600,
                        "ada@example.com", Role.STUDENT, 7L, null));

        // No .with(...): an anonymous caller, as a new user always is.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Ada", "lastName": "Lovelace",
                                  "email": "ada@example.com", "password": "correct-horse" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("tkn"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.studentId").value(7));
    }

    @Test
    void aShortPasswordIs400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Ada", "lastName": "Lovelace",
                                  "email": "ada@example.com", "password": "short" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void badCredentialsAre401WithAGenericMessage() throws Exception {
        when(authService.login("ada@example.com", "wrong"))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ada@example.com", "password": "wrong" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void meNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(asStudent(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
    }
}
