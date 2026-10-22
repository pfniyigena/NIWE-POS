package com.posapp.register.dto;

import java.math.BigDecimal;
import java.util.List;

/** Where the drawer stands right now: the figures behind "expected cash", the reasons to choose from and the rules in force. */
public record CashSummaryDto(Long sessionId, String registerName, String status, BigDecimal openingFloat, BigDecimal cashSales, BigDecimal cashRefunds,
                             BigDecimal paidIn, BigDecimal paidOut, BigDecimal drops, BigDecimal expectedCash,
                             BigDecimal paidOutApprovalOver, BigDecimal dropAlertAmount, boolean dropAlert, boolean denominationCountRequired,
                             List<CashMovementDto> movements) {
}
