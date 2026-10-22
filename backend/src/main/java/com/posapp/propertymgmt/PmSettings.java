package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The one settings row of the Property Management module. */
@Getter
@Setter
@Entity
public class PmSettings extends BaseEntity {

    /** A lease shows as expiring this many days before its end date. */
    @Column(nullable = false)
    private int expiryWarningDays = 60;

    /** The day of the month rent falls due, unless the lease says otherwise. */
    @Column(nullable = false)
    private int defaultDueDay = 1;

    /** The agency's management fee, as a percentage of the rent collected, that a new property starts with (used by owner statements later). */
    @Column(precision = 7, scale = 2)
    private BigDecimal defaultFeePercent;

    /** VAT charged on top of the management fee, as a percentage of the fee (the owner bears it). Empty or zero: no VAT is charged. */
    @Column(precision = 7, scale = 2)
    private BigDecimal feeVatPercent;

    /** Rent is invoiced this many days before it falls due. */
    @Column(nullable = false)
    private int invoiceAheadDays = 7;

    /** When on, rent that is ready to invoice is invoiced every morning without anyone pressing the button. */
    @Column(nullable = false)
    private boolean autoInvoice = false;

    /** When on, last month's owner statements are made automatically from the chosen day of each month. */
    @Column(nullable = false)
    private boolean autoStatements = false;

    /** The day of the month (1 to 28) from which last month's statements are made. */
    @Column(nullable = false)
    private int autoStatementsDay = 5;

    /** When on, the statements that need no attention are also agreed and e-mailed to their owners without anyone pressing a button. */
    @Column(nullable = false)
    private boolean autoSendStatements = false;

    /** The last day of the last month that was made automatically; stops the same month being made twice. */
    private java.time.LocalDate autoStatementsThrough;

    /** The service product the rent invoices are made under (made on first use). */
    private Long rentProductId;

    /** The service product that repairs charged to a tenant are invoiced under (made on first use). */
    private Long maintenanceProductId;

    /** Areas offered as suggestions on properties; comma separated. */
    @Column(length = 4000)
    private String areas = "";

    /** When on, tenants are e-mailed about unpaid rent: before it is due and, once it is late, again every few days (see the fields below). */
    @Column(nullable = false)
    private boolean remindersEnabled = false;

    /** E-mail the tenant this many days before the due date (0: do not remind before). */
    @Column(nullable = false)
    private int reminderBeforeDays = 3;

    /** First reminder this many days after the due date (0: no reminders once the rent is late). */
    @Column(nullable = false)
    private int overdueFirstDays = 3;

    /** Remind again every this many days while still unpaid (0: only the first reminder). */
    @Column(nullable = false)
    private int overdueRepeatDays = 7;

    /** At most this many reminders for one late rent. */
    @Column(nullable = false)
    private int overdueMaxReminders = 3;

    /** When on, a late fee can be charged on rent that stays unpaid past the grace period. */
    @Column(nullable = false)
    private boolean lateFeeEnabled = false;

    /** When on (and the late fee is on), the morning job charges the fee itself; otherwise it is charged by hand from the Rent screen. */
    @Column(nullable = false)
    private boolean lateFeeAuto = false;

    /** The fee can be charged once the rent is more than this many days past due. */
    @Column(nullable = false)
    private int lateFeeGraceDays = 5;

    /** PERCENT of the unpaid rent, or FIXED amount. */
    @Column(nullable = false, length = 10)
    private String lateFeeType = "PERCENT";

    @Column(precision = 18, scale = 2)
    private BigDecimal lateFeeValue;

    /** The most a percentage fee can come to (empty: no limit). */
    @Column(precision = 18, scale = 2)
    private BigDecimal lateFeeMax;

    /** The service product late fees are invoiced under (made on first use). */
    private Long lateFeeProductId;

    /** The service product one-off charges with no charge type are invoiced under (made on first use). */
    private Long chargesProductId;

    private java.time.LocalDate remindersLastRun;

    @Column(length = 500)
    private String remindersLastSummary;
}
