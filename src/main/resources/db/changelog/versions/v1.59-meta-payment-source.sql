ALTER TABLE marketing_events_db
    ADD COLUMN action_source VARCHAR(32) NOT NULL DEFAULT 'other'
        CHECK (action_source IN ('physical_store', 'other'));
