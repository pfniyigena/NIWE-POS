package com.posapp.reports;

import com.posapp.law.DeadlineStatus;
import com.posapp.law.DocReview;
import com.posapp.law.LawAccess;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.LawBillingService;
import com.posapp.law.LawDocumentRepository;
import com.posapp.law.LawMatter;
import com.posapp.law.LawMatterDeadline;
import com.posapp.law.LawMatterDeadlineRepository;
import com.posapp.law.LawMatterPortalRepository;
import com.posapp.law.LawMatterRepository;
import com.posapp.law.LawPortalMessageRepository;
import com.posapp.law.LawService;
import com.posapp.law.LawSettings;
import com.posapp.law.LawTrustService;
import com.posapp.law.MatterStatus;
import com.posapp.law.dto.LawBillingDtos.OverviewRow;
import com.posapp.law.dto.LawDtos.SummaryDto;
import com.posapp.reports.dto.LawInsightRows.AgeingRow;
import com.posapp.reports.dto.LawOverviewDto.Attention;
import com.posapp.reports.dto.LawOverviewDto.Overview;
import com.posapp.reports.dto.LawOverviewDto.Slice;
import com.posapp.timetracking.TimeEntry;
import com.posapp.timetracking.TimeEntryRepository;
import com.posapp.timetracking.TimeEntryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the Law Firm overview for one person: only matters they may see are counted, and money figures follow the billing overview's rules. */
@Service
public class LawOverviewService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final LawAccess access;
    private final LawService lawService;
    private final LawMatterRepository matterRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final TimeEntryRepository timeRepository;
    private final LawBillingService billingService;
    private final LawInsightService insight;
    private final LawTrustService trustService;
    private final LawDocumentRepository documentRepository;
    private final LawMatterPortalRepository portalRepository;
    private final LawPortalMessageRepository messageRepository;

    public LawOverviewService(LawAccess access, LawService lawService, LawMatterRepository matterRepository, LawMatterDeadlineRepository deadlineRepository,
                              TimeEntryRepository timeRepository, LawBillingService billingService, LawInsightService insight, LawTrustService trustService,
                              LawDocumentRepository documentRepository, LawMatterPortalRepository portalRepository, LawPortalMessageRepository messageRepository) {
        this.access = access;
        this.lawService = lawService;
        this.matterRepository = matterRepository;
        this.deadlineRepository = deadlineRepository;
        this.timeRepository = timeRepository;
        this.billingService = billingService;
        this.insight = insight;
        this.trustService = trustService;
        this.documentRepository = documentRepository;
        this.portalRepository = portalRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Overview overview(Actor a) {
        access.on();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<LawMatter> visible = matterRepository.findAllByOrderByOpenedDateDescIdDesc().stream().filter(m -> access.canView(m, a)).toList();
        Map<Long, LawMatter> byId = visible.stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        Set<Long> openIds = visible.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).map(LawMatter::getId).collect(Collectors.toSet());
        SummaryDto s = lawService.summary(a);

        int openedThisMonth = (int) visible.stream().filter(m -> m.getOpenedDate() != null && !m.getOpenedDate().isBefore(monthStart)).count();
        int closedThisMonth = (int) visible.stream().filter(m -> m.getClosedDate() != null && !m.getClosedDate().isBefore(monthStart)).count();

        // hours this month and per lawyer
        Map<Long, Long> matterOfProject = new HashMap<>();
        visible.forEach(m -> {
            if (m.getTimeProjectId() != null) matterOfProject.put(m.getTimeProjectId(), m.getId());
        });
        long minutes = 0;
        long billable = 0;
        Map<String, Long> perLawyer = new LinkedHashMap<>();
        for (TimeEntry e : timeRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(monthStart, today)) {
            if (!matterOfProject.containsKey(e.getProjectId()) || e.getStatus() == TimeEntryStatus.REJECTED) continue;
            minutes += e.getMinutes();
            if (e.isBillable()) billable += e.getMinutes();
            perLawyer.merge(e.getUserName(), (long) e.getMinutes(), Long::sum);
        }

        // money
        List<OverviewRow> rows = billingService.rows(m -> byId.containsKey(m.getId()), false);
        BigDecimal unbilled = rows.stream().map(r -> r.unbilledTime().add(r.unbilledCosts())).reduce(ZERO, BigDecimal::add);
        BigDecimal outstanding = rows.stream().map(OverviewRow::outstanding).reduce(ZERO, BigDecimal::add);
        List<AgeingRow> aged = insight.ageing(m -> byId.containsKey(m.getId()));
        Slice[] bands = {new Slice("0-30 days", sum(aged, AgeingRow::band0to30)), new Slice("31-60 days", sum(aged, AgeingRow::band31to60)),
                new Slice("61-90 days", sum(aged, AgeingRow::band61to90)), new Slice("Over 90 days", sum(aged, AgeingRow::bandOver90))};
        BigDecimal over60 = bands[2].value().add(bands[3].value());

        // fees billed per month, last six months
        YearMonth first = YearMonth.from(today).minusMonths(5);
        Set<String> numbers = visible.stream().map(LawMatter::getNumber).collect(Collectors.toSet());
        Map<YearMonth, BigDecimal> billedBy = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) billedBy.put(first.plusMonths(i), ZERO);
        billingService.feeRows(first.atDay(1), today).stream().filter(f -> numbers.contains(f.matterNumber()))
                .forEach(f -> billedBy.merge(YearMonth.from(f.date()), f.amount(), BigDecimal::add));
        BigDecimal billedThisMonth = billedBy.get(YearMonth.from(today));

        LawSettings settings = lawService.settingsEntity();
        BigDecimal held = ZERO;
        if (settings.isTrustEnabled()) {
            held = trustService.balanceLines(m -> byId.containsKey(m.getId()), true).stream().map(LawTrustService.BalanceLine::balance).reduce(ZERO, BigDecimal::add);
        }

        // client portal
        int waiting = 0;
        Map<Long, Integer> waitingBy = new HashMap<>();
        documentRepository.findAll().stream().filter(d -> byId.containsKey(d.getMatterId()) && !d.isArchived() && d.isFromClient() && d.getReviewStatus() == DocReview.PENDING)
                .forEach(d -> waitingBy.merge(d.getMatterId(), 1, Integer::sum));
        waiting = waitingBy.values().stream().mapToInt(Integer::intValue).sum();
        Map<Long, Long> unreadBy = messageRepository.findAll().stream().filter(x -> byId.containsKey(x.getMatterId()) && x.isFromClient() && !x.isReadByStaff())
                .collect(Collectors.groupingBy(x -> x.getMatterId(), Collectors.counting()));
        int unread = unreadBy.values().stream().mapToInt(Long::intValue).sum();

        // what needs a person
        List<Attention> attention = new ArrayList<>();
        deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream().filter(d -> openIds.contains(d.getMatterId()) && d.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(LawMatterDeadline::getDueDate)).limit(6).forEach(d -> {
                    LawMatter m = byId.get(d.getMatterId());
                    attention.add(new Attention("DEADLINE", m.getId(), m.getNumber(), "Overdue: " + d.getTitle(), d.getDueDate()));
                });
        waitingBy.forEach((id, n) -> attention.add(new Attention("UPLOAD", id, byId.get(id).getNumber(), n + (n == 1 ? " document" : " documents") + " from the client waiting for review", null)));
        unreadBy.forEach((id, n) -> attention.add(new Attention("MESSAGE", id, byId.get(id).getNumber(), n + (n == 1 ? " message" : " messages") + " from the client not yet read", null)));
        visible.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED && !m.isConflictCleared()).limit(5)
                .forEach(m -> attention.add(new Attention("CONFLICT", m.getId(), m.getNumber(), "Conflict check not cleared", m.getOpenedDate())));

        Map<String, Integer> areas = new LinkedHashMap<>();
        visible.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED)
                .forEach(m -> areas.merge(m.getPracticeArea() == null || m.getPracticeArea().isBlank() ? "(none)" : m.getPracticeArea(), 1, Integer::sum));

        return new Overview(s.openMatters(), s.onHold(), openedThisMonth, closedThisMonth, s.deadlinesThisWeek(), s.overdueDeadlines(), s.overdueTasks(), s.conflictsNotCleared(),
                LawInsightService.hours(minutes), LawInsightService.hours(billable), billedThisMonth, unbilled, outstanding, over60, settings.isTrustEnabled(), held,
                settings.isPortalEnabled(), waiting, unread,
                areas.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue()))).toList(),
                billedBy.entrySet().stream().map(e -> new Slice(e.getKey().toString(), e.getValue())).toList(), List.of(bands),
                perLawyer.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(8)
                        .map(e -> new Slice(e.getKey(), LawInsightService.hours(e.getValue()))).toList(),
                attention);
    }

    private static BigDecimal sum(List<AgeingRow> rows, Function<AgeingRow, BigDecimal> f) {
        return rows.stream().map(f).reduce(ZERO, BigDecimal::add);
    }
}
