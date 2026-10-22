package com.posapp.propertymgmt;

import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.ContractorRow;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.RentIncreaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.TrendRow;
import com.posapp.propertymgmt.dto.PropertyMgmtMaintenanceDtos.MaintenanceRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.CollectionRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Property Management reports that need working out across leases, months or contractors: rent increases due, the monthly trend, repairs by contractor. */
@Service
public class PmInsightService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PmViews views;
    private final PmRentService rentService;
    private final PmMaintenanceService maintenanceService;
    private final PropertyManagementAccess access;

    public PmInsightService(PmViews views, PmRentService rentService, PmMaintenanceService maintenanceService, PropertyManagementAccess access) {
        this.views = views;
        this.rentService = rentService;
        this.maintenanceService = maintenanceService;
        this.access = access;
    }

    /**
     * Leases in force that carry a yearly increase, soonest end first. The increase takes effect when the lease is renewed, so the date that matters is the end date.
     * {@code soonOnly} keeps those ending within {@code withinDays} days (or already past their end).
     */
    @Transactional
    public List<RentIncreaseRow> rentIncreases(boolean soonOnly, int withinDays) {
        access.on();
        Snapshot s = views.snapshot();
        List<RentIncreaseRow> out = new ArrayList<>();
        for (PmLease l : s.leases) {
            if (l.getStatus() != LeaseStatus.ACTIVE || l.getEscalationPercent() == null || l.getEscalationPercent().signum() == 0) continue;
            LeaseRow r = views.leaseRow(s, l);
            if ("RENEWED".equals(r.state())) continue;
            if (soonOnly && r.daysToEnd() != null && r.daysToEnd() > withinDays) continue;
            BigDecimal newRent = PmLeaseService.renewalRent(l);
            out.add(new RentIncreaseRow(l.getId(), l.getCode(), r.propertyName(), r.unitNo(), r.ownerName(), r.tenantName(), l.getEndDate(), r.daysToEnd(), l.getRent(),
                    l.getEscalationPercent(), newRent, newRent.subtract(l.getRent()), l.getFrequency().name(), r.state(), r.renewalCode()));
        }
        out.sort(Comparator.comparing(RentIncreaseRow::endDate).thenComparing(RentIncreaseRow::code));
        return out;
    }

    /** The last {@code months} months (default 12, at most 36): units let at month end next to the rent that fell due and how much of it was collected. */
    @Transactional
    public List<TrendRow> trend(int months, boolean problemsOnly) {
        access.on();
        int n = Math.max(1, Math.min(36, months));
        Snapshot s = views.snapshot();
        YearMonth last = YearMonth.from(s.today);
        YearMonth first = last.minusMonths(n - 1L);
        Map<String, CollectionRow> money = new LinkedHashMap<>();
        for (CollectionRow c : rentService.collection(first.atDay(1), last.atEndOfMonth(), false)) money.put(c.month(), c);
        Set<Long> unitIds = new HashSet<>();
        for (PmUnit u : s.units.values()) {
            PmProperty p = s.properties.get(u.getPropertyId());
            if (p != null && p.isActive()) unitIds.add(u.getId());
        }
        List<TrendRow> out = new ArrayList<>();
        for (YearMonth ym = first; !ym.isAfter(last); ym = ym.plusMonths(1)) {
            LocalDate at = ym.equals(last) ? s.today : ym.atEndOfMonth();
            Set<Long> occupied = new HashSet<>();
            for (PmLease l : s.leases) {
                if (l.getStatus() == LeaseStatus.DRAFT || !unitIds.contains(l.getUnitId())) continue;
                LocalDate end = l.getTerminatedDate() != null ? l.getTerminatedDate() : l.getEndDate();
                if (!l.getStartDate().isAfter(at) && !end.isBefore(at)) occupied.add(l.getUnitId());
            }
            BigDecimal occ = unitIds.isEmpty() ? ZERO : BigDecimal.valueOf(occupied.size()).multiply(HUNDRED).divide(BigDecimal.valueOf(unitIds.size()), 1, RoundingMode.HALF_UP);
            CollectionRow c = money.get(ym.toString());
            if (problemsOnly && (c == null || c.outstanding().signum() == 0) && occ.compareTo(HUNDRED) >= 0) continue;
            out.add(new TrendRow(ym.toString(), unitIds.size(), occupied.size(), occ, c == null ? 0 : c.charges(), c == null ? ZERO : c.invoiced(), c == null ? ZERO : c.collected(),
                    c == null ? ZERO : c.outstanding(), c == null ? ZERO : c.collectedPercent()));
        }
        return out;
    }

    /** Repair jobs per contractor (cancelled ones left out): how many, how many done and still open, what they cost and how long they took. */
    @Transactional
    public List<ContractorRow> contractors(boolean costedOnly) {
        access.on();
        Map<String, List<MaintenanceRow>> by = new LinkedHashMap<>();
        for (MaintenanceRow m : maintenanceService.list(null, null, null, null, null, null, null, null, null, null)) {
            if (m.status() == MaintenanceStatus.CANCELLED) continue;
            String name = m.contractorName() == null || m.contractorName().isBlank() ? "No contractor named" : m.contractorName().trim();
            by.computeIfAbsent(name, k -> new ArrayList<>()).add(m);
        }
        List<ContractorRow> out = new ArrayList<>();
        for (Map.Entry<String, List<MaintenanceRow>> e : by.entrySet()) {
            long done = 0, open = 0, late = 0, daysN = 0, days = 0;
            BigDecimal cost = ZERO;
            int costN = 0;
            for (MaintenanceRow m : e.getValue()) {
                if (m.status() == MaintenanceStatus.COMPLETED) {
                    done++;
                    if (m.actualCost() != null) { cost = cost.add(m.actualCost()); costN++; }
                    if (m.daysOpen() != null && m.daysOpen() >= 0) { days += m.daysOpen(); daysN++; }
                } else {
                    open++;
                }
                if (m.overdue()) late++;
            }
            if (costedOnly && cost.signum() == 0) continue;
            BigDecimal avgCost = costN == 0 ? null : cost.divide(BigDecimal.valueOf(costN), 2, RoundingMode.HALF_UP);
            BigDecimal avgDays = daysN == 0 ? null : BigDecimal.valueOf(days).divide(BigDecimal.valueOf(daysN), 1, RoundingMode.HALF_UP);
            out.add(new ContractorRow(e.getKey(), e.getValue().size(), done, open, late, cost, avgCost, avgDays));
        }
        out.sort(Comparator.comparing(ContractorRow::totalCost).reversed().thenComparing(ContractorRow::contractorName));
        return out;
    }
}
