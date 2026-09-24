package com.example.CompetencyHub.web.dto.response;

import java.time.Instant;
import java.util.List;

/** The caller, as the token describes them. */
public record MeResponse(String email, List<String> roles, Long userId, Long studentId,
                         Long mentorId, Instant tokenExpiresAt) {
}
