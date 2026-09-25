package com.example.CompetencyHub.config;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN in production, once.
 *
 * <p>The production counterpart of DevDataSeeder, with the opposite rules: no sample data,
 * no shared password, credentials only from the environment. Runs on every start but acts
 * only when no admin exists, so it is safe to leave in place (idempotent).
 */
@Component
@Profile("prod")
public class ProdAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProdAdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties admin;

    public ProdAdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                              BootstrapAdminProperties admin) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.admin = admin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN account already exists; admin bootstrap skipped");
            return;
        }

        // Fail fast: an app nobody can administer is a broken deploy, not a running one.
        if (isBlank(admin.email()) || isBlank(admin.password())) {
            throw new IllegalStateException(
                    "No ADMIN account exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set. "
                            + "Set both for the first start, then remove ADMIN_PASSWORD from the server's .env.");
        }
        if (admin.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = admin.email().trim();
        users.save(new AppUser(email, passwordEncoder.encode(admin.password()), Role.ADMIN));
        log.info("Bootstrap ADMIN created for {}", email);   // the email, never the password
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}