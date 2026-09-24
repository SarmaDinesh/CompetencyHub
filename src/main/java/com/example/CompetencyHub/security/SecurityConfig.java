package com.example.CompetencyHub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Two layers, on purpose:
 *
 * <ol>
 *   <li><b>URL rules here</b> (coarse): which paths are public, which need any login, and a
 *       few whole areas that are admin-only. Checked by a filter, before any controller.</li>
 *   <li><b>{@code @PreAuthorize} on each controller method</b> (fine): the exact role, and
 *       ownership -- "a student may read THEIR OWN submissions". Only the method knows its
 *       arguments, so only the method can check that the studentId in the URL is yours.</li>
 * </ol>
 *
 * <p><b>Old vs new.</b> Tutorials before 2022 extend {@code WebSecurityConfigurerAdapter} and
 * override {@code configure(HttpSecurity)}, using {@code antMatchers(...)} and
 * {@code .and()} chaining, with {@code @EnableGlobalMethodSecurity(prePostEnabled = true)}.
 * The adapter was deprecated in Spring Security 5.7 and removed in 6; antMatchers and
 * {@code .and()} are gone in 7. Today it is a {@code SecurityFilterChain} bean, the lambda DSL
 * below, {@code requestMatchers}, and {@code @EnableMethodSecurity} (pre/post on by default).
 *
 * <p>And the JWT part: older code writes a {@code JwtAuthenticationFilter extends
 * OncePerRequestFilter} that parses the header with jjwt and sets the SecurityContext by
 * hand. {@code oauth2ResourceServer().jwt()} is that filter, maintained by Spring, with the
 * edge cases (clock skew, algorithm confusion, WWW-Authenticate) already handled.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           ProblemDetailAuthHandlers problemHandlers) throws Exception {
        http
                /*
                 * CSRF protection off -- and why that is safe HERE. CSRF works by tricking a
                 * browser into sending a request with cookies it holds automatically. This API
                 * has no session cookie: the token travels in the Authorization header, which a
                 * browser never attaches on its own. No ambient credential, nothing to forge.
                 * (If you ever store the JWT in a cookie, turn this back on.)
                 */
                .csrf(csrf -> csrf.disable())

                // No HttpSession. Every request proves itself with its token; nothing is
                // remembered between requests, so any instance can serve any request.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // First match wins, so specific rules come before general ones.
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(PUBLIC_DOCS).permitAll()
                        // Liveness/readiness probes and the Prometheus scrape come from
                        // infrastructure with no token. In production they belong on a separate
                        // management port that is not exposed publicly (see application.properties).
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**", "/api/diagnostics/**").hasRole("ADMIN")
                        // Everything else: any valid token. Method rules narrow it further.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers));

        return http.build();
    }

    /**
     * Turns the token's "roles" claim into Spring authorities.
     *
     * <p>By default the resource server reads the OAuth2 "scope" claim and prefixes SCOPE_ --
     * right for tokens from an external authorization server granting scopes like
     * "courses:read". We issue role-based tokens, so read "roles" and prefix ROLE_, which is
     * what hasRole('ADMIN') looks for. Get this wrong and every hasRole check fails with a 403
     * even for a perfectly valid admin token -- the most common resource-server setup bug.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Delegating encoder: stores "{bcrypt}$2a$10$..." -- the algorithm name travels WITH the
     * hash. When bcrypt is someday replaced, old hashes still verify (their prefix says
     * bcrypt) while new ones use the new algorithm; no mass password reset.
     *
     * <p>Old vs new: tutorials often use {@code new BCryptPasswordEncoder()} directly (works,
     * but locks you to one algorithm) or, worse, {@code NoOpPasswordEncoder} (plain text).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
