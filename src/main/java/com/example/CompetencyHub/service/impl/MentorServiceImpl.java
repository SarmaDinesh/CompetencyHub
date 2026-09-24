package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.DuplicateEmailException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.repository.AppUserRepository;
import com.example.CompetencyHub.repository.MentorRepository;
import com.example.CompetencyHub.service.MentorService;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MentorServiceImpl implements MentorService {

    private final MentorRepository mentorRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public MentorServiceImpl(MentorRepository mentorRepository, AppUserRepository appUserRepository,
                             PasswordEncoder passwordEncoder) {
        this.mentorRepository = mentorRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Mentor create(String firstName, String lastName, String email, String specialization,
                         String rawPassword) {
        // Same pattern as course codes: check first for a clear 409, and let the UNIQUE
        // constraints be the real guarantee under concurrency. Both tables: a mentor's login
        // email must not already be someone else's login.
        if (mentorRepository.existsByEmail(email) || appUserRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with email " + email + " already exists");
        }
        AppUser login = appUserRepository.save(
                new AppUser(email, passwordEncoder.encode(rawPassword), Role.MENTOR));

        Mentor mentor = new Mentor(firstName, lastName, email, specialization);
        mentor.linkUser(login);
        return mentorRepository.save(mentor);
    }

    @Override
    @Transactional(readOnly = true)
    public Mentor findById(Long id) {
        return mentorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Mentor not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mentor> findAll() {
        // Unpaged on purpose: mentors number in the dozens, not thousands. Courses are paged
        // because a catalog grows without limit; a staff list does not.
        return mentorRepository.findAll(Sort.by("lastName", "firstName"));
    }
}
