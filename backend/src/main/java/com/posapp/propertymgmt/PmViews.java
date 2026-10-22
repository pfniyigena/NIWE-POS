package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitRow;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out what the screens show about units and leases (who is in a unit, is a lease about to run out) from the stored records. One {@link Snapshot} is read per request and
 * everything is derived from it, so a list never asks the database again for each row.
 */
@Component
public class PmViews {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PmOwnerRepository ownerRepository;
    private final PmPropertyRepository propertyRepository;
    private final PmUnitRepository unitRepository;
    private final PmTenantRepository tenantRepository;
    private final PmLeaseRepository leaseRepository;
    private final PmSettingsService settingsService;
    private final PmPropertyOwnerRepository propertyOwnerRepository;

    public PmViews(PmOwnerRepository ownerRepository, PmPropertyRepository propertyRepository, PmUnitRepository unitRepository, PmTenantRepository tenantRepository,
                   PmLeaseRepository leaseRepository, PmSettingsService settingsService, PmPropertyOwnerRepository propertyOwnerRepository) {
        this.ownerRepository = ownerRepository;
        this.propertyRepository = propertyRepository;
        this.unitRepository = unitRepository;
        this.tenantRepository = tenantRepository;
        this.leaseRepository = leaseRepository;
        this.settingsService = settingsService;
        this.propertyOwnerRepository = propertyOwnerRepository;
    }

    public static final class Snapshot {
        public final LocalDate today = LocalDate.now();
        public int warnDays;
        public BigDecimal moduleFeePercent;
        public BigDecimal feeVatPercent;
        public Map<Long, PmOwner> owners;
        public Map<Long, PmProperty> properties;
        public Map<Long, PmUnit> units;
        public Map<Long, PmTenant> tenants;
        public List<PmLease> leases;
        public Map<Long, List<PmLease>> leasesByUnit;
        public Map<Long, PmLease> byId;
        /** lease id -> the lease that renews it (the newest, if several). */
        public Map<Long, PmLease> renewals;
        /** property id -> who owns it, as splits (oldest first). */
        public Map<Long, List<PmOwnership.Split>> splits;
    }

