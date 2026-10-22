package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerPaymentAllocation;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.GenerateAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.GenerateStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.PayStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.StatementAdjustmentRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.StatementNotesRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.*;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owner statements: for a period, the rent collected on an owner's properties (payments dated in the period), less the management fee on it, less the repairs the owner pays for
 * (jobs finished in the period), plus or minus manual adjustments. A draft is built from the records and can be rebuilt; an agreed (final) statement is locked; a paid one has been
 * paid out. Periods of one owner cannot overlap, so nothing is counted twice.
 */
@Service
public class PmStatementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Set<StatementLineKind> AUTO = EnumSet.of(StatementLineKind.RENT, StatementLineKind.FEE, StatementLineKind.VAT, StatementLineKind.REPAIR);

    private final PmOwnerStatementRepository repository;
    private final PmStatementLineRepository lineRepository;
    private final PmPaymentQueryRepository paymentQuery;
    private final PmRentChargeRepository chargeRepository;
    private final PmMaintenanceRepository maintenanceRepository;
    private final PmSettingsService settings;
    private final PmViews views;
    private final PaymentMethodService paymentMethodService;
    private final PmLedgerService ledger;
    private final PmFeeInvoiceService feeInvoiceService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmStatementService(PmOwnerStatementRepository repository, PmStatementLineRepository lineRepository, PmPaymentQueryRepository paymentQuery, PmRentChargeRepository chargeRepository,
                              PmMaintenanceRepository maintenanceRepository, PmSettingsService settings, PmViews views, PaymentMethodService paymentMethodService,
                              PmLedgerService ledger, PropertyManagementAccess access, AuditLogService auditLogService, PmFeeInvoiceService feeInvoiceService) {
        this.feeInvoiceService = feeInvoiceService;
        this.ledger = ledger;
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.paymentQuery = paymentQuery;
        this.chargeRepository = chargeRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.settings = settings;
        this.views = views;
        this.paymentMethodService = paymentMethodService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private String ownerName(PmOwnerStatement st) {
        PmOwner o = views.snapshot().owners.get(st.getOwnerId());
        return o == null ? "owner " + st.getOwnerId() : o.getName();
    }

    private PmOwnerStatement find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Owner statement not found: " + id));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ building the lines

    /**
     * For rent invoices that carry charges on top of the rent: how much of each payment on them counts as rent (rent first, in the order the payments were made). Payments on invoices
     * without charges are not in the map, and count in full.
     */
    private Map<Long, BigDecimal> rentParts(Map<Long, PmRentCharge> byInvoice) {
        Map<Long, BigDecimal> out = new HashMap<>();
        Set<Long> mixed = byInvoice.entrySet().stream().filter(e -> e.getValue().getExtras() != null && e.getValue().getExtras().signum() > 0).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (mixed.isEmpty()) return out;
        Map<Long, List<CustomerPaymentAllocation>> perInvoice = new HashMap<>();
        for (CustomerPaymentAllocation a : paymentQuery.forInvoices(mixed)) perInvoice.computeIfAbsent(a.getInvoice().getId(), k -> new ArrayList<>()).add(a);
        for (Map.Entry<Long, List<CustomerPaymentAllocation>> e : perInvoice.entrySet()) {
            BigDecimal rent = byInvoice.get(e.getKey()).getAmount();
            BigDecimal paid = BigDecimal.ZERO;
            for (CustomerPaymentAllocation a : e.getValue().stream().sorted(Comparator.comparing((CustomerPaymentAllocation x) -> x.getPayment().getPaidAt()).thenComparing(CustomerPaymentAllocation::getId)).toList()) {
                BigDecimal before = paid;
                paid = paid.add(money(a.getAmountApplied()));
                out.put(a.getId(), paid.min(rent).subtract(before.min(rent)).max(BigDecimal.ZERO));
            }
        }
        return out;
    }

    /** The automatic lines (rent, fee, repairs) for an owner and period; {@code exclude} is the statement being rebuilt (-1 for a new one). */
    private List<PmStatementLine> autoLines(Snapshot s, Long ownerId, LocalDate from, LocalDate to, Long exclude) {
        List<PmStatementLine> out = new ArrayList<>();
        Map<Long, PmProperty> props = s.properties.values().stream().filter(p -> PmViews.everOwned(s, ownerId, p.getId())).collect(Collectors.toMap(PmProperty::getId, p -> p));
        if (props.isEmpty()) return out;
        Map<Long, PmUnit> units = s.units.values().stream().filter(u -> props.containsKey(u.getPropertyId())).collect(Collectors.toMap(PmUnit::getId, u -> u));

        // rent: payments dated in the period on invoices of rent charges of leases on those units
        Map<Long, PmLease> leases = s.leases.stream().filter(l -> units.containsKey(l.getUnitId())).collect(Collectors.toMap(PmLease::getId, l -> l));
        Map<Long, PmRentCharge> byInvoice = new HashMap<>();
        if (!leases.isEmpty()) {
            for (PmRentCharge c : chargeRepository.findByLeaseIdIn(leases.keySet())) if (c.getInvoiceId() != null) byInvoice.put(c.getInvoiceId(), c);
        }
        Set<Long> usedRent = new HashSet<>(lineRepository.sourceIdsElsewhere(StatementLineKind.RENT, ownerId, exclude));
        Map<Long, BigDecimal> rentByProperty = new LinkedHashMap<>();
        if (!byInvoice.isEmpty()) {
            Instant start = from.atStartOfDay(ZONE).toInstant();
            Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
            List<CustomerPaymentAllocation> allocs = paymentQuery.collectedOn(byInvoice.keySet(), start, end).stream()
                    .sorted(Comparator.comparing((CustomerPaymentAllocation a) -> a.getPayment().getPaidAt()).thenComparing(CustomerPaymentAllocation::getId)).toList();
            // an invoice that also carries utilities or service charges: what the tenant pays goes to the rent first, and only that part is the owner's
            Map<Long, BigDecimal> rentPart = rentParts(byInvoice);
            for (CustomerPaymentAllocation a : allocs) {
                BigDecimal whole = rentPart.containsKey(a.getId()) ? rentPart.get(a.getId()) : money(a.getAmountApplied());
                if (whole.signum() == 0 || usedRent.contains(a.getId())) continue;
                PmRentCharge c = byInvoice.get(a.getInvoice().getId());
                PmLease l = leases.get(c.getLeaseId());
                PmUnit u = units.get(l.getUnitId());
                // a property with several owners: this owner's part of the payment, by the split in force the day it was paid
                LocalDate paidOn = a.getPayment().getPaidAt().atZone(ZONE).toLocalDate();
                PmOwnership.Split split = PmOwnership.at(s.splits.get(u.getPropertyId()), paidOn);
                BigDecimal amt = split == null ? whole : PmOwnership.part(split, ownerId, whole);
                if (amt.signum() == 0) continue;
                boolean shared = split != null && split.shares().size() > 1;
                PmTenant t = s.tenants.get(l.getTenantId());
                PmStatementLine line = new PmStatementLine();
                line.setKind(StatementLineKind.RENT);
                line.setPropertyId(u.getPropertyId());
                line.setUnitId(u.getId());
                line.setTenantId(l.getTenantId());
                line.setLineDate(a.getPayment().getPaidAt().atZone(ZONE).toLocalDate());
                line.setDescription("Rent " + c.getPeriodStart() + " to " + c.getPeriodEnd() + (t == null ? "" : " · " + t.getName()) + (shared ? " · your share " + split.of(ownerId).percent().stripTrailingZeros().toPlainString() + "% of " + whole.toPlainString() : ""));
                line.setReference(c.getInvoiceNumber());
                if (shared) line.setBaseAmount(whole);
                line.setAmount(amt);
                line.setSourceId(a.getId());
                out.add(line);
                rentByProperty.merge(u.getPropertyId(), amt, BigDecimal::add);
            }
        }

        // the management fee, property by property
        for (Map.Entry<Long, BigDecimal> e : rentByProperty.entrySet()) {
            PmProperty p = props.get(e.getKey());
            BigDecimal pct = PmViews.effectiveFee(s, p);
            if (pct == null || pct.signum() <= 0) continue;
            BigDecimal fee = e.getValue().multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (fee.signum() == 0) continue;
            PmStatementLine line = new PmStatementLine();
            line.setKind(StatementLineKind.FEE);
            line.setPropertyId(p.getId());
            line.setLineDate(to);
            line.setDescription("Management fee " + pct.stripTrailingZeros().toPlainString() + "% of " + e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString());
            line.setBaseAmount(e.getValue());
            line.setFeePercent(pct);
            line.setAmount(fee.negate());
            out.add(line);
            BigDecimal vatPct = s.feeVatPercent;
            if (vatPct != null && vatPct.signum() > 0) {
                BigDecimal vat = fee.multiply(vatPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (vat.signum() > 0) {
                    PmStatementLine v = new PmStatementLine();
                    v.setKind(StatementLineKind.VAT);
                    v.setPropertyId(p.getId());
                    v.setLineDate(to);
                    v.setDescription("VAT " + vatPct.stripTrailingZeros().toPlainString() + "% on the management fee of " + fee.toPlainString());
                    v.setBaseAmount(fee);
                    v.setFeePercent(vatPct);
                    v.setAmount(vat.negate());
                    out.add(v);
                }
            }
        }

        // repairs the owner pays for, finished in the period
        if (!units.isEmpty()) {
            Set<Long> usedRepairs = new HashSet<>(lineRepository.sourceIdsElsewhere(StatementLineKind.REPAIR, ownerId, exclude));
            for (PmMaintenance m : maintenanceRepository.findByUnitIdInAndStatusAndCostBearerAndCompletedDateBetween(units.keySet(), MaintenanceStatus.COMPLETED, CostBearer.OWNER, from, to)) {
                BigDecimal whole = money(m.getActualCost());
                if (whole.signum() <= 0 || usedRepairs.contains(m.getId())) continue;
                PmUnit u = units.get(m.getUnitId());
                PmOwnership.Split split = PmOwnership.at(s.splits.get(u.getPropertyId()), m.getCompletedDate());
                BigDecimal cost = split == null ? whole : PmOwnership.part(split, ownerId, whole);
                if (cost.signum() <= 0) continue;
                boolean shared = split != null && split.shares().size() > 1;
                PmStatementLine line = new PmStatementLine();
                line.setKind(StatementLineKind.REPAIR);
                line.setPropertyId(u.getPropertyId());
                line.setUnitId(u.getId());
                line.setTenantId(m.getTenantId());
                line.setLineDate(m.getCompletedDate());
                line.setDescription(m.getTitle() + (shared ? " · your share " + split.of(ownerId).percent().stripTrailingZeros().toPlainString() + "% of " + whole.toPlainString() : ""));
                line.setReference(m.getCode());
                if (shared) line.setBaseAmount(whole);
                line.setAmount(cost.negate());
                line.setSourceId(m.getId());
                out.add(line);
            }
        }
        return out;
    }

    /** Recalculates the totals of a statement from its lines. */
    private void retotal(PmOwnerStatement st, List<PmStatementLine> lines) {
        BigDecimal rent = ZERO, fee = ZERO, vat = ZERO, repairs = ZERO, adj = ZERO;
        BigDecimal vatPct = null;
        for (PmStatementLine l : lines) {
            switch (l.getKind()) {
                case RENT -> rent = rent.add(l.getAmount());
                case FEE -> fee = fee.add(l.getAmount().negate());
                case VAT -> { vat = vat.add(l.getAmount().negate()); if (vatPct == null) vatPct = l.getFeePercent(); }
                case REPAIR -> repairs = repairs.add(l.getAmount().negate());
                case ADJUSTMENT -> adj = adj.add(l.getAmount());
            }
        }
        st.setRentCollected(money(rent));
        st.setManagementFee(money(fee));
        st.setVatAmount(money(vat));
        st.setVatPercent(vatPct);
        st.setRepairs(money(repairs));
        st.setAdjustments(money(adj));
        st.setNetPayable(money(rent.subtract(fee).subtract(vat).subtract(repairs).add(adj)));
    }

    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("Give the first and last day of the period.");
        if (to.isBefore(from)) throw ApiException.badRequest("The period cannot end before it starts.");
        if (to.isAfter(LocalDate.now())) throw ApiException.badRequest("A statement cannot cover days that have not happened yet.");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.badRequest("A statement can cover at most a year.");
    }

    private PmOwnerStatement overlapping(Long ownerId, LocalDate from, LocalDate to, Long exclude) {
        return repository.findByOwnerIdAndPeriodFromLessThanEqualAndPeriodToGreaterThanEqual(ownerId, to, from).stream()
                .filter(x -> !x.getId().equals(exclude)).findFirst().orElse(null);
    }

    private PmOwnerStatement create(Actor a, PmOwner owner, LocalDate from, LocalDate to, String notes, List<PmStatementLine> lines) {
        PmOwnerStatement st = new PmOwnerStatement();
        st.setCode(settings.nextCode("ST"));
        st.setOwnerId(owner.getId());
        st.setPeriodFrom(from);
        st.setPeriodTo(to);
        st.setNotes(notes);
        st.setCreatedByName(PropertyManagementAccess.name(a.user()));
        retotal(st, lines);
        repository.save(st);
        for (PmStatementLine l : lines) l.setStatementId(st.getId());
        lineRepository.saveAll(lines);
        auditLogService.record("PM_STATEMENT_CREATED", "Made owner statement " + st.getCode() + " for " + owner.getName() + ", " + from + " to " + to, "PmOwnerStatement", st.getId());
        return st;
    }

    @Transactional
    public StatementDto generate(Actor a, GenerateStatementRequest r) {
        access.on();
        if (r == null || r.ownerId() == null) throw ApiException.badRequest("Choose the owner.");
        Snapshot s = views.snapshot();
        PmOwner owner = s.owners.get(r.ownerId());
        if (owner == null) throw ApiException.badRequest("Owner not found.");
        checkPeriod(r.periodFrom(), r.periodTo());
        PmOwnerStatement clash = overlapping(owner.getId(), r.periodFrom(), r.periodTo(), -1L);
        if (clash != null) throw ApiException.conflict("Statement " + clash.getCode() + " already covers " + clash.getPeriodFrom() + " to " + clash.getPeriodTo() + " for this owner. Delete it, or pick days after it.");
        List<PmStatementLine> lines = autoLines(s, owner.getId(), r.periodFrom(), r.periodTo(), -1L);
        if (lines.isEmpty()) throw ApiException.badRequest("No rent was collected and no repair was paid for " + owner.getName() + " in that period.");
        PmOwnerStatement st = create(a, owner, r.periodFrom(), r.periodTo(), PmUtil.clean(r.notes(), 2000, "The notes"), lines);
        return dto(a, s, st);
    }

    @Transactional
    public GenerateAllResult generateAll(Actor a, GenerateAllStatementsRequest r) {
        access.on();
        if (r == null) throw ApiException.badRequest("Give the period.");
        checkPeriod(r.periodFrom(), r.periodTo());
        Snapshot s = views.snapshot();
        List<StatementRow> made = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int skipped = 0;
        for (PmOwner o : s.owners.values().stream().filter(PmOwner::isActive).sorted(Comparator.comparing(PmOwner::getCode)).toList()) {
            PmOwnerStatement clash = overlapping(o.getId(), r.periodFrom(), r.periodTo(), -1L);
            if (clash != null) { skipped++; notes.add(o.getName() + ": already on " + clash.getCode() + "."); continue; }
            List<PmStatementLine> lines = autoLines(s, o.getId(), r.periodFrom(), r.periodTo(), -1L);
            if (lines.isEmpty()) { skipped++; continue; }
            made.add(row(s, create(a, o, r.periodFrom(), r.periodTo(), null, lines)));
        }
        return new GenerateAllResult(made.size(), skipped, notes, made);
    }

    /** Rebuilds the automatic lines of a draft from the records now (manual lines are kept). */
    @Transactional
    public StatementDto refresh(Actor a, Long id) {
        access.on();
        PmOwnerStatement st = draft(id, "rebuild it");
        Snapshot s = views.snapshot();
        lineRepository.deleteByStatementIdAndKindIn(st.getId(), AUTO);
        lineRepository.flush();
        List<PmStatementLine> fresh = autoLines(s, st.getOwnerId(), st.getPeriodFrom(), st.getPeriodTo(), st.getId());
        for (PmStatementLine l : fresh) l.setStatementId(st.getId());
        lineRepository.saveAll(fresh);
        retotal(st, lineRepository.findByStatementId(st.getId()));
        repository.save(st);
        auditLogService.record("PM_STATEMENT_REBUILT", "Rebuilt owner statement " + st.getCode(), "PmOwnerStatement", id);
        return dto(a, s, st);
    }

    private PmOwnerStatement draft(Long id, String what) {
        PmOwnerStatement st = find(id);
        if (st.getStatus() != StatementStatus.DRAFT) throw ApiException.conflict("Statement " + st.getCode() + " is " + st.getStatus().name().toLowerCase() + ", so you cannot " + what + ". Reopen it first.");
        return st;
    }

    // ------------------------------------------------------------------ changes

    @Transactional
    public StatementDto addAdjustment(Actor a, Long id, StatementAdjustmentRequest r) {
        access.on();
        PmOwnerStatement st = draft(id, "add a line");
        Snapshot s = views.snapshot();
        if (r == null || r.amount() == null || r.amount().signum() == 0) throw ApiException.badRequest("Enter the amount: positive adds to what the owner gets, negative takes from it.");
        if (r.amount().abs().compareTo(new BigDecimal("1000000000000")) > 0) throw ApiException.badRequest("That amount is too large.");
        String text = PmUtil.required(r.description(), 500, "The description");
        PmProperty p = null;
        if (r.propertyId() != null) {
            p = s.properties.get(r.propertyId());
            if (p == null || !PmViews.everOwned(s, st.getOwnerId(), p.getId())) throw ApiException.badRequest("That property does not belong to this owner.");
        }
        LocalDate date = r.date() == null ? st.getPeriodTo() : r.date();
        if (date.isBefore(st.getPeriodFrom()) || date.isAfter(st.getPeriodTo())) throw ApiException.badRequest("The date must be inside the statement period.");
        PmStatementLine l = new PmStatementLine();
        l.setStatementId(st.getId());
        l.setKind(StatementLineKind.ADJUSTMENT);
        l.setPropertyId(p == null ? null : p.getId());
        l.setLineDate(date);
        l.setDescription(text);
        l.setAmount(money(r.amount()));
        lineRepository.save(l);
        retotal(st, lineRepository.findByStatementId(st.getId()));
        repository.save(st);
        auditLogService.record("PM_STATEMENT_ADJUSTED", "Added a line to owner statement " + st.getCode() + ": " + text + " " + l.getAmount().toPlainString(), "PmOwnerStatement", id);
        return dto(a, s, st);
    }

    @Transactional
    public StatementDto removeAdjustment(Actor a, Long id, Long lineId) {
        access.on();
        PmOwnerStatement st = draft(id, "remove a line");
        PmStatementLine l = lineRepository.findById(lineId).filter(x -> x.getStatementId().equals(st.getId())).orElseThrow(() -> ApiException.notFound("Line not found."));
        if (l.getKind() != StatementLineKind.ADJUSTMENT) throw ApiException.conflict("Only lines you added yourself can be removed. Rebuild the statement to refresh the others.");
        lineRepository.delete(l);
        lineRepository.flush();
        retotal(st, lineRepository.findByStatementId(st.getId()));
        repository.save(st);
        auditLogService.record("PM_STATEMENT_ADJUSTED", "Removed a line from owner statement " + st.getCode(), "PmOwnerStatement", id);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public StatementDto notes(Actor a, Long id, StatementNotesRequest r) {
        access.on();
        PmOwnerStatement st = find(id);
        if (st.getStatus() == StatementStatus.PAID) throw ApiException.conflict("A paid statement cannot be changed.");
        st.setNotes(PmUtil.clean(r == null ? null : r.notes(), 2000, "The notes"));
        repository.save(st);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public StatementDto finalizeStatement(Actor a, Long id) {
        access.on();
        PmOwnerStatement st = draft(id, "finalize it");
        ledger.agreement(st, ownerName(st), false);
        st.setAgreementPosted(true);
        st.setStatus(StatementStatus.FINAL);
        st.setFinalizedDate(LocalDate.now());
        st.setFinalizedByName(PropertyManagementAccess.name(a.user()));
        repository.save(st);
        feeInvoiceService.issueFor(st, PropertyManagementAccess.name(a.user()));
        auditLogService.record("PM_STATEMENT_FINALIZED", "Finalized owner statement " + st.getCode() + ", net " + st.getNetPayable().toPlainString(), "PmOwnerStatement", id);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public StatementDto reopen(Actor a, Long id) {
        access.on();
        PmOwnerStatement st = find(id);
        if (st.getStatus() == StatementStatus.DRAFT) throw ApiException.conflict("This statement is already a draft.");
        if (st.getStatus() == StatementStatus.PAID) throw ApiException.conflict("It has been paid. Undo the payment first.");
        if (st.isAgreementPosted()) {
            ledger.agreement(st, ownerName(st), true);
            st.setAgreementPosted(false);
        }
        feeInvoiceService.voidFor(st, "Statement " + st.getCode() + " was reopened");
        st.setEmailedAt(null);
        st.setEmailedTo(null);
        st.setStatus(StatementStatus.DRAFT);
        st.setFinalizedDate(null);
        st.setFinalizedByName(null);
        repository.save(st);
        auditLogService.record("PM_STATEMENT_REOPENED", "Reopened owner statement " + st.getCode(), "PmOwnerStatement", id);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public StatementDto pay(Actor a, Long id, PayStatementRequest r) {
        access.on();
        PmOwnerStatement st = find(id);
        if (st.getStatus() != StatementStatus.FINAL) throw ApiException.conflict(st.getStatus() == StatementStatus.PAID ? "This statement is already paid." : "Finalize the statement before paying it.");
        LocalDate date = r == null || r.paidDate() == null ? LocalDate.now() : r.paidDate();
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("The payment date cannot be in the future.");
        if (date.isBefore(st.getPeriodTo())) throw ApiException.badRequest("The owner cannot be paid before the end of the period (" + st.getPeriodTo() + ").");
        String method = null;
        if (st.getNetPayable().signum() > 0) {
            if (r == null || r.method() == null || r.method().isBlank()) throw ApiException.badRequest("Choose how the owner was paid.");
            method = paymentMethodService.validateActiveCodeOrThrow(r.method());
        }
        if (!st.isAgreementPosted()) {
            ledger.agreement(st, ownerName(st), false);
            st.setAgreementPosted(true);
        }
        ledger.payout(st, ownerName(st), date, false);
        st.setPayoutPosted(true);
        st.setStatus(StatementStatus.PAID);
        st.setPaidDate(date);
        st.setPaidMethod(method);
        st.setPaidReference(PmUtil.clean(r == null ? null : r.reference(), 120, "The reference"));
        repository.save(st);
        auditLogService.record("PM_STATEMENT_PAID", "Marked owner statement " + st.getCode() + " paid, " + st.getNetPayable().toPlainString(), "PmOwnerStatement", id);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public StatementDto unpay(Actor a, Long id) {
        access.on();
        PmOwnerStatement st = find(id);
        if (st.getStatus() != StatementStatus.PAID) throw ApiException.conflict("This statement has not been paid.");
        if (st.isPayoutPosted()) {
            ledger.payout(st, ownerName(st), null, true);
            st.setPayoutPosted(false);
        }
        st.setStatus(StatementStatus.FINAL);
        st.setPaidDate(null);
        st.setPaidMethod(null);
        st.setPaidReference(null);
        repository.save(st);
        auditLogService.record("PM_STATEMENT_UNPAID", "Undid the payment of owner statement " + st.getCode(), "PmOwnerStatement", id);
        return dto(a, views.snapshot(), st);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmOwnerStatement st = draft(id, "delete it");
        lineRepository.deleteByStatementId(id);
        repository.delete(st);
        auditLogService.record("PM_STATEMENT_DELETED", "Deleted owner statement " + st.getCode());
    }

    // ------------------------------------------------------------------ reading

    private StatementRow row(Snapshot s, PmOwnerStatement st) {
        PmOwner o = s.owners.get(st.getOwnerId());
        return new StatementRow(st.getId(), st.getCode(), st.getOwnerId(), o == null ? null : o.getName(), st.getPeriodFrom(), st.getPeriodTo(), st.getStatus(), st.getRentCollected(),
                st.getManagementFee(), st.getVatAmount(), st.getRepairs(), st.getAdjustments(), st.getNetPayable(), st.getPaidDate());
    }

    @Transactional(readOnly = true)
    public List<StatementRow> list(Long ownerId, StatementStatus status, LocalDate from, LocalDate to, boolean unpaidOnly) {
        access.on();
        Snapshot s = views.snapshot();
        return repository.findAll().stream()
                .filter(x -> ownerId == null || ownerId.equals(x.getOwnerId()))
                .filter(x -> status == null || x.getStatus() == status)
                .filter(x -> !unpaidOnly || x.getStatus() != StatementStatus.PAID)
                .filter(x -> from == null || !x.getPeriodTo().isBefore(from))
                .filter(x -> to == null || !x.getPeriodFrom().isAfter(to))
                .sorted(Comparator.comparing(PmOwnerStatement::getPeriodTo).reversed().thenComparing(PmOwnerStatement::getCode, Comparator.reverseOrder()))
                .map(x -> row(s, x)).toList();
    }

    @Transactional(readOnly = true)
    public StatementDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    @Transactional(readOnly = true)
    public StatementSummary summary() {
        List<PmOwnerStatement> all = repository.findAll();
        long drafts = all.stream().filter(x -> x.getStatus() == StatementStatus.DRAFT).count();
        List<PmOwnerStatement> due = all.stream().filter(x -> x.getStatus() == StatementStatus.FINAL).toList();
        BigDecimal amount = due.stream().map(PmOwnerStatement::getNetPayable).filter(v -> v.signum() > 0).reduce(ZERO, BigDecimal::add);
        return new StatementSummary(drafts, due.size(), amount);
    }

    private static int order(StatementLineKind k) {
        return switch (k) { case RENT -> 0; case FEE -> 1; case VAT -> 2; case REPAIR -> 3; case ADJUSTMENT -> 4; };
    }

    private StatementDto dto(Actor a, Snapshot s, PmOwnerStatement st) {
        PmOwner owner = s.owners.get(st.getOwnerId());
        List<PmStatementLine> lines = lineRepository.findByStatementId(st.getId()).stream()
                .sorted(Comparator.comparingInt((PmStatementLine l) -> order(l.getKind())).thenComparing(PmStatementLine::getLineDate).thenComparing(PmStatementLine::getId)).toList();
        boolean editable = st.getStatus() == StatementStatus.DRAFT && a.manager();
        List<StatementLineDto> out = new ArrayList<>();
        Map<Long, BigDecimal[]> byProp = new LinkedHashMap<>(); // rent, fee, repairs, adjustments, vat
        Map<Long, BigDecimal> pct = new HashMap<>();
        for (PmStatementLine l : lines) {
            PmProperty p = l.getPropertyId() == null ? null : s.properties.get(l.getPropertyId());
            PmUnit u = l.getUnitId() == null ? null : s.units.get(l.getUnitId());
            PmTenant t = l.getTenantId() == null ? null : s.tenants.get(l.getTenantId());
            out.add(new StatementLineDto(l.getId(), l.getKind(), l.getPropertyId(), p == null ? null : p.getName(), l.getUnitId(), u == null ? null : u.getUnitNo(), l.getTenantId(),
                    t == null ? null : t.getName(), l.getLineDate(), l.getDescription(), l.getReference(), l.getBaseAmount(), l.getFeePercent(), l.getAmount(), editable && l.getKind() == StatementLineKind.ADJUSTMENT));
            BigDecimal[] sums = byProp.computeIfAbsent(l.getPropertyId(), k -> new BigDecimal[]{ZERO, ZERO, ZERO, ZERO, ZERO});
            switch (l.getKind()) {
                case RENT -> sums[0] = sums[0].add(l.getAmount());
                case FEE -> { sums[1] = sums[1].add(l.getAmount().negate()); pct.put(l.getPropertyId(), l.getFeePercent()); }
                case VAT -> sums[4] = sums[4].add(l.getAmount().negate());
                case REPAIR -> sums[2] = sums[2].add(l.getAmount().negate());
                case ADJUSTMENT -> sums[3] = sums[3].add(l.getAmount());
            }
        }
        List<StatementPropertyRow> props = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : byProp.entrySet()) {
            PmProperty p = e.getKey() == null ? null : s.properties.get(e.getKey());
            BigDecimal[] v = e.getValue();
            props.add(new StatementPropertyRow(e.getKey(), p == null ? "Not tied to a property" : p.getName(), money(v[0]), pct.get(e.getKey()), money(v[1]), money(v[4]), money(v[2]), money(v[3]),
                    money(v[0].subtract(v[1]).subtract(v[4]).subtract(v[2]).add(v[3]))));
        }
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : byProp.entrySet()) {
            PmProperty p = e.getKey() == null ? null : s.properties.get(e.getKey());
            if (p != null && e.getValue()[0].signum() > 0 && !pct.containsKey(e.getKey()) && st.getStatus() != StatementStatus.PAID) {
                warnings.add(p.getName() + " has rent on this statement but no management fee is set, so none was charged. Set the fee on the property, the owner or in Settings, then rebuild.");
            }
        }
        BigDecimal nowVat = s.feeVatPercent == null ? ZERO : s.feeVatPercent;
        BigDecimal builtVat = st.getVatPercent() == null ? ZERO : st.getVatPercent();
        if (st.getStatus() == StatementStatus.DRAFT && nowVat.compareTo(builtVat) != 0) {
            warnings.add("The VAT rate on the fee in Settings is " + nowVat.stripTrailingZeros().toPlainString() + "% but this draft was built with " + builtVat.stripTrailingZeros().toPlainString() + "%. Rebuild it to apply the new rate.");
        }
        if (st.getNetPayable().signum() < 0) warnings.add("The net is negative: the owner owes " + st.getNetPayable().negate().toPlainString() + ". Charge it on the next statement with an adjustment line.");
        if (st.getStatus() != StatementStatus.PAID && st.getNetPayable().signum() > 0 && owner != null && (owner.getBankAccount() == null || owner.getBankAccount().isBlank())) {
            warnings.add("The owner has no bank account on file.");
        }
        return new StatementDto(row(s, st), st.getNotes(), out, props, warnings, owner == null ? null : owner.getPhone(), owner == null ? null : owner.getBankName(),
                owner == null ? null : owner.getBankAccount(), st.getFinalizedDate(), st.getFinalizedByName(), st.getPaidMethod(), st.getPaidReference(), a.manager(), st.getCreatedByName(), st.getCreatedAt(), st.isAgreementPosted(),
                owner == null ? null : owner.getEmail(), st.getEmailedAt(), st.getEmailedTo());
    }
}
