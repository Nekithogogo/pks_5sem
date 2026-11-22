package ru.mirea.autosalon.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.autosalon.api.domain.AuditEvent;

import java.util.UUID;

/** Persistence gateway for immutable audit records. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
