package com.posapp.law;

import com.posapp.common.ApiException;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawCalendarDtos.CalendarEntry;
import com.posapp.law.dto.LawDtos.DeadlineDto;
import com.posapp.law.dto.LawDtos.TaskDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The calendar: deadlines and tasks with a date in a range, for the screen and for the .ics download. Same visibility rules as the lists. */
@Service
public class LawCalendarService {

    private static final long MAX_DAYS = 400;

    private final LawWorkService work;

    public LawCalendarService(LawWorkService work) {
        this.work = work;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntry> entries(Actor a, boolean mine, Long assigneeId, LocalDate from, LocalDate to, boolean includeDone, boolean withTasks) {
        if (from == null || to == null) throw ApiException.badRequest("Choose the dates to show.");
        if (to.isBefore(from)) throw ApiException.badRequest("The end date is before the start date.");
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) throw ApiException.badRequest("Choose a shorter period (at most " + MAX_DAYS + " days).");
        List<CalendarEntry> out = new ArrayList<>();
        for (DeadlineDto d : work.deadlines(a, mine, assigneeId, from, to, includeDone)) {
            out.add(new CalendarEntry("DEADLINE", d.id(), d.matterId(), d.matterNumber(), d.matterTitle(), d.title(), d.type() == null ? null : d.type().name(), d.dueDate(), d.dueTime(),
                    d.status().name(), d.assignedToName(), d.overdue(), d.status() != DeadlineStatus.OPEN));
        }
        if (withTasks) {
            for (TaskDto t : work.tasks(a, mine, assigneeId, includeDone)) {
                if (t.dueDate() == null || t.dueDate().isBefore(from) || t.dueDate().isAfter(to)) continue;
                out.add(new CalendarEntry("TASK", t.id(), t.matterId(), t.matterNumber(), t.matterTitle(), t.title(), t.priority() == null ? null : t.priority().name(), t.dueDate(), null,
                        t.status().name(), t.assignedToName(), t.overdue(), t.status() == TaskStatus.DONE));
            }
        }
        out.sort(Comparator.comparing(CalendarEntry::date).thenComparing(CalendarEntry::time, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(CalendarEntry::kind));
        return out;
    }

    public byte[] ics(List<CalendarEntry> entries) {
        return LawIcal.build("Law Firm deadlines", entries, LocalDateTime.now());
    }
}
