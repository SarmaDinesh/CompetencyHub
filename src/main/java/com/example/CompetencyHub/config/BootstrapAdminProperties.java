package com.example.CompetencyHub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the very first ADMIN in a fresh production database.
 * Bound from competencyhub.bootstrap.admin.*, which application-prod.properties maps
 * to the ADMIN_EMAIL / ADMIN_PASSWORD environment variables.
 *
 * <p>Both are optional: once an admin exists they are ignored, and ADMIN_PASSWORD should be
 * removed from the server's .env.
 */
@ConfigurationProperties(prefix = "competencyhub.bootstrap.admin")
public record BootstrapAdminProperties(String email, String password) {

    /**
     * A record's generated toString() prints EVERY field -- including the password -- if this
     * object is ever logged or shown in an error. Overriding it keeps the secret out of logs.
     */
    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", password=" + (password == null ? "null" : "****") + "]";
    }
}
