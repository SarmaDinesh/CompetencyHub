package com.example.CompetencyHub.web.dto.response;

import com.example.CompetencyHub.domain.model.Mentor;

import java.time.LocalDate;

public record MentorResponse(Long id, String firstName, String lastName, String email,
                             String specialization, LocalDate joinedOn) {

    public static MentorResponse from(Mentor m) {
        return new MentorResponse(m.getId(), m.getFirstName(), m.getLastName(), m.getEmail(),
                m.getSpecialization(), m.getJoinedOn());
    }
}
