package com.example.CompetencyHub.health;

import com.example.CompetencyHub.domain.model.JobRun;
import com.example.CompetencyHub.repository.JobRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @ExtendWith(MockitoExtension.class) replaces @RunWith(MockitoJUnitRunner.class).
 *
 * Note what is NOT here: the slides' @TestConfiguration static class that builds the bean
 * by hand (slide 26). That existed because @Autowired field injection made the service
 * impossible to construct yourself. Constructor injection -- the rule from Module 1 --
 * means @InjectMocks just works, and Spring disappears from the test entirely.
 *
 * That is the payoff for a decision made three months ago.
 */
@ExtendWith(MockitoExtension.class)
class ProgressJobHealthIndicatorTest {

    @Mock
    private JobRunRepository jobRunRepository;

    @InjectMocks
    private ProgressJobHealthIndicator healthIndicator;

    @Test
    void reportsUnknownWhenNoRunHasEverSucceeded() {
        when(jobRunRepository.findFirstByJobNameAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        // UNKNOWN, not DOWN. On a fresh database this is normal, and a health check that
        // reports a problem on every new deployment gets ignored -- at which point it is
        // worth nothing. This test pins that judgment so nobody "fixes" it to DOWN later.
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void reportsUpWhenLastSuccessIsRecent() {
        when(jobRunRepository.findFirstByJobNameAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.of(succeededRunFinishedHoursAgo(2)));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenLastSuccessIsOlderThanTheStalenessWindow() {
        when(jobRunRepository.findFirstByJobNameAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.of(succeededRunFinishedHoursAgo(30)));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        // The detail matters as much as the status: whoever gets paged needs to know how
        // stale, not just that it is stale.
        assertThat(health.getDetails()).containsKey("ageHours");
    }

    /*
     * Boundary. The window is 26 hours; 25 must still be UP. Off-by-one at a threshold is
     * the bug this class is most likely to have, so it gets its own test rather than being
     * folded into the one above with an if -- slide 14, no conditional logic.
     */
    @Test
    void justInsideTheWindowIsStillUp() {
        when(jobRunRepository.findFirstByJobNameAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.of(succeededRunFinishedHoursAgo(25)));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    private JobRun succeededRunFinishedHoursAgo(int hours) {
        JobRun run = new JobRun("progress-recalculation");
        run.succeeded(42);
        // JobRun stamps finishedAt itself, so reach in for the test. If your entity has no
        // setter, add a package-private one or a test constructor -- test-visible seams are
        // fine, public setters that break encapsulation for production code are not.
        ReflectionTestUtils.setField(run, "finishedAt", Instant.now().minus(hours, ChronoUnit.HOURS));
        return run;
    }
}
