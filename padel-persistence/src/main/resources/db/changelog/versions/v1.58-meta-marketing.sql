CREATE TABLE marketing_visitors_db (
    id UUID PRIMARY KEY,
    consent BOOLEAN NOT NULL,
    first_touch TEXT,
    last_touch TEXT,
    fbc VARCHAR(256),
    fbp VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE marketing_registrations_db (
    registration_id BIGINT PRIMARY KEY REFERENCES tournament_registrations_db(id),
    visitor_id UUID REFERENCES marketing_visitors_db(id),
    first_touch TEXT,
    last_touch TEXT,
    expected_amount NUMERIC(10,2),
    expected_currency VARCHAR(10),
    fbc VARCHAR(256),
    fbp VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE marketing_payment_progress_db (
    payment_id BIGINT PRIMARY KEY REFERENCES payments_db(id),
    high_water NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL
);
CREATE TABLE marketing_events_db (
    event_id UUID PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    event_name VARCHAR(40) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    test_mode BOOLEAN NOT NULL,
    registration_id BIGINT NOT NULL REFERENCES tournament_registrations_db(id),
    payment_id BIGINT REFERENCES payments_db(id),
    visitor_id UUID NOT NULL REFERENCES marketing_visitors_db(id),
    registration_status VARCHAR(30),
    tournament_id BIGINT NOT NULL,
    amount NUMERIC(10,2),
    cumulative_amount NUMERIC(10,2),
    currency VARCHAR(10),
    paid_participants INTEGER NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    last_http_status INTEGER
);
CREATE INDEX idx_marketing_events_db_delivery ON marketing_events_db(channel, state, next_attempt_at);
CREATE INDEX idx_marketing_registrations_db_visitor ON marketing_registrations_db(visitor_id);
CREATE TABLE marketing_paid_slots_db (
    registration_id BIGINT NOT NULL REFERENCES tournament_registrations_db(id),
    partner_slot BOOLEAN NOT NULL,
    PRIMARY KEY (registration_id, partner_slot)
);
-- Существующие записи не получают рекламную атрибуцию задним числом.
INSERT INTO marketing_registrations_db(registration_id)
SELECT id FROM tournament_registrations_db;
INSERT INTO marketing_payment_progress_db(payment_id, high_water, currency)
SELECT id, CASE WHEN status = 'PAID' THEN GREATEST(amount, 0) ELSE 0 END, currency
FROM payments_db;
