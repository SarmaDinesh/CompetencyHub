package com.example.CompetencyHub.security;

import com.example.CompetencyHub.domain.model.Submission;
import com.example.CompetencyHub.repository.EnrollmentRepository;
import com.example.CompetencyHub.repository.SubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static com.example.CompetencyHub.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRulesTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private SubmissionRepository submissionRepository;
    @InjectMocks private AccessRules access;

    @AfterEach
    void clearContext() {
        // The context is thread-bound; leaving it set would leak this identity into the next
        // test on the same thread.
        SecurityContextHolder.clearContext();
    }

    @Test
    void isStudentComparesWithTheTokensStudentId() {
        loginAsStudent(7L);

        assertThat(access.isStudent(7L)).isTrue();
        assertThat(access.isStudent(8L)).isFalse();
        assertThat(access.isStudent(null)).isFalse();
    }

    @Test
    void aTokenWithoutAStudentIdIsNeverAStudent() {
        SecurityContextHolder.getContext().setAuthentication(token(Jwt.withTokenValue("t")
                .header("alg", "RS256").subject("admin@example.com").claim("roles", List.of("ADMIN")).build()));

        assertThat(access.isStudent(7L)).isFalse();
    }

    @Test
    void ownsSubmissionChecksTheSubmissionsStudent() {
        loginAsStudent(7L);
        var student = student();
        ReflectionTestUtils.setField(student, "id", 7L);
        Submission mine = new Submission(student, objectiveAssessment(
                new com.example.CompetencyHub.domain.model.Competency("c", 1, 1), "q"), 1, null);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(submissionRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(access.ownsSubmission(1L)).isTrue();
        // Not found -> false (403), same as "someone else's": ids are not probeable.
        assertThat(access.ownsSubmission(2L)).isFalse();
    }

    @Test
    void emailMatchIsCaseInsensitive() {
        loginAsStudent(7L);   // subject student7@example.com
        assertThat(access.isEmail("Student7@Example.com")).isTrue();
        assertThat(access.isEmail("someone@example.com")).isFalse();
    }

    private static void loginAsStudent(long studentId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("student" + studentId + "@example.com")
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(token(jwt));
    }

    private static JwtAuthenticationToken token(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsStringList("roles").get(0))));
    }
}
