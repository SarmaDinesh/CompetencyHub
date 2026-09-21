package com.example.CompetencyHub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings, bound from properties prefixed {@code competencyhub.*}.
 *
 * <p><b>Why this instead of @Value.</b> @Value("${competencyhub.default-capacity}")
 * scatters string keys through the codebase: a typo compiles fine and fails at
 * runtime, the type is whatever the annotation site declares, and nothing lists what
 * the application can be configured with. Binding to a class gives you one place to
 * look, real types, IDE autocomplete, and validation.
 *
 * <p>{@code @Validated} makes the constraints below enforced at startup. An invalid
 * value stops the application from booting with a message naming the property —
 * failing immediately and loudly beats failing at 3am on the one code path that
 * reads a misconfigured value.
 *
 * <p>Relaxed binding means {@code default-course-capacity},
 * {@code defaultCourseCapacity}, and {@code DEFAULT_COURSE_CAPACITY} all bind to the
 * same field. That last form matters for deployment: environment variables can
 * override any property without changing a file.
 */
@ConfigurationProperties(prefix = "competencyhub")
@Validated
public class AppProperties {

    /** Shown in API responses and logs. Identifies which institution this instance serves. */
    @NotBlank
    private String institutionName;

    /** Capacity applied to a course created without one. */
    @Min(1)
    @Max(500)
    private int defaultCourseCapacity = 30;

    @Valid
    private Audit audit = new Audit();

    /**
     * Nested group. Nesting keeps related settings together and produces natural
     * property names: competencyhub.audit.slow-call-threshold-ms.
     */
    public static class Audit {

        /** Service calls slower than this are logged at WARN. */
        @Min(1)
        private long slowCallThresholdMs = 200;

        /** Master switch for the timing aspect. */
        private boolean enabled = true;

        public long getSlowCallThresholdMs() { return slowCallThresholdMs; }
        public void setSlowCallThresholdMs(long slowCallThresholdMs) {
            this.slowCallThresholdMs = slowCallThresholdMs;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // Setters are required — Spring binds by calling them after constructing the
    // object. (A record or constructor-bound class works too, via @ConstructorBinding,
    // but mutable binding is still the more common form you'll meet in codebases.)

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public int getDefaultCourseCapacity() { return defaultCourseCapacity; }
    public void setDefaultCourseCapacity(int defaultCourseCapacity) {
        this.defaultCourseCapacity = defaultCourseCapacity;
    }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }
}