package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rent. Signing a lease lays out its rent: one charge per payment period. A charge is invoiced to the tenant (who is a customer by then) through the normal Invoices, by hand or by
 * the morning sweep, and the tenant pays on that invoice as anyone does. Overdue rent (invoiced, unpaid, past its due date) is the arrears.
 */
@Service
public class PmRentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PmRentChargeRepository repository;
    private final PmLeaseRepository leaseRepository;
    private final PmTenantService tenantService;
    private final PmSettingsService settings;
    private final PmViews views;
    private final InvoiceService invoiceService;
    private final CatalogService catalogService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;
    private final PmRentReminderRepository reminderRepository;
    private final PmLateFeeRepository lateFeeRepository;
    private final PmChargeService chargeService;

    public PmRentService(PmRentChargeRepository repository, PmLeaseRepository leaseRepository, PmTenantService tenantService, PmSettingsService settings, PmViews views,
                         InvoiceService invoiceService, CatalogService catalogService, PropertyManagementAccess access, AuditLogService auditLogService,
                         PlatformTransactionManager transactionManager, PmRentReminderRepository reminderRepository, PmLateFeeRepository lateFeeRepository, PmChargeService chargeService) {
        this.chargeService = chargeService;
        this.reminderRepository = reminderRepository;
        this.lateFeeRepository = lateFeeRepository;
        this.repository = repository;
        this.leaseRepository = leaseRepository;
        this.tenantService = tenantService;
        this.settings = settings;
        this.views = views;
        this.invoiceService = invoiceService;
        this.catalogService = catalogService;
        this.access = access;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ the schedule

    record Period(LocalDate start, LocalDate end, LocalDate due, BigDecimal amount, boolean prorated) {
    }

    /** The day rent falls due for a period starting on {@code start}: the lease's due day of that month, or the first day of the period if that day has passed. */
    static LocalDate dueFor(LocalDate start, int dueDay) {
        LocalDate d = start.withDayOfMonth(Math.min(dueDay, start.lengthOfMonth()));
        return d.isBefore(start) ? start : d;
    }

    static BigDecimal prorate(BigDecimal rent, long days, long fullDays) {
        return rent.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(fullDays), 2, RoundingMode.HALF_UP);
    }

    /** Every payment period of the lease. A last period shorter than a full one is charged by the day. Periods ending before {@code billFrom} are left out. */
    static List<Period> schedule(PmLease l, LocalDate billFrom) {
        List<Period> out = new ArrayList<>();
        int n = l.getFrequency().months();
        for (int k = 0; ; k++) {
            LocalDate start = l.getStartDate().plusMonths((long) n * k);
            if (start.isAfter(l.getEndDate())) break;
            LocalDate next = l.getStartDate().plusMonths((long) n * (k + 1));
            LocalDate end = next.minusDays(1);
            boolean full = !end.isAfter(l.getEndDate());
            if (!full) end = l.getEndDate();
            if (billFrom != null && end.isBefore(billFrom)) continue;
            BigDecimal amount = full ? l.getRent() : prorate(l.getRent(), ChronoUnit.DAYS.between(start, end) + 1, ChronoUnit.DAYS.between(start, next));
            out.add(new Period(start, end, dueFor(start, l.getDueDay()), amount, !full));
        }
        return out;
    }

    /** Lays out the rent of a lease that has just been signed. */
    @Transactional
    public int generate(PmLease l, LocalDate billFrom) {
        if (repository.existsByLeaseId(l.getId())) return 0;
        int count = 0;
        for (Period p : schedule(l, billFrom)) {
            PmRentCharge c = new PmRentCharge();
            c.setLeaseId(l.getId());
            c.setPeriodStart(p.start());
            c.setPeriodEnd(p.end());
            c.setDueDate(p.due());
            c.setAmount(p.amount());
            c.setProrated(p.prorated());
            repository.save(c);
            count++;
        }
        return count;
    }

    /** The tenant's last day was {@code lastDay}: rent for later periods is dropped, and a period that straddles it is cut short and charged by the day. Rent already invoiced stays. */
    @Transactional
    public void endRentOn(PmLease l, LocalDate lastDay) {
        int n = l.getFrequency().months();
        for (PmRentCharge c : repository.findByLeaseIdOrderByPeriodStartAsc(l.getId())) {
            if (c.getStatus() == RentChargeStatus.INVOICED) continue;
            if (c.getPeriodStart().isAfter(lastDay)) {
                repository.delete(c);
            } else if (c.getPeriodEnd().isAfter(lastDay) && c.getStatus() == RentChargeStatus.PENDING) {
                long full = ChronoUnit.DAYS.between(c.getPeriodStart(), c.getPeriodStart().plusMonths(n));
                c.setPeriodEnd(lastDay);
                c.setAmount(prorate(l.getRent(), ChronoUnit.DAYS.between(c.getPeriodStart(), lastDay) + 1, full));
                c.setProrated(true);
                repository.save(c);
            }
        }
    }

    /** The lease's due day changed: charges not yet invoiced fall due on the new day. */
    @Transactional
    public void refreshDueDates(PmLease l) {
        for (PmRentCharge c : repository.findByLeaseIdOrderByPeriodStartAsc(l.getId())) {
            if (c.getStatus() == RentChargeStatus.INVOICED) continue;
            c.setDueDate(dueFor(c.getPeriodStart(), l.getDueDay()));
            repository.save(c);
        }
    }

    // ------------------------------------------------------------------ invoicing

    /** The service product rent invoices are made under (made the first time it is needed). */
    @Transactional
    public Product rentProduct() {
        PmSettings s = settings.entity();
        if (s.getRentProductId() != null) {
            try {
                return catalogService.getProductOrThrow(s.getRentProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        ProductRequest req = new ProductRequest();
        req.setName("Rent");
        req.setDescription("Property rent");
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(0);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        s.setRentProductId(created.getId());
        settings.save(s);
        return created;
    }

    private InvoiceDto invoice(Long id) {
        if (id == null) return null;
        try {
            return invoiceService.getById(id);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    private static boolean live(InvoiceDto i) {
        return i != null && !"VOID".equals(i.status());
    }

    private PmRentCharge find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Rent charge not found: " + id));
    }

    /** Invoices one charge: an invoice for the tenant (as a customer), issued at once. */
    @Transactional
    public InvoiceDto invoiceCharge(Long chargeId) {
        PmRentCharge c = find(chargeId);
        if (c.getStatus() == RentChargeStatus.WAIVED) throw ApiException.conflict("This rent was waived. Restore it first.");
        if (c.getStatus() == RentChargeStatus.INVOICED && live(invoice(c.getInvoiceId()))) throw ApiException.conflict("This rent is already invoiced (" + c.getInvoiceNumber() + ").");
        PmLease l = leaseRepository.findById(c.getLeaseId()).orElseThrow(() -> ApiException.notFound("Lease not found."));
        PmTenant t = tenantService.entity(l.getTenantId());
        Customer cust = tenantService.ensureCustomer(t);
        Snapshot s = views.snapshot();
        PmUnit u = s.units.get(l.getUnitId());
        PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(cust.getId());
        req.setDueDate(c.getDueDate());
        req.setNotes("Lease " + l.getCode() + ": rent for " + c.getPeriodStart() + " to " + c.getPeriodEnd());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(rentProduct().getId());
        line.setQuantity(1);
        line.setUnitPrice(c.getAmount());
        line.setDescription("Rent" + (u == null ? "" : " for " + (p == null ? "" : p.getName() + ", ") + "unit " + u.getUnitNo()) + ", " + c.getPeriodStart() + " to " + c.getPeriodEnd()
                + (c.isProrated() ? " (part period)" : ""));
        List<InvoiceRequest.LineEntry> lines = new ArrayList<>();
        lines.add(line);
        chargeService.release(c);
        List<PmChargeService.ChargeLine> extraLines = chargeService.collect(l, c);
        for (PmChargeService.ChargeLine x : extraLines) {
            if (x.amount().signum() <= 0) continue;
            InvoiceRequest.LineEntry e = new InvoiceRequest.LineEntry();
            e.setProductId(chargeService.productIdFor(x));
            e.setQuantity(1);
            e.setUnitPrice(x.amount());
            e.setDescription(x.description());
            lines.add(e);
        }
        req.setLines(lines);
        InvoiceDto inv = invoiceService.issue(invoiceService.createDraft(req).id());
        chargeService.record(c, l, extraLines.stream().filter(x -> x.amount().signum() > 0 || x.extra() != null).toList(), inv.invoiceNumber());
        c.setStatus(RentChargeStatus.INVOICED);
        c.setInvoiceId(inv.id());
        c.setInvoiceNumber(inv.invoiceNumber());
        c.setSettled(false);
        repository.save(c);
        return inv;
    }

    /**
     * Invoices the rent that is ready: charges not yet invoiced whose due date is on or before {@code through} (default: today plus the invoicing lead time in the settings),
     * for one lease or all of them. Each charge is its own transaction, so one failure does not hold up the rest.
     */
    public InvoiceRun runInvoicing(Long leaseId, LocalDate through, String by) {
        int ahead = settings.entity().getInvoiceAheadDays();
        LocalDate limit = through != null ? through : LocalDate.now().plusDays(ahead);
        List<PmRentCharge> due = repository.findByStatus(RentChargeStatus.PENDING).stream()
                .filter(c -> leaseId == null || leaseId.equals(c.getLeaseId()))
                .filter(c -> !c.getDueDate().isAfter(limit))
                .sorted(Comparator.comparing(PmRentCharge::getDueDate).thenComparing(PmRentCharge::getId)).toList();
        int ok = 0;
        BigDecimal total = ZERO;
        List<String> problems = new ArrayList<>();
        for (PmRentCharge c : due) {
            try {
                InvoiceDto inv = tx.execute(st -> invoiceCharge(c.getId()));
                ok++;
                total = total.add(inv.totalAmount());
            } catch (Exception e) {
                problems.add("Rent due " + c.getDueDate() + " (lease " + c.getLeaseId() + "): " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        if (ok > 0 || !problems.isEmpty()) {
            auditLogService.record("PM_RENT_INVOICED", by + " invoiced " + ok + " rent charge" + (ok == 1 ? "" : "s") + (problems.isEmpty() ? "" : "; " + problems.size() + " failed"));
        }
        return new InvoiceRun(ok, problems.size(), total, problems);
    }

    @Transactional
    public InvoiceRun invoiceDue(Actor a, Long leaseId, LocalDate through) {
        access.on();
        return runInvoicing(leaseId, through, PropertyManagementAccess.name(a.user()));
    }

    @Transactional
    public RentChargeRow invoiceOne(Actor a, Long chargeId) {
        access.on();
        invoiceCharge(chargeId);
        PmRentCharge c = find(chargeId);
        return rows(List.of(c), views.snapshot(), true).get(0);
    }

    @Transactional
    public RentChargeRow waive(Actor a, Long id, String reason) {
        access.on();
        PmRentCharge c = find(id);
        if (c.getStatus() == RentChargeStatus.INVOICED) throw ApiException.conflict("This rent is already invoiced. Void the invoice in Invoices first if it should not be charged.");
        c.setStatus(RentChargeStatus.WAIVED);
        c.setWaivedReason(PmUtil.required(reason, 500, "The reason"));
        repository.save(c);
        auditLogService.record("PM_RENT_WAIVED", "Waived rent " + c.getPeriodStart() + " to " + c.getPeriodEnd() + ": " + c.getWaivedReason(), "PmRentCharge", id);
        return rows(List.of(c), views.snapshot(), true).get(0);
    }

    @Transactional
    public RentChargeRow restore(Actor a, Long id) {
        access.on();
        PmRentCharge c = find(id);
        if (c.getStatus() != RentChargeStatus.WAIVED) throw ApiException.conflict("Only waived rent can be restored.");
        c.setStatus(RentChargeStatus.PENDING);
        c.setWaivedReason(null);
        repository.save(c);
        auditLogService.record("PM_RENT_RESTORED", "Restored rent " + c.getPeriodStart() + " to " + c.getPeriodEnd(), "PmRentCharge", id);
        return rows(List.of(c), views.snapshot(), true).get(0);
    }

    // ------------------------------------------------------------------ rows

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    /**
     * The charges as the screens show them. A charge whose invoice was seen paid is remembered as settled and is not looked up again, unless {@code fresh}. The remembering is saved
     * here, so callers need no transaction of their own.
     */
    List<RentChargeRow> rows(List<PmRentCharge> charges, Snapshot s, boolean fresh) {
        LocalDate today = s.today;
        int ahead = settings.entity().getInvoiceAheadDays();
        Map<Long, InvoiceDto> invoices = new HashMap<>();
        List<Long> settle = new ArrayList<>();
        List<Long> unsettle = new ArrayList<>();
        List<RentChargeRow> out = new ArrayList<>();
        List<Long> chargeIds = charges.stream().map(PmRentCharge::getId).toList();
        Map<Long, List<PmRentReminder>> remindersOf = new HashMap<>();
        Map<Long, PmLateFee> feeOf = new HashMap<>();
        if (!chargeIds.isEmpty()) {
            for (PmRentReminder r : reminderRepository.findByRentChargeIdInOrderBySentAtAsc(chargeIds)) remindersOf.computeIfAbsent(r.getRentChargeId(), k -> new ArrayList<>()).add(r);
            for (PmLateFee f : lateFeeRepository.findByRentChargeIdIn(chargeIds)) feeOf.put(f.getRentChargeId(), f);
        }
        for (PmRentCharge c : charges) {
            PmLease l = s.byId.get(c.getLeaseId());
            PmUnit u = l == null ? null : s.units.get(l.getUnitId());
            PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
            PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
            PmTenant t = l == null ? null : s.tenants.get(l.getTenantId());
            String status;
            BigDecimal billed = null, paid = null, outstanding = null;
            Long invoiceId = null;
            String invoiceNumber = null;
            Long daysOverdue = null;
            if (c.getStatus() == RentChargeStatus.WAIVED) {
                status = "WAIVED";
            } else if (c.getStatus() == RentChargeStatus.PENDING) {
                status = "NOT_INVOICED";
            } else if (c.isSettled() && !fresh) {
                status = "PAID";
                billed = c.getAmount().add(nz(c.getExtras()));
                paid = billed;
                outstanding = ZERO;
                invoiceId = c.getInvoiceId();
                invoiceNumber = c.getInvoiceNumber();
            } else {
                InvoiceDto inv = invoices.computeIfAbsent(c.getInvoiceId(), id -> invoice(id));
                if (!live(inv)) {
                    status = "NOT_INVOICED";
                    if (c.isSettled()) unsettle.add(c.getId());
                } else {
                    billed = nz(inv.totalAmount());
                    paid = nz(inv.amountPaid());
                    outstanding = nz(inv.amountOutstanding());
                    invoiceId = inv.id();
                    invoiceNumber = inv.invoiceNumber();
                    if (outstanding.signum() <= 0) {
                        status = "PAID";
                        if (!c.isSettled()) settle.add(c.getId());
                    } else {
                        if (c.isSettled()) unsettle.add(c.getId());
                        boolean late = c.getDueDate().isBefore(today);
                        status = late ? "OVERDUE" : paid.signum() > 0 ? "PARTLY_PAID" : "UNPAID";
                        if (late) daysOverdue = ChronoUnit.DAYS.between(c.getDueDate(), today);
                    }
                }
            }
            boolean ready = "NOT_INVOICED".equals(status) && !c.getDueDate().isAfter(today.plusDays(ahead));
            boolean after = l != null && l.getStatus() == LeaseStatus.TERMINATED && l.getTerminatedDate() != null && c.getPeriodStart().isAfter(l.getTerminatedDate());
            out.add(new RentChargeRow(c.getId(), c.getLeaseId(), l == null ? null : l.getCode(), u == null ? null : u.getId(), u == null ? null : u.getUnitNo(), p == null ? null : p.getId(),
                    p == null ? null : p.getName(), o == null ? null : o.getId(), o == null ? null : o.getName(), t == null ? null : t.getId(), t == null ? null : t.getName(),
                    c.getPeriodStart(), c.getPeriodEnd(), c.getDueDate(), c.getAmount(), c.isProrated(), status, invoiceId, invoiceNumber, billed, paid, outstanding, daysOverdue,
                    c.getWaivedReason(), ready, after, remindersOf.getOrDefault(c.getId(), List.of()).size(), lastOn(remindersOf.get(c.getId())),
                    feeOf.containsKey(c.getId()) ? feeOf.get(c.getId()).getAmount() : null, feeOf.containsKey(c.getId()) ? feeOf.get(c.getId()).getInvoiceNumber() : null, nz(c.getExtras())));
        }
        if (!settle.isEmpty() || !unsettle.isEmpty()) {
            tx.executeWithoutResult(st -> {
                for (Long id : settle) repository.findById(id).ifPresent(c -> { c.setSettled(true); repository.save(c); });
                for (Long id : unsettle) repository.findById(id).ifPresent(c -> { c.setSettled(false); repository.save(c); });
            });
        }
        return out;
    }

    private static LocalDate lastOn(List<PmRentReminder> list) {
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1).getSentAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private boolean leaseMatches(Snapshot s, PmLease l, Long tenantId, Long propertyId, Long ownerId) {
        if (l == null) return false;
        if (tenantId != null && !tenantId.equals(l.getTenantId())) return false;
        PmUnit u = s.units.get(l.getUnitId());
        if (propertyId != null && (u == null || !propertyId.equals(u.getPropertyId()))) return false;
        if (ownerId != null) {
            PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
            if (p == null || !PmViews.ownsNow(s, ownerId, p.getId())) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ reading

    @Transactional
    public RentSchedule schedule(Actor a, Long leaseId) {
        access.on();
        Snapshot s = views.snapshot();
        PmLease l = s.byId.get(leaseId);
        if (l == null) throw ApiException.notFound("Lease not found: " + leaseId);
        List<RentChargeRow> rows = rows(repository.findByLeaseIdOrderByPeriodStartAsc(leaseId), s, true);
        BigDecimal billed = ZERO, paid = ZERO, outstanding = ZERO, overdue = ZERO, notYet = ZERO;
        long after = 0;
        for (RentChargeRow r : rows) {
            if (r.billed() != null) {
                billed = billed.add(r.billed());
                paid = paid.add(r.paid());
                outstanding = outstanding.add(r.outstanding());
                if ("OVERDUE".equals(r.status())) overdue = overdue.add(r.outstanding());
            } else if (!"WAIVED".equals(r.status())) {
                notYet = notYet.add(r.amount());
            }
            if (r.afterLeaving() && r.billed() != null) after++;
        }
        return new RentSchedule(l.getId(), l.getCode(), rows, billed, paid, outstanding, overdue, notYet, after, a.manager());
    }

    /**
     * Rent charges for the Rent screen. status: TO_INVOICE (ready to invoice), NOT_INVOICED, OPEN (invoiced and unpaid), OVERDUE, PAID, WAIVED; empty for all. Without a status,
     * or with PAID, NOT_INVOICED or WAIVED, only charges due from {@code from} (default six months back) to {@code to} (default a month ahead) are shown.
     */
    @Transactional
    public List<RentChargeRow> list(String status, Long leaseId, Long tenantId, Long propertyId, Long ownerId, LocalDate from, LocalDate to) {
        access.on();
        Snapshot s = views.snapshot();
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean windowed = st.isEmpty() || st.equals("PAID") || st.equals("WAIVED") || st.equals("NOT_INVOICED");
        LocalDate lo = from != null ? from : windowed ? s.today.minusMonths(6) : null;
        LocalDate hi = to != null ? to : windowed ? s.today.plusMonths(1) : null;
        List<PmRentCharge> pre = (leaseId != null ? repository.findByLeaseIdOrderByPeriodStartAsc(leaseId) : repository.findAll()).stream()
                .filter(c -> leaseMatches(s, s.byId.get(c.getLeaseId()), tenantId, propertyId, ownerId))
                .filter(c -> lo == null || !c.getDueDate().isBefore(lo))
                .filter(c -> hi == null || !c.getDueDate().isAfter(hi))
                .filter(c -> switch (st) {
                    case "TO_INVOICE", "NOT_INVOICED" -> c.getStatus() == RentChargeStatus.PENDING || c.getStatus() == RentChargeStatus.INVOICED && !c.isSettled();
                    case "WAIVED" -> c.getStatus() == RentChargeStatus.WAIVED;
                    case "OPEN", "OVERDUE" -> c.getStatus() == RentChargeStatus.INVOICED && !c.isSettled();
                    case "PAID" -> c.getStatus() == RentChargeStatus.INVOICED;
                    default -> true;
                })
                .sorted(Comparator.comparing(PmRentCharge::getDueDate).thenComparing(PmRentCharge::getId)).toList();
        return rows(pre, s, false).stream().filter(r -> switch (st) {
            case "TO_INVOICE" -> r.readyToInvoice();
            case "NOT_INVOICED" -> "NOT_INVOICED".equals(r.status());
            case "OPEN" -> "UNPAID".equals(r.status()) || "PARTLY_PAID".equals(r.status()) || "OVERDUE".equals(r.status());
            case "OVERDUE" -> "OVERDUE".equals(r.status());
            case "PAID" -> "PAID".equals(r.status());
            case "WAIVED" -> "WAIVED".equals(r.status());
            default -> true;
        }).toList();
    }

    @Transactional
    public Arrears arrears(Long tenantId, Long propertyId, Long ownerId) {
        access.on();
        return arrearsInternal(views.snapshot(), tenantId, propertyId, ownerId);
    }

    Arrears arrearsInternal(Snapshot s, Long tenantId, Long propertyId, Long ownerId) {
        List<PmRentCharge> pre = repository.findByStatus(RentChargeStatus.INVOICED).stream()
                .filter(c -> !c.isSettled() && c.getDueDate().isBefore(s.today))
                .filter(c -> leaseMatches(s, s.byId.get(c.getLeaseId()), tenantId, propertyId, ownerId)).toList();
        List<RentChargeRow> late = rows(pre, s, false).stream().filter(r -> "OVERDUE".equals(r.status())).toList();
        Map<Long, List<RentChargeRow>> byTenant = new LinkedHashMap<>();
        late.stream().sorted(Comparator.comparing(RentChargeRow::dueDate)).forEach(r -> byTenant.computeIfAbsent(r.tenantId(), k -> new ArrayList<>()).add(r));
        List<ArrearsRow> rows = new ArrayList<>();
        BigDecimal t30 = ZERO, t60 = ZERO, t90 = ZERO, t90p = ZERO;
        for (Map.Entry<Long, List<RentChargeRow>> e : byTenant.entrySet()) {
            PmTenant t = s.tenants.get(e.getKey());
            BigDecimal b30 = ZERO, b60 = ZERO, b90 = ZERO, b90p = ZERO;
            for (RentChargeRow r : e.getValue()) {
                long d = r.daysOverdue();
                if (d <= 30) b30 = b30.add(r.outstanding());
                else if (d <= 60) b60 = b60.add(r.outstanding());
                else if (d <= 90) b90 = b90.add(r.outstanding());
                else b90p = b90p.add(r.outstanding());
            }
            BigDecimal total = b30.add(b60).add(b90).add(b90p);
            RentChargeRow oldest = e.getValue().get(0);
            rows.add(new ArrearsRow(e.getKey(), t == null ? null : t.getCode(), t == null ? null : t.getName(), t == null ? null : t.getPhone(), e.getValue().size(), total, b30, b60, b90, b90p,
                    oldest.dueDate(), oldest.daysOverdue(), e.getValue()));
            t30 = t30.add(b30);
            t60 = t60.add(b60);
            t90 = t90.add(b90);
            t90p = t90p.add(b90p);
        }
        rows.sort(Comparator.comparing(ArrearsRow::outstanding).reversed());
        return new Arrears(t30.add(t60).add(t90).add(t90p), t30, t60, t90, t90p, rows.size(), rows);
    }

    /** For the overview: what is ready to invoice, and the arrears. */
    @Transactional
    public RentSummary summary(Snapshot s) {
        int ahead = settings.entity().getInvoiceAheadDays();
        List<PmRentCharge> ready = repository.findByStatus(RentChargeStatus.PENDING).stream().filter(c -> !c.getDueDate().isAfter(s.today.plusDays(ahead))).toList();
        BigDecimal amount = ready.stream().map(PmRentCharge::getAmount).reduce(ZERO, BigDecimal::add);
        Arrears ar = arrearsInternal(s, null, null, null);
        return new RentSummary(ready.size(), amount, ar.total(), ar.tenants());
    }

    /** Rent by the month it fell due, from {@code from} to {@code to} (default: the last twelve months). {@code openOnly} leaves out months with nothing outstanding or not yet invoiced. */
    @Transactional
    public List<CollectionRow> collection(LocalDate from, LocalDate to, boolean openOnly) {
        access.on();
        Snapshot s = views.snapshot();
        LocalDate hi = to != null ? to : s.today.withDayOfMonth(s.today.lengthOfMonth());
        LocalDate lo = from != null ? from : hi.minusMonths(11).withDayOfMonth(1);
        List<PmRentCharge> in = repository.findAll().stream().filter(c -> c.getStatus() != RentChargeStatus.WAIVED)
                .filter(c -> !c.getDueDate().isBefore(lo) && !c.getDueDate().isAfter(hi)).toList();
        Map<String, List<RentChargeRow>> byMonth = new TreeMap<>();
        for (RentChargeRow r : rows(in, s, false)) byMonth.computeIfAbsent(YearMonth.from(r.dueDate()).toString(), k -> new ArrayList<>()).add(r);
        List<CollectionRow> out = new ArrayList<>();
        for (Map.Entry<String, List<RentChargeRow>> e : byMonth.entrySet()) {
            BigDecimal invoiced = ZERO, collected = ZERO, outstanding = ZERO, notYet = ZERO;
            for (RentChargeRow r : e.getValue()) {
                if (r.billed() != null) {
                    invoiced = invoiced.add(r.billed());
                    collected = collected.add(r.paid());
                    outstanding = outstanding.add(r.outstanding());
                } else {
                    notYet = notYet.add(r.amount());
                }
            }
            if (openOnly && outstanding.signum() == 0 && notYet.signum() == 0) continue;
            BigDecimal pct = invoiced.signum() == 0 ? ZERO : collected.multiply(BigDecimal.valueOf(100)).divide(invoiced, 1, RoundingMode.HALF_UP);
            out.add(new CollectionRow(e.getKey(), e.getValue().size(), invoiced, collected, outstanding, notYet, pct));
        }
        return out;
    }

    /** Charges of these leases, for callers that need to know whether any rent was invoiced. */
    public boolean hasInvoicedRent(Long leaseId) {
        return repository.findByLeaseIdOrderByPeriodStartAsc(leaseId).stream().anyMatch(c -> c.getStatus() == RentChargeStatus.INVOICED);
    }
}
