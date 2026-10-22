package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.PropertyDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.PropertyRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.BulkUnitsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.PropertyRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.UnitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Managed properties (buildings, compounds, houses) and their rentable units. */
@Service
public class PmPropertyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PmPropertyRepository repository;
    private final PmUnitRepository unitRepository;
    private final PmOwnerRepository ownerRepository;
    private final PmLeaseRepository leaseRepository;
    private final PmMaintenanceRepository maintenanceRepository;
    private final PmSettingsService settings;
    private final PmViews views;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    private final PmDocumentService documentService;
    private final PmOwnershipService ownershipService;

    public PmPropertyService(PmPropertyRepository repository, PmUnitRepository unitRepository, PmOwnerRepository ownerRepository, PmLeaseRepository leaseRepository, PmMaintenanceRepository maintenanceRepository,
                             PmSettingsService settings, PmViews views, PropertyManagementAccess access, AuditLogService auditLogService, PmDocumentService documentService, PmOwnershipService ownershipService) {
        this.documentService = documentService;
        this.ownershipService = ownershipService;
        this.repository = repository;
        this.unitRepository = unitRepository;
        this.ownerRepository = ownerRepository;
        this.leaseRepository = leaseRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.settings = settings;
        this.views = views;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmProperty find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Property not found: " + id));
    }

    private PmUnit findUnit(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit not found: " + id));
    }

    // ------------------------------------------------------------------ rows

    private List<UnitRow> unitRowsOf(Snapshot s, Long propertyId) {
        return s.units.values().stream().filter(u -> u.getPropertyId().equals(propertyId)).sorted(Comparator.comparing(PmUnit::getUnitNo, NATURAL)).map(u -> views.unitRow(s, u)).toList();
    }

    /** Unit numbers in a natural order: A-2 before A-10. */
    private static final Comparator<String> NATURAL = (a, b) -> {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                java.math.BigInteger na = new java.math.BigInteger(a.substring(si, i)), nb = new java.math.BigInteger(b.substring(sj, j));
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                int c = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    };

    private PropertyRow row(Snapshot s, PmProperty p, List<UnitRow> units, Long forOwner) {
        PmOwner o = s.owners.get(p.getOwnerId());
        long occupied = units.stream().filter(u -> "OCCUPIED".equals(u.occupancy())).count();
        long vacant = units.stream().filter(u -> "VACANT".equals(u.occupancy())).count();
        PmOwnership.Split sp = PmOwnership.latest(s.splits.get(p.getId()));
        PmOwnership.Share mine = sp == null || forOwner == null ? null : sp.of(forOwner);
        return new PropertyRow(p.getId(), p.getCode(), p.getName(), p.getKind(), p.getOwnerId(), o == null ? null : o.getName(), p.getArea(), p.isActive(), units.size(), occupied, vacant,
                PmViews.rentRoll(units), PmViews.effectiveFee(s, p), sp == null ? 1 : sp.shares().size(), PmOwnershipService.label(s, p.getId()), mine == null ? null : mine.percent());
    }

    /** The properties (of one owner, or all) as list rows. */
    List<PropertyRow> rows(Snapshot s, Long ownerId) {
        return s.properties.values().stream().filter(p -> ownerId == null || PmViews.ownsNow(s, ownerId, p.getId()))
                .sorted(Comparator.comparing((PmProperty p) -> p.getName().toLowerCase(Locale.ROOT)))
                .map(p -> row(s, p, unitRowsOf(s, p.getId()), ownerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<PropertyRow> list(String q, Long ownerId, Boolean activeOnly) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return rows(s, ownerId).stream().filter(r -> !Boolean.TRUE.equals(activeOnly) || r.active())
                .filter(r -> needle.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(needle) || r.code().toLowerCase(Locale.ROOT).contains(needle)
                        || r.area() != null && r.area().toLowerCase(Locale.ROOT).contains(needle) || r.ownersLabel() != null && r.ownersLabel().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    private PropertyDto dto(Actor a, Snapshot s, PmProperty p) {
        List<UnitRow> units = unitRowsOf(s, p.getId());
        PmOwner o = s.owners.get(p.getOwnerId());
        long occ = units.stream().filter(u -> "OCCUPIED".equals(u.occupancy())).count();
        long up = units.stream().filter(u -> "UPCOMING".equals(u.occupancy())).count();
        long vac = units.stream().filter(u -> "VACANT".equals(u.occupancy())).count();
        long off = units.stream().filter(u -> "OFF_MARKET".equals(u.occupancy())).count();
        BigDecimal vacantRent = units.stream().filter(u -> "VACANT".equals(u.occupancy()) && u.marketRent() != null).map(UnitRow::marketRent).reduce(ZERO, BigDecimal::add);
        return new PropertyDto(p.getId(), p.getCode(), p.getName(), p.getKind(), p.getOwnerId(), o == null ? null : o.getName(), p.getArea(), p.getAddress(), p.getTitleReference(),
                p.getFeePercent(), PmViews.effectiveFee(s, p), p.getManagedFrom(), p.isActive(), p.getNotes(), units.size(), occ, up, vac, off, PmViews.rentRoll(units), vacantRent, units,
                a.manager(), p.getCreatedAt(), PmOwnership.latest(s.splits.get(p.getId())) == null ? 1 : PmOwnership.latest(s.splits.get(p.getId())).shares().size(), PmOwnershipService.label(s, p.getId()));
    }

    // ------------------------------------------------------------------ property edits

    private void apply(PmProperty p, PropertyRequest r, boolean isNew) {
        if (isNew || r.name() != null) p.setName(PmUtil.required(r.name(), 200, "The name"));
        if (r.kind() != null) p.setKind(r.kind());
        if (isNew || r.ownerId() != null) {
            if (r.ownerId() == null) throw ApiException.badRequest("Choose the owner.");
            PmOwner o = ownerRepository.findById(r.ownerId()).orElseThrow(() -> ApiException.badRequest("That owner does not exist."));
            if (!isNew && !r.ownerId().equals(p.getOwnerId())) throw ApiException.badRequest("Change the owners of a property on its Owners panel: it keeps track of the shares and the date of the change.");
            if (!o.isActive() && isNew) throw ApiException.badRequest("That owner is inactive.");
            p.setOwnerId(o.getId());
        }
        if (r.area() != null) p.setArea(PmUtil.clean(r.area(), 120, "The area"));
        if (r.address() != null) p.setAddress(PmUtil.clean(r.address(), 500, "The address"));
        if (r.titleReference() != null) p.setTitleReference(PmUtil.clean(r.titleReference(), 120, "The title reference"));
        if (r.notes() != null) p.setNotes(PmUtil.clean(r.notes(), 2000, "The notes"));
        if (Boolean.TRUE.equals(r.clearFee())) {
            p.setFeePercent(null);
        } else if (r.feePercent() != null) {
            PmUtil.percent(r.feePercent(), "A management fee");
            p.setFeePercent(r.feePercent());
        }
        if (Boolean.TRUE.equals(r.clearManagedFrom())) p.setManagedFrom(null);
        else if (r.managedFrom() != null) p.setManagedFrom(r.managedFrom());
    }

    @Transactional
    public PropertyDto create(Actor a, PropertyRequest r) {
        access.on();
        PmProperty p = new PmProperty();
        apply(p, r, true);
        p.setCode(settings.nextCode("MP"));
        repository.save(p);
        ownershipService.start(p);
        auditLogService.record("PM_PROPERTY_CREATED", "Added managed property " + p.getName() + " (" + p.getCode() + ")", "PmProperty", p.getId());
        return dto(a, views.snapshot(), p);
    }

    @Transactional
    public PropertyDto update(Actor a, Long id, PropertyRequest r) {
        access.on();
        PmProperty p = find(id);
        apply(p, r, false);
        if (r.active() != null && r.active() != p.isActive()) {
            if (!r.active()) {
                Snapshot s = views.snapshot();
                boolean inUse = s.units.values().stream().filter(u -> u.getPropertyId().equals(id)).anyMatch(u -> s.leasesByUnit.getOrDefault(u.getId(), List.of()).stream().anyMatch(l -> l.getStatus() == LeaseStatus.ACTIVE));
                if (inUse) throw ApiException.badRequest("A lease is still in force on this property. End or terminate it first.");
            }
            p.setActive(r.active());
        }
        repository.save(p);
        auditLogService.record("PM_PROPERTY_UPDATED", "Changed managed property " + p.getName() + " (" + p.getCode() + ")", "PmProperty", id);
        return dto(a, views.snapshot(), p);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmProperty p = find(id);
        List<PmUnit> units = unitRepository.findByPropertyIdOrderByUnitNoAsc(id);
        if (!units.isEmpty() && leaseRepository.countByUnitIdIn(units.stream().map(PmUnit::getId).toList()) > 0) {
            throw ApiException.badRequest("This property has leases. Leases are kept as a record: mark the property inactive instead.");
        }
        for (PmUnit du : units) documentService.deleteFor(PmDocSubject.UNIT, du.getId());
        documentService.deleteFor(PmDocSubject.PROPERTY, id);
        unitRepository.deleteAll(units);
        ownershipService.deleteFor(id);
        repository.delete(p);
        auditLogService.record("PM_PROPERTY_DELETED", "Deleted managed property " + p.getName() + " (" + p.getCode() + ")");
    }

    // ------------------------------------------------------------------ units

    private static BigDecimal amount(BigDecimal v, String what) {
        if (v.signum() < 0) throw ApiException.badRequest(what + " cannot be negative.");
        return v.signum() == 0 ? null : v;
    }

    private static Integer count(Integer v, String what) {
        if (v != null && (v < 0 || v > 50)) throw ApiException.badRequest(what + " is between 0 and 50.");
        return v;
    }

    private void applyUnit(PmUnit u, UnitRequest r, boolean isNew, Snapshot s) {
        if (isNew || r.unitNo() != null) {
            String no = PmUtil.required(r.unitNo(), 40, "The unit number");
            boolean changed = isNew || !no.equalsIgnoreCase(u.getUnitNo());
            if (changed && unitRepository.existsByPropertyIdAndUnitNoIgnoreCase(u.getPropertyId(), no)) throw ApiException.badRequest("This property already has a unit " + no + ".");
            u.setUnitNo(no);
        }
        if (r.kind() != null) u.setKind(r.kind());
        if (r.floor() != null) u.setFloor(PmUtil.clean(r.floor(), 40, "The floor"));
        if (r.bedrooms() != null) u.setBedrooms(count(r.bedrooms(), "Bedrooms"));
        if (r.bathrooms() != null) u.setBathrooms(count(r.bathrooms(), "Bathrooms"));
        if (r.sizeSqm() != null) u.setSizeSqm(amount(r.sizeSqm(), "The size"));
        if (r.marketRent() != null) u.setMarketRent(amount(r.marketRent(), "The rent"));
        if (r.defaultDeposit() != null) u.setDefaultDeposit(amount(r.defaultDeposit(), "The deposit"));
        if (r.notes() != null) u.setNotes(PmUtil.clean(r.notes(), 1000, "The notes"));
        if (r.offMarketNote() != null) u.setOffMarketNote(PmUtil.clean(r.offMarketNote(), 500, "The note"));
        if (r.offMarket() != null) {
            if (r.offMarket() && !u.isOffMarket() && u.getId() != null) {
                boolean signed = s.leasesByUnit.getOrDefault(u.getId(), List.of()).stream().anyMatch(l -> l.getStatus() == LeaseStatus.ACTIVE);
                if (signed) throw ApiException.badRequest("A lease is in force on this unit: end it before taking the unit off the market.");
            }
            u.setOffMarket(r.offMarket());
            if (!r.offMarket()) u.setOffMarketNote(null);
        }
    }

    private PmProperty activeProperty(Long propertyId) {
        PmProperty p = find(propertyId);
        if (!p.isActive()) throw ApiException.badRequest("This property is inactive: activate it before adding units.");
        return p;
    }

    @Transactional
    public UnitRow addUnit(Long propertyId, UnitRequest r) {
        access.on();
        activeProperty(propertyId);
        PmUnit u = new PmUnit();
        u.setPropertyId(propertyId);
        Snapshot s = views.snapshot();
        applyUnit(u, r, true, s);
        unitRepository.save(u);
        auditLogService.record("PM_UNIT_CREATED", "Added unit " + u.getUnitNo() + " to " + find(propertyId).getName(), "PmUnit", u.getId());
        return views.unitRow(views.snapshot(), u);
    }

    @Transactional
    public List<UnitRow> addUnitsBulk(Long propertyId, BulkUnitsRequest r) {
        access.on();
        PmProperty p = activeProperty(propertyId);
        if (r.from() == null || r.to() == null) throw ApiException.badRequest("Give the first and last number.");
        if (r.from() < 0 || r.to() < r.from()) throw ApiException.badRequest("The last number must not be below the first.");
        if (r.to() - r.from() + 1 > 200) throw ApiException.badRequest("Make at most 200 units at a time.");
        int pad = r.pad() == null ? 0 : r.pad();
        if (pad < 0 || pad > 6) throw ApiException.badRequest("Padding is 0 to 6 digits.");
        String prefix = r.prefix() == null ? "" : r.prefix().trim();
        if (prefix.length() > 30) throw ApiException.badRequest("The prefix can be at most 30 characters.");
        Snapshot s = views.snapshot();
        List<PmUnit> made = new ArrayList<>();
        for (int n = r.from(); n <= r.to(); n++) {
            String no = prefix + (pad > 0 ? String.format("%0" + pad + "d", n) : String.valueOf(n));
            if (no.length() > 40) throw ApiException.badRequest("A unit number can be at most 40 characters.");
            if (unitRepository.existsByPropertyIdAndUnitNoIgnoreCase(propertyId, no)) throw ApiException.badRequest("This property already has a unit " + no + ". Nothing was added.");
            PmUnit u = new PmUnit();
            u.setPropertyId(propertyId);
            applyUnit(u, new UnitRequest(no, r.kind(), r.floor(), r.bedrooms(), r.bathrooms(), r.sizeSqm(), r.marketRent(), r.defaultDeposit(), null, null, null), true, s);
            made.add(u);
        }
        unitRepository.saveAll(made);
        auditLogService.record("PM_UNIT_BULK", "Added " + made.size() + " units to " + p.getName(), "PmProperty", propertyId);
        Snapshot fresh = views.snapshot();
        return made.stream().map(u -> views.unitRow(fresh, u)).toList();
    }

    @Transactional(readOnly = true)
    public List<UnitRow> units(String q, Long propertyId, String occupancy, Long ownerId) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return s.units.values().stream().filter(u -> propertyId == null || propertyId.equals(u.getPropertyId()))
                .filter(u -> {
                    if (ownerId == null) return true;
                    PmProperty p = s.properties.get(u.getPropertyId());
                    return p != null && PmViews.ownsNow(s, ownerId, p.getId());
                })
                .map(u -> views.unitRow(s, u))
                .filter(r -> occupancy == null || occupancy.isBlank() || occupancy.equalsIgnoreCase(r.occupancy()))
                .filter(r -> needle.isEmpty() || r.unitNo().toLowerCase(Locale.ROOT).contains(needle) || r.propertyName() != null && r.propertyName().toLowerCase(Locale.ROOT).contains(needle)
                        || r.tenantName() != null && r.tenantName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((UnitRow r) -> r.propertyName() == null ? "" : r.propertyName().toLowerCase(Locale.ROOT)).thenComparing(UnitRow::unitNo, NATURAL))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitDto unit(Actor a, Long id) {
        access.on();
        Snapshot s = views.snapshot();
        PmUnit u = findUnit(id);
        PmProperty p = s.properties.get(u.getPropertyId());
        PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
        List<LeaseRow> leases = s.leasesByUnit.getOrDefault(id, List.of()).stream().sorted(Comparator.comparing(PmLease::getStartDate).thenComparing(PmLease::getId).reversed())
                .map(l -> views.leaseRow(s, l)).toList();
        return new UnitDto(views.unitRow(s, u), o == null ? null : o.getName(), o == null ? null : o.getId(), leases, a.manager());
    }

    @Transactional
    public UnitRow updateUnit(Long id, UnitRequest r) {
        access.on();
        PmUnit u = findUnit(id);
        applyUnit(u, r, false, views.snapshot());
        unitRepository.save(u);
        auditLogService.record("PM_UNIT_UPDATED", "Changed unit " + u.getUnitNo(), "PmUnit", id);
        return views.unitRow(views.snapshot(), u);
    }

    @Transactional
    public void deleteUnit(Long id) {
        access.on();
        PmUnit u = findUnit(id);
        if (leaseRepository.existsByUnitId(id)) throw ApiException.badRequest("This unit has leases. Leases are kept as a record: take the unit off the market instead.");
        if (maintenanceRepository.existsByUnitId(id)) throw ApiException.badRequest("This unit has maintenance requests. They are kept as a record: take the unit off the market instead.");
        documentService.deleteFor(PmDocSubject.UNIT, id);
        unitRepository.delete(u);
        auditLogService.record("PM_UNIT_DELETED", "Deleted unit " + u.getUnitNo());
    }

    /** Units with nobody in them, longest vacant first. */
    List<UnitRow> vacantUnits(Snapshot s) {
        LocalDate today = s.today;
        return s.units.values().stream().map(u -> views.unitRow(s, u)).filter(r -> "VACANT".equals(r.occupancy()))
                .sorted(Comparator.comparing((UnitRow r) -> r.vacantSince() == null ? today : r.vacantSince())).toList();
    }
}
