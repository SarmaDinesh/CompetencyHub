package com.example.CompetencyHub.domain.model;

import com.example.CompetencyHub.domain.enums.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A login account. Named AppUser, not User: "user" is a reserved word in PostgreSQL, and
 * Spring Security already has a User class -- two reasons the plain name causes trouble.
 *
 * <p>Deliberately does NOT implement Spring Security's UserDetails. That would make a domain
 * entity depend on the security framework; AppUserDetailsService translates instead.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Hash only. There is no getter for a raw password because none is ever stored. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AppUser() { }

    /** @param passwordHash already encoded -- this class never sees a plain password */
    public AppUser(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
