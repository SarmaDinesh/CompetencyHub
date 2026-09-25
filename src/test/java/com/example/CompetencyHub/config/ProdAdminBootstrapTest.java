package com.example.CompetencyHub.config;

import com.example.CompetencyHub.domain.enums.Role;
import com.example.CompetencyHub.domain.model.AppUser;
import com.example.CompetencyHub.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdAdminBootstrapTest {

    @Mock private AppUserRepository users;
    @Mock private PasswordEncoder passwordEncoder;

    private ProdAdminBootstrap bootstrap(String email, String password) {
        return new ProdAdminBootstrap(users, passwordEncoder, new BootstrapAdminProperties(email, password));
    }

    @Test
    void createsTheFirstAdminWithAHashedPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("a-long-secret-123")).thenReturn("{bcrypt}hashed");

        bootstrap("owner@example.com", "a-long-secret-123").run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hashed");
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap(null, null).run(null);   // no credentials needed after the first start

        verify(users, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void refusesToStartWhenNoAdminExistsAndNoCredentialsAreGiven() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrap("", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL");
        verify(users, never()).save(any());
    }

    @Test
    void rejectsAShortPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrap("owner@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");
        verify(users, never()).save(any());
    }

    @Test
    void toStringNeverRevealsThePassword() {
        assertThat(new BootstrapAdminProperties("owner@example.com", "super-secret-pw").toString())
                .doesNotContain("super-secret-pw");
    }
}