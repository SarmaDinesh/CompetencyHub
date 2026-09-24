package com.example.CompetencyHub.service.impl;

import com.example.CompetencyHub.common.exception.DuplicateEmailException;
import com.example.CompetencyHub.common.exception.NotFoundException;
import com.example.CompetencyHub.domain.model.Mentor;
import com.example.CompetencyHub.repository.MentorRepository;
import com.example.CompetencyHub.service.MentorService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MentorServiceImpl implements MentorService {

    private final MentorRepository mentorRepository;

    public MentorServiceImpl(MentorRepository mentorRepository) {
        this.mentorRepository = mentorRepository;
    }

    @Override
    @Transactional
    public Mentor create(String firstName, String lastName, String email, String specialization) {
        // Same pattern as course codes: check first for a clear 409, and let the UNIQUE
        // constraint be the real guarantee under concurrency.
        if (mentorRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("A mentor with email " + email + " already exists");
        }
        return mentorRepository.save(new Mentor(firstName, lastName, email, specialization));
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
