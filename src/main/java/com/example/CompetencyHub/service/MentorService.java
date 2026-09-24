package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.model.Mentor;

import java.util.List;

public interface MentorService {

    Mentor create(String firstName, String lastName, String email, String specialization);

    Mentor findById(Long id);

    List<Mentor> findAll();
}
