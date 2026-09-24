package com.example.CompetencyHub.security;

import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bridge between our AppUser table and Spring Security's login machinery. Used ONLY at
 * login (POST /api/auth/login). After that, requests carry a JWT and never touch this class
 * -- which is the whole point of a stateless token.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                // Spring turns this into the same BadCredentialsException as a wrong password
                // (hideUserNotFoundExceptions defaults to true), so the response never reveals
                // whether an email is registered. Attackers learn nothing from "wrong email".
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())   // adds the ROLE_ prefix itself
                .disabled(!user.isEnabled())
                .build();
    }
}
