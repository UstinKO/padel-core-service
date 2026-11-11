CREATE SCHEMA ui_preview;
CREATE TABLE ui_preview.control(instance_id uuid PRIMARY KEY, state jsonb);
INSERT INTO ui_preview.control(instance_id)
SELECT btrim(pg_read_file('/run/secrets/instance_id'))::uuid;
