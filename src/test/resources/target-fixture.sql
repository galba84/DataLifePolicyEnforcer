CREATE SCHEMA telemetry;
CREATE TABLE telemetry.events (
    id BIGINT NOT NULL, occurred_on DATE NOT NULL, payload TEXT DEFAULT 'sample',
    PRIMARY KEY (id, occurred_on)
) PARTITION BY RANGE (occurred_on);
CREATE TABLE telemetry.events_old PARTITION OF telemetry.events FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE telemetry.events_recent PARTITION OF telemetry.events FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE telemetry.events_future PARTITION OF telemetry.events FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE telemetry.events_default PARTITION OF telemetry.events DEFAULT;
CREATE INDEX events_payload_idx ON telemetry.events (payload);
INSERT INTO telemetry.events VALUES (1, '2025-01-15', 'preserve me'), (2, '2026-08-15', 'also preserve me');
ANALYZE telemetry.events_old;
CREATE TABLE telemetry.plain (id BIGINT PRIMARY KEY);
CREATE TABLE telemetry.nested (occurred_on DATE NOT NULL, tenant INTEGER) PARTITION BY RANGE (occurred_on);
CREATE TABLE telemetry.nested_2025 PARTITION OF telemetry.nested FOR VALUES FROM ('2025-01-01') TO ('2026-01-01') PARTITION BY LIST (tenant);
CREATE TABLE telemetry.nested_tenant_1 PARTITION OF telemetry.nested_2025 FOR VALUES IN (1);
CREATE TABLE telemetry."odd'table" ("odd column" INTEGER DEFAULT 7);
CREATE ROLE metadata_reader LOGIN PASSWORD 'target-test-secret' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE target TO metadata_reader;
GRANT USAGE ON SCHEMA telemetry TO metadata_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA telemetry TO metadata_reader;
ALTER ROLE metadata_reader SET default_transaction_read_only = on;

