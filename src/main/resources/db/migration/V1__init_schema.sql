CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    location VARCHAR(200) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_by BIGINT NOT NULL REFERENCES app_users(id),
    reviewed_by BIGINT REFERENCES app_users(id),
    review_comment VARCHAR(500),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_event_time_range CHECK (end_time > start_time),
    CONSTRAINT chk_event_capacity CHECK (capacity > 0)
);

CREATE TABLE event_category_map (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    PRIMARY KEY (event_id, category_id)
);

CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    request_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency UNIQUE (idempotency_key, operation, user_id)
);

CREATE INDEX idx_events_status_start_time ON events(status, start_time);
CREATE INDEX idx_events_start_time ON events(start_time);
CREATE INDEX idx_events_submitted_by ON events(submitted_by);
CREATE INDEX idx_events_lower_title ON events((lower(title)));
CREATE INDEX idx_events_lower_location ON events((lower(location)));
CREATE INDEX idx_event_category_category_id ON event_category_map(category_id);
CREATE INDEX idx_idempotency_lookup ON idempotency_records(user_id, operation, idempotency_key);
