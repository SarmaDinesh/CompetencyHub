-- V3: Assessment hierarchy (JOINED inheritance) and per-student course progress.
--
-- JOINED strategy: the shared columns live in `assessment`; each subtype gets its
-- own table holding only its specific columns. A subtype row's primary key is also
-- a foreign key back to the parent, so ObjectiveAssessment #7 is one row in
-- `assessment` and one row in `objective_assessment`, sharing id 7.
--
-- Chosen over SINGLE_TABLE because subtype columns (passing_score, question_count)
-- are genuinely mandatory for their type. A single shared table would force them
-- nullable and push that validation into application code.

-- Parent table: columns common to every kind of assessment.
CREATE TABLE assessment (
    id            BIGSERIAL PRIMARY KEY,
    competency_id BIGINT NOT NULL REFERENCES competency(id) ON DELETE CASCADE, -- ON DELETE CASCADE: an assessment has no meaning without its competency.
    title         VARCHAR(200) NOT NULL,
    -- min_score / max_score are mapped as one @Embeddable (ScoreRange) in Java,
    -- but remain plain columns here — embeddables never get their own table.
    min_score     INT NOT NULL,
    max_score     INT NOT NULL
);

-- Subtype: a scored test with a fixed number of questions.
CREATE TABLE objective_assessment (
    -- Both PK and FK. This is what makes JOINED inheritance work: the child row
    -- cannot exist without its parent, and shares the parent's identity.
    id             BIGINT PRIMARY KEY REFERENCES assessment(id) ON DELETE CASCADE,
    question_count INT NOT NULL,
    passing_score  INT NOT NULL
);

-- Subtype: an open-ended task graded against a rubric.
CREATE TABLE performance_assessment (
    id         BIGINT PRIMARY KEY REFERENCES assessment(id) ON DELETE CASCADE,
    -- Both nullable: not every performance task has a published rubric or a word cap.
    -- Mapped to Integer (not int) in Java so null survives the round trip.
    rubric_url VARCHAR(500),
    word_limit INT
);

-- Derived summary of how far a student has got in a course.
-- Recomputed by a scheduled job later; not a source of truth, a cached rollup.
CREATE TABLE progress (
    student_id            BIGINT NOT NULL REFERENCES student(id),
    course_id             BIGINT NOT NULL REFERENCES course(id),
    competencies_mastered INT NOT NULL DEFAULT 0,
    -- NUMERIC, not FLOAT: percentages are compared and displayed, so exact
    -- decimal arithmetic matters. Maps to BigDecimal in Java.
    percent_complete      NUMERIC(5,2) NOT NULL DEFAULT 0,
    recalculated_at       TIMESTAMP NOT NULL,
    -- Composite natural key: one progress row per student per course.
    -- No surrogate id, because the pair already identifies the row uniquely.
    PRIMARY KEY (student_id, course_id)
);

-- Supports "find all assessments for this competency", the most common lookup.
CREATE INDEX idx_assessment_competency ON assessment(competency_id);