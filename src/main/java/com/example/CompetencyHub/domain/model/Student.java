package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.embeddable.Address;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Embedded
    private Address address;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    /**
     * The login this student uses, if any. LAZY and one-directional: the student knows its account,
     * the account does not know it is a student. Almost every read of a student (listing
     * submissions, grading) has no use for the login row, so it is never fetched unless asked.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    protected Student() {
    }

    public Student(String firstName, String lastName, String email, Address address) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.address = address;
        this.joinedOn = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public LocalDate getJoinedOn() {
        return joinedOn;
    }

    /** Attaches a login. Once only: re-pointing a student at another account would hand their history to someone else. */
    public void linkUser(AppUser user) {
        if (this.user != null) {
            throw new IllegalStateException("Student " + id + " already has a login");
        }
        this.user = user;
    }

    public AppUser getUser() { return user; }
}
