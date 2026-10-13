package com.posapp.reportschedule;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A saved "email me this report on a schedule" rule. Date-range reports always cover the period that just
 * ended (yesterday for DAILY, the previous 7 days for WEEKLY, the previous calendar month for MONTHLY);
 * snapshot reports (stock valuation, aging, ...) are simply taken at run time.
 */
@Getter
@Setter
@Entity
@Table(name = "report_schedule")
public class ReportSchedule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** One of the keys in {@link ScheduledReportCatalog}. */
    @Column(name = "report_key", nullable = false)
    private String reportKey;

    /** XLSX or PDF. */
    @Column(nullable = false, length = 8)
    private String format = "XLSX";

    /** DAILY, WEEKLY or MONTHLY. */
    @Column(nullable = false, length = 8)
    private String frequency = "DAILY";

    /** "HH:mm" in the store time zone; the report goes out from this time onward on its day. */
    @Column(name = "run_time", nullable = false, length = 5)
    private String runTime = "07:00";

    /** 1 = Monday ... 7 = Sunday; only used by WEEKLY. */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /** 1-28; only used by MONTHLY. */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /** Comma-separated email addresses; blank = the store's notifications email. */
    @Column(columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "branch_id")
    private Long branchId;

    /** Look-ahead / look-back days for Expiring Stock and Dead Stock; null = the report's own default. */
    @Column(name = "day_window")
    private Integer dayWindow;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** Store-local date it last ran, so it goes out once per due day. */
    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    /** OK, FAILED or SKIPPED. */
    @Column(name = "last_status", length = 10)
    private String lastStatus;

    @Column(name = "last_message", length = 500)
    private String lastMessage;
}
