package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Mentor;

import java.util.List;

public interface MentorService {

    /** Creates the mentor AND their MENTOR login, in one transaction. */
    Mentor create(String firstName, String lastName, String email, String specialization, String rawPassword);

    Mentor findById(Long id);

    List<Mentor> findAll();
}
