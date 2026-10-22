package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDepositDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.RentChargeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The security deposit of a lease. Money comes in (received from the tenant, or moved over from the tenant's earlier lease), and goes out (kept for unpaid rent or for what was
 * left damaged, refunded, or moved to a renewal). Every movement is a line that is never deleted: a mistake is voided, and the books are reversed. What is kept or refunded can
 * only be settled once the lease is over, and never more than is held.
 */
@Service
public class PmDepositService {

    private static final Set<String> METHODS = Set.of("CASH", "BANK", "MOBILE_MONEY", "CHEQUE", "CARD", "OTHER");

    private final PmDepositTxnRepository repository;
    private final PmLeaseRepository leaseRepository;
    private final PmInspectionRepository inspectionRepository;
    private final PmInspectionItemRepository itemRepository;
    private final PmViews views;
    private final PmRentService rentService;
    private final PmLedgerService ledger;
    private final CustomerCreditService customerCreditService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmDepositService(PmDepositTxnRepository repository, PmLeaseRepository leaseRepository, PmInspectionRepository inspectionRepository, PmInspectionItemRepository itemRepository,
                            PmViews views, PmRentService rentService, PmLedgerService ledger, CustomerCreditService customerCreditService, PropertyManagementAccess access,
                            AuditLogService auditLogService) {
        this.repository = repository;
        this.leaseRepository = leaseRepository;
        this.inspectionRepository = inspectionRepository;
        this.itemRepository = itemRepository;
        this.views = views;
        this.rentService = rentService;
        this.ledger = ledger;
        this.customerCreditService = customerCreditService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ sums

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** What has moved on a lease so far (voided lines leave out). */
    record Sums(BigDecimal received, BigDecimal transferredIn, BigDecimal deducted, BigDecimal refunded, BigDecimal transferredOut) {
        BigDecimal paidIn() {
            return received.add(transferredIn);
        }

        BigDecimal held() {
            return paidIn().subtract(deducted).subtract(refunded).subtract(transferredOut);
        }
    }

    static Sums sums(List<PmDepositTxn> txns) {
        BigDecimal rec = BigDecimal.ZERO, tin = BigDecimal.ZERO, ded = BigDecimal.ZERO, ref = BigDecimal.ZERO, tout = BigDecimal.ZERO;
        for (PmDepositTxn t : txns) {
            if (t.isVoided()) continue;
            switch (t.getKind()) {
                case RECEIVED -> rec = rec.add(t.getAmount());
                case TRANSFER_IN -> tin = tin.add(t.getAmount());
                case DEDUCTION -> ded = ded.add(t.getAmount());
                case REFUND -> ref = ref.add(t.getAmount());
                case TRANSFER_OUT -> tout = tout.add(t.getAmount());
            }
        }
        return new Sums(rec, tin, ded, ref, tout);
    }

    static boolean over(PmLease l) {
        return l.getStatus() == LeaseStatus.ENDED || l.getStatus() == LeaseStatus.TERMINATED;
    }

    static String state(PmLease l, Sums s) {
        BigDecimal agreed = nz(l.getDeposit()), paidIn = s.paidIn(), held = s.held();
        if (agreed.signum() == 0 && paidIn.signum() == 0) return "NONE";
        if (paidIn.signum() == 0) return "DUE";
        if (held.signum() > 0) {
            if (over(l)) return "REFUND_DUE";
            return paidIn.compareTo(agreed) < 0 ? "PART_PAID" : "HELD";
        }
        return "SETTLED";
    }

    private PmLease find(Long id) {
        return leaseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lease not found: " + id));
    }

    private PmLease lock(Long id) {
        return leaseRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Lease not found: " + id));
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public DepositView get(Actor a, Long leaseId) {
        access.on();
        return view(a, views.snapshot(), find(leaseId));
    }

    private TxnDto txnDto(PmDepositTxn t, Snapshot s, Map<Long, String> invoiceNumbers) {
        PmLease other = t.getOtherLeaseId() == null ? null : s.byId.get(t.getOtherLeaseId());
        boolean canVoid = !t.isVoided() && t.getReason() != PmDepositReason.UNPAID_RENT && t.getKind() != PmDepositKind.TRANSFER_IN && t.getKind() != PmDepositKind.TRANSFER_OUT;
        return new TxnDto(t.getId(), t.getKind(), t.getAmount(), t.getTxnDate(), t.getMethod(), t.getReason(), t.getNote(), t.getInvoiceId(), t.getInvoiceId() == null ? null : invoiceNumbers.get(t.getInvoiceId()),
                t.getReceiptNumber(), t.getOtherLeaseId(), other == null ? null : other.getCode(), t.isVoided(), t.getVoidReason(), t.getVoidedAt(), t.getRecordedByName(), canVoid);
    }

    private DepositView view(Actor a, Snapshot s, PmLease l) {
        List<PmDepositTxn> txns = repository.findByLeaseIdOrderByTxnDateAscIdAsc(l.getId());
        Sums sum = sums(txns);
        LeaseRow row = views.leaseRow(s, l);
        BigDecimal agreed = nz(l.getDeposit()), paidIn = sum.paidIn(), held = sum.held();
        BigDecimal stillToPay = agreed.subtract(paidIn).max(BigDecimal.ZERO);
        boolean over = over(l);
        List<RentChargeRow> charges = rentService.list(null, l.getId(), null, null, null, null, null);
        List<OpenInvoice> open = charges.stream().filter(c -> c.invoiceId() != null && c.outstanding() != null && c.outstanding().signum() > 0)
                .map(c -> new OpenInvoice(c.invoiceId(), c.invoiceNumber(), c.dueDate(), c.outstanding())).sorted(Comparator.comparing(OpenInvoice::dueDate)).toList();
        Map<Long, String> numbers = new HashMap<>();
        for (RentChargeRow c : charges) if (c.invoiceId() != null) numbers.put(c.invoiceId(), c.invoiceNumber());
        List<TransferTarget> targets = new ArrayList<>();
        if (held.signum() > 0) {
            for (PmLease o : s.leases) {
                if (o.getId().equals(l.getId()) || !o.getTenantId().equals(l.getTenantId()) || over(o)) continue;
                if (!over && !l.getId().equals(o.getRenewedFromId())) continue;
                Sums os = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(o.getId()));
                BigDecimal room = nz(o.getDeposit()).subtract(os.paidIn()).max(BigDecimal.ZERO);
                targets.add(new TransferTarget(o.getId(), o.getCode(), o.getStatus().name(), nz(o.getDeposit()), os.paidIn(), room));
            }
        }
        PmInspection moveOut = inspectionRepository.findByLeaseIdOrderByInspectionDateDescIdDesc(l.getId()).stream().filter(i -> i.getKind() == PmInspectionKind.MOVE_OUT).findFirst().orElse(null);
        BigDecimal suggested = moveOut == null ? BigDecimal.ZERO : itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(moveOut.getId()).stream()
                .map(i -> nz(i.getRepairCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean canTransfer = held.signum() > 0 && !targets.isEmpty();
        return new DepositView(l.getId(), l.getCode(), l.getStatus().name(), l.getTenantId(), row.tenantName(), row.propertyName(), row.unitNo(), agreed, sum.received(), sum.transferredIn(),
                sum.deducted(), sum.refunded(), sum.transferredOut(), paidIn, held, stillToPay, state(l, sum), !over && stillToPay.signum() > 0, over && held.signum() > 0, canTransfer, a.manager(),
                suggested, moveOut == null ? null : moveOut.getId(), txns.stream().map(t -> txnDto(t, s, numbers)).toList(), over && held.signum() > 0 ? open : List.of(), targets);
    }

    /** Every lease that has a deposit, or has had money moved. */
    @Transactional(readOnly = true)
    public List<DepositRow> list(String q, String state, Long propertyId, Long ownerId, Long tenantId) {
        access.on();
        Snapshot s = views.snapshot();
        Map<Long, List<PmDepositTxn>> byLease = repository.findAll().stream().collect(Collectors.groupingBy(PmDepositTxn::getLeaseId));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<DepositRow> out = new ArrayList<>();
        for (PmLease l : s.leases) {
            List<PmDepositTxn> txns = byLease.getOrDefault(l.getId(), List.of());
            if (nz(l.getDeposit()).signum() == 0 && txns.isEmpty()) continue;
            LeaseRow r = views.leaseRow(s, l);
            if ("RENEWED".equals(r.state()) && txns.isEmpty()) continue;
            Sums sum = sums(txns);
            String st = state(l, sum);
            if (state != null && !state.isBlank() && !state.equalsIgnoreCase(st)) continue;
            if (propertyId != null && !propertyId.equals(r.propertyId())) continue;
            if (ownerId != null && !PmViews.ownsNow(s, ownerId, r.propertyId())) continue;
            if (tenantId != null && !tenantId.equals(r.tenantId())) continue;
            if (!needle.isEmpty() && !(r.code().toLowerCase(Locale.ROOT).contains(needle) || r.tenantName() != null && r.tenantName().toLowerCase(Locale.ROOT).contains(needle)
                    || r.propertyName() != null && r.propertyName().toLowerCase(Locale.ROOT).contains(needle) || r.unitNo() != null && r.unitNo().toLowerCase(Locale.ROOT).contains(needle))) continue;
            out.add(new DepositRow(l.getId(), l.getCode(), l.getStatus().name(), r.state(), r.tenantId(), r.tenantName(), r.propertyId(), r.propertyName(), r.unitNo(), r.ownerId(), r.ownerName(),
                    l.getEndDate(), r.daysToEnd(), nz(l.getDeposit()), sum.paidIn(), sum.deducted(), sum.refunded(), sum.held(), st));
        }
        out.sort(Comparator.comparing((DepositRow d) -> "REFUND_DUE".equals(d.state()) ? 0 : 1).thenComparing(d -> d.propertyName() == null ? "" : d.propertyName().toLowerCase(Locale.ROOT)).thenComparing(d -> d.unitNo() == null ? "" : d.unitNo()));
        return out;
    }

    @Transactional(readOnly = true)
    public DepositTotals totals() {
        access.on();
        BigDecimal held = BigDecimal.ZERO, toCollect = BigDecimal.ZERO, refundDue = BigDecimal.ZERO;
        long refundCount = 0, heldCount = 0;
        for (DepositRow r : list(null, null, null, null, null)) {
            held = held.add(r.held());
            if (r.held().signum() > 0) heldCount++;
            if ("REFUND_DUE".equals(r.state())) {
                refundDue = refundDue.add(r.held());
                refundCount++;
            }
            if (("DUE".equals(r.state()) || "PART_PAID".equals(r.state())) && !"ENDED".equals(r.leaseStatus()) && !"TERMINATED".equals(r.leaseStatus())) toCollect = toCollect.add(r.agreed().subtract(r.paidIn()).max(BigDecimal.ZERO));
        }
        return new DepositTotals(held, toCollect, refundDue, refundCount, heldCount);
    }

    // ------------------------------------------------------------------ writing

    private static BigDecimal money(BigDecimal v, String what) {
        if (v == null || v.signum() <= 0) throw ApiException.badRequest(what + " must be more than zero.");
        if (v.scale() > 2 && v.stripTrailingZeros().scale() > 2) throw ApiException.badRequest(what + " can have at most two decimals.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate when(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d == null) return today;
        if (d.isAfter(today)) throw ApiException.badRequest("The date cannot be in the future.");
        if (d.isBefore(today.minusYears(15))) throw ApiException.badRequest("That date is too far back.");
        return d;
    }

    private static String method(String m) {
        String t = PmUtil.clean(m, 40, "The payment method");
        if (t == null) return "CASH";
        String key = t.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!METHODS.contains(key)) throw ApiException.badRequest("Choose the payment method from the list.");
        return key;
    }

    private PmDepositTxn txn(PmLease l, PmDepositKind kind, BigDecimal amount, LocalDate date, String method, String note, Actor a) {
        PmDepositTxn t = new PmDepositTxn();
        t.setLeaseId(l.getId());
        t.setKind(kind);
        t.setAmount(amount);
        t.setTxnDate(date);
        t.setMethod(method);
        t.setNote(PmUtil.clean(note, 1000, "The note"));
        t.setRecordedByName(PropertyManagementAccess.name(a.user()));
        return t;
    }

    private String tenantName(PmLease l) {
        PmTenant t = views.snapshot().tenants.get(l.getTenantId());
        return t == null ? "tenant" : t.getName();
    }

    @Transactional
    public DepositView receive(Actor a, Long leaseId, ReceiveRequest r) {
        access.on();
        PmLease l = lock(leaseId);
        if (over(l)) throw ApiException.conflict("This lease is over, so no more deposit can be taken on it.");
        BigDecimal amount = money(r.amount(), "The amount");
        Sums sum = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(l.getId()));
        BigDecimal agreed = nz(l.getDeposit());
        if (agreed.signum() == 0) throw ApiException.badRequest("No deposit is agreed on this lease. Set the deposit on the lease first.");
        BigDecimal room = agreed.subtract(sum.paidIn());
        if (amount.compareTo(room) > 0) throw ApiException.badRequest("The deposit agreed is " + agreed + " and " + sum.paidIn() + " is already in, so at most " + room.max(BigDecimal.ZERO) + " can be received.");
        PmDepositTxn t = repository.save(txn(l, PmDepositKind.RECEIVED, amount, when(r.date()), method(r.method()), r.note(), a));
        ledger.deposit(t, l.getCode(), tenantName(l), false);
        auditLogService.record("PM_DEPOSIT_RECEIVED", "Received deposit " + amount + " on lease " + l.getCode(), "PmLease", l.getId());
        return view(a, views.snapshot(), l);
    }

    private void needOver(PmLease l, String what) {
        if (!over(l)) throw ApiException.conflict("End or terminate the lease first, then " + what + ".");
    }

    @Transactional
    public DepositView deduct(Actor a, Long leaseId, DeductRequest r) {
        access.on();
        PmLease l = lock(leaseId);
        needOver(l, "keep part of the deposit");
        BigDecimal amount = money(r.amount(), "The amount");
        if (r.reason() == null) throw ApiException.badRequest("Say why the deposit is being kept.");
        Sums sum = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(l.getId()));
        if (amount.compareTo(sum.held()) > 0) throw ApiException.badRequest("Only " + sum.held() + " is held, so you cannot keep " + amount + ".");
        LocalDate date = when(r.date());
        PmDepositTxn t = txn(l, PmDepositKind.DEDUCTION, amount, date, null, r.note(), a);
        t.setReason(r.reason());
        if (r.reason() == PmDepositReason.UNPAID_RENT) {
            if (r.invoiceId() == null) throw ApiException.badRequest("Choose the rent invoice to settle.");
            RentChargeRow charge = rentService.list(null, l.getId(), null, null, null, null, null).stream().filter(c -> r.invoiceId().equals(c.invoiceId())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("That invoice is not rent of this lease."));
            if (charge.outstanding() == null || charge.outstanding().signum() <= 0) throw ApiException.badRequest("That invoice is already paid.");
            if (amount.compareTo(charge.outstanding()) > 0) throw ApiException.badRequest("Invoice " + charge.invoiceNumber() + " is only owed " + charge.outstanding() + ".");
            CustomerPaymentDto pay = customerCreditService.recordInvoicePaymentFromDeposit(r.invoiceId(), amount, "Paid from the security deposit of lease " + l.getCode(), a.user());
            t.setInvoiceId(r.invoiceId());
            t.setReceiptNumber(pay.receiptNumber());
        } else if (r.invoiceId() != null) {
            throw ApiException.badRequest("Only a deduction for unpaid rent settles an invoice.");
        }
        t = repository.save(t);
        ledger.deposit(t, l.getCode(), tenantName(l), false);
        auditLogService.record("PM_DEPOSIT_KEPT", "Kept " + amount + " of the deposit of lease " + l.getCode() + " (" + r.reason() + ")", "PmLease", l.getId());
        return view(a, views.snapshot(), l);
    }

    @Transactional
    public DepositView refund(Actor a, Long leaseId, RefundRequest r) {
        access.on();
        PmLease l = lock(leaseId);
        needOver(l, "refund the deposit");
        BigDecimal amount = money(r.amount(), "The amount");
        Sums sum = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(l.getId()));
        if (amount.compareTo(sum.held()) > 0) throw ApiException.badRequest("Only " + sum.held() + " is held, so you cannot refund " + amount + ".");
        PmDepositTxn t = repository.save(txn(l, PmDepositKind.REFUND, amount, when(r.date()), method(r.method()), r.note(), a));
        ledger.deposit(t, l.getCode(), tenantName(l), false);
        auditLogService.record("PM_DEPOSIT_REFUNDED", "Refunded " + amount + " of the deposit of lease " + l.getCode(), "PmLease", l.getId());
        return view(a, views.snapshot(), l);
    }

