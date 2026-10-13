package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of hotel-wide settings (Hotel > Settings). */
@Getter
@Setter
@Entity
public class HotelSettings extends BaseEntity {

    /** Switches the Hotel menu and the "Charge to room" tender on. */
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    private PostingMode postingMode = PostingMode.NIGHTLY;

    /** Tax (VAT / tourism levy) added on top of room and service rates, in percent. */
    private BigDecimal taxPercent = BigDecimal.ZERO;

    /** Informational times shown at the front desk. */
    private String checkInTime = "14:00";
    private String checkOutTime = "12:00";

    /** Time of day the night audit (room charges, housekeeping reset) runs. */
    private String auditTime = "03:00";
    private LocalDate auditLastRun;
    private String auditLastSummary;
}
