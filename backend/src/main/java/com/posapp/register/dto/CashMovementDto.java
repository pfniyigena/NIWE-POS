package com.posapp.register.dto;

import com.posapp.register.CashMovement;

import java.math.BigDecimal;
import java.time.Instant;

public record CashMovementDto(Long id, Long sessionId, String registerName, String kind, String reasonCode, String reason, BigDecimal amount,
                              BigDecimal expectedCash, BigDecimal variance, String note, String byUser, String approvedBy, boolean voided,
                              String voidReason, String voidedBy, Instant createdAt, String countDetail) {
    public static CashMovementDto from(CashMovement m, String registerName, String reason) {
        return new CashMovementDto(m.getId(), m.getRegisterSessionId(), registerName, m.getKind(), m.getReasonCode(), reason, m.getAmount(),
                m.getExpectedCash(), m.getVariance(), m.getNote(), m.getByUser(), m.getApprovedBy(), m.isVoided(), m.getVoidReason(), m.getVoidedBy(),
                m.getCreatedAt(), m.getCountDetail());
    }
}
