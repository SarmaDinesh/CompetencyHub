-- V4: Seat tracking with optimistic locking, plus an audit trail of enrollment attempts.

-- Remaining seats, tracked separately from capacity so we can tell
-- "this course holds 30" from "this course has 3 left".
ALTER TABLE course ADD COLUMN seats_available INT;

-- Backfill before adding the constraint: existing rows have no value yet, and
-- NOT NULL would be rejected while any row is null. Add nullable → fill → constrain
-- is the standard three-step for adding a required column to a populated table.
UPDATE course SET seats_available = capacity;
ALTER TABLE course ALTER COLUMN seats_available SET NOT NULL;

-- Optimistic locking counter, managed entirely by Hibernate via @Version.
-- DEFAULT 0 so existing rows start at a valid version.
ALTER TABLE course ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Audit trail: every enrollment attempt, successful or not.
--
-- Deliberately NO foreign keys to student or course. An audit record must survive
-- the deletion of what it describes — "student 42 was rejected from course 7" stays
-- true and stays useful even after course 7 is removed. Audit tables record history,
-- not current relationships.
CREATE TABLE enrollment_attempt (
    id           BIGSERIAL PRIMARY KEY,
    student_id   BIGINT NOT NULL,
    course_id    BIGINT NOT NULL,
    attempted_at TIMESTAMP NOT NULL,
    outcome      VARCHAR(30) NOT NULL,
    detail       VARCHAR(500)
);

CREATE INDEX idx_attempt_student ON enrollment_attempt(student_id);
CREATE INDEX idx_attempt_course  ON enrollment_attempt(course_id);