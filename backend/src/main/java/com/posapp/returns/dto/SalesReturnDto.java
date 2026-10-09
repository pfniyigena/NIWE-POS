package com.posapp.returns.dto;

import com.posapp.returns.SalesReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesReturnDto(
        Long id,
        Long orderId,
        String invoiceNumber,
        String customerName,
        Instant returnDate,
        BigDecimal refundTotal,
        String refundMethod,
        String reason,
        /** New for the "print out the return" task — the return receipt's own line-item table. Empty for a return with no persisted lines (shouldn't happen, but never null). */
        List<SalesReturnLineDto> lines,
        /** Task: "add two logo (URA LOGO, EFRIS LOGO)" equivalent for the return receipt — the original sale's own terminal, so the return receipt can fetch that terminal's EFRIS logos. Null for a return whose order has no session/register at all. */
        Long registerId,
        /** This return's own EFRIS erp_code — see SalesReturn.returnNumber's javadoc. */
        String returnNumber,
        /** EFRIS fiscal receipt fields for this return — all null until it's actually submitted to EFRIS. See SalesReturn's javadoc. */
        String sdcId,
        String sdcDate,
        String sdcReceiptNumber,
        String internalData,
        String signatureData,
        String cisNumber,
        String cisDate,
        String mrcData,
        /** When an EFRIS submission was last attempted for this return (success or failure) — null if never attempted. */
        Instant efrisSubmittedAt,
        /** Set only when an attempted EFRIS submission failed — see SalesReturn.efrisError's javadoc. */
        String efrisError,
        /** The raw EFRIS response body from the most recent attempt — see SalesReturn.efrisResponse's javadoc. */
        String efrisResponse
) {
    public static SalesReturnDto from(SalesReturn r) {
        var order = r.getOrder();
        var customer = order != null ? order.getCustomer() : null;
        var session = order != null ? order.getSession() : null;
        var register = session != null ? session.getRegister() : null;
        return new SalesReturnDto(
                r.getId(),
                order != null ? order.getId() : null,
                order != null ? order.getInvoiceNumber() : null,
                customer != null ? customer.getName() : "Walk-in Customer",
                r.getReturnDate(),
                r.getRefundTotal(),
                r.getRefundMethod(),
                r.getReason(),
                r.getLines().stream().map(SalesReturnLineDto::from).toList(),
                register != null ? register.getId() : null,
                r.getReturnNumber(),
                r.getSdcId(),
                r.getSdcDate(),
                r.getSdcReceiptNumber(),
                r.getInternalData(),
                r.getSignatureData(),
                r.getCisNumber(),
                r.getCisDate(),
                r.getMrcData(),
                r.getEfrisSubmittedAt(),
                r.getEfrisError(),
                r.getEfrisResponse()
        );
    }
}
