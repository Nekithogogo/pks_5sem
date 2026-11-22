-- Защита расписания действует также для клиентов, обращающихся к БД напрямую.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE test_drive_requests
    ADD CONSTRAINT ex_test_drive_car_time
    EXCLUDE USING gist (
        car_id WITH =,
        tsrange(scheduled_at, scheduled_at + duration_minutes * interval '1 minute', '[)') WITH &&
    ) WHERE (status IN ('NEW', 'CONFIRMED'));

ALTER TABLE test_drive_requests
    ADD CONSTRAINT ex_test_drive_client_time
    EXCLUDE USING gist (
        client_id WITH =,
        tsrange(scheduled_at, scheduled_at + duration_minutes * interval '1 minute', '[)') WITH &&
    ) WHERE (status IN ('NEW', 'CONFIRMED'));

-- Поиск по подстроке выполняется через lower(...) LIKE '%...%'.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_client_name_search ON clients USING gin (lower(full_name) gin_trgm_ops);
CREATE INDEX idx_client_phone_search ON clients USING gin (lower(phone) gin_trgm_ops);
CREATE INDEX idx_car_brand_search ON cars USING gin (lower(brand) gin_trgm_ops);
CREATE INDEX idx_car_model_search ON cars USING gin (lower(model) gin_trgm_ops);
CREATE INDEX idx_car_vin_search ON cars USING gin (lower(vin) gin_trgm_ops);
CREATE INDEX idx_request_external_search ON test_drive_requests USING gin (lower(external_id) gin_trgm_ops);
