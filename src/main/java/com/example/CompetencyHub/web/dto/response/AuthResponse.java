package com.example.CompetencyHub.web.dto.response;

/**
 * Returned by register and login. Field names follow the OAuth2 token response (RFC 6749
 * section 5.1: access_token, token_type, expires_in) in camelCase, plus who you are, so a
 * client does not need to decode the token just to know its own studentId.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String email,
        String role,
        Long studentId,
        Long mentorId
) {
}
