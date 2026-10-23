package com.posapp.register.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The X report (a look at the shift so far, nothing is closed or cleared) and the Z report (the same figures, numbered and frozen when the
 * shift was closed). Both are this one shape; a Z report is read back from the copy saved at closing, so a reprint always shows what was printed first.
 */
public record ShiftReportDto(
        String type,
        Long sessionId,
        Integer zNumber,
        String registerName,
        String cashierName,
        Instant openedAt,
        Instant closedAt,
        Instant generatedAt,
        String closedBy,
        /** 0 = the first printing; 1, 2... = the Nth copy. */
        int copyNumber,
        Sales sales,
        List<PaymentLine> payments,
        List<TaxLine> taxes,
        Refunds refunds,
        Drawer drawer,
        Controls controls,
        List<CashierLine> cashiers,
        List<CashMovementDto> cashMovements
) {
    public record Sales(int count, BigDecimal items, BigDecimal shelfValue, BigDecimal discounts, BigDecimal netSales, BigDecimal tax, BigDecimal totalSales, BigDecimal averageSale) {
    }

    public record PaymentLine(String method, String name, boolean cash, int count, BigDecimal amount) {
    }

    public record TaxLine(String code, BigDecimal ratePercent, BigDecimal net, BigDecimal tax) {
    }

    public record Refunds(int count, BigDecimal total, List<PaymentLine> byMethod) {
    }

    public record Drawer(BigDecimal openingFloat, BigDecimal cashSales, BigDecimal cashRefunds, BigDecimal paidIn, BigDecimal paidOut, BigDecimal drops,
                         BigDecimal expectedCash, BigDecimal countedCash, BigDecimal variance, String varianceNote, String countDetail) {
    }

    public record Controls(int discountedSales, BigDecimal discounts, int abandonedCarts, BigDecimal abandonedValue, int offlineSales, BigDecimal creditSales,
                           BigDecimal roomCharges, BigDecimal giftCardsSold, int voidedCashMovements) {
    }

    public record CashierLine(String name, String status, Instant startedAt, Instant endedAt, int sales, BigDecimal total) {
    }
}
