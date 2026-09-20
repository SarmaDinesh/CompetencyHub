package com.example.CompetencyHub.service;

import com.example.CompetencyHub.domain.enums.AttemptOutcome;
import com.example.CompetencyHub.domain.model.EnrollmentAttempt;
import com.example.CompetencyHub.repository.EnrollmentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records enrollment attempts in a transaction of their own.
 *
 * <p><b>This is what REQUIRES_NEW is for.</b> A rejected enrollment rolls back the
 * whole enroll() transaction — the seat is restored, no enrollment row is written.
 * With the default REQUIRED propagation, the audit insert would be part of that same
 * transaction and would roll back with it, so the one case you most want recorded
 * would leave no trace.
 *
 * <p>REQUIRES_NEW suspends the caller's transaction, runs this one to its own commit,
 * then resumes the caller. The audit row survives the rollback.
 *
 * <p>The cost is real and worth knowing: two connections are held at once while the
 * outer transaction is suspended. Under load that can exhaust the pool, so REQUIRES_NEW
 * is for things that genuinely must outlive a rollback — audit trails, attempt counters,
 * rate limits — not a default to reach for.
 *
 * <p>Note this is a separate bean. Calling a REQUIRES_NEW method from within the same
 * class would bypass the Spring proxy and silently run in the caller's transaction —
 * the same self-invocation trap AOP has.
 */
@Service
public class EnrollmentAuditService {

    private final EnrollmentAttemptRepository attemptRepository;

    public EnrollmentAuditService(EnrollmentAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long studentId, Long courseId, AttemptOutcome outcome, String detail) {
        attemptRepository.save(new EnrollmentAttempt(studentId, courseId, outcome, detail));
    }
}
