package com.posapp.law.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class LawCalendarDtos {

    private LawCalendarDtos() {
    }

    /** One line on the calendar: a deadline or a task with a due date. {@code kind} is DEADLINE or TASK; {@code type} is the deadline type or the task priority. */
    public record CalendarEntry(String kind, Long id, Long matterId, String matterNumber, String matterTitle, String title, String type, LocalDate date, LocalTime time,
                                String status, String assignedToName, boolean overdue, boolean done) {
    }

    /** A limitation date that is due a warning e-mail (or has had one). {@code problem} says why nobody can be mailed. */
    public record EscalationItem(Long deadlineId, Long matterId, String matterNumber, String matterTitle, String title, LocalDate dueDate, long daysLeft, int stage, String stageLabel,
                                 List<String> recipients, String problem) {
    }

    public record EscalationResult(int mailsSent, int deadlines, int skipped, List<EscalationItem> items) {
    }
}
