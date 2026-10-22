package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDocDtos.ValuationDto;
import com.posapp.realestate.dto.RealEstateDocRequests.ValuationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Valuations of properties: who valued it, when, for what, and what they said it is worth against what we ask. The latest valuation of a property is its current one. */
@Service
public class ReValuationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReValuationRepository repository;
    private final RePropertyRepository propertyRepository;
    private final ReDocumentService documentService;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;

    public ReValuationService(ReValuationRepository repository, RePropertyRepository propertyRepository, ReDocumentService documentService, RealEstateAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.documentService = documentService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private ReValuation find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Valuation not found: " + id));
    }

    private ReProperty property(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Property not found: " + id));
    }

    private ValuationDto dto(ReValuation v, ReProperty p, boolean current, LocalDate today) {
        BigDecimal asking = p == null ? null : p.getSalePrice();
        BigDecimal vs = asking == null || asking.signum() <= 0 ? null : v.getMarketValue().subtract(asking).multiply(HUNDRED).divide(asking, 1, RoundingMode.HALF_UP);
        return new ValuationDto(v.getId(), v.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), v.getValuationDate(), v.getValuerName(), v.getValuerFirm(),
                v.getPurpose(), v.getMethod(), v.getMarketValue(), v.getForcedSaleValue(), v.getMonthlyRent(), v.getValidUntil(), v.getValidUntil() != null && v.getValidUntil().isBefore(today),
                current, asking, vs, v.getNotes(), documentService.countFor(DocSubject.VALUATION, v.getId()), v.getCreatedByName(), v.getCreatedAt());
    }

    /** The newest valuation of each property (by valuation date, then by entry). */
    private static Map<Long, Long> currentIds(List<ReValuation> all) {
        Map<Long, ReValuation> best = new HashMap<>();
        for (ReValuation v : all) {
            ReValuation b = best.get(v.getPropertyId());
            if (b == null || v.getValuationDate().isAfter(b.getValuationDate()) || v.getValuationDate().equals(b.getValuationDate()) && v.getId() > b.getId()) best.put(v.getPropertyId(), v);
        }
        return best.values().stream().collect(Collectors.toMap(ReValuation::getPropertyId, ReValuation::getId));
    }

    @Transactional(readOnly = true)
    public List<ValuationDto> list(Long propertyId, Boolean currentOnly, Boolean expiredOnly) {
        access.on();
        LocalDate today = LocalDate.now();
        List<ReValuation> all = repository.findAll();
        Map<Long, Long> cur = currentIds(all);
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        List<ValuationDto> out = new ArrayList<>();
        for (ReValuation v : all) {
            if (propertyId != null && !propertyId.equals(v.getPropertyId())) continue;
            boolean isCurrent = v.getId().equals(cur.get(v.getPropertyId()));
            if (Boolean.TRUE.equals(currentOnly) && !isCurrent) continue;
            ValuationDto d = dto(v, props.get(v.getPropertyId()), isCurrent, today);
            if (Boolean.TRUE.equals(expiredOnly) && !d.expired()) continue;
            out.add(d);
        }
        out.sort(Comparator.comparing(ValuationDto::valuationDate, Comparator.reverseOrder()).thenComparing(ValuationDto::id, Comparator.reverseOrder()));
        return out;
    }

    @Transactional(readOnly = true)
    public ValuationDto get(Long id) {
        access.on();
        ReValuation v = find(id);
        List<ReValuation> mine = repository.findByPropertyIdOrderByValuationDateDescIdDesc(v.getPropertyId());
        return dto(v, propertyRepository.findById(v.getPropertyId()).orElse(null), id.equals(currentIds(mine).get(v.getPropertyId())), LocalDate.now());
    }

    /** How many properties' current valuation has run out. */
    @Transactional(readOnly = true)
    public long countExpiredCurrent() {
        LocalDate today = LocalDate.now();
        List<ReValuation> all = repository.findAll();
        Map<Long, Long> cur = currentIds(all);
        return all.stream().filter(v -> v.getId().equals(cur.get(v.getPropertyId())) && v.getValidUntil() != null && v.getValidUntil().isBefore(today)).count();
    }

    private void apply(ReValuation v, ValuationRequest r, boolean isNew) {
        if (r.valuationDate() != null) {
            if (r.valuationDate().isAfter(LocalDate.now())) throw ApiException.badRequest("A valuation cannot be dated in the future.");
            v.setValuationDate(r.valuationDate());
        }
        if (r.valuerName() != null) {
            String n = clean(r.valuerName(), 200, "The valuer's name");
            if (n == null) throw ApiException.badRequest("Give the valuer's name.");
            v.setValuerName(n);
        }
        if (r.valuerFirm() != null) v.setValuerFirm(clean(r.valuerFirm(), 200, "The firm"));
        if (r.purpose() != null) v.setPurpose(r.purpose());
        if (r.method() != null) v.setMethod(r.method());
        if (r.marketValue() != null) {
            if (r.marketValue().signum() <= 0) throw ApiException.badRequest("Enter the market value.");
            v.setMarketValue(money(r.marketValue()));
        }
        if (r.forcedSaleValue() != null) {
            if (r.forcedSaleValue().signum() < 0) throw ApiException.badRequest("The forced-sale value cannot be negative.");
            v.setForcedSaleValue(r.forcedSaleValue().signum() == 0 ? null : money(r.forcedSaleValue()));   // 0 clears it
        }
        if (r.monthlyRent() != null) {
            if (r.monthlyRent().signum() < 0) throw ApiException.badRequest("The rent cannot be negative.");
            v.setMonthlyRent(r.monthlyRent().signum() == 0 ? null : money(r.monthlyRent()));
        }
        if (Boolean.TRUE.equals(r.clearValidUntil())) v.setValidUntil(null);
        else if (r.validUntil() != null) v.setValidUntil(r.validUntil());
        if (v.getForcedSaleValue() != null && v.getMarketValue() != null && v.getForcedSaleValue().compareTo(v.getMarketValue()) > 0) {
            throw ApiException.badRequest("The forced-sale value cannot be more than the market value.");
        }
        if (v.getValidUntil() != null && v.getValuationDate() != null && v.getValidUntil().isBefore(v.getValuationDate())) {
            throw ApiException.badRequest("The valuation cannot end before its date.");
        }
        if (r.notes() != null) v.setNotes(clean(r.notes(), 2000, "The notes"));
    }

    @Transactional
    public ValuationDto create(Actor a, ValuationRequest r) {
        access.on();
        if (r.propertyId() == null) throw ApiException.badRequest("Choose the property.");
        ReProperty p = property(r.propertyId());
        if (r.valuationDate() == null) throw ApiException.badRequest("Enter the date of the valuation.");
        if (r.valuerName() == null || r.valuerName().isBlank()) throw ApiException.badRequest("Give the valuer's name.");
        if (r.marketValue() == null) throw ApiException.badRequest("Enter the market value.");
        ReValuation v = new ReValuation();
        v.setPropertyId(p.getId());
        apply(v, r, true);
        v.setCreatedByName(RealEstateAccess.name(a.user()));
        repository.save(v);
        auditLogService.record("RE_VALUATION_ADDED", "Valuation of " + p.getCode() + " at " + v.getMarketValue() + " by " + v.getValuerName(), "ReValuation", v.getId());
        return get(v.getId());
    }

    @Transactional
    public ValuationDto update(Long id, ValuationRequest r) {
        access.on();
        ReValuation v = find(id);
        apply(v, r, false);
        repository.save(v);
        auditLogService.record("RE_VALUATION_UPDATED", "Updated a valuation of property " + v.getPropertyId(), "ReValuation", id);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReValuation v = find(id);
        documentService.deleteFor(DocSubject.VALUATION, id);
        repository.delete(v);
        auditLogService.record("RE_VALUATION_DELETED", "Deleted a valuation of property " + v.getPropertyId());
    }

    /** Removes every valuation of a property that is being deleted. */
    @Transactional
    public void deleteFor(Long propertyId) {
        for (ReValuation v : repository.findByPropertyIdOrderByValuationDateDescIdDesc(propertyId)) {
            documentService.deleteFor(DocSubject.VALUATION, v.getId());
            repository.delete(v);
        }
    }
}
