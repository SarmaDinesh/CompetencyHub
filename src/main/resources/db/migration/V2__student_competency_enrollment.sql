CREATE TABLE student (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    street      VARCHAR(200),
    city        VARCHAR(100),
    state       VARCHAR(50),
    postal_code VARCHAR(20),
    joined_on   DATE NOT NULL
);

CREATE TABLE competency (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    weight      INT NOT NULL,
    order_index INT NOT NULL
);

CREATE TABLE enrollment (
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT NOT NULL REFERENCES student(id),
    course_id   BIGINT NOT NULL REFERENCES course(id),
    enrolled_at TIMESTAMP NOT NULL,
    status      VARCHAR(20) NOT NULL,
    CONSTRAINT uq_enrollment_student_course UNIQUE (student_id, course_id)
);

CREATE INDEX idx_competency_course ON competency(course_id);
CREATE INDEX idx_enrollment_student ON enrollment(student_id);
CREATE INDEX idx_enrollment_course  ON enrollment(course_id);