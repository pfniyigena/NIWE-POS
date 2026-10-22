package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantDayClose;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One line of the list of closed days. */
public record DayCloseRowDto(Long id, int zNumber, LocalDate date, String status, int checks, BigDecimal netSales, BigDecimal totalBilled, BigDecimal tips,
                             BigDecimal cashExpected, BigDecimal cashCounted, BigDecimal variance, BigDecimal banked, String closedBy, Instant closedAt,
                             String notes, String reopenedBy, String reopenReason) {
    public static DayCloseRowDto from(RestaurantDayClose c) {
        return new DayCloseRowDto(c.getId(), c.getZNumber(), c.getBusinessDate(), c.getStatus(), c.getChecks(), c.getNetSales(), c.getTotalBilled(), c.getTips(),
                c.getCashExpected(), c.getCashCounted(), c.getVariance(), c.getBanked(), c.getClosedBy(), c.getClosedAt(), c.getNotes(), c.getReopenedBy(), c.getReopenReason());
    }
}
