CREATE TABLE marketing_attribution_visitors_db (
    id UUID PRIMARY KEY,
    first_touch TEXT,
    last_touch TEXT,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '180 days',
    bound_player_id BIGINT REFERENCES player_padel_db(id) ON DELETE SET NULL,
    consent BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE marketing_acquisition_db (
    player_id BIGINT PRIMARY KEY REFERENCES player_padel_db(id) ON DELETE CASCADE,
    visitor_id UUID REFERENCES marketing_attribution_visitors_db(id),
    first_touch TEXT,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '12 months'
);
ALTER TABLE marketing_registrations_db
    ADD COLUMN attribution_visitor_id UUID REFERENCES marketing_attribution_visitors_db(id),
    ADD COLUMN acquisition_player_id BIGINT REFERENCES player_padel_db(id) ON DELETE SET NULL,
    ADD COLUMN acquisition_touch TEXT,
    ADD COLUMN registration_touch TEXT,
    ADD COLUMN attribution_recorded_at TIMESTAMPTZ,
    ADD COLUMN attribution_expires_at TIMESTAMPTZ;
CREATE INDEX idx_marketing_acquisition_visitor ON marketing_acquisition_db(visitor_id);
CREATE INDEX idx_marketing_attribution_registration_visitor ON marketing_registrations_db(attribution_visitor_id);
CREATE INDEX idx_marketing_attribution_registration_account ON marketing_registrations_db(acquisition_player_id);
CREATE INDEX idx_marketing_attribution_expiry ON marketing_attribution_visitors_db(expires_at);
