package com.example.CompetencyHub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The username/password check used by POST /api/auth/login -- and only there.
 *
 * <p>DaoAuthenticationProvider = "load the user through a UserDetailsService, then compare
 * the password with the PasswordEncoder". It does the comparison in constant time and hashes
 * a dummy password when the user does not exist, so a login for an unknown email takes as
 * long as a wrong password: response timing cannot be used to discover which emails exist.
 *
 * <p>Separate from SecurityConfig so a @WebMvcTest can import the filter chain without also
 * needing a UserDetailsService and a database.
 */
@Configuration
public class AuthenticationConfig {

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
