package com.posapp.reports;

import com.posapp.helpdesk.HelpDeskPriority;
import com.posapp.helpdesk.HelpDeskSla;
import com.posapp.helpdesk.HelpDeskSlaService;
import com.posapp.helpdesk.HelpDeskStatus;
import com.posapp.helpdesk.HelpDeskTicket;
import com.posapp.helpdesk.HelpDeskTicketRepository;
import com.posapp.reports.dto.HelpDeskReportRows.AgentRow;
import com.posapp.reports.dto.HelpDeskReportRows.CategoryRow;
import com.posapp.reports.dto.HelpDeskReportRows.OverdueRow;
import com.posapp.reports.dto.HelpDeskReportRows.SatisfactionRow;
import com.posapp.reports.dto.HelpDeskReportRows.SlaRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Help Desk reports: deadlines kept, tickets by agent and by category, ratings, and what is overdue now. */
@Service
public class HelpDeskReportService {

    private final HelpDeskTicketRepository repository;
    private final HelpDeskSlaService sla;

    public HelpDeskReportService(HelpDeskTicketRepository repository, HelpDeskSlaService sla) {
        this.repository = repository;
        this.sla = sla;
    }

    private static double netHours(HelpDeskTicket t, Instant end) {
        return Math.max(0, Duration.between(t.getCreatedAt(), end).getSeconds() - t.getWaitingSeconds()) / 3600.0;
    }

    private static Double avg(List<Double> v) {
        return v.isEmpty() ? null : Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;
    }

    private static boolean ended(HelpDeskTicket t) {
        return t.getStatus() == HelpDeskStatus.RESOLVED || t.getStatus() == HelpDeskStatus.CLOSED;
    }

    private static Instant endedAt(HelpDeskTicket t) {
        return t.getResolvedAt() != null ? t.getResolvedAt() : t.getClosedAt();
    }

    @Transactional(readOnly = true)
    public List<SlaRow> sla(Instant from, Instant to) {
        Map<HelpDeskPriority, List<HelpDeskTicket>> by = new LinkedHashMap<>();
        for (HelpDeskPriority p : new HelpDeskPriority[]{HelpDeskPriority.URGENT, HelpDeskPriority.HIGH, HelpDeskPriority.MEDIUM, HelpDeskPriority.LOW}) by.put(p, new ArrayList<>());
        for (HelpDeskTicket t : repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (t.getResolutionDueAt() == null || t.getStatus() == HelpDeskStatus.MERGED) continue;
            by.get(t.getPriority()).add(t);
        }
        List<SlaRow> rows = new ArrayList<>();
        List<HelpDeskTicket> every = new ArrayList<>();
        by.forEach((p, list) -> {
            if (!list.isEmpty()) rows.add(row(p.name(), list));
            every.addAll(list);
        });
        if (!every.isEmpty()) rows.add(row("All", every));
        return rows;
    }

    private SlaRow row(String label, List<HelpDeskTicket> list) {
        Instant now = Instant.now();
        int respMet = 0, respMissed = 0, resolved = 0, met = 0, missed = 0;
        List<Double> first = new ArrayList<>();
        List<Double> reso = new ArrayList<>();
        for (HelpDeskTicket t : list) {
            String rs = HelpDeskSla.responseState(t, now);
            if ("MET".equals(rs)) respMet++;
            else if ("MISSED".equals(rs) || "BREACHED".equals(rs)) respMissed++;
            if (t.getFirstRespondedAt() != null) first.add(netHours(t, t.getFirstRespondedAt()));
            String ns = HelpDeskSla.resolutionState(t, now, 0);
            if (ended(t) && endedAt(t) != null) {
                resolved++;
                reso.add(netHours(t, endedAt(t)));
            }
            if ("MET".equals(ns)) met++;
            else if ("MISSED".equals(ns) || "BREACHED".equals(ns)) missed++;
        }
        int judged = met + missed;
        return new SlaRow(label, list.size(), respMet, respMissed, avg(first), resolved, met, missed, avg(reso),
                judged == 0 ? null : Math.round(met * 1000.0 / judged) / 10.0);
    }

