package com.example.CompetencyHub.web;

import com.example.CompetencyHub.service.AuthService;
import com.example.CompetencyHub.web.dto.request.LoginRequest;
import com.example.CompetencyHub.web.dto.request.RegisterRequest;
import com.example.CompetencyHub.web.dto.response.AuthResponse;
import com.example.CompetencyHub.web.dto.response.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * @SecurityRequirements with no arguments = "this operation needs no token" in the OpenAPI
     * document, overriding the global bearer requirement. Documentation only: the actual
     * permitAll() is in SecurityConfig. OpenApiDocsIT checks the two agree.
     */
    @SecurityRequirements
    @Operation(summary = "Register as a student", description = "Creates a STUDENT account and returns a token.")
    @ApiResponse(responseCode = "201", description = "Registered and logged in")
    @ApiResponse(responseCode = "400", description = "Invalid body, e.g. password shorter than 8")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(authService.register(
                request.firstName(), request.lastName(), request.email(), request.password()));
    }

    @SecurityRequirements
    @Operation(summary = "Log in", description = "Exchange email and password for a bearer token.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "400", description = "email or password missing")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.email(), request.password()));
    }

    /**
     * {@code @AuthenticationPrincipal Jwt} injects the already-verified token for this request.
     * No database call: everything shown here was signed into the token at login.
     */
    @Operation(summary = "Who am I", description = "Reads the caller's identity from their token.")
    @ApiResponse(responseCode = "200", description = "The caller")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsStringList("roles"),
                asLong(jwt.getClaims().get("uid")),
                asLong(jwt.getClaims().get("studentId")),
                asLong(jwt.getClaims().get("mentorId")),
                jwt.getExpiresAt());
    }

    private static AuthResponse toResponse(AuthService.AuthResult r) {
        return new AuthResponse(r.token(), "Bearer", r.expiresInSeconds(), r.email(),
                r.role().name(), r.studentId(), r.mentorId());
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
