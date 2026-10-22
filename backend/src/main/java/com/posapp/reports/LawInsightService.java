package com.posapp.reports;

import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.law.BillStatus;
import com.posapp.law.LawBill;
import com.posapp.law.LawBillRepository;
import com.posapp.law.LawBillingService;
import com.posapp.law.LawConflictCheck;
import com.posapp.law.LawConflictCheckRepository;
import com.posapp.law.LawCostRate;
import com.posapp.law.LawCostRateRepository;
import com.posapp.law.BillKind;
import com.posapp.law.LawDocument;
import com.posapp.law.LawDocumentRepository;
import com.posapp.law.LawDocumentVersion;
import com.posapp.law.LawDocumentVersionRepository;
import com.posapp.law.LawMatter;
import com.posapp.law.LawMatterExpense;
import com.posapp.law.LawMatterExpenseRepository;
import com.posapp.law.LawMatterPortal;
import com.posapp.law.LawMatterPortalRepository;
import com.posapp.law.LawMatterRepository;
import com.posapp.law.LawPortalAccount;
import com.posapp.law.LawPortalAccountRepository;
import com.posapp.law.LawPortalMessage;
import com.posapp.law.LawPortalMessageRepository;
import com.posapp.law.MatterStatus;
import com.posapp.law.DocReview;
import com.posapp.law.dto.LawBillingDtos.OverviewRow;
import com.posapp.reports.dto.LawInsightRows.*;
import com.posapp.timetracking.TimeEntry;
import com.posapp.timetracking.TimeEntryRepository;
import com.posapp.timetracking.TimeEntryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Law firm reports, second set: lawyer productivity, matter profitability, receivables ageing, documents and the client portal, the conflict-check log and
 * caseload by practice area and court. Like {@link LawReportService}, a restricted matter keeps its number but shows no title or client.
 */
