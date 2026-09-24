-- V8: let a student re-enroll in a course they withdrew from.
--
-- V2 declared UNIQUE (student_id, course_id). That was stricter than the real rule:
-- it forbids ANY second row for the pair, including one after a withdrawal. The real
-- rule is "at most one ACTIVE enrollment per student per course".
--
-- V2 has already run, so it is frozen. The correction goes forward in a new migration,
-- the same way V7 undid the V6 columns.

ALTER TABLE enrollment DROP CONSTRAINT uq_enrollment_student_course;

-- A PARTIAL unique index: the uniqueness check only applies to rows matching the WHERE.
-- WITHDRAWN rows are ignored, so history can hold any number of them, but two ACTIVE rows
-- for the same pair are impossible -- even if two requests slip past the service check at
-- the same moment. The service check gives the nice 409 message; this index is the
-- guarantee.
--
-- PostgreSQL-specific. JPA's @UniqueConstraint has no way to say "WHERE status = ...",
-- which is why the entity no longer declares one.
CREATE UNIQUE INDEX uq_enrollment_active_student_course
    ON enrollment (student_id, course_id)
    WHERE status = 'ACTIVE';

-- The service's lookup is (student_id, course_id, status IN ...). The old constraint's
-- index used to serve it; the partial index above cannot (it only covers ACTIVE rows),
-- so give the query a plain composite index of its own.
CREATE INDEX idx_enrollment_student_course ON enrollment (student_id, course_id);
