package com.posapp.law;

import com.posapp.law.dto.LawDtos.DeadlineDto;
import com.posapp.law.dto.LawDtos.TaskDto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Turns task and deadline rows into DTOs; used by the matter screens and the cross-matter lists. */
final class LawMapper {

    private LawMapper() {
    }

    static TaskDto task(LawMatterTask t, LawMatter m, LocalDate today) {
        boolean overdue = t.getStatus() != TaskStatus.DONE && t.getDueDate() != null && t.getDueDate().isBefore(today);
        return new TaskDto(t.getId(), t.getMatterId(), m != null ? m.getNumber() : null, m != null ? m.getTitle() : null, t.getTitle(), t.getDescription(), t.getDueDate(),
                t.getAssignedToUserId(), t.getAssignedToName(), t.getStatus(), t.getPriority(), overdue, t.getCompletedAt());
    }

    static DeadlineDto deadline(LawMatterDeadline d, LawMatter m, LocalDate today) {
        boolean open = d.getStatus() == DeadlineStatus.OPEN;
        return new DeadlineDto(d.getId(), d.getMatterId(), m != null ? m.getNumber() : null, m != null ? m.getTitle() : null, d.getTitle(), d.getType(), d.getDueDate(),
                d.getDueTime(), d.getStatus(), d.getAssignedToUserId(), d.getAssignedToName(), d.getNotes(), open && d.getDueDate().isBefore(today),
                ChronoUnit.DAYS.between(today, d.getDueDate()), d.getCompletedByName());
    }
}
