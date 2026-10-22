package com.posapp.reports;

import com.posapp.law.DeadlineStatus;
import com.posapp.law.LawBillingService;
import com.posapp.law.LawMatter;
import com.posapp.law.LawMatterDeadline;
import com.posapp.law.LawMatterDeadlineRepository;
import com.posapp.law.LawMatterRepository;
import com.posapp.law.LawMatterTask;
import com.posapp.law.LawMatterTaskRepository;
import com.posapp.law.LawMatterTeam;
import com.posapp.law.LawMatterTeamRepository;
import com.posapp.law.LawTrustService;
import com.posapp.law.MatterStatus;
import com.posapp.law.TaskStatus;
import com.posapp.law.TeamRole;
import com.posapp.reports.dto.LawReportRows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Law firm reports: the matter list, deadlines in a period, and what each lawyer is carrying. Restricted matters are shown without title or client. */
@Service
public class LawReportService {

    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawMatterTaskRepository taskRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final LawBillingService billingService;
    private final LawTrustService trustService;

    public LawReportService(LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository, LawMatterTaskRepository taskRepository,
                            LawMatterDeadlineRepository deadlineRepository, LawBillingService billingService, LawTrustService trustService) {
        this.trustService = trustService;
        this.billingService = billingService;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.taskRepository = taskRepository;
        this.deadlineRepository = deadlineRepository;
    }

    private static String title(LawMatter m) {
        return m.isRestricted() ? "(restricted)" : m.getTitle();
    }

    private static String client(LawMatter m) {
        return m.isRestricted() ? "" : m.getClientName();
    }

