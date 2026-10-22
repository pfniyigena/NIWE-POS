package com.posapp.hr.dto;

import com.posapp.hr.ChecklistKind;
import com.posapp.hr.EmployeeDocCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Employee documents and onboarding / offboarding checklists. */
public final class HrDocDtos {

    private HrDocDtos() {
    }

    /** {@code expiryState}: NONE (no date), OK, SOON (within 30 days) or EXPIRED. */
    public record EmployeeDocumentDto(Long id, Long employeeId, String employeeNumber, String employeeName, String name, EmployeeDocCategory category,
                                      String description, LocalDate expiryDate, String expiryState, Long daysToExpiry, boolean sharedWithEmployee,
                                      String fileName, String contentType, long sizeBytes, String uploadedByName, Instant createdAt) {
    }

    public record EmployeeDocumentRequest(String name, EmployeeDocCategory category, String description, LocalDate expiryDate, Boolean clearExpiry, Boolean sharedWithEmployee) {
    }

    public record DocumentSummaryDto(long total, long expired, long expiringSoon) {
    }

    // ---- checklists ----

    public record TemplateItemDto(Long id, int sortOrder, String title, String responsible, int dueOffsetDays) {
    }

    public record TemplateDto(Long id, String name, ChecklistKind kind, String description, boolean active, List<TemplateItemDto> items) {
    }

    public record TemplateItemRequest(String title, String responsible, Integer dueOffsetDays) {
    }

    public record TemplateRequest(String name, ChecklistKind kind, String description, Boolean active, List<TemplateItemRequest> items) {
    }

    public record ChecklistItemDto(Long id, int sortOrder, String title, String responsible, LocalDate dueDate, boolean done, Instant doneAt,
                                   String doneByName, String note, boolean overdue) {
    }

    public record ChecklistDto(Long id, Long employeeId, String employeeNumber, String employeeName, String name, ChecklistKind kind, LocalDate startDate,
                               int total, int done, boolean complete, String createdByName, List<ChecklistItemDto> items) {
    }

    public record StartChecklistRequest(Long templateId, LocalDate startDate) {
    }

    public record AddTaskRequest(String title, String responsible, LocalDate dueDate) {
    }

    public record DoneRequest(Boolean done, String note) {
    }

    /** One open task across all employees, for the "open tasks" overview. */
    public record OpenTaskDto(Long itemId, Long checklistId, Long employeeId, String employeeNumber, String employeeName, String checklistName,
                              ChecklistKind kind, String title, String responsible, LocalDate dueDate, boolean overdue, String note) {
    }
}
