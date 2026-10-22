package com.posapp.restaurant.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class DayCloseRequest {
    private LocalDate date;
    private BigDecimal cashCounted;
    /** Cash left in the drawer for tomorrow; the rest is banked. Left out, it is the opening float (or all the cash if that is less). */
    private BigDecimal floatKept;
    /** Overrides the opening float suggested from the last close. */
    private BigDecimal openingFloat;
    private String notes;
    /** A manager may close with tables still open. */
    private boolean closeWithOpenOrders;
}
