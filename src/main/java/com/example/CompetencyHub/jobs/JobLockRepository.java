package com.example.CompetencyHub.jobs;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Postgres advisory locks: a lock on an arbitrary number rather than on a row. Postgres
 * attaches no meaning to the number, it only guarantees that two sessions asking for the
 * same one cannot both hold it. That makes it a cluster-wide mutex for free, using the
 * database you already have, with no extra service and no new dependency.
 *
 * pg_try_advisory_XACT_lock, not pg_try_advisory_lock. The difference matters a lot with a
 * connection pool:
 *
 *   - pg_try_advisory_lock is SESSION scoped. It is held by the connection until explicitly
 *     released. HikariCP hands you a different connection next time, so releasing it is a
 *     coin flip, and a lock you fail to release is held until that connection is recycled.
 *     Your nightly job then never runs again and nothing logs an error.
 *   - pg_try_advisory_xact_lock is TRANSACTION scoped. Postgres releases it at commit or
 *     rollback, whichever happens, including when the JVM is killed mid-run. There is no
 *     release call to forget.
 *
 * "try" rather than the blocking form on purpose. A second instance arriving at 2 AM should
 * conclude "someone else has this" and go home, not queue up to run the same work again the
 * moment the first one finishes.
 */
@Repository
public class JobLockRepository {

    private final JdbcClient jdbcClient;

    // JdbcClient is the Spring 6.1+ fluent API over JdbcTemplate. Same machinery as the
    // JdbcTemplate in Lesson 3, readable enough to use for a one-liner like this.
    public JobLockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * MUST be called inside an active transaction, or the lock is taken and released in the
     * same implicit statement and this method always returns true -- which looks like it
     * works right up until you deploy a second instance.
     */
    public boolean tryLock(long key) {
        return jdbcClient.sql("SELECT pg_try_advisory_xact_lock(:key)")
                .param("key", key)
                .query(Boolean.class)
                .single();
    }
}