package com.posapp.propertymgmt;

import com.posapp.common.ApiException;
import com.posapp.credit.CustomerPayment;
import com.posapp.credit.CustomerPaymentAllocation;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.AccountLine;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.PaymentRow;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.Receipt;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.ReceiptLine;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.TenantAccount;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.RentChargeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a tenant was charged and has paid (the account statement), the rent payments received, and the data of a rent receipt. Rent is charged when it is invoiced and dated on its due
 * date; a payment is the money recorded against those invoices. Voided invoices are left out.
 */
@Service
public class PmTenantAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final PmViews views;
    private final PmRentChargeRepository charges;
    private final PmRentService rent;
    private final PmPaymentQueryRepository payments;
    private final PropertyManagementAccess access;

    public PmTenantAccountService(PmViews views, PmRentChargeRepository charges, PmRentService rent, PmPaymentQueryRepository payments, PropertyManagementAccess access) {
        this.views = views;
        this.charges = charges;
        this.rent = rent;
        this.payments = payments;
        this.access = access;
    }

    private static LocalDate dateOf(Instant i) {
        return i == null ? null : i.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static String period(RentChargeRow r) {
        return r.periodStart().format(DAY) + " to " + r.periodEnd().format(DAY);
    }

    /** The invoiced, not voided rent of the given charges, as the screens see it (asked afresh). */
    private List<RentChargeRow> invoiced(List<PmRentCharge> list, Snapshot s) {
        List<PmRentCharge> withInvoice = list.stream().filter(c -> c.getStatus() == RentChargeStatus.INVOICED).toList();
        return rent.rows(withInvoice, s, true).stream().filter(r -> r.invoiceId() != null && r.billed() != null).toList();
    }

    private static Set<Long> invoiceIds(List<RentChargeRow> rows) {
        return rows.stream().map(RentChargeRow::invoiceId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public TenantAccount account(Long tenantId, LocalDate from, LocalDate to) {
        access.on();
        Snapshot s = views.snapshot();
        PmTenant t = s.tenants.get(tenantId);
        if (t == null) throw ApiException.notFound("Tenant not found");
        LocalDate hi = to != null ? to : s.today;
        LocalDate lo = from != null ? from : hi.minusMonths(5).withDayOfMonth(1);
        if (lo.isAfter(hi)) throw ApiException.badRequest("The start date is after the end date.");
        List<Long> leaseIds = s.leases.stream().filter(l -> tenantId.equals(l.getTenantId())).map(PmLease::getId).toList();
        List<RentChargeRow> rows = leaseIds.isEmpty() ? List.of() : invoiced(charges.findByLeaseIdIn(leaseIds), s);
        Set<Long> ids = invoiceIds(rows);
        List<CustomerPaymentAllocation> allocs = ids.isEmpty() ? List.of() : payments.forInvoices(ids);

        record Ev(LocalDate date, int order, long id, AccountLine line) {}
        List<Ev> events = new ArrayList<>();
        for (RentChargeRow r : rows) {
            String what = (r.extras() != null && r.extras().signum() > 0 ? "Rent and charges " : "Rent ") + r.leaseCode() + " · " + r.propertyName() + " " + r.unitNo() + " (" + period(r) + (r.prorated() ? ", part period" : "") + ")";
            events.add(new Ev(r.dueDate(), 0, r.id(), new AccountLine(r.dueDate(), "RENT", what, r.invoiceNumber(), r.billed(), null, null, null)));
        }
        Map<Long, List<CustomerPaymentAllocation>> byPayment = new LinkedHashMap<>();
        for (CustomerPaymentAllocation a : allocs) byPayment.computeIfAbsent(a.getPayment().getId(), k -> new ArrayList<>()).add(a);
        for (List<CustomerPaymentAllocation> group : byPayment.values()) {
            CustomerPayment p = group.get(0).getPayment();
            BigDecimal amount = group.stream().map(a -> nz(a.getAmountApplied())).reduce(ZERO, BigDecimal::add);
            String what = "Payment received" + (p.getMethod() == null || p.getMethod().isBlank() ? "" : " (" + p.getMethod() + ")");
            LocalDate d = dateOf(p.getPaidAt());
            events.add(new Ev(d, 1, p.getId(), new AccountLine(d, "PAYMENT", what, p.getReceiptNumber(), null, amount, null, p.getId())));
        }
        events.sort(Comparator.comparing(Ev::date).thenComparingInt(Ev::order).thenComparingLong(Ev::id));

        BigDecimal opening = ZERO;
        for (Ev e : events) {
            if (!e.date().isBefore(lo)) break;
            opening = opening.add(nz(e.line().charge())).subtract(nz(e.line().payment()));
        }
        List<AccountLine> lines = new ArrayList<>();
        BigDecimal bal = opening, charged = ZERO, paid = ZERO;
        for (Ev e : events) {
            if (e.date().isBefore(lo) || e.date().isAfter(hi)) continue;
            AccountLine l = e.line();
            bal = bal.add(nz(l.charge())).subtract(nz(l.payment()));
            charged = charged.add(nz(l.charge()));
            paid = paid.add(nz(l.payment()));
            lines.add(new AccountLine(l.date(), l.kind(), l.description(), l.reference(), l.charge(), l.payment(), bal, l.paymentId()));
        }
        BigDecimal overdue = rows.stream().filter(r -> "OVERDUE".equals(r.status())).map(r -> nz(r.outstanding())).reduce(ZERO, BigDecimal::add);
        return new TenantAccount(t.getId(), t.getCode(), t.getName(), t.getEmail(), lo, hi, opening, lines, charged, paid, bal, overdue);
    }

    /** Payments received for rent: of one lease or tenant, or (with neither) of the last 90 days. */
    @Transactional
    public List<PaymentRow> payments(Long leaseId, Long tenantId, LocalDate from, LocalDate to) {
        access.on();
        Snapshot s = views.snapshot();
        LocalDate lo = from, hi = to;
        if (leaseId == null && tenantId == null && lo == null) lo = s.today.minusDays(90);
        List<PmRentCharge> cs;
        if (leaseId != null) cs = charges.findByLeaseIdOrderByPeriodStartAsc(leaseId);
        else if (tenantId != null) {
            List<Long> ids = s.leases.stream().filter(l -> tenantId.equals(l.getTenantId())).map(PmLease::getId).toList();
            cs = ids.isEmpty() ? List.of() : charges.findByLeaseIdIn(ids);
        } else cs = charges.findAll();
        cs = cs.stream().filter(c -> c.getStatus() == RentChargeStatus.INVOICED && c.getInvoiceId() != null).toList();
        if (cs.isEmpty()) return List.of();
        Map<Long, PmRentCharge> byInvoice = new HashMap<>();
        for (PmRentCharge c : cs) byInvoice.put(c.getInvoiceId(), c);
        List<CustomerPaymentAllocation> allocs = payments.forInvoices(byInvoice.keySet());
        Map<Long, List<CustomerPaymentAllocation>> byPayment = new LinkedHashMap<>();
        for (CustomerPaymentAllocation a : allocs) byPayment.computeIfAbsent(a.getPayment().getId(), k -> new ArrayList<>()).add(a);
        List<PaymentRow> out = new ArrayList<>();
        for (List<CustomerPaymentAllocation> group : byPayment.values()) {
            LocalDate d = dateOf(group.get(0).getPayment().getPaidAt());
            if (lo != null && d.isBefore(lo)) continue;
            if (hi != null && d.isAfter(hi)) continue;
            out.add(paymentRow(s, group, byInvoice));
        }
        out.sort(Comparator.comparing(PaymentRow::paidDate).reversed().thenComparing(PaymentRow::paymentId, Comparator.reverseOrder()));
        return out;
    }

    private PaymentRow paymentRow(Snapshot s, List<CustomerPaymentAllocation> group, Map<Long, PmRentCharge> byInvoice) {
        CustomerPayment p = group.get(0).getPayment();
        BigDecimal amount = ZERO;
        Set<Long> leaseIds = new LinkedHashSet<>();
        List<String> invoices = new ArrayList<>();
        for (CustomerPaymentAllocation a : group) {
            PmRentCharge c = byInvoice.get(a.getInvoice().getId());
            if (c == null) continue;
            amount = amount.add(nz(a.getAmountApplied()));
            leaseIds.add(c.getLeaseId());
            if (c.getInvoiceNumber() != null && !invoices.contains(c.getInvoiceNumber())) invoices.add(c.getInvoiceNumber());
        }
        PmLease l = leaseIds.isEmpty() ? null : s.byId.get(leaseIds.iterator().next());
        PmUnit u = l == null ? null : s.units.get(l.getUnitId());
        PmProperty pr = u == null ? null : s.properties.get(u.getPropertyId());
        PmTenant t = l == null ? null : s.tenants.get(l.getTenantId());
        String unit = u == null ? null : (pr == null ? "" : pr.getName() + " · ") + u.getUnitNo();
        return new PaymentRow(p.getId(), p.getReceiptNumber(), dateOf(p.getPaidAt()), p.getMethod(), p.getNote(), amount, t == null ? null : t.getId(), t == null ? null : t.getName(),
                leaseIds.size() == 1 && l != null ? l.getId() : null, leaseIds.size() == 1 && l != null ? l.getCode() : leaseIds.size() > 1 ? "several leases" : null, unit, invoices);
    }

    /** The data of the receipt for one payment: what it paid and what was still owed on each invoice right after it. */
    @Transactional
    public Receipt receipt(Long paymentId) {
        access.on();
        List<CustomerPaymentAllocation> mine = payments.ofPayment(paymentId);
        Set<Long> invoiceIds = mine.stream().map(a -> a.getInvoice().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<PmRentCharge> cs = invoiceIds.isEmpty() ? List.of() : charges.findByInvoiceIdIn(invoiceIds);
        if (cs.isEmpty()) throw ApiException.notFound("That payment is not rent from a lease, so there is no rent receipt for it.");
        Snapshot s = views.snapshot();
        Map<Long, PmRentCharge> byInvoice = new HashMap<>();
        for (PmRentCharge c : cs) byInvoice.put(c.getInvoiceId(), c);
        Map<Long, RentChargeRow> rowOf = new HashMap<>();
        for (RentChargeRow r : rent.rows(cs, s, true)) rowOf.put(r.id(), r);
        CustomerPayment p = mine.get(0).getPayment();
        List<CustomerPaymentAllocation> all = payments.forInvoices(byInvoice.keySet());
        List<ReceiptLine> lines = new ArrayList<>();
        for (CustomerPaymentAllocation a : mine) {
            PmRentCharge c = byInvoice.get(a.getInvoice().getId());
            if (c == null) continue;
            RentChargeRow r = rowOf.get(c.getId());
            BigDecimal total = r != null && r.billed() != null ? r.billed() : c.getAmount();
            BigDecimal upTo = ZERO;
            for (CustomerPaymentAllocation o : all) {
                if (!o.getInvoice().getId().equals(c.getInvoiceId())) continue;
                CustomerPayment op = o.getPayment();
                boolean before = op.getPaidAt().isBefore(p.getPaidAt()) || op.getPaidAt().equals(p.getPaidAt()) && op.getId() <= p.getId();
                if (before) upTo = upTo.add(nz(o.getAmountApplied()));
            }
            lines.add(new ReceiptLine(c.getInvoiceNumber(), r == null ? null : r.leaseCode(), c.getPeriodStart().format(DAY) + " to " + c.getPeriodEnd().format(DAY), total, nz(a.getAmountApplied()),
                    total.subtract(upTo).max(ZERO)));
        }
        lines.sort(Comparator.comparing(ReceiptLine::period));
        PaymentRow row = paymentRow(s, mine, byInvoice);
        PmTenant t = row.tenantId() == null ? null : s.tenants.get(row.tenantId());
        String by = p.getRecordedBy() == null ? null : PropertyManagementAccess.name(p.getRecordedBy());
        return new Receipt(row, t == null ? null : t.getEmail(), by, lines);
    }
}