    @Transactional(readOnly = true)
    public List<AgentRow> byAgent(Instant from, Instant to) {
        Instant now = Instant.now();
        Map<String, List<HelpDeskTicket>> by = new LinkedHashMap<>();
        for (HelpDeskTicket t : repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (t.getStatus() == HelpDeskStatus.MERGED) continue;
            by.computeIfAbsent(t.getAssignedToUserFullName() == null ? "Unassigned" : t.getAssignedToUserFullName(), k -> new ArrayList<>()).add(t);
        }
        List<AgentRow> rows = new ArrayList<>();
        by.forEach((agent, list) -> {
            int open = 0, resolved = 0, overdue = 0;
            List<Double> reso = new ArrayList<>();
            List<Double> ratings = new ArrayList<>();
            for (HelpDeskTicket t : list) {
                if (!HelpDeskSla.terminal(t.getStatus())) {
                    open++;
                    if (HelpDeskSla.overdue(t, now)) overdue++;
                }
                if (ended(t) && endedAt(t) != null) {
                    resolved++;
                    reso.add(netHours(t, endedAt(t)));
                }
                if (t.getSatisfactionRating() != null) ratings.add(t.getSatisfactionRating().doubleValue());
            }
            rows.add(new AgentRow(agent, list.size(), open, resolved, overdue, avg(reso), avg(ratings)));
        });
        rows.sort(Comparator.comparingInt(AgentRow::assigned).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> byCategory(Instant from, Instant to) {
        Map<String, List<HelpDeskTicket>> by = new LinkedHashMap<>();
        for (HelpDeskTicket t : repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (t.getStatus() == HelpDeskStatus.MERGED) continue;
            by.computeIfAbsent(t.getCategory() == null ? "(no category)" : t.getCategory().getName(), k -> new ArrayList<>()).add(t);
        }
        List<CategoryRow> rows = new ArrayList<>();
        by.forEach((cat, list) -> {
            int open = 0, resolved = 0;
            List<Double> reso = new ArrayList<>();
            List<Double> ratings = new ArrayList<>();
            for (HelpDeskTicket t : list) {
                if (!HelpDeskSla.terminal(t.getStatus())) open++;
                if (ended(t) && endedAt(t) != null) {
                    resolved++;
                    reso.add(netHours(t, endedAt(t)));
                }
                if (t.getSatisfactionRating() != null) ratings.add(t.getSatisfactionRating().doubleValue());
            }
            rows.add(new CategoryRow(cat, list.size(), open, resolved, avg(reso), avg(ratings)));
        });
        rows.sort(Comparator.comparingInt(CategoryRow::tickets).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<SatisfactionRow> satisfaction(Instant from, Instant to) {
        List<SatisfactionRow> rows = new ArrayList<>();
        for (HelpDeskTicket t : repository.findAllByOrderByIdDesc()) {
            if (t.getRatedAt() == null || t.getSatisfactionRating() == null) continue;
            if (t.getRatedAt().isBefore(from) || t.getRatedAt().isAfter(to)) continue;
            rows.add(new SatisfactionRow(t.getTicketNumber(), t.getSubject(), t.getRequesterName(), t.getAssignedToUserFullName(),
                    t.getSatisfactionRating(), t.getSatisfactionComment(), t.getRatedAt()));
        }
        rows.sort(Comparator.comparing(SatisfactionRow::ratedAt).reversed());
        return rows;
    }

    /** Open tickets whose first-response or resolution deadline has already passed, worst first. */
    @Transactional(readOnly = true)
    public List<OverdueRow> overdue() {
        Instant now = Instant.now();
        List<OverdueRow> rows = new ArrayList<>();
        for (HelpDeskTicket t : repository.findAllByOrderByIdDesc()) {
            if (HelpDeskSla.terminal(t.getStatus())) continue;
            boolean resp = "BREACHED".equals(HelpDeskSla.responseState(t, now));
            boolean reso = "BREACHED".equals(HelpDeskSla.resolutionState(t, now, 0));
            if (!resp && !reso) continue;
            Instant due = resp ? t.getFirstResponseDueAt() : t.getResolutionDueAt();
            rows.add(new OverdueRow(t.getTicketNumber(), t.getSubject(), t.getPriority().name(), t.getStatus().name(),
                    t.getAssignedToUserFullName(), resp ? "First response" : "Resolution", due, Math.max(0, Duration.between(due, now).toHours())));
        }
        rows.sort(Comparator.comparingLong(OverdueRow::hoursLate).reversed());
        return rows;
    }
}