    @Transactional
    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        PmSettings st = settingsService.entity();
        s.warnDays = st.getExpiryWarningDays();
        s.moduleFeePercent = st.getDefaultFeePercent();
        s.feeVatPercent = st.getFeeVatPercent();
        s.owners = ownerRepository.findAll().stream().collect(Collectors.toMap(PmOwner::getId, Function.identity()));
        s.properties = propertyRepository.findAll().stream().collect(Collectors.toMap(PmProperty::getId, Function.identity()));
        s.units = unitRepository.findAll().stream().collect(Collectors.toMap(PmUnit::getId, Function.identity()));
        s.tenants = tenantRepository.findAll().stream().collect(Collectors.toMap(PmTenant::getId, Function.identity()));
        Map<Long, List<PmPropertyOwner>> shareRows = new HashMap<>();
        for (PmPropertyOwner r : propertyOwnerRepository.findAll()) shareRows.computeIfAbsent(r.getPropertyId(), k -> new ArrayList<>()).add(r);
        s.splits = new HashMap<>();
        for (Map.Entry<Long, List<PmPropertyOwner>> e : shareRows.entrySet()) s.splits.put(e.getKey(), PmOwnership.build(e.getValue()));
        s.leases = leaseRepository.findAll();
        s.byId = s.leases.stream().collect(Collectors.toMap(PmLease::getId, Function.identity()));
        s.leasesByUnit = new HashMap<>();
        s.renewals = new HashMap<>();
        for (PmLease l : s.leases) {
            s.leasesByUnit.computeIfAbsent(l.getUnitId(), k -> new ArrayList<>()).add(l);
            if (l.getRenewedFromId() != null) s.renewals.merge(l.getRenewedFromId(), l, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        return s;
    }

    // ------------------------------------------------------------------ money

    /** What one rent payment comes to per month. */
    public static BigDecimal monthly(BigDecimal rent, RentFrequency f) {
        if (rent == null) return ZERO;
        return rent.divide(BigDecimal.valueOf(f.months()), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ leases

    /** {@code renewal} is the lease that renews this one, if any. A lease past its end date whose renewal has been signed shows as RENEWED, not EXPIRED, and one about to end whose renewal is signed is not flagged as expiring. */
    public static String state(PmLease l, LocalDate today, int warnDays, PmLease renewal) {
        return switch (l.getStatus()) {
            case DRAFT -> "DRAFT";
            case ENDED -> "ENDED";
            case TERMINATED -> "TERMINATED";
            case ACTIVE -> l.getStartDate().isAfter(today) ? "UPCOMING"
                    : l.getEndDate().isBefore(today) ? (renewal != null && renewal.getStatus() == LeaseStatus.ACTIVE ? "RENEWED" : "EXPIRED")
                    : ChronoUnit.DAYS.between(today, l.getEndDate()) <= warnDays && !(renewal != null && renewal.getStatus() == LeaseStatus.ACTIVE) ? "EXPIRING" : "CURRENT";
        };
    }

    /** Days from today to the end date (negative once past); only for leases still in force. */
    public static Long daysToEnd(PmLease l, LocalDate today) {
        return l.getStatus() == LeaseStatus.ACTIVE ? ChronoUnit.DAYS.between(today, l.getEndDate()) : null;
    }

    /** A signed lease that is in force today (its start has come); the newest if there are several. */
    public static PmLease current(List<PmLease> leases, LocalDate today) {
        return leases == null ? null : leases.stream().filter(l -> l.getStatus() == LeaseStatus.ACTIVE && !l.getStartDate().isAfter(today))
                .max(Comparator.comparing(PmLease::getStartDate).thenComparing(PmLease::getId)).orElse(null);
    }

    /** The next signed lease that has not started yet. */
    public static PmLease upcoming(List<PmLease> leases, LocalDate today) {
        return leases == null ? null : leases.stream().filter(l -> l.getStatus() == LeaseStatus.ACTIVE && l.getStartDate().isAfter(today))
                .min(Comparator.comparing(PmLease::getStartDate).thenComparing(PmLease::getId)).orElse(null);
    }

    public LeaseRow leaseRow(Snapshot s, PmLease l) {
        PmUnit u = s.units.get(l.getUnitId());
        PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
        PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
        PmTenant t = s.tenants.get(l.getTenantId());
        PmLease from = l.getRenewedFromId() == null ? null : s.byId.get(l.getRenewedFromId());
        PmLease renewal = s.renewals.get(l.getId());
        return new LeaseRow(l.getId(), l.getCode(), l.getStatus(), state(l, s.today, s.warnDays, renewal), l.getUnitId(), u == null ? null : u.getUnitNo(), p == null ? null : p.getId(),
                p == null ? null : p.getCode(), p == null ? null : p.getName(), o == null ? null : o.getId(), o == null ? null : o.getName(), l.getTenantId(), t == null ? null : t.getName(),
                l.getStartDate(), l.getEndDate(), l.getRent(), l.getFrequency(), monthly(l.getRent(), l.getFrequency()), l.getDueDay(), l.getDeposit(), daysToEnd(l, s.today),
                l.getRenewedFromId(), from == null ? null : from.getCode(), renewal == null ? null : renewal.getId(), renewal == null ? null : renewal.getCode());
    }

    // ------------------------------------------------------------------ units

    private static LocalDate dateOf(java.time.Instant i) {
        return i == null ? null : i.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** When a unit with nobody in it last became free: the end of its latest finished lease, else the day the unit was entered. */
    private static LocalDate vacantSince(PmUnit u, List<PmLease> leases, LocalDate today) {
        LocalDate best = null;
        if (leases != null) {
            for (PmLease l : leases) {
                if (l.getStatus() != LeaseStatus.ENDED && l.getStatus() != LeaseStatus.TERMINATED) continue;
                LocalDate d = l.getTerminatedDate() != null ? l.getTerminatedDate() : l.getEndDate();
                if (d.isAfter(today)) d = today;
                if (best == null || d.isAfter(best)) best = d;
            }
        }
        return best != null ? best : dateOf(u.getCreatedAt());
    }

    public UnitRow unitRow(Snapshot s, PmUnit u) {
        PmProperty p = s.properties.get(u.getPropertyId());
        List<PmLease> leases = s.leasesByUnit.get(u.getId());
        PmLease cur = current(leases, s.today);
        PmLease up = cur == null ? upcoming(leases, s.today) : null;
        String occupancy = cur != null ? "OCCUPIED" : u.isOffMarket() ? "OFF_MARKET" : up != null ? "UPCOMING" : "VACANT";
        PmLease shown = cur != null ? cur : up;
        PmTenant t = shown == null ? null : s.tenants.get(shown.getTenantId());
        return new UnitRow(u.getId(), u.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getName(), u.getUnitNo(), u.getKind(), u.getFloor(), u.getBedrooms(),
                u.getBathrooms(), u.getSizeSqm(), u.getMarketRent(), u.getDefaultDeposit(), u.isOffMarket(), u.getOffMarketNote(), occupancy, shown == null ? null : shown.getId(),
                shown == null ? null : shown.getCode(), shown == null ? null : shown.getTenantId(), t == null ? null : t.getName(), shown == null ? null : shown.getRent(),
                shown == null ? null : shown.getFrequency(), shown == null ? null : monthly(shown.getRent(), shown.getFrequency()), shown == null ? null : shown.getStartDate(),
                shown == null ? null : shown.getEndDate(), shown == null ? null : ChronoUnit.DAYS.between(s.today, shown.getEndDate()),
                cur != null && cur.getEndDate().isBefore(s.today), cur == null ? vacantSince(u, leases, s.today) : null, u.getNotes());
    }

    /** The rent the unit earns per month while a lease is in force. */
    public static BigDecimal rentRoll(List<UnitRow> units) {
        return units.stream().filter(r -> "OCCUPIED".equals(r.occupancy()) && r.monthlyRent() != null).map(UnitRow::monthlyRent).reduce(ZERO, BigDecimal::add);
    }

    /** Is this owner an owner of the property now (in the latest split)? */
    public static boolean ownsNow(Snapshot s, Long ownerId, Long propertyId) {
        if (ownerId == null || propertyId == null) return false;
        PmOwnership.Split sp = PmOwnership.latest(s.splits.get(propertyId));
        if (sp != null) return sp.of(ownerId) != null;
        PmProperty p = s.properties.get(propertyId);
        return p != null && ownerId.equals(p.getOwnerId());
    }

    /** Has this owner ever been an owner of the property (in any split)? */
    public static boolean everOwned(Snapshot s, Long ownerId, Long propertyId) {
        List<PmOwnership.Split> list = s.splits.get(propertyId);
        if (list == null) return ownsNow(s, ownerId, propertyId);
        return list.stream().anyMatch(sp -> sp.of(ownerId) != null);
    }

    /** The management fee percentage that applies to a property: its own, else its lead owner's, else the module's. */
    public static BigDecimal effectiveFee(Snapshot s, PmProperty p) {
        if (p.getFeePercent() != null) return p.getFeePercent();
        PmOwner o = s.owners.get(p.getOwnerId());
        if (o != null && o.getFeePercent() != null) return o.getFeePercent();
        return s.moduleFeePercent;
    }
}
