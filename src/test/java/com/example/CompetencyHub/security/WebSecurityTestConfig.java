package com.example.CompetencyHub.security;

import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

/**
 * Brings the REAL security rules into a @WebMvcTest.
 *
 * <p>@WebMvcTest scans controllers and advice, not @Configuration classes -- so without this,
 * the slice would run with Spring Boot's DEFAULT security (everything needs HTTP Basic, CSRF
 * on) and the tests would be checking rules the application does not have.
 *
 * <p>The JwtDecoder is a mock because it is never called: MockMvc's jwt() post-processor puts
 * an already-authenticated token straight into the security context, skipping decoding. It
 * only has to exist so the resource-server configuration can start. Same for the two
 * repositories AccessRules needs -- tests that exercise ownership stub them.
 */
@TestConfiguration
@Import({SecurityConfig.class, ProblemDetailAuthHandlers.class, AccessRules.class})
public class WebSecurityTestConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    EnrollmentRepository enrollmentRepository() {
        return mock(EnrollmentRepository.class);
    }

    @Bean
    SubmissionRepository submissionRepository() {
        return mock(SubmissionRepository.class);
    }
}
