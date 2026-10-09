package com.posapp.tax;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.tax.dto.TaxRateDto;
import com.posapp.tax.dto.TaxRateRequest;
import com.posapp.tax.dto.UpdateTaxRateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin-manageable tax rates. As of this pass a product picks one of these (see
 * {@code CatalogService}/{@code Product.taxRate}) rather than the whole store sharing a single
 * active rate, so — unlike the eleventh-pass version of this service — there is no "deactivate
 * every other row" exclusivity to enforce; this now mirrors {@code PaymentMethodService} almost
 * exactly, code included.
 */
@Service
public class TaxRateService {

    private final TaxRateRepository repository;
    private final AuditLogService auditLogService;

    public TaxRateService(TaxRateRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Admin-only: includes deactivated rates so a product that was assigned one keeps resolving, and so it can be reactivated. */
    @Transactional(readOnly = true)
    public List<TaxRateDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(TaxRateDto::from).toList();
    }

    /** Task: "Implement an audit log of who did what and when" — tax rates are a curated action, see AuditLogService's javadoc. */
    @Transactional
    public TaxRateDto create(TaxRateRequest request) {
        if (repository.existsByCodeIgnoreCase(request.getCode())) {
            throw ApiException.conflict("A tax rate with code '" + request.getCode() + "' already exists");
        }
        TaxRate taxRate = new TaxRate();
        taxRate.setCode(request.getCode().trim());
        taxRate.setName(request.getName().trim());
        taxRate.setRatePercent(request.getRatePercent());
        taxRate.setActive(true);
        taxRate.setInclusive(request.isInclusive());
        TaxRate saved = repository.save(taxRate);
        auditLogService.record("TAX_RATE_CREATED",
                "Created tax rate " + saved.getCode() + " (" + saved.getName() + ", " + saved.getRatePercent() + "%)",
                "TaxRate", saved.getId());
        return TaxRateDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — logs the rate/active before-and-after so a percent change is visible without cross-referencing anything else. */
    @Transactional
    public TaxRateDto update(Long id, UpdateTaxRateRequest request) {
        TaxRate taxRate = getEntityOrThrow(id);
        BigDecimal oldRate = taxRate.getRatePercent();
        boolean wasActive = taxRate.isActive();
        taxRate.setName(request.getName().trim());
        taxRate.setRatePercent(request.getRatePercent());
        taxRate.setActive(request.isActive());
        taxRate.setInclusive(request.isInclusive());
        TaxRate saved = repository.save(taxRate);
        StringBuilder description = new StringBuilder("Updated tax rate ").append(saved.getCode()).append(" (").append(saved.getName()).append(")");
        if (oldRate == null || oldRate.compareTo(saved.getRatePercent()) != 0) {
            description.append(": ").append(oldRate).append("% -> ").append(saved.getRatePercent()).append("%");
        }
        if (wasActive != saved.isActive()) {
            description.append(saved.isActive() ? " — reactivated" : " — deactivated");
        }
        auditLogService.record("TAX_RATE_UPDATED", description.toString(), "TaxRate", saved.getId());
        return TaxRateDto.from(saved);
    }

    /**
     * Used by CatalogService to resolve a product's assigned tax rate id into the entity. Any
     * rate — active or not — may still be referenced by a product: a product keeps its assignment
     * even after an admin deactivates that rate (mirrors PaymentMethodEntity's history-preserving
     * `code`); only the product form's picker itself is limited to active rates.
     */
    @Transactional(readOnly = true)
    public TaxRate getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Tax rate not found: " + id));
    }
}
