package com.posapp.propertymgmt;

import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.Overview;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** The Property Management home page: occupancy, the rent roll, vacancies and leases that need attention. */
@Service
public class PmOverviewService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PmViews views;
    private final PmPropertyService propertyService;
    private final PmRentService rentService;
    private final PmMaintenanceService maintenanceService;
    private final PmStatementService statementService;
    private final PropertyManagementAccess access;
    private final PmDocumentService documentService;
    private final PmDepositService depositService;

    public PmOverviewService(PmViews views, PmPropertyService propertyService, PmRentService rentService, PmMaintenanceService maintenanceService, PmStatementService statementService, PropertyManagementAccess access, PmDocumentService documentService, PmDepositService depositService) {
        this.depositService = depositService;
        this.documentService = documentService;
        this.statementService = statementService;
        this.rentService = rentService;
        this.maintenanceService = maintenanceService;
        this.views = views;
        this.propertyService = propertyService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        access.on();
        Snapshot s = views.snapshot();
        List<UnitRow> units = s.units.values().stream().filter(u -> {
            PmProperty p = s.properties.get(u.getPropertyId());
            return p != null && p.isActive();
        }).map(u -> views.unitRow(s, u)).toList();
        long occupied = units.stream().filter(u -> "OCCUPIED".equals(u.occupancy())).count();
        long upcoming = units.stream().filter(u -> "UPCOMING".equals(u.occupancy())).count();
        long vacant = units.stream().filter(u -> "VACANT".equals(u.occupancy())).count();
        long off = units.stream().filter(u -> "OFF_MARKET".equals(u.occupancy())).count();
        long lettable = units.size() - off;
        BigDecimal pct = lettable <= 0 ? ZERO : BigDecimal.valueOf(occupied * 100.0).divide(BigDecimal.valueOf(lettable), 1, RoundingMode.HALF_UP);
        BigDecimal vacantRent = units.stream().filter(u -> "VACANT".equals(u.occupancy()) && u.marketRent() != null).map(UnitRow::marketRent).reduce(ZERO, BigDecimal::add);
        List<LeaseRow> rows = s.leases.stream().map(l -> views.leaseRow(s, l)).toList();
        long expiring = rows.stream().filter(r -> "EXPIRING".equals(r.state())).count();
        long expired = rows.stream().filter(r -> "EXPIRED".equals(r.state())).count();
        long drafts = rows.stream().filter(r -> "DRAFT".equals(r.state())).count();
        List<LeaseRow> attention = rows.stream().filter(r -> "EXPIRING".equals(r.state()) || "EXPIRED".equals(r.state())).sorted(Comparator.comparing(LeaseRow::endDate)).limit(8).toList();
        List<UnitRow> vacantUnits = propertyService.vacantUnits(s).stream().filter(r -> {
            PmProperty p = s.properties.get(r.propertyId());
            return p != null && p.isActive();
        }).limit(8).toList();
        long properties = s.properties.values().stream().filter(PmProperty::isActive).count();
        var rent = rentService.summary(s);
        var mx = maintenanceService.summary(s);
        var sm = statementService.summary();
        var dep = depositService.totals();
        return new Overview(s.owners.values().stream().filter(PmOwner::isActive).count(), properties, units.size(), occupied, upcoming, vacant, off, pct, PmViews.rentRoll(units), vacantRent,
                expiring, expired, drafts, s.tenants.values().stream().filter(PmTenant::isActive).count(), s.warnDays, attention, vacantUnits, rent.toInvoice(), rent.toInvoiceAmount(), rent.arrears(), rent.tenantsInArrears(), mx.open(), mx.urgent(), mx.overdue(), mx.attention(), sm.drafts(), sm.toPay(), sm.toPayAmount(), documentService.countExpiringSoon(), documentService.countExpired(), dep.held(), dep.refundDueCount(), dep.refundDue());
    }
}
