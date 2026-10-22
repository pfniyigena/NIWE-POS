package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.OwnerDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.OwnerRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.PropertyRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.OwnerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Landlords: the people and companies whose properties we manage. */
@Service
public class PmOwnerService {

    private final PmOwnerStatementRepository statementRepository;
    private final PmOwnerRepository repository;
    private final PmPropertyRepository propertyRepository;
    private final PmPropertyOwnerRepository propertyOwnerRepository;
    private final PmPropertyService propertyService;
    private final PmSettingsService settings;
    private final PmViews views;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    private final PmDocumentService documentService;

    public PmOwnerService(PmOwnerRepository repository, PmPropertyRepository propertyRepository, PmPropertyService propertyService, PmSettingsService settings, PmViews views,
                          PropertyManagementAccess access, AuditLogService auditLogService, PmOwnerStatementRepository statementRepository, PmDocumentService documentService, PmPropertyOwnerRepository propertyOwnerRepository) {
        this.propertyOwnerRepository = propertyOwnerRepository;
        this.documentService = documentService;
        this.statementRepository = statementRepository;
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.propertyService = propertyService;
        this.settings = settings;
        this.views = views;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmOwner find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Owner not found: " + id));
    }

    private OwnerRow row(Snapshot s, PmOwner o) {
        List<PropertyRow> props = propertyService.rows(s, o.getId());
        long units = props.stream().mapToLong(PropertyRow::units).sum();
        long occupied = props.stream().mapToLong(PropertyRow::occupied).sum();
        BigDecimal rent = props.stream().map(x -> x.sharePercent() == null || x.sharePercent().compareTo(new BigDecimal("100")) == 0 ? x.monthlyRent() : x.monthlyRent().multiply(x.sharePercent()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OwnerRow(o.getId(), o.getCode(), o.getName(), o.getPhone(), o.getEmail(), o.isActive(), props.size(), units, occupied, rent);
    }

    @Transactional(readOnly = true)
    public List<OwnerRow> list(String q, Boolean activeOnly) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String digits = PmUtil.digits(q);
        return s.owners.values().stream()
                .filter(o -> !Boolean.TRUE.equals(activeOnly) || o.isActive())
                .filter(o -> needle.isEmpty() || o.getName().toLowerCase(Locale.ROOT).contains(needle) || o.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || o.getEmail() != null && o.getEmail().toLowerCase(Locale.ROOT).contains(needle) || digits != null && digits.equals(o.getPhoneKey()))
                .sorted(Comparator.comparing((PmOwner o) -> o.getName().toLowerCase(Locale.ROOT)))
                .map(o -> row(s, o)).toList();
    }

    @Transactional(readOnly = true)
    public OwnerDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    private OwnerDto dto(Actor a, Snapshot s, PmOwner o) {
        OwnerRow r = row(s, o);
        return new OwnerDto(o.getId(), o.getCode(), o.getName(), o.getPhone(), o.getEmail(), o.getIdNumber(), o.getTaxNumber(), o.getAddress(), o.getBankName(), o.getBankAccount(),
                o.getFeePercent(), o.isActive(), o.getNotes(), propertyService.rows(s, o.getId()), r.units(), r.occupied(), r.monthlyRent(), a.manager(), o.getCreatedAt());
    }

    private void apply(PmOwner o, OwnerRequest r, boolean isNew) {
        if (isNew || r.name() != null) o.setName(PmUtil.required(r.name(), 200, "The name"));
        if (r.phone() != null) {
            o.setPhone(PmUtil.clean(r.phone(), 40, "The phone number"));
            o.setPhoneKey(PmUtil.digits(o.getPhone()));
        }
        if (r.email() != null) o.setEmail(PmUtil.clean(r.email(), 255, "The e-mail"));
        if (r.idNumber() != null) o.setIdNumber(PmUtil.clean(r.idNumber(), 60, "The ID number"));
        if (r.taxNumber() != null) o.setTaxNumber(PmUtil.clean(r.taxNumber(), 60, "The tax number"));
        if (r.address() != null) o.setAddress(PmUtil.clean(r.address(), 500, "The address"));
        if (r.bankName() != null) o.setBankName(PmUtil.clean(r.bankName(), 120, "The bank name"));
        if (r.bankAccount() != null) o.setBankAccount(PmUtil.clean(r.bankAccount(), 60, "The bank account"));
        if (r.notes() != null) o.setNotes(PmUtil.clean(r.notes(), 2000, "The notes"));
        if (Boolean.TRUE.equals(r.clearFee())) {
            o.setFeePercent(null);
        } else if (r.feePercent() != null) {
            PmUtil.percent(r.feePercent(), "A management fee");
            o.setFeePercent(r.feePercent());
        }
        if (r.active() != null) o.setActive(r.active());
    }

    @Transactional
    public OwnerDto create(Actor a, OwnerRequest r) {
        access.on();
        PmOwner o = new PmOwner();
        apply(o, r, true);
        o.setCode(settings.nextCode("OW"));
        repository.save(o);
        auditLogService.record("PM_OWNER_CREATED", "Added owner " + o.getName() + " (" + o.getCode() + ")", "PmOwner", o.getId());
        return dto(a, views.snapshot(), o);
    }

    @Transactional
    public OwnerDto update(Actor a, Long id, OwnerRequest r) {
        access.on();
        PmOwner o = find(id);
        apply(o, r, false);
        repository.save(o);
        auditLogService.record("PM_OWNER_UPDATED", "Changed owner " + o.getName() + " (" + o.getCode() + ")", "PmOwner", id);
        return dto(a, views.snapshot(), o);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmOwner o = find(id);
        Snapshot s = views.snapshot();
        if (s.properties.keySet().stream().anyMatch(pid -> PmViews.ownsNow(s, id, pid))) throw ApiException.badRequest("This owner still has properties. Move or delete them first, or mark the owner inactive.");
        if (propertyOwnerRepository.existsByOwnerId(id)) throw ApiException.badRequest("This owner is on the ownership history of a property, so the record is kept. Mark the owner inactive instead.");
        if (statementRepository.existsByOwnerId(id)) throw ApiException.badRequest("This owner has statements, so the record is kept. Mark the owner inactive instead.");
        documentService.deleteFor(PmDocSubject.OWNER, id);
        repository.delete(o);
        auditLogService.record("PM_OWNER_DELETED", "Deleted owner " + o.getName() + " (" + o.getCode() + ")");
    }

    /** Units of every property of this owner. */
    @Transactional(readOnly = true)
    public List<UnitRow> units(Long id) {
        access.on();
        find(id);
        Snapshot s = views.snapshot();
        return s.units.values().stream().filter(u -> {
            PmProperty p = s.properties.get(u.getPropertyId());
            return p != null && PmViews.ownsNow(s, id, p.getId());
        }).map(u -> views.unitRow(s, u)).toList();
    }
}
