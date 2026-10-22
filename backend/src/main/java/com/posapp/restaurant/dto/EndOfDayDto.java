package com.posapp.restaurant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The cash-up / Z-report of one trading day. */
public record EndOfDayDto(
        LocalDate date,
        boolean closed,
        Integer zNumber,
        String closedBy,
        Instant closedAt,
        /** True when a closed day's live figures no longer match what was frozen at closing (something was paid after). */
        boolean changedSinceClose,
        Sales sales,
        List<RestaurantAnalysisDto.Type> types,
        List<Tax> taxes,
        List<Payment> payments,
        List<TipRow> tips,
        List<Deposit> deposits,
        List<RestaurantAnalysisDto.Control> controls,
        OpenOrders openOrders,
        Cash cash,
        List<CashEventDto> cashEvents,
        String notes
) {
    public record Sales(int checks, int parties, int covers, int itemsSold, BigDecimal gross, BigDecimal discounts, BigDecimal netSales,
                        BigDecimal serviceCharge, BigDecimal tax, BigDecimal rounding, BigDecimal billed, BigDecimal tips,
                        BigDecimal depositApplied, BigDecimal collected) {
    }

    /** {@code kind} is CASH, ROOM (charged to a room) or OTHER; DEPOSIT is the part paid earlier as a booking deposit. */
    public record Payment(String method, String kind, int checks, BigDecimal amount) {
    }

    public record Tax(String label, BigDecimal base, BigDecimal tax) {
    }

    public record TipRow(String server, int checks, BigDecimal tips) {
    }

    public record Deposit(String kind, int count, BigDecimal amount, BigDecimal cashAmount) {
    }

    public record OpenOrders(int count, BigDecimal value, List<String> labels) {
    }

    public record Cash(BigDecimal openingFloat, BigDecimal cashSales, BigDecimal depositsIn, BigDecimal depositsOut, BigDecimal paidIn, BigDecimal paidOut,
                       BigDecimal tipsPaid, BigDecimal expected, BigDecimal counted, BigDecimal variance, BigDecimal floatKept, BigDecimal banked) {
    }

    public record CashEventDto(Long id, Instant at, String kind, String method, BigDecimal amount, String label, String reason, String by) {
    }
}