@Service
public class LawInsightService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LawMatterRepository matterRepository;
    private final TimeEntryRepository timeRepository;
    private final LawBillingService billingService;
    private final LawMatterExpenseRepository costRepository;
    private final LawBillRepository billRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final LawDocumentRepository documentRepository;
    private final LawDocumentVersionRepository versionRepository;
    private final LawMatterPortalRepository portalRepository;
    private final LawPortalAccountRepository accountRepository;
    private final LawPortalMessageRepository messageRepository;
    private final LawConflictCheckRepository conflictRepository;
    private final LawCostRateRepository costRateRepository;

    public LawInsightService(LawMatterRepository matterRepository, TimeEntryRepository timeRepository, LawBillingService billingService, LawMatterExpenseRepository costRepository,
                             LawBillRepository billRepository, InvoiceRepository invoiceRepository, InvoiceService invoiceService, LawDocumentRepository documentRepository,
                             LawDocumentVersionRepository versionRepository, LawMatterPortalRepository portalRepository, LawPortalAccountRepository accountRepository,
                             LawPortalMessageRepository messageRepository, LawConflictCheckRepository conflictRepository, LawCostRateRepository costRateRepository) {
        this.matterRepository = matterRepository;
        this.timeRepository = timeRepository;
        this.billingService = billingService;
        this.costRepository = costRepository;
        this.billRepository = billRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.portalRepository = portalRepository;
        this.accountRepository = accountRepository;
        this.messageRepository = messageRepository;
        this.conflictRepository = conflictRepository;
        this.costRateRepository = costRateRepository;
    }

    private static String title(LawMatter m) {
        return m.isRestricted() ? "(restricted)" : m.getTitle();
    }

    private static String client(LawMatter m) {
        return m.isRestricted() ? "" : (m.getClientName() == null ? "" : m.getClientName());
    }

    static BigDecimal value(TimeEntry e) {
        return BigDecimal.valueOf(e.getMinutes()).multiply(e.getHourlyRate()).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static LocalDate day(Instant at) {
        return at == null ? null : at.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ------------------------------------------------------------------ lawyer productivity

    /** Time logged on law matters between two dates, per lawyer. Rejected time is not counted. */
    @Transactional(readOnly = true)
    public List<ProductivityRow> productivity(LocalDate from, LocalDate to) {
        Map<Long, Long> matterOfProject = new HashMap<>();
        for (LawMatter m : matterRepository.findAll()) {
            if (m.getTimeProjectId() != null) matterOfProject.put(m.getTimeProjectId(), m.getId());
        }
        record Acc(Set<Long> matters, long minutes, long billableMinutes, BigDecimal value, BigDecimal billed, BigDecimal notBilled) {
        }
        Map<String, Acc> by = new LinkedHashMap<>();
        for (TimeEntry e : timeRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(from, to)) {
            Long matterId = matterOfProject.get(e.getProjectId());
            if (matterId == null || e.getStatus() == TimeEntryStatus.REJECTED) continue;
            Acc a = by.getOrDefault(e.getUserName(), new Acc(new HashSet<>(), 0, 0, ZERO, ZERO, ZERO));
            a.matters().add(matterId);
            BigDecimal v = e.isBillable() ? value(e) : ZERO;
            boolean invoiced = e.getStatus() == TimeEntryStatus.INVOICED;
            by.put(e.getUserName(), new Acc(a.matters(), a.minutes() + e.getMinutes(), a.billableMinutes() + (e.isBillable() ? e.getMinutes() : 0), a.value().add(v),
                    a.billed().add(invoiced ? v : ZERO), a.notBilled().add(invoiced ? ZERO : v)));
        }
        return by.entrySet().stream().map(en -> {
            Acc a = en.getValue();
            BigDecimal h = hours(a.minutes());
            BigDecimal bh = hours(a.billableMinutes());
            return new ProductivityRow(en.getKey(), a.matters().size(), h, bh, h.subtract(bh), percent(bh, h), a.value(), a.billed(), a.notBilled());
        }).sorted(Comparator.comparing(ProductivityRow::billableValue).reversed().thenComparing(ProductivityRow::lawyer, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // ------------------------------------------------------------------ matter profitability

    /** A bill counts while it is not taken back and its invoice (if it has one) is not void. */
    private boolean live(LawBill b, Map<Long, InvoiceStatus> invoiceStatus) {
        if (b.getStatus() != BillStatus.ACTIVE) return false;
        if (b.getInvoiceId() == null) return true;
        InvoiceStatus st = invoiceStatus.get(b.getInvoiceId());
        return st != null && st != InvoiceStatus.VOID;
    }

    /**
     * Per matter: the value of the time put in, what has been billed and collected, what the time cost the firm (hours x each person's cost per hour, set by an
     * admin in the law settings) and the result. All logged time counts as a cost, billable or not; rejected time does not. Time of a person with no cost rate
     * counts as free and is shown in "hours without a cost rate", so a missing rate cannot pass for a profit.
     */
    @Transactional
    public List<ProfitRow> profitability(boolean openOnly) {
        Map<Long, LawMatter> matters = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        Map<Long, BigDecimal> rates = costRateRepository.findAll().stream().collect(Collectors.toMap(LawCostRate::getUserId, LawCostRate::getHourlyCost, (x, y) -> x));
        Map<Long, Long> matterOfProject = new HashMap<>();
        for (LawMatter m : matters.values()) {
            if (m.getTimeProjectId() != null) matterOfProject.put(m.getTimeProjectId(), m.getId());
        }
        Map<Long, BigDecimal> timeValue = new HashMap<>();
        Map<Long, BigDecimal> timeCost = new HashMap<>();
        Map<Long, Long> minutesNoRate = new HashMap<>();
        Map<Long, Long> minutesRated = new HashMap<>();
        for (TimeEntry e : timeRepository.findAll()) {
            Long matterId = matterOfProject.get(e.getProjectId());
            if (matterId == null || e.getStatus() == TimeEntryStatus.REJECTED) continue;
            if (e.isBillable()) timeValue.merge(matterId, value(e), BigDecimal::add);
            BigDecimal rate = rates.get(e.getUserId());
            if (rate == null) {
                minutesNoRate.merge(matterId, (long) e.getMinutes(), Long::sum);
            } else {
                minutesRated.merge(matterId, (long) e.getMinutes(), Long::sum);
                timeCost.merge(matterId, BigDecimal.valueOf(e.getMinutes()).multiply(rate).divide(SIXTY, 2, RoundingMode.HALF_UP), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> paidOut = new HashMap<>();
        for (LawMatterExpense c : costRepository.findAll()) paidOut.merge(c.getMatterId(), c.getAmount(), BigDecimal::add);
        Map<Long, InvoiceStatus> invoiceStatus = new HashMap<>();
        invoiceRepository.findAll().forEach(i -> invoiceStatus.put(i.getId(), i.getStatus()));
        Map<Long, BigDecimal> fees = new HashMap<>();
        Map<Long, BigDecimal> recovered = new HashMap<>();
        for (LawBill b : billRepository.findByStatus(BillStatus.ACTIVE)) {
            if (b.getKind() != BillKind.INVOICE || !live(b, invoiceStatus)) continue;
            fees.merge(b.getMatterId(), b.getFeesAmount(), BigDecimal::add);
            recovered.merge(b.getMatterId(), b.getExpensesAmount(), BigDecimal::add);
        }
        LocalDate today = LocalDate.now();
        List<ProfitRow> out = new ArrayList<>();
        for (OverviewRow r : billingService.rows(m -> true, openOnly)) {
            LawMatter m = matters.get(r.matterId());
            LocalDate end = m.getClosedDate() != null ? m.getClosedDate() : today;
            long days = m.getOpenedDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(m.getOpenedDate(), end));
            BigDecimal po = paidOut.getOrDefault(m.getId(), ZERO);
            BigDecimal rec = recovered.getOrDefault(m.getId(), ZERO);
            BigDecimal fb = fees.getOrDefault(m.getId(), ZERO);
            BigDecimal tc = timeCost.getOrDefault(m.getId(), ZERO);
            boolean anyRate = minutesRated.getOrDefault(m.getId(), 0L) > 0;
            BigDecimal profit = anyRate ? fb.subtract(tc).add(rec).subtract(po) : null;
            out.add(new ProfitRow(m.getNumber(), title(m), client(m), m.getBillingType().name(), m.getStatus().name(), m.getOpenedDate(), days, r.hoursLogged(),
                    timeValue.getOrDefault(m.getId(), ZERO), fb, r.billed(), r.paid(), r.outstanding(), percent(r.paid(), r.billed()), tc, po, rec, profit,
                    profit == null ? null : percent(profit, fb), hours(minutesNoRate.getOrDefault(m.getId(), 0L)), r.unbilledTime().add(r.unbilledCosts())));
        }
        return out;
    }

    // ------------------------------------------------------------------ receivables ageing

    /** Unpaid law invoices, each in the band for the days since the invoice date. */
    @Transactional
    public List<AgeingRow> ageing() {
        return ageing(m -> true);
    }

    /** The same, for the matters the caller may see (the overview page). */
    @Transactional
    public List<AgeingRow> ageing(java.util.function.Predicate<LawMatter> include) {
        Map<Long, LawMatter> matters = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        List<AgeingRow> out = new ArrayList<>();
        for (LawBill b : billRepository.findByStatus(BillStatus.ACTIVE)) {
            if (b.getInvoiceId() == null) continue;
            LawMatter m = matters.get(b.getMatterId());
            Invoice inv = invoiceRepository.findById(b.getInvoiceId()).orElse(null);
            if (m == null || !include.test(m) || inv == null || inv.getStatus() != InvoiceStatus.ISSUED) continue;
            InvoiceDto d = invoiceService.getById(inv.getId());
            BigDecimal owed = d.amountOutstanding() == null ? ZERO : d.amountOutstanding();
            if (owed.signum() <= 0) continue;
            LocalDate date = day(inv.getInvoiceDate());
            long days = Math.max(0, ChronoUnit.DAYS.between(date, today));
            out.add(new AgeingRow(m.getNumber(), title(m), client(m), b.getInvoiceNumber(), date, days, b.getAmount(), owed,
                    days <= 30 ? owed : ZERO, days > 30 && days <= 60 ? owed : ZERO, days > 60 && days <= 90 ? owed : ZERO, days > 90 ? owed : ZERO));
        }
        out.sort(Comparator.comparing(AgeingRow::days).reversed().thenComparing(AgeingRow::matter));
        return out;
    }

    // ------------------------------------------------------------------ documents and client portal

    @Transactional(readOnly = true)
    public List<DocumentsPortalRow> documentsAndPortal(boolean openOnly) {
        Map<Long, List<LawDocument>> docs = documentRepository.findAll().stream().filter(d -> !d.isArchived()).collect(Collectors.groupingBy(LawDocument::getMatterId));
        Map<Long, Long> matterOfDoc = new HashMap<>();
        for (List<LawDocument> list : docs.values()) list.forEach(d -> matterOfDoc.put(d.getId(), d.getMatterId()));
        Map<Long, Long> bytes = new HashMap<>();
        for (LawDocumentVersion v : versionRepository.findAll()) {
            Long matterId = matterOfDoc.get(v.getDocumentId());
            if (matterId != null) bytes.merge(matterId, v.getSizeBytes(), Long::sum);
        }
        Map<Long, LawMatterPortal> portals = portalRepository.findAll().stream().collect(Collectors.toMap(LawMatterPortal::getMatterId, Function.identity(), (x, y) -> x));
        Map<Long, List<LawPortalAccount>> people = accountRepository.findAll().stream().filter(LawPortalAccount::isActive).collect(Collectors.groupingBy(LawPortalAccount::getCustomerId));
        Map<Long, Long> unanswered = messageRepository.findAll().stream().filter(x -> x.isFromClient() && !x.isReadByStaff())
                .collect(Collectors.groupingBy(LawPortalMessage::getMatterId, Collectors.counting()));
        List<DocumentsPortalRow> out = new ArrayList<>();
        for (LawMatter m : matterRepository.findAllByOrderByOpenedDateDescIdDesc()) {
            if (openOnly && m.getStatus() == MatterStatus.CLOSED) continue;
            List<LawDocument> list = docs.getOrDefault(m.getId(), List.of());
            LawMatterPortal p = portals.get(m.getId());
            List<LawPortalAccount> ppl = m.getClientCustomerId() == null ? List.of() : people.getOrDefault(m.getClientCustomerId(), List.of());
            boolean open = p != null && p.isEnabled();
            LocalDate last = ppl.stream().map(LawPortalAccount::getLastLoginAt).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).map(LawInsightService::day).orElse(null);
            long unread = unanswered.getOrDefault(m.getId(), 0L);
            if (list.isEmpty() && !open && unread == 0) continue;
            out.add(new DocumentsPortalRow(m.getNumber(), title(m), client(m), m.getStatus().name(), list.size(),
                    BigDecimal.valueOf(bytes.getOrDefault(m.getId(), 0L)).divide(BigDecimal.valueOf(1024L * 1024L), 2, RoundingMode.HALF_UP),
                    (int) list.stream().filter(LawDocument::isConfidential).count(), (int) list.stream().filter(LawDocument::isSharedWithClient).count(),
                    (int) list.stream().filter(d -> d.isFromClient() && d.getReviewStatus() == DocReview.PENDING).count(), open ? "Open" : "Closed", open ? ppl.size() : 0,
                    last == null || !open ? "" : last.toString(), (int) unread));
        }
        return out;
    }

    // ------------------------------------------------------------------ conflict-check log

    @Transactional(readOnly = true)
    public List<ConflictLogRow> conflictLog(LocalDate from, LocalDate to) {
        List<ConflictLogRow> out = new ArrayList<>();
        for (LawConflictCheck c : conflictRepository.findAll()) {
            LocalDate d = day(c.getCreatedAt());
            if (d.isBefore(from) || d.isAfter(to)) continue;
            out.add(new ConflictLogRow(c.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T', ' '), c.getCheckedByName() == null ? "" : c.getCheckedByName(),
                    c.getQuery() == null ? "" : c.getQuery(), c.getMatterNumber() == null ? "" : c.getMatterNumber(), c.getHits(), c.getHits() == 0 ? "No conflicts found" : "Possible conflicts"));
        }
        out.sort(Comparator.comparing(ConflictLogRow::when).reversed());
        return out;
    }

    // ------------------------------------------------------------------ caseload

    /** Matters by practice area and court: opened and closed in the period, and how many are open or on hold now. */
    @Transactional(readOnly = true)
    public List<CaseloadRow> caseload(LocalDate from, LocalDate to) {
        record Key(String area, String court) {
        }
        Map<Key, List<LawMatter>> by = matterRepository.findAll().stream().collect(Collectors.groupingBy(
                m -> new Key(m.getPracticeArea() == null || m.getPracticeArea().isBlank() ? "(none)" : m.getPracticeArea(),
                        m.getCourtOrForum() == null || m.getCourtOrForum().isBlank() ? "" : m.getCourtOrForum().trim())));
        List<CaseloadRow> out = new ArrayList<>();
        for (Map.Entry<Key, List<LawMatter>> e : by.entrySet()) {
            List<LawMatter> list = e.getValue();
            List<LawMatter> closedIn = list.stream().filter(m -> m.getClosedDate() != null && !m.getClosedDate().isBefore(from) && !m.getClosedDate().isAfter(to)).toList();
            int opened = (int) list.stream().filter(m -> m.getOpenedDate() != null && !m.getOpenedDate().isBefore(from) && !m.getOpenedDate().isAfter(to)).count();
            int openNow = (int) list.stream().filter(m -> m.getStatus() == MatterStatus.OPEN).count();
            int onHold = (int) list.stream().filter(m -> m.getStatus() == MatterStatus.ON_HOLD).count();
            if (opened == 0 && closedIn.isEmpty() && openNow == 0 && onHold == 0) continue;
            BigDecimal avg = null;
            List<LawMatter> dated = closedIn.stream().filter(m -> m.getOpenedDate() != null).toList();
            if (!dated.isEmpty()) {
                long total = dated.stream().mapToLong(m -> Math.max(0, ChronoUnit.DAYS.between(m.getOpenedDate(), m.getClosedDate()))).sum();
                avg = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(dated.size()), 0, RoundingMode.HALF_UP);
            }
            out.add(new CaseloadRow(e.getKey().area(), e.getKey().court(), opened, closedIn.size(), openNow, onHold, avg));
        }
        out.sort(Comparator.comparing(CaseloadRow::practiceArea, String.CASE_INSENSITIVE_ORDER).thenComparing(CaseloadRow::court, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
