-- V1: esquema relacional inicial do EventOS.
-- Todos os comandos são idempotentes (IF NOT EXISTS) para também adotar bancos criados por versões anteriores.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    organizer_id BIGINT,
    max_capacity INT NOT NULL DEFAULT 500,
    cert_policy_type VARCHAR(50),
    cert_policy_param DOUBLE,
    activity_selection_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    activity_selection_required BOOLEAN NOT NULL DEFAULT FALSE,
    registration_deadline TIMESTAMP,
    time_zone VARCHAR(100) NOT NULL DEFAULT 'America/Sao_Paulo'
);

CREATE TABLE IF NOT EXISTS activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    room VARCHAR(100) NOT NULL,
    space_or_track VARCHAR(100),
    capacity INT NOT NULL,
    current_enrollments INT NOT NULL DEFAULT 0,
    type VARCHAR(50) NOT NULL,
    attendance_policy VARCHAR(50) NOT NULL,
    requires_registration BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS speakers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    role_in_activity VARCHAR(100),
    bio TEXT,
    photo_url VARCHAR(500),
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    registration_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    UNIQUE(event_id, user_id),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS registration_activities (
    registration_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    PRIMARY KEY (registration_id, activity_id),
    FOREIGN KEY (registration_id) REFERENCES registrations(id) ON DELETE CASCADE,
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    type VARCHAR(50) NOT NULL,
    recorded_by VARCHAR(100),
    notes TEXT,
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS feedback_surveys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS survey_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT NOT NULL,
    question_text VARCHAR(500) NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    options_csv TEXT,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (survey_id) REFERENCES feedback_surveys(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS survey_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    survey_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    answers_json TEXT NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    UNIQUE(activity_id, user_id),
    FOREIGN KEY (survey_id) REFERENCES feedback_surveys(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS certificates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    event_title VARCHAR(255) NOT NULL,
    completion_hours DOUBLE NOT NULL,
    issue_date DATE NOT NULL,
    verification_code VARCHAR(100) NOT NULL UNIQUE,
    UNIQUE(event_id, user_id),
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
