package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtChargeDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utilities and service charges. A charge type is a name (Service charge, Water...). A lease carries fixed monthly charges and metered ones; a meter reading or a one-off charge waits
 * until the next rent invoice of the lease takes it. The rent invoice then has a line for each, and what it took is kept (so the screens and the report can show it). The money
 * is the agency's: the owner statement only counts the rent part of what the tenant pays, rent first.
 */
@Service
public class PmChargeService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PmChargeTypeRepository types;
    private final PmLeaseChargeRepository leaseCharges;
    private final PmExtraChargeRepository extras;
    private final PmBilledChargeRepository billed;
    private final PmRentChargeRepository rentCharges;
    private final PmLeaseRepository leaseRepository;
    private final PmSettingsService settings;
    private final PmViews views;
    private final CatalogService catalogService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmChargeService(PmChargeTypeRepository types, PmLeaseChargeRepository leaseCharges, PmExtraChargeRepository extras, PmBilledChargeRepository billed, PmRentChargeRepository rentCharges,
                           PmLeaseRepository leaseRepository, PmSettingsService settings, PmViews views, CatalogService catalogService, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.types = types;
        this.leaseCharges = leaseCharges;
        this.extras = extras;
        this.billed = billed;
        this.rentCharges = rentCharges;
        this.leaseRepository = leaseRepository;
        this.settings = settings;
        this.views = views;
        this.catalogService = catalogService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ charge types

    private PmChargeType type(Long id) {
        return types.findById(id).orElseThrow(() -> ApiException.notFound("Charge type not found: " + id));
    }

    private boolean inUse(Long typeId) {
        return leaseCharges.existsByChargeTypeId(typeId) || extras.existsByChargeTypeId(typeId);
    }

    private ChargeTypeDto typeDto(PmChargeType t, Map<Long, Long> counts) {
        return new ChargeTypeDto(t.getId(), t.getName(), t.getKind(), t.getUnitLabel(), t.getDefaultAmount(), t.isActive(), counts.getOrDefault(t.getId(), 0L), inUse(t.getId()));
    }

    private Map<Long, Long> leaseCounts() {
        return leaseCharges.findAll().stream().collect(Collectors.groupingBy(PmLeaseCharge::getChargeTypeId, Collectors.counting()));
    }

    @Transactional(readOnly = true)
    public List<ChargeTypeDto> listTypes(boolean activeOnly) {
        access.on();
        Map<Long, Long> counts = leaseCounts();
        return types.findAllByOrderByNameAsc().stream().filter(t -> !activeOnly || t.isActive()).map(t -> typeDto(t, counts)).toList();
    }

    private void applyType(PmChargeType t, ChargeTypeRequest r, boolean isNew) {
        if (isNew || r.name() != null) {
            String name = PmUtil.required(r.name(), 100, "The name");
            for (PmChargeType o : types.findAll()) {
                if (!o.getId().equals(t.getId()) && o.getName().equalsIgnoreCase(name)) throw ApiException.conflict("There is already a charge type called " + o.getName() + ".");
            }
            t.setName(name);
        }
        if (r.kind() != null && r.kind() != t.getKind()) {
            if (!isNew && inUse(t.getId())) throw ApiException.conflict("This charge type is already on leases, so its kind cannot change. Make a new type instead.");
            t.setKind(r.kind());
        }
        if (r.unitLabel() != null) t.setUnitLabel(PmUtil.clean(r.unitLabel(), 20, "The unit"));
        if (t.getKind() == PmChargeKind.METERED && (t.getUnitLabel() == null || t.getUnitLabel().isBlank())) throw ApiException.badRequest("Say what the meter counts (for example m3 or kWh).");
        if (t.getKind() == PmChargeKind.FIXED) t.setUnitLabel(null);
        if (Boolean.TRUE.equals(r.clearDefaultAmount())) t.setDefaultAmount(null);
        else if (r.defaultAmount() != null) {
            if (r.defaultAmount().signum() < 0) throw ApiException.badRequest("The usual amount cannot be negative.");
            t.setDefaultAmount(r.defaultAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public ChargeTypeDto createType(ChargeTypeRequest r) {
        access.on();
        PmChargeType t = new PmChargeType();
        if (r.kind() == null) throw ApiException.badRequest("Say whether the charge is fixed or metered.");
        applyType(t, r, true);
        t.setActive(r.active() == null || r.active());
        types.save(t);
        auditLogService.record("PM_CHARGE_TYPE_CREATED", "Added charge type " + t.getName() + " (" + t.getKind() + ")", "PmChargeType", t.getId());
        return typeDto(t, Map.of());
    }

    @Transactional
    public ChargeTypeDto updateType(Long id, ChargeTypeRequest r) {
        access.on();
        PmChargeType t = type(id);
        applyType(t, r, false);
        if (r.active() != null && r.active() != t.isActive()) {
            if (!r.active()) {
                LocalDate today = LocalDate.now();
                boolean current = leaseCharges.findAll().stream().anyMatch(c -> c.getChargeTypeId().equals(id) && (c.getEndDate() == null || !c.getEndDate().isBefore(today)));
                boolean pending = extras.findAll().stream().anyMatch(e -> id.equals(e.getChargeTypeId()) && e.getStatus() == PmExtraStatus.PENDING);
                if (current || pending) throw ApiException.conflict("This charge is still on leases or waiting to be billed. End it on those leases first.");
            }
            t.setActive(r.active());
        }
        types.save(t);
        auditLogService.record("PM_CHARGE_TYPE_UPDATED", "Changed charge type " + t.getName(), "PmChargeType", id);
        return typeDto(t, leaseCounts());
    }

    @Transactional
    public void deleteType(Long id) {
        access.on();
        PmChargeType t = type(id);
        if (inUse(id) || !billed.findAll().stream().noneMatch(b -> id.equals(b.getChargeTypeId()))) throw ApiException.conflict("This charge type has been used. Switch it off instead of deleting it.");
        types.delete(t);
        auditLogService.record("PM_CHARGE_TYPE_DELETED", "Deleted charge type " + t.getName());
    }

    /** The service product the lines of a charge type are invoiced under (made on first use). */
    @Transactional
    public Product productFor(PmChargeType t) {
        if (t.getProductId() != null) {
            try {
                return catalogService.getProductOrThrow(t.getProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        Product created = newServiceProduct(t.getName(), "Charge on rent invoices: " + t.getName());
        t.setProductId(created.getId());
        types.save(t);
        return created;
    }

    /** The service product for one-off charges that have no charge type. */
    @Transactional
    public Product otherProduct() {
        PmSettings s = settings.entity();
        if (s.getChargesProductId() != null) {
            try {
                return catalogService.getProductOrThrow(s.getChargesProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        Product created = newServiceProduct("Other charges", "Other charges on rent invoices");
        s.setChargesProductId(created.getId());
        settings.save(s);
        return created;
    }

    private Product newServiceProduct(String name, String description) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setDescription(description);
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(BigDecimal.ZERO);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        return catalogService.getProductOrThrow(catalogService.createProduct(req).id());
    }

    // ------------------------------------------------------------------ charges on a lease

    private PmLease lease(Long id) {
        return leaseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lease not found: " + id));
    }

    private PmLeaseCharge leaseCharge(Long id) {
        return leaseCharges.findById(id).orElseThrow(() -> ApiException.notFound("Lease charge not found: " + id));
    }

    private static boolean overlaps(PmLeaseCharge c, LocalDate start, LocalDate end) {
        LocalDate cEnd = c.getEndDate() == null ? LocalDate.MAX : c.getEndDate();
        LocalDate eEnd = end == null ? LocalDate.MAX : end;
        return !c.getStartDate().isAfter(eEnd) && !start.isAfter(cEnd);
    }

    /** The latest meter reading of a metered lease charge: its value and day (the opening reading before there is any). */
    private record LastReading(BigDecimal value, LocalDate on) {
    }

    private LastReading lastReading(PmLeaseCharge c) {
        PmExtraCharge last = extras.findByLeaseChargeIdOrderByIdAsc(c.getId()).stream().filter(e -> e.getStatus() != PmExtraStatus.CANCELLED).reduce((a, b) -> b).orElse(null);
        if (last != null) return new LastReading(last.getReadingTo(), last.getChargeDate());
        return c.getOpeningReading() == null ? null : new LastReading(c.getOpeningReading(), c.getStartDate());
    }

    @Transactional
    public LeaseChargesView addLeaseCharge(Actor a, Long leaseId, LeaseChargeRequest r) {
        access.on();
        PmLease l = lease(leaseId);
        if (l.getStatus() != LeaseStatus.DRAFT && l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.conflict("Charges can only be put on a lease that is a draft or in force.");
        if (r.chargeTypeId() == null) throw ApiException.badRequest("Choose the charge.");
        PmChargeType t = type(r.chargeTypeId());
        if (!t.isActive()) throw ApiException.badRequest("That charge type is switched off.");
        BigDecimal amount = r.amount() != null ? r.amount() : t.getDefaultAmount();
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest(t.getKind() == PmChargeKind.METERED ? "Give the rate per " + t.getUnitLabel() + "." : "Give the amount per month.");
        LocalDate start = r.startDate() != null ? r.startDate() : l.getStartDate();
        if (r.endDate() != null && r.endDate().isBefore(start)) throw ApiException.badRequest("The end date is before the start date.");
        for (PmLeaseCharge o : leaseCharges.findByLeaseIdOrderByIdAsc(leaseId)) {
            if (o.getChargeTypeId().equals(t.getId()) && overlaps(o, start, r.endDate())) throw ApiException.conflict(t.getName() + " is already on this lease for those dates.");
        }
        PmLeaseCharge c = new PmLeaseCharge();
        c.setLeaseId(leaseId);
        c.setChargeTypeId(t.getId());
        c.setAmount(amount.setScale(4, RoundingMode.HALF_UP));
        c.setStartDate(start);
        c.setEndDate(r.endDate());
        c.setNote(PmUtil.clean(r.note(), 300, "The note"));
        if (t.getKind() == PmChargeKind.METERED) {
            if (r.openingReading() == null || r.openingReading().signum() < 0) throw ApiException.badRequest("Give the meter reading as it is now (0 or more).");
            c.setOpeningReading(r.openingReading().setScale(3, RoundingMode.HALF_UP));
        }
        leaseCharges.save(c);
        auditLogService.record("PM_LEASE_CHARGE_ADDED", "Put " + t.getName() + " on lease " + l.getCode() + " at " + plain(c.getAmount()), "PmLease", leaseId);
        return view(a, leaseId);
    }

    @Transactional
    public LeaseChargesView updateLeaseCharge(Actor a, Long id, LeaseChargeRequest r) {
        access.on();
        PmLeaseCharge c = leaseCharge(id);
        PmLease l = lease(c.getLeaseId());
        PmChargeType t = type(c.getChargeTypeId());
        if (r.amount() != null) {
            if (r.amount().signum() <= 0) throw ApiException.badRequest("The amount must be more than zero.");
            c.setAmount(r.amount().setScale(4, RoundingMode.HALF_UP));
        }
        LocalDate start = r.startDate() != null ? r.startDate() : c.getStartDate();
        LocalDate end = Boolean.TRUE.equals(r.clearEndDate()) ? null : r.endDate() != null ? r.endDate() : c.getEndDate();
        if (end != null && end.isBefore(start)) throw ApiException.badRequest("The end date is before the start date.");
        for (PmLeaseCharge o : leaseCharges.findByLeaseIdOrderByIdAsc(c.getLeaseId())) {
            if (!o.getId().equals(id) && o.getChargeTypeId().equals(c.getChargeTypeId()) && overlaps(o, start, end)) throw ApiException.conflict(t.getName() + " is already on this lease for those dates.");
        }
        c.setStartDate(start);
        c.setEndDate(end);
        if (r.openingReading() != null) {
            if (t.getKind() != PmChargeKind.METERED) throw ApiException.badRequest("Only a metered charge has a meter reading.");
            if (!extras.findByLeaseChargeIdOrderByIdAsc(id).isEmpty()) throw ApiException.conflict("Readings were already taken, so the opening reading cannot change.");
            if (r.openingReading().signum() < 0) throw ApiException.badRequest("The meter reading cannot be negative.");
            c.setOpeningReading(r.openingReading().setScale(3, RoundingMode.HALF_UP));
        }
        if (r.note() != null) c.setNote(PmUtil.clean(r.note(), 300, "The note"));
        leaseCharges.save(c);
        auditLogService.record("PM_LEASE_CHARGE_UPDATED", "Changed " + t.getName() + " on lease " + l.getCode() + " (now " + plain(c.getAmount()) + ")", "PmLease", c.getLeaseId());
        return view(a, c.getLeaseId());
    }

    @Transactional
    public LeaseChargesView removeLeaseCharge(Actor a, Long id) {
        access.on();
        PmLeaseCharge c = leaseCharge(id);
        PmLease l = lease(c.getLeaseId());
        PmChargeType t = type(c.getChargeTypeId());
        if (!extras.findByLeaseChargeIdOrderByIdAsc(id).isEmpty()) throw ApiException.conflict("Readings were taken on this meter, so it is kept. Give it an end date instead.");
        leaseCharges.delete(c);
        auditLogService.record("PM_LEASE_CHARGE_REMOVED", "Took " + t.getName() + " off lease " + l.getCode(), "PmLease", l.getId());
        return view(a, l.getId());
    }

    // ------------------------------------------------------------------ readings and one-off charges

    private PmLease requireActive(Long leaseId) {
        PmLease l = lease(leaseId);
        if (l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.conflict("Readings and one-off charges can only be added to a lease that is in force.");
        return l;
    }

    @Transactional
    public LeaseChargesView addReading(Actor a, Long leaseChargeId, ReadingRequest r) {
        access.on();
        PmLeaseCharge c = leaseCharge(leaseChargeId);
        PmLease l = requireActive(c.getLeaseId());
        PmChargeType t = type(c.getChargeTypeId());
        if (t.getKind() != PmChargeKind.METERED) throw ApiException.badRequest("Only a metered charge takes readings.");
        if (r.reading() == null || r.reading().signum() < 0) throw ApiException.badRequest("Give the meter reading.");
        LocalDate today = LocalDate.now();
        LocalDate day = r.date() != null ? r.date() : today;
        if (day.isAfter(today)) throw ApiException.badRequest("The reading cannot be dated in the future.");
        LastReading prev = lastReading(c);
        if (prev == null) throw ApiException.conflict("This meter has no opening reading.");
        if (day.isBefore(prev.on())) throw ApiException.badRequest("The reading is dated before the last one (" + prev.on() + ").");
        BigDecimal reading = r.reading().setScale(3, RoundingMode.HALF_UP);
        if (reading.compareTo(prev.value()) < 0) throw ApiException.badRequest("The reading is lower than the last one (" + plain(prev.value()) + ").");
        BigDecimal used = reading.subtract(prev.value());
        BigDecimal amount = used.multiply(c.getAmount()).setScale(2, RoundingMode.HALF_UP);
        PmExtraCharge e = new PmExtraCharge();
        e.setLeaseId(l.getId());
        e.setLeaseChargeId(c.getId());
        e.setChargeTypeId(t.getId());
        e.setKind(PmChargeSource.METER);
        e.setChargeDate(day);
        e.setQuantity(used);
        e.setUnitLabel(t.getUnitLabel());
        e.setRate(c.getAmount());
        e.setAmount(amount);
        e.setReadingFrom(prev.value());
        e.setReadingTo(reading);
        e.setDescription(t.getName() + ": " + plain(used) + " " + t.getUnitLabel() + " at " + plain(c.getAmount()) + " (meter " + plain(prev.value()) + " to " + plain(reading) + ", read " + day + ")");
        e.setNote(PmUtil.clean(r.note(), 300, "The note"));
        e.setCreatedByName(PropertyManagementAccess.name(a.user()));
        extras.save(e);
        auditLogService.record("PM_METER_READING", "Read " + t.getName() + " on lease " + l.getCode() + ": " + plain(used) + " " + t.getUnitLabel() + " = " + amount.toPlainString(), "PmLease", l.getId());
        return view(a, l.getId());
    }

    @Transactional
    public LeaseChargesView addExtra(Actor a, Long leaseId, ExtraRequest r) {
        access.on();
        PmLease l = requireActive(leaseId);
        String description = PmUtil.required(r.description(), 300, "What the charge is for");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("The amount must be more than zero.");
        PmChargeType t = null;
        if (r.chargeTypeId() != null) {
            t = type(r.chargeTypeId());
            if (!t.isActive()) throw ApiException.badRequest("That charge type is switched off.");
        }
        LocalDate day = r.date() != null ? r.date() : LocalDate.now();
        if (day.isAfter(LocalDate.now())) throw ApiException.badRequest("The charge cannot be dated in the future.");
        PmExtraCharge e = new PmExtraCharge();
        e.setLeaseId(leaseId);
        e.setChargeTypeId(t == null ? null : t.getId());
        e.setKind(PmChargeSource.ONE_OFF);
        e.setChargeDate(day);
        e.setDescription(description);
        e.setAmount(r.amount().setScale(2, RoundingMode.HALF_UP));
        e.setNote(PmUtil.clean(r.note(), 300, "The note"));
        e.setCreatedByName(PropertyManagementAccess.name(a.user()));
        extras.save(e);
        auditLogService.record("PM_EXTRA_CHARGE", "Added a one-off charge of " + e.getAmount().toPlainString() + " to lease " + l.getCode() + ": " + description, "PmLease", leaseId);
        return view(a, leaseId);
    }

    @Transactional
    public LeaseChargesView cancelExtra(Actor a, Long id) {
        access.on();
        PmExtraCharge e = extras.findById(id).orElseThrow(() -> ApiException.notFound("Charge not found: " + id));
        if (e.getStatus() != PmExtraStatus.PENDING) throw ApiException.conflict(e.getStatus() == PmExtraStatus.BILLED ? "This charge is already on invoice " + e.getInvoiceNumber() + ". Void that invoice first." : "This charge was already cancelled.");
        if (e.getKind() == PmChargeSource.METER) {
            PmExtraCharge latest = extras.findByLeaseChargeIdOrderByIdAsc(e.getLeaseChargeId()).stream().filter(x -> x.getStatus() != PmExtraStatus.CANCELLED).reduce((x, y) -> y).orElse(null);
            if (latest == null || !latest.getId().equals(id)) throw ApiException.conflict("Only the latest reading of a meter can be cancelled.");
        }
        e.setStatus(PmExtraStatus.CANCELLED);
        extras.save(e);
        auditLogService.record("PM_EXTRA_CANCELLED", "Cancelled a charge of " + e.getAmount().toPlainString() + " on lease " + lease(e.getLeaseId()).getCode() + ": " + e.getDescription(), "PmLease", e.getLeaseId());
        return view(a, e.getLeaseId());
    }

    // ------------------------------------------------------------------ taking charges onto a rent invoice

    /** One line a rent invoice takes. */
    public record ChargeLine(PmChargeSource source, PmChargeType type, String name, String description, BigDecimal amount, PmExtraCharge extra) {
    }

    private static BigDecimal ratioOf(PmRentCharge c, PmLease l) {
        if (!c.isProrated()) return BigDecimal.ONE;
        long full = ChronoUnit.DAYS.between(c.getPeriodStart(), c.getPeriodStart().plusMonths(l.getFrequency().months()));
        long days = ChronoUnit.DAYS.between(c.getPeriodStart(), c.getPeriodEnd()) + 1;
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(full), 10, RoundingMode.HALF_UP);
    }

    /** What the invoice of this rent period carries besides the rent: the fixed charges in force for the period, then the pending readings and one-off charges of the lease. */
    List<ChargeLine> collect(PmLease l, PmRentCharge c) {
        List<ChargeLine> out = new ArrayList<>();
        BigDecimal ratio = ratioOf(c, l);
        int months = l.getFrequency().months();
        for (PmLeaseCharge lc : leaseCharges.findByLeaseIdOrderByIdAsc(l.getId())) {
            PmChargeType t = types.findById(lc.getChargeTypeId()).orElse(null);
            if (t == null || t.getKind() != PmChargeKind.FIXED) continue;
            if (c.getPeriodStart().isBefore(lc.getStartDate()) || lc.getEndDate() != null && c.getPeriodStart().isAfter(lc.getEndDate())) continue;
            BigDecimal amount = lc.getAmount().multiply(BigDecimal.valueOf(months)).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) continue;
            out.add(new ChargeLine(PmChargeSource.FIXED, t, t.getName(), t.getName() + ", " + c.getPeriodStart() + " to " + c.getPeriodEnd() + (c.isProrated() ? " (part period)" : ""), amount, null));
        }
        for (PmExtraCharge e : extras.findByLeaseIdAndStatusOrderByChargeDateAscIdAsc(l.getId(), PmExtraStatus.PENDING)) {
            PmChargeType t = e.getChargeTypeId() == null ? null : types.findById(e.getChargeTypeId()).orElse(null);
            out.add(new ChargeLine(e.getKind(), t, t == null ? "Other charges" : t.getName(), e.getDescription(), e.getAmount(), e));
        }
        return out;
    }

    /** The invoice of this rent period is being made again (the earlier one was voided): what it took goes back to waiting. */
    @Transactional
    public void release(PmRentCharge c) {
        for (PmExtraCharge e : extras.findByRentChargeIdAndStatus(c.getId(), PmExtraStatus.BILLED)) {
            e.setStatus(PmExtraStatus.PENDING);
            e.setRentChargeId(null);
            e.setInvoiceNumber(null);
            extras.save(e);
        }
        billed.deleteByRentChargeId(c.getId());
        c.setExtras(ZERO);
    }

    /** The product a line is invoiced under. */
    Long productIdFor(ChargeLine line) {
        return line.type() == null ? otherProduct().getId() : productFor(line.type()).getId();
    }

    /** The rent invoice was made with these lines: remember them, mark the readings and one-off charges as billed, and set what the rent charge took on top of the rent. */
    @Transactional
    public void record(PmRentCharge c, PmLease l, List<ChargeLine> lines, String invoiceNumber) {
        BigDecimal total = ZERO;
        for (ChargeLine ln : lines) {
            PmBilledCharge b = new PmBilledCharge();
            b.setRentChargeId(c.getId());
            b.setLeaseId(l.getId());
            b.setChargeTypeId(ln.type() == null ? null : ln.type().getId());
            b.setName(ln.name());
            b.setDescription(ln.description().length() > 400 ? ln.description().substring(0, 400) : ln.description());
            b.setSource(ln.source());
            b.setAmount(ln.amount());
            if (ln.extra() != null) {
                PmExtraCharge e = ln.extra();
                e.setStatus(PmExtraStatus.BILLED);
                e.setRentChargeId(c.getId());
                e.setInvoiceNumber(invoiceNumber);
                extras.save(e);
                b.setExtraId(e.getId());
            }
            billed.save(b);
            total = total.add(ln.amount());
        }
        c.setExtras(total);
    }

    /** The charge lines the lease's next uninvoiced rent period will carry (for the lease screen). */
    private InvoicePreview preview(PmLease l) {
        PmRentCharge next = rentCharges.findByLeaseIdOrderByPeriodStartAsc(l.getId()).stream().filter(c -> c.getStatus() == RentChargeStatus.PENDING).findFirst().orElse(null);
        if (next == null) return null;
        List<PreviewLine> lines = collect(l, next).stream().map(x -> new PreviewLine(x.source(), x.name(), x.description(), x.amount())).toList();
        BigDecimal sum = lines.stream().map(PreviewLine::amount).reduce(ZERO, BigDecimal::add);
        return new InvoicePreview(next.getId(), next.getPeriodStart(), next.getPeriodEnd(), next.getDueDate(), next.getAmount(), lines, sum, next.getAmount().add(sum));
    }

    // ------------------------------------------------------------------ reading

    private ExtraDto extraDto(PmExtraCharge e, Map<Long, PmChargeType> typeMap, Long latestReadingId) {
        PmChargeType t = e.getChargeTypeId() == null ? null : typeMap.get(e.getChargeTypeId());
        boolean can = e.getStatus() == PmExtraStatus.PENDING && (e.getKind() != PmChargeSource.METER || e.getId().equals(latestReadingId));
        return new ExtraDto(e.getId(), e.getKind(), e.getLeaseChargeId(), e.getChargeTypeId(), t == null ? null : t.getName(), e.getChargeDate(), e.getDescription(), e.getQuantity(), e.getUnitLabel(), e.getRate(),
                e.getAmount(), e.getReadingFrom(), e.getReadingTo(), e.getStatus(), e.getInvoiceNumber(), e.getNote(), e.getCreatedByName(), can);
    }

    @Transactional(readOnly = true)
    public LeaseChargesView get(Actor a, Long leaseId) {
        access.on();
        return view(a, leaseId);
    }

    private LeaseChargesView view(Actor a, Long leaseId) {
        PmLease l = lease(leaseId);
        Snapshot s = views.snapshot();
        PmTenant t = s.tenants.get(l.getTenantId());
        PmUnit u = s.units.get(l.getUnitId());
        PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
        Map<Long, PmChargeType> typeMap = types.findAll().stream().collect(Collectors.toMap(PmChargeType::getId, x -> x));
        Map<Long, Long> counts = leaseCounts();
        LocalDate today = s.today;
        List<LeaseChargeDto> charges = new ArrayList<>();
        Map<Long, Long> latestOfMeter = new HashMap<>();
        List<PmExtraCharge> all = extras.findByLeaseIdOrderByChargeDateDescIdDesc(leaseId);
        for (PmLeaseCharge c : leaseCharges.findByLeaseIdOrderByIdAsc(leaseId)) {
            PmChargeType ct = typeMap.get(c.getChargeTypeId());
            LastReading lr = ct != null && ct.getKind() == PmChargeKind.METERED ? lastReading(c) : null;
            all.stream().filter(e -> c.getId().equals(e.getLeaseChargeId()) && e.getStatus() != PmExtraStatus.CANCELLED).map(PmExtraCharge::getId).max(Long::compare).ifPresent(id -> latestOfMeter.put(c.getId(), id));
            boolean inForce = !c.getStartDate().isAfter(today) && (c.getEndDate() == null || !c.getEndDate().isBefore(today));
            boolean canDelete = extras.findByLeaseChargeIdOrderByIdAsc(c.getId()).isEmpty();
            charges.add(new LeaseChargeDto(c.getId(), c.getChargeTypeId(), ct == null ? "?" : ct.getName(), ct == null ? PmChargeKind.FIXED : ct.getKind(), ct == null ? null : ct.getUnitLabel(), c.getAmount(), c.getStartDate(),
                    c.getEndDate(), c.getOpeningReading(), lr == null ? null : lr.value(), lr == null ? null : lr.on(), inForce, canDelete, c.getNote()));
        }
        List<ExtraDto> pending = new ArrayList<>();
        List<ExtraDto> history = new ArrayList<>();
        for (PmExtraCharge e : all) {
            ExtraDto d = extraDto(e, typeMap, e.getLeaseChargeId() == null ? null : latestOfMeter.get(e.getLeaseChargeId()));
            if (e.getStatus() == PmExtraStatus.PENDING) pending.add(d);
            else if (history.size() < 30) history.add(d);
        }
        pending.sort(Comparator.comparing(ExtraDto::date).thenComparing(ExtraDto::id));
        List<PmRentCharge> rents = rentCharges.findByLeaseIdOrderByPeriodStartAsc(leaseId);
        Map<Long, PmRentCharge> rentById = rents.stream().collect(Collectors.toMap(PmRentCharge::getId, x -> x));
        List<BilledDto> billedList = billed.findByRentChargeIdIn(rentById.keySet().isEmpty() ? List.of(-1L) : rentById.keySet()).stream()
                .sorted(Comparator.comparing((PmBilledCharge b) -> rentById.get(b.getRentChargeId()).getPeriodStart()).reversed().thenComparing(PmBilledCharge::getId))
                .limit(60).map(b -> {
                    PmRentCharge rc = rentById.get(b.getRentChargeId());
                    return new BilledDto(b.getId(), rc.getId(), rc.getPeriodStart(), rc.getPeriodEnd(), rc.getInvoiceNumber(), b.getName(), b.getDescription(), b.getSource(), b.getAmount());
                }).toList();
        boolean canManage = a != null && a.manager();
        List<ChargeTypeDto> typeList = types.findAllByOrderByNameAsc().stream().filter(PmChargeType::isActive).map(x -> typeDto(x, counts)).toList();
        return new LeaseChargesView(l.getId(), l.getCode(), l.getStatus().name(), t == null ? null : t.getName(), (p == null ? "" : p.getName() + " · ") + (u == null ? "" : u.getUnitNo()), canManage,
                canManage && l.getStatus() == LeaseStatus.ACTIVE, charges, pending, history, billedList, preview(l), typeList);
    }

    /** The charge lines taken by rent invoices, by the month the rent fell due, for the report. */
    @Transactional(readOnly = true)
    public List<ChargeReportRow> report(LocalDate from, LocalDate to) {
        access.on();
        Snapshot s = views.snapshot();
        Map<Long, PmRentCharge> rentById = rentCharges.findAll().stream()
                .filter(c -> c.getStatus() == RentChargeStatus.INVOICED && (from == null || !c.getDueDate().isBefore(from)) && (to == null || !c.getDueDate().isAfter(to)))
                .collect(Collectors.toMap(PmRentCharge::getId, x -> x));
        if (rentById.isEmpty()) return List.of();
        List<ChargeReportRow> out = new ArrayList<>();
        for (PmBilledCharge b : billed.findByRentChargeIdIn(rentById.keySet())) {
            PmRentCharge c = rentById.get(b.getRentChargeId());
            PmLease l = s.byId.get(c.getLeaseId());
            PmUnit u = l == null ? null : s.units.get(l.getUnitId());
            PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
            PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
            PmTenant t = l == null ? null : s.tenants.get(l.getTenantId());
            out.add(new ChargeReportRow(c.getPeriodStart(), c.getPeriodEnd(), c.getDueDate(), p == null ? null : p.getName(), u == null ? null : u.getUnitNo(), o == null ? null : o.getName(), t == null ? null : t.getName(),
                    l == null ? null : l.getCode(), b.getName(), b.getSource(), b.getDescription(), c.getInvoiceNumber(), b.getAmount()));
        }
        out.sort(Comparator.comparing(ChargeReportRow::dueDate).thenComparing(ChargeReportRow::propertyName, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ChargeReportRow::unitNo, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ChargeReportRow::name));
        return out;
    }

    /** A draft lease is being deleted: its charges go with it. */
    @Transactional
    public void deleteForLease(Long leaseId) {
        leaseCharges.deleteByLeaseId(leaseId);
        extras.deleteByLeaseId(leaseId);
    }

    public boolean hasCharges(Long leaseId) {
        return !leaseCharges.findByLeaseIdOrderByIdAsc(leaseId).isEmpty() || extras.existsByLeaseId(leaseId);
    }
}