    /** Moves held deposit to another lease of the same tenant, normally the renewal. Nothing moves in the books: it is the same debt to the same tenant. */
    @Transactional
    public DepositView transfer(Actor a, Long leaseId, TransferRequest r) {
        access.on();
        if (r.toLeaseId() == null) throw ApiException.badRequest("Choose the lease to move the deposit to.");
        if (r.toLeaseId().equals(leaseId)) throw ApiException.badRequest("Choose a different lease.");
        // lock both leases in id order so two opposite transfers cannot wait for each other
        PmLease first = lock(Math.min(leaseId, r.toLeaseId())), second = lock(Math.max(leaseId, r.toLeaseId()));
        PmLease from = first.getId().equals(leaseId) ? first : second, to = first.getId().equals(leaseId) ? second : first;
        if (!from.getTenantId().equals(to.getTenantId())) throw ApiException.badRequest("A deposit can only be moved to another lease of the same tenant.");
        if (over(to)) throw ApiException.badRequest("That lease is over.");
        if (!over(from) && !from.getId().equals(to.getRenewedFromId())) throw ApiException.conflict("The deposit can be moved while the lease is running only to the lease that renews it.");
        BigDecimal amount = money(r.amount(), "The amount");
        Sums fs = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(from.getId()));
        if (amount.compareTo(fs.held()) > 0) throw ApiException.badRequest("Only " + fs.held() + " is held, so you cannot move " + amount + ".");
        Sums ts = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(to.getId()));
        BigDecimal room = nz(to.getDeposit()).subtract(ts.paidIn()).max(BigDecimal.ZERO);
        if (amount.compareTo(room) > 0) throw ApiException.badRequest("Lease " + to.getCode() + " needs only " + room + " more deposit (agreed " + nz(to.getDeposit()) + ").");
        LocalDate date = when(r.date());
        PmDepositTxn out = txn(from, PmDepositKind.TRANSFER_OUT, amount, date, null, r.note(), a);
        out.setOtherLeaseId(to.getId());
        out = repository.save(out);
        PmDepositTxn in = txn(to, PmDepositKind.TRANSFER_IN, amount, date, null, r.note(), a);
        in.setOtherLeaseId(from.getId());
        in.setOtherTxnId(out.getId());
        in = repository.save(in);
        out.setOtherTxnId(in.getId());
        repository.save(out);
        auditLogService.record("PM_DEPOSIT_MOVED", "Moved " + amount + " of the deposit from lease " + from.getCode() + " to lease " + to.getCode(), "PmLease", from.getId());
        return view(a, views.snapshot(), find(leaseId));
    }

    /** Cancels a line entered by mistake. Moves between leases and deductions that settled a rent invoice cannot be voided here. */
    @Transactional
    public DepositView voidTxn(Actor a, Long txnId, VoidRequest r) {
        access.on();
        PmDepositTxn t = repository.findById(txnId).orElseThrow(() -> ApiException.notFound("Deposit entry not found: " + txnId));
        PmLease l = lock(t.getLeaseId());
        t = repository.findById(txnId).orElseThrow();
        if (t.isVoided()) throw ApiException.conflict("That entry is already voided.");
        String reason = PmUtil.required(r == null ? null : r.reason(), 500, "The reason");
        if (t.getKind() == PmDepositKind.TRANSFER_IN || t.getKind() == PmDepositKind.TRANSFER_OUT)
            throw ApiException.conflict("A move between leases cannot be voided. Move the money back with a new transfer.");
        if (t.getReason() == PmDepositReason.UNPAID_RENT) throw ApiException.conflict("This entry paid rent invoice " + t.getReceiptNumber() + ". Reverse that payment from the invoice instead.");
        t.setVoided(true);
        t.setVoidReason(reason);
        t.setVoidedAt(Instant.now());
        // money must not go negative: voiding a receipt that has since been partly refunded or kept would leave the balance below zero
        Sums after = sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(l.getId()));
        if (after.held().signum() < 0) {
            t.setVoided(false);
            t.setVoidReason(null);
            t.setVoidedAt(null);
            throw ApiException.conflict("That receipt cannot be voided because part of it has already been kept or refunded. Void those entries first.");
        }
        repository.save(t);
        ledger.deposit(t, l.getCode(), tenantName(l), true);
        auditLogService.record("PM_DEPOSIT_VOIDED", "Voided a " + t.getKind() + " of " + t.getAmount() + " on lease " + l.getCode() + ": " + reason, "PmLease", l.getId());
        return view(a, views.snapshot(), l);
    }

    // ------------------------------------------------------------------ for the lease page

    /** A draft lease with money on it cannot be deleted (the books would be left with a deposit that belongs to nobody). */
    @Transactional(readOnly = true)
    public void requireNone(Long leaseId) {
        if (repository.existsByLeaseId(leaseId)) throw ApiException.badRequest("This lease has deposit entries, so it cannot be deleted.");
    }

    /** What is still held on this lease, for the lease page and the renewal. */
    @Transactional(readOnly = true)
    public BigDecimal held(Long leaseId) {
        return sums(repository.findByLeaseIdOrderByTxnDateAscIdAsc(leaseId)).held();
    }
}
