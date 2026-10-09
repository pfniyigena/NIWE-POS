package com.posapp.audit.dto;

import com.posapp.audit.AuditLog;

import java.time.Instant;

/** One audit log entry as shown in the admin viewer — see {@code AuditLog}'s own javadoc for what each field means. */
public record AuditLogDto(
        Long id,
        Instant occurredAt,
        Long actorId,
        String actorUsername,
        String actorFullName,
        String action,
        String description,
        String entityType,
        Long entityId
) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getCreatedAt(),
                log.getActorId(),
                log.getActorUsername(),
                log.getActorFullName(),
                log.getAction(),
                log.getDescription(),
                log.getEntityType(),
                log.getEntityId()
        );
    }
}
