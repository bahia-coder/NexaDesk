package com.nexadesk.audit.dto;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.ticket.dto.TicketDtos.UserRefDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogDto(Long id, UserRefDto actor, String actorEmail, AuditAction action, String entityType,
                              String entityId, String summary, Map<String, Object> oldValue,
                              Map<String, Object> newValue, String ipAddress, String traceId, Instant createdAt) {
    }

    public record AuditFilter(Long actorId, AuditAction action, String entityType, String entityId,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    }
}
