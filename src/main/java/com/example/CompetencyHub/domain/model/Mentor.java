package com.example.CompetencyHub.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Someone who grades performance tasks.
 *
 * <p>Deliberately a plain entity for now. The security branch will add AppUser (login,
 * password hash, role) and link Mentor to it one-to-one -- the same way Student will be
 * linked. Keeping the domain person and the login account separate means a mentor's name
 * and specialization do not live in the security table, and changing how people log in
 * does not touch grading.
 */
@Entity
@Table(name = "mentor")
public class Mentor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 200)
    private String specialization;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    protected Mentor() {
    }

    public Mentor(String firstName, String lastName, String email, String specialization) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.specialization = specialization;
        this.joinedOn = LocalDate.now();
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getSpecialization() { return specialization; }
    public LocalDate getJoinedOn() { return joinedOn; }
}
