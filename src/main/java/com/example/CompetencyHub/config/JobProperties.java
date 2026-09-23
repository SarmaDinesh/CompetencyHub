package com.example.CompetencyHub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for scheduled jobs.
 *
 * Deliberately separate from AppProperties rather than another block inside it. One
 * @ConfigurationProperties class per concern keeps each one's validation rules next to the
 * thing they protect. A single app-wide properties object becomes a god object that every
 * class depends on for one field, which is the same coupling problem constructor injection
 * was meant to solve.
 *
 * This is a record, so Spring uses CONSTRUCTOR BINDING: it reads the properties, builds the
 * object once, and hands it over immutable. The slides show the 2022 style -- a mutable class
 * with setters that Spring calls one at a time. Constructor binding is better because there is
 * no window where the object exists half-populated, and because @NotNull on a component means
 * "this property is required" rather than "someone forgot to call the setter".
 */
@ConfigurationProperties(prefix = "competencyhub.jobs")
@Validated
public record JobProperties(

        @Valid @NotNull ProgressRecalculation progressRecalculation,

        /*
         * The key for the Postgres advisory lock. It is just an arbitrary 64-bit number that
         * every instance agrees on -- Postgres does not care what it means, only that two
         * callers asking for the same number cannot both hold it. Keeping it in config means
         * a second job later can be given a different number without recompiling.
         */
        @NotNull Long lockKey
) {

    public record ProgressRecalculation(

            /*
             * Spring cron: SECOND MINUTE HOUR DAY-OF-MONTH MONTH DAY-OF-WEEK. Six fields.
             * Quartz (slide 8) has an optional seventh for the year -- a 7-field expression
             * copied from a Quartz example fails at startup with a parse error.
             *
             * The special value "-" disables the task entirely (Scheduled.CRON_DISABLED),
             * which is how you switch a job off in one environment without an if-statement.
             */
            @NotBlank String cron,

            /*
             * Cron without a zone runs in the server's default timezone. That is fine until
             * the app moves to a container whose TZ is UTC and your "2 AM" job starts running
             * at 8 PM. Always state the zone you mean.
             */
            @NotBlank String zone,

            /*
             * Separate from the cron "-" trick on purpose. "-" stops the task from being
             * registered at all; this flag lets it fire and exit immediately, which is what
             * you want when you are testing whether the schedule itself is correct.
             */
            boolean enabled,

            @NotNull @Positive Integer batchSize,

            /*
             * Duration binds from strings like "5m", "30s", "2h" with no converter on your
             * part. Not in the slides -- Boot also does this for DataSize ("10MB") and for
             * java.time types generally.
             */
            @NotNull Duration maxRuntime
    ) {}
}
