package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.DuplicateEmailException;
import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.domain.model.Student;
import com.example.CompetencyHub.repository.AppUserRepository;
import com.example.CompetencyHub.repository.MentorRepository;
import com.example.CompetencyHub.repository.StudentRepository;
import com.example.CompetencyHub.security.TokenService;
import com.example.CompetencyHub.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;
    private final MentorRepository mentorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthServiceImpl(AppUserRepository appUserRepository, StudentRepository studentRepository,
                           MentorRepository mentorRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
        this.mentorRepository = mentorRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /**
     * One transaction for both rows: a login without a student profile, or a profile without
     * a login, would be a half-registered account that can neither be used nor re-registered.
     */
    @Override
    @Transactional
    public AuthResult register(String firstName, String lastName, String email, String rawPassword) {
        // Also refuse an email that belongs to an existing student with no login yet. Linking
        // to it would be friendlier -- and would let anyone who types a classmate's email
        // take over their record, since nothing verifies that you own the address. Claiming an
        // existing profile needs an email-verification step first; until then, 409.
        if (appUserRepository.existsByEmail(email) || studentRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with email " + email + " already exists");
        }

        AppUser user = appUserRepository.save(
                new AppUser(email, passwordEncoder.encode(rawPassword), Role.STUDENT));

        Student student = new Student(firstName, lastName, email, null);
        student.linkUser(user);
        studentRepository.save(student);

        return respond(user, student.getId(), null);
    }

    /**
     * Delegates the password check to Spring's AuthenticationManager rather than calling
     * passwordEncoder.matches() here. The manager adds what a hand-written check forgets:
     * constant-time comparison, the disabled-account check, the same error for unknown email
     * and wrong password, and (via the delegating encoder) support for older hash formats.
     * On failure it throws BadCredentialsException -> 401 via the advice.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResult login(String email, String rawPassword) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));

        AppUser user = appUserRepository.findByEmail(email).orElseThrow();   // just authenticated
        Long studentId = studentRepository.findByUserId(user.getId()).map(Student::getId).orElse(null);
        Long mentorId = mentorRepository.findByUserId(user.getId()).map(Mentor::getId).orElse(null);
        return respond(user, studentId, mentorId);
    }

    private AuthResult respond(AppUser user, Long studentId, Long mentorId) {
        TokenService.IssuedToken token = tokenService.issue(user, studentId, mentorId);
        return new AuthResult(token.value(), token.expiresAt(), token.expiresInSeconds(),
                user.getEmail(), user.getRole(), studentId, mentorId);
    }
}
