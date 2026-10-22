package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One closed trading day (the Z-report): the cash count against what the books expected, and a frozen copy of the day's figures. */
@Getter
@Setter
@Entity
public class RestaurantDayClose extends BaseEntity {

    private LocalDate businessDate;
    private int zNumber;
    /** CLOSED, or REOPENED once a manager has undone it (a new close can then be made for the date). */
    private String status = "CLOSED";
    private BigDecimal openingFloat = BigDecimal.ZERO;
    private BigDecimal cashExpected = BigDecimal.ZERO;
    private BigDecimal cashCounted = BigDecimal.ZERO;
    private BigDecimal variance = BigDecimal.ZERO;
    private BigDecimal floatKept = BigDecimal.ZERO;
    private BigDecimal banked = BigDecimal.ZERO;
    private int checks;
    private BigDecimal netSales = BigDecimal.ZERO;
    private BigDecimal totalBilled = BigDecimal.ZERO;
    private BigDecimal tips = BigDecimal.ZERO;
    private String notes;
    private String closedBy;
    private Instant closedAt;
    private String reopenedBy;
    private Instant reopenedAt;
    private String reopenReason;
    @Column(columnDefinition = "text")
    private String snapshot;
}
