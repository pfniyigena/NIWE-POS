package com.posapp.reportschedule;

public record ReportScheduleDto(
        Long id,
        String name,
        String reportKey,
        String reportLabel,
        String format,
        String frequency,
        String runTime,
        Integer dayOfWeek,
        Integer dayOfMonth,
        String recipients,
        Long categoryId,
        Long warehouseId,
        Long branchId,
        Integer dayWindow,
        boolean active,
        String lastRunAt,
        String lastStatus,
        String lastMessage
) {
    static ReportScheduleDto from(ReportSchedule s) {
        ScheduledReportCatalog.Def def = ScheduledReportCatalog.find(s.getReportKey());
        return new ReportScheduleDto(s.getId(), s.getName(), s.getReportKey(), def != null ? def.label() : s.getReportKey(),
                s.getFormat(), s.getFrequency(), s.getRunTime(), s.getDayOfWeek(), s.getDayOfMonth(), s.getRecipients(),
                s.getCategoryId(), s.getWarehouseId(), s.getBranchId(), s.getDayWindow(), s.isActive(),
                s.getLastRunAt() != null ? s.getLastRunAt().toString() : null, s.getLastStatus(), s.getLastMessage());
    }
}
