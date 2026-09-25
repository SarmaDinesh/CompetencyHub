package com.example.CompetencyHub.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * 401 and 403 bodies in the same RFC 9457 shape as every other error.
 *
 * <p><b>Why this is not in GlobalExceptionHandler.</b> @RestControllerAdvice only sees
 * exceptions thrown from controllers. A missing or invalid token is rejected by a SERVLET
 * FILTER, before any controller is chosen -- the advice never hears about it. Without this
 * class, Spring Security answers with an empty body, and a client that parses problem+json
 * for every other error gets nothing here.
 *
 * <p>Picture the request passing a security desk before it reaches the offices (controllers).
 * The advice is the offices' receptionist; the desk needs its own way of saying no.
 *
 * <p>Delegates to Spring's bearer-token handlers first, so the standard
 * {@code WWW-Authenticate: Bearer error="invalid_token", ...} header is still set -- clients
 * and API gateways read it -- then writes the body.
 */
@Component
public class ProblemDetailAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();

    /** 401: no token, or a token that failed verification (bad signature, expired, wrong issuer). */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        write(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                "Authentication required: send a valid bearer token", request);
    }

    /** 403: the token is fine, the role is not enough. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        bearerDeniedHandler.handle(request, response, accessDeniedException);
        write(response, HttpStatus.FORBIDDEN, "forbidden",
                "You do not have permission to perform this action", request);
    }

    /*
     * Hand-written JSON. Five fixed fields, two of them constants: pulling in the ObjectMapper
     * for this would add a dependency to save four lines. The only request-derived value is the
     * path, which is escaped.
     */
    private static void write(HttpServletResponse response, HttpStatus status, String type,
                              String detail, HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"https://competencyhub.example/errors/%s","title":"%s","status":%d,\
                "detail":"%s","instance":"%s","timestamp":"%s"}"""
                .formatted(type, status.getReasonPhrase(), status.value(), detail,
                        escape(request.getRequestURI()), Instant.now()));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
