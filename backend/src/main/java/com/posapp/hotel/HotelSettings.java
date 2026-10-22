package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
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
    /** Cancelling less than this many days before arrival is a late cancellation (0 = never charged). */
    private int cancelFreeDays = 0;
    /** Nights charged for a late cancellation / a no-show / leaving before the planned departure (0 = no fee). */
    private int cancelFeeNights = 0;
    private int noShowFeeNights = 0;
    private int earlyDepartureFeeNights = 0;
    /** Checking out after this time of day (on or after the planned departure date) costs lateCheckoutPercent of a night. */
    @Column(name = "late_checkout_from")
    private String lateCheckoutFrom;
    private BigDecimal lateCheckoutPercent = BigDecimal.ZERO;
    /** Whether fees carry the hotel tax. */
    private boolean taxOnFees = false;

    private LocalDate auditLastRun;
    private String auditLastSummary;

    // ---- online booking (HO-8)

    /** The public booking page is open only while this is on (and the module is). */
    private boolean onlineEnabled = false;
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String onlineIntro;
    /** Share of the stay (tax included) a guest must pay to keep an online booking; 0 = confirmed straight away. */
    private BigDecimal onlineDepositPercent = BigDecimal.ZERO;
    private int onlineHoldHours = 24;
    private int onlineMaxNights = 14;
    private int onlineMaxDaysAhead = 365;
    private int onlineMaxPerPhonePerDay = 3;
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String onlinePaymentInstructions;

    // ---- guest e-mails (HO-8)
    private boolean mailConfirmation = false;
    private boolean mailPrearrival = false;
    private int mailPrearrivalDays = 2;
    private boolean mailThanks = false;
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String thanksMessage;
    private String reviewUrl;
}
