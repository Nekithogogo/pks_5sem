CREATE TABLE app_user (
                          id UUID PRIMARY KEY,
                          email VARCHAR(254) NOT NULL UNIQUE,
                          display_name VARCHAR(160) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
                          failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                          locked_until TIMESTAMPTZ,
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user_role (
                               user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                               role VARCHAR(32) NOT NULL,
                               PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_session (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                                 token_hash VARCHAR(64) NOT NULL UNIQUE,
                                 expires_at TIMESTAMPTZ NOT NULL,
                                 revoked_at TIMESTAMPTZ,
                                 replaced_by UUID,
                                 user_agent VARCHAR(500),
                                 ip_address VARCHAR(64),
                                 created_at TIMESTAMPTZ NOT NULL,
                                 last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user
    ON refresh_session(user_id);

CREATE INDEX idx_refresh_expires
    ON refresh_session(expires_at);


CREATE TABLE audit_event (
                             id UUID PRIMARY KEY,
                             actor_id UUID,
                             actor_email VARCHAR(254),
                             event_type VARCHAR(80) NOT NULL,
                             entity_type VARCHAR(80),
                             entity_id UUID,
                             summary VARCHAR(1000) NOT NULL,
                             correlation_id VARCHAR(100),
                             ip_address VARCHAR(64),
                             user_agent VARCHAR(500),
                             occurred_at TIMESTAMPTZ NOT NULL,
                             details_json TEXT
);

CREATE INDEX idx_audit_occurred
    ON audit_event(occurred_at DESC);

CREATE INDEX idx_audit_actor
    ON audit_event(actor_email);

CREATE INDEX idx_audit_entity
    ON audit_event(entity_type, entity_id);

-- Дополняем таблицы КР 1, не меняя их числовые первичные ключи и связи.
ALTER TABLE clients ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE clients ADD CONSTRAINT uq_clients_public_id UNIQUE (public_id);
ALTER TABLE cars ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE cars ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE cars ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cars ADD CONSTRAINT uq_cars_public_id UNIQUE (public_id);
ALTER TABLE test_drive_requests ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE test_drive_requests ADD COLUMN external_id VARCHAR(100);
UPDATE test_drive_requests SET external_id = 'TD-' || id WHERE external_id IS NULL;
ALTER TABLE test_drive_requests ALTER COLUMN external_id SET NOT NULL;
ALTER TABLE test_drive_requests ALTER COLUMN external_id SET DEFAULT gen_random_uuid()::text;
ALTER TABLE test_drive_requests ADD COLUMN callback_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_drive_requests ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'LOW';
ALTER TABLE test_drive_requests ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_drive_requests ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE test_drive_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE test_drive_requests ADD CONSTRAINT uq_request_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX uq_request_external_id_ci ON test_drive_requests (lower(external_id));
CREATE INDEX idx_request_archive ON test_drive_requests (archived);
CREATE INDEX idx_request_priority ON test_drive_requests (priority);
CREATE INDEX idx_request_status ON test_drive_requests (status);

CREATE TABLE request_tag (
  request_id BIGINT NOT NULL REFERENCES test_drive_requests(id) ON DELETE CASCADE,
  tag VARCHAR(50) NOT NULL, PRIMARY KEY (request_id, tag)
);
CREATE INDEX idx_request_tag ON request_tag(tag);
CREATE TABLE request_note (
  id UUID PRIMARY KEY, request_id BIGINT NOT NULL REFERENCES test_drive_requests(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES app_user(id), body VARCHAR(4000) NOT NULL,
  pinned BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_request_note_request ON request_note(request_id, pinned DESC, created_at DESC);

-- Изменения из старого JavaFX-клиента тоже увеличивают версию и обновляют приоритет.
CREATE FUNCTION sync_request_metadata() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.version = OLD.version THEN NEW.version := OLD.version + 1; END IF;
  NEW.priority := CASE WHEN NEW.status IN ('COMPLETED','CANCELLED','NO_SHOW') THEN 'NONE'
                       WHEN NEW.callback_requested AND NEW.status = 'NEW' THEN 'HIGH'
                       WHEN NEW.callback_requested OR NEW.status = 'NEW' THEN 'MEDIUM' ELSE 'LOW' END;
  RETURN NEW;
END $$;
CREATE TRIGGER request_metadata BEFORE INSERT OR UPDATE ON test_drive_requests
  FOR EACH ROW EXECUTE FUNCTION sync_request_metadata();
UPDATE test_drive_requests SET version = version;
