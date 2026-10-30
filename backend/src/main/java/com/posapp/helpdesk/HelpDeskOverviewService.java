package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskSummaryDto;
import com.posapp.helpdesk.dto.HelpDeskSummaryDto.AgeBucket;
import com.posapp.helpdesk.dto.HelpDeskSummaryDto.AgentLoad;
import com.posapp.helpdesk.dto.HelpDeskSummaryDto.AttentionLine;
import com.posapp.helpdesk.dto.HelpDeskSummaryDto.PriorityCount;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HelpDeskOverviewService {

    private final HelpDeskTicketRepository repository;
    private final HelpDeskSlaService sla;
    private final HelpDeskTicketService ticketService;

    public HelpDeskOverviewService(HelpDeskTicketRepository repository, HelpDeskSlaService sla, HelpDeskTicketService ticketService) {
        this.repository = repository;
        this.sla = sla;
        this.ticketService = ticketService;
    }

    /** Net working time: created to resolved, less the time spent waiting for the customer. */
    static double netHours(HelpDeskTicket t, Instant end) {
        long secs = Duration.between(t.getCreatedAt(), end).getSeconds() - t.getWaitingSeconds();
        return Math.max(0, secs) / 3600.0;
    }

    static Double avg(List<Double> v) {
        return v.isEmpty() ? null : Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10.0) / 10.0;
    }

    @Transactional(readOnly = true)
    public HelpDeskSummaryDto summary() {
        Instant now = Instant.now();
        Instant d7 = now.minus(7, ChronoUnit.DAYS);
        Instant d30 = now.minus(30, ChronoUnit.DAYS);
        int warn = sla.warnHours();
        AppUser me = ticketService.currentActor();
        List<HelpDeskTicket> all = repository.findAllByOrderByIdDesc();

        int open = 0, prog = 0, wait = 0, resolved = 0, unassigned = 0, overdue = 0, soon = 0, unanswered = 0, mine = 0, created7 = 0, resolved7 = 0;
        List<Double> firstResp = new ArrayList<>();
        List<Double> reso = new ArrayList<>();
        int slaMet = 0, slaTotal = 0;
        List<Double> ratings = new ArrayList<>();
        Map<HelpDeskPriority, Integer> prio = new EnumMap<>(HelpDeskPriority.class);
        for (HelpDeskPriority p : HelpDeskPriority.values()) prio.put(p, 0);
        int[] age = new int[4];
        Map<String, int[]> agents = new LinkedHashMap<>();   // key userId|name -> open, overdue, resolved30
        Map<String, Long> agentIds = new LinkedHashMap<>();
        List<AttentionLine> attention = new ArrayList<>();

        for (HelpDeskTicket t : all) {
            if (t.getCreatedAt().isAfter(d7)) created7++;
            if (t.getResolvedAt() != null && t.getResolvedAt().isAfter(d7)) resolved7++;
            HelpDeskStatus st = t.getStatus();
            boolean active = !HelpDeskSla.terminal(st);
            if (active) {
                switch (st) {
                    case OPEN -> open++;
                    case IN_PROGRESS -> prog++;
                    case WAITING -> wait++;
                    default -> { }
                }
                if (t.getAssignedToUserId() == null) unassigned++;
                if (me != null && me.getId().equals(t.getAssignedToUserId())) mine++;
                if ((st == HelpDeskStatus.OPEN || st == HelpDeskStatus.IN_PROGRESS) && t.getFirstRespondedAt() == null) unanswered++;
                prio.merge(t.getPriority(), 1, Integer::sum);
                long days = Duration.between(t.getCreatedAt(), now).toDays();
                age[days <= 1 ? 0 : days <= 3 ? 1 : days <= 7 ? 2 : 3]++;
                boolean late = HelpDeskSla.overdue(t, now);
                boolean near = !late && HelpDeskSla.dueSoon(t, now, warn);
                if (late) overdue++;
                if (near) soon++;
                String who = t.getAssignedToUserFullName() == null ? "Unassigned" : t.getAssignedToUserFullName();
                String key = who;
                agentIds.putIfAbsent(key, t.getAssignedToUserId());
                int[] a = agents.computeIfAbsent(key, k -> new int[3]);
                a[0]++;
                if (late) a[1]++;
                if (late || near) {
                    boolean respLate = "BREACHED".equals(HelpDeskSla.responseState(t, now));
                    attention.add(new AttentionLine(t.getId(), t.getTicketNumber(), t.getSubject(), t.getPriority(), st, t.getAssignedToUserFullName(),
                            late ? (respLate ? "First response overdue" : "Resolution overdue") : "Resolution due soon",
                            respLate ? t.getFirstResponseDueAt() : t.getResolutionDueAt()));
                }
            } else if (st == HelpDeskStatus.RESOLVED) {
                resolved++;
            }
            if (t.getResolvedAt() != null && t.getResolvedAt().isAfter(d30)) {
                String who = t.getAssignedToUserFullName() == null ? "Unassigned" : t.getAssignedToUserFullName();
                agentIds.putIfAbsent(who, t.getAssignedToUserId());
                agents.computeIfAbsent(who, k -> new int[3])[2]++;
                reso.add(netHours(t, t.getResolvedAt()));
                if (t.getResolutionDueAt() != null) {
                    slaTotal++;
                    if (!t.getResolvedAt().isAfter(t.getResolutionDueAt())) slaMet++;
                }
            }
            if (t.getFirstRespondedAt() != null && t.getCreatedAt().isAfter(d30)) {
                firstResp.add(netHours(t, t.getFirstRespondedAt()));
            }
            if (t.getRatedAt() != null && t.getRatedAt().isAfter(d30) && t.getSatisfactionRating() != null) {
                ratings.add(t.getSatisfactionRating().doubleValue());
            }
        }
        attention.sort(Comparator.comparing(AttentionLine::due, Comparator.nullsLast(Comparator.naturalOrder())));
        List<AttentionLine> top = attention.size() > 10 ? attention.subList(0, 10) : attention;

        List<PriorityCount> byPriority = new ArrayList<>();
        for (HelpDeskPriority p : new HelpDeskPriority[]{HelpDeskPriority.URGENT, HelpDeskPriority.HIGH, HelpDeskPriority.MEDIUM, HelpDeskPriority.LOW}) {
            byPriority.add(new PriorityCount(p, prio.get(p)));
        }
        List<AgeBucket> byAge = List.of(new AgeBucket("Up to 1 day", age[0]), new AgeBucket("2 to 3 days", age[1]),
                new AgeBucket("4 to 7 days", age[2]), new AgeBucket("Over 7 days", age[3]));
        List<AgentLoad> agentList = new ArrayList<>();
        agents.forEach((name, a) -> agentList.add(new AgentLoad(agentIds.get(name), name, a[0], a[1], a[2])));
        agentList.sort(Comparator.comparingInt(AgentLoad::open).reversed());

        return new HelpDeskSummaryDto(open, prog, wait, resolved, unassigned, overdue, soon, unanswered, mine, created7, resolved7,
                avg(firstResp), avg(reso), slaTotal == 0 ? null : Math.round(slaMet * 1000.0 / slaTotal) / 10.0,
                avg(ratings), ratings.size(), byPriority, byAge, agentList, top);
    }
}
