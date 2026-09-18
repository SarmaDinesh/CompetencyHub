CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(20) NOT NULL,
    description TEXT,
    capacity INT NOT NULL
)