    @Transactional(readOnly = true)
    public List<MatterRow> matters(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        Map<Long, List<LawMatterTask>> tasks = taskRepository.findByStatusNot(TaskStatus.DONE).stream().collect(Collectors.groupingBy(LawMatterTask::getMatterId));
        Map<Long, LocalDate> next = new LinkedHashMap<>();
        for (LawMatterDeadline d : deadlineRepository.findByStatus(DeadlineStatus.OPEN)) {
            if (!d.getDueDate().isBefore(today)) next.merge(d.getMatterId(), d.getDueDate(), (x, y) -> x.isBefore(y) ? x : y);
        }
        return matterRepository.findAllByOrderByOpenedDateDescIdDesc().stream()
                .filter(m -> m.getOpenedDate() != null && !m.getOpenedDate().isBefore(from) && !m.getOpenedDate().isAfter(to))
                .map(m -> new MatterRow(m.getNumber(), title(m), client(m), m.getPracticeArea(), m.getStatus().name(), m.getLeadName(), m.getOpenedDate(), m.getClosedDate(),
                        m.getBillingType().name(), tasks.getOrDefault(m.getId(), List.of()).size(), next.get(m.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<DeadlineRow> deadlines(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        Map<Long, LawMatter> byId = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        return deadlineRepository.findAll().stream()
                .filter(d -> !d.getDueDate().isBefore(from) && !d.getDueDate().isAfter(to) && byId.containsKey(d.getMatterId()))
                .sorted(Comparator.comparing(LawMatterDeadline::getDueDate).thenComparing(LawMatterDeadline::getDueTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> {
                    LawMatter m = byId.get(d.getMatterId());
                    String matter = m.getNumber() + (m.isRestricted() ? "" : " " + m.getTitle());
                    return new DeadlineRow(d.getDueDate(), d.getDueTime() != null ? d.getDueTime().toString() : "", d.getType().name(), d.getStatus().name(),
                            m.isRestricted() ? "(restricted)" : d.getTitle(), matter, client(m), d.getAssignedToName(),
                            d.getStatus() == DeadlineStatus.OPEN && d.getDueDate().isBefore(today) ? "Yes" : "No");
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkloadRow> workload() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);
        Map<Long, LawMatter> open = matterRepository.findAll().stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        Map<Long, List<LawMatterTeam>> byUser = teamRepository.findAll().stream().filter(t -> open.containsKey(t.getMatterId())).collect(Collectors.groupingBy(LawMatterTeam::getUserId));
        List<LawMatterTask> tasks = taskRepository.findByStatusNot(TaskStatus.DONE).stream().filter(t -> open.containsKey(t.getMatterId())).toList();
        List<LawMatterDeadline> deadlines = deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream().filter(d -> open.containsKey(d.getMatterId())).toList();
        return byUser.entrySet().stream().map(e -> {
            Long uid = e.getKey();
            String name = e.getValue().get(0).getUserName();
            int led = (int) e.getValue().stream().filter(t -> t.getRole() == TeamRole.LEAD).count();
            List<LawMatterTask> mine = tasks.stream().filter(t -> uid.equals(t.getAssignedToUserId())).toList();
            List<LawMatterDeadline> myDl = deadlines.stream().filter(d -> uid.equals(d.getAssignedToUserId())
                    || (d.getAssignedToUserId() == null && uid.equals(open.get(d.getMatterId()).getLeadUserId()))).toList();
            return new WorkloadRow(name, led, e.getValue().size(), mine.size(), (int) mine.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count(),
                    (int) myDl.stream().filter(d -> !d.getDueDate().isBefore(today) && !d.getDueDate().isAfter(horizon)).count(), (int) myDl.stream().filter(d -> d.getDueDate().isBefore(today)).count());
        }).sorted(Comparator.comparing(WorkloadRow::lawyer, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Client money held per matter. A restricted matter keeps its number but shows no title or client. */
    @Transactional
    public List<TrustBalancesRow> trustBalances(boolean heldOnly) {
        Map<String, Boolean> hidden = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getNumber, LawMatter::isRestricted, (x, y) -> x));
        return trustService.balanceLines(m -> true, heldOnly).stream().map(r -> {
            boolean restricted = Boolean.TRUE.equals(hidden.get(r.matterNumber()));
            return new TrustBalancesRow(r.matterNumber(), restricted ? "(restricted)" : r.matterTitle(), restricted ? "" : r.clientName(), r.matterStatus(), r.balance(),
                    r.pending(), r.lastActivity());
        }).toList();
    }

    /** Every posted client-money movement in a period, with the matter's running balance. */
    @Transactional
    public List<TrustLedgerRow> trustLedger(LocalDate from, LocalDate to) {
        Map<String, Boolean> restricted = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getNumber, LawMatter::isRestricted, (x, y) -> x));
        return trustService.ledgerLines(m -> true, from, to).stream().map(r -> {
            boolean hide = Boolean.TRUE.equals(restricted.get(r.matterNumber()));
            return new TrustLedgerRow(r.date(), r.number(), r.matterNumber(), hide ? "" : r.clientName(), r.type(), hide ? "" : (r.party() == null ? "" : r.party()),
                    r.reference() == null ? "" : r.reference(), r.moneyIn(), r.moneyOut(), r.balance());
        }).toList();
    }

    /** Work in progress and billing per matter. A restricted matter keeps its number but shows no title or client. */
    @Transactional
    public List<BillingRow> billing(boolean openOnly) {
        return billingService.rows(m -> true, openOnly).stream().map(r -> new BillingRow(r.number(), r.restricted() ? "(restricted)" : r.title(), r.restricted() ? "" : r.clientName(),
                r.billingType().name(), r.status(), r.hoursLogged(), r.unbilledTime(), r.unbilledCosts(), r.stagesPlanned(), r.billed(), r.paid(), r.outstanding(),
                r.retainerBalance())).toList();
    }

    /** Bills made in a period (invoices, retainers), with what has been paid on them. */
    @Transactional
    public List<FeeRow> fees(LocalDate from, LocalDate to) {
        return billingService.feeRows(from, to).stream().map(r -> new FeeRow(r.date(), r.matterNumber(), r.restricted() ? "(restricted)" : r.title(),
                r.restricted() ? "" : r.clientName(), r.kind(), r.invoiceNumber(), r.invoiceStatus(), r.fees(), r.costs(), r.retainerApplied(), r.amount(), r.paid(),
                r.outstanding())).toList();
    }
}
