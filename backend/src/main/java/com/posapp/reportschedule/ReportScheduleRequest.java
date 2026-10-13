package com.posapp.reportschedule;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportScheduleRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String reportKey;

    /** XLSX or PDF. */
    private String format = "XLSX";

    /** DAILY, WEEKLY or MONTHLY. */
    private String frequency = "DAILY";

    /** "HH:mm". */
    private String runTime = "07:00";

    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private String recipients;
    private Long categoryId;
    private Long warehouseId;
    private Long branchId;
    private Integer dayWindow;
    private boolean active = true;
}
