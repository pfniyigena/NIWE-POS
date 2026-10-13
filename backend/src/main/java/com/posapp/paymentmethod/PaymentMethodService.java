package com.posapp.paymentmethod;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.paymentmethod.dto.PaymentMethodDto;
import com.posapp.paymentmethod.dto.PaymentMethodRequest;
import com.posapp.paymentmethod.dto.UpdatePaymentMethodRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final AuditLogService auditLogService;

    public PaymentMethodService(PaymentMethodRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Active methods only — what the checkout/refund dropdowns show. Open to any logged-in user. */
    @Transactional(readOnly = true)
    public List<PaymentMethodDto> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(PaymentMethodDto::from).toList();
    }

    /** Admin-only: includes deactivated methods so they can be reactivated. */
    @Transactional(readOnly = true)
    public List<PaymentMethodDto> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(PaymentMethodDto::from).toList();
    }

    /** Task: "Implement an audit log of who did what and when" — payment methods are a curated action, see AuditLogService's javadoc. */
    @Transactional
    public PaymentMethodDto create(PaymentMethodRequest request) {
        if (repository.existsByCodeIgnoreCase(request.getCode())) {
            throw ApiException.conflict("A payment method with code '" + request.getCode() + "' already exists");
        }
        PaymentMethodEntity pm = new PaymentMethodEntity();
        pm.setCode(request.getCode().trim());
        pm.setName(request.getName());
        pm.setCash(request.isCash());
        pm.setCredit(request.isCredit());
        pm.setRoomCharge(request.isRoomCharge());
        pm.setSortOrder(request.getSortOrder());
        pm.setActive(true);
        PaymentMethodEntity saved = repository.save(pm);
        auditLogService.record("PAYMENT_METHOD_CREATED", "Created payment method " + saved.getCode() + " (" + saved.getName() + ")",
                "PaymentMethod", saved.getId());
        return PaymentMethodDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — see create's identical treatment just above. */
    @Transactional
    public PaymentMethodDto update(Long id, UpdatePaymentMethodRequest request) {
        PaymentMethodEntity pm = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Payment method not found: " + id));
        boolean wasActive = pm.isActive();
        pm.setName(request.getName());
        pm.setCash(request.isCash());
        pm.setCredit(request.isCredit());
        pm.setRoomCharge(request.isRoomCharge());
        pm.setActive(request.isActive());
        pm.setSortOrder(request.getSortOrder());
        PaymentMethodEntity saved = repository.save(pm);
        String description = "Updated payment method " + saved.getCode() + " (" + saved.getName() + ")"
                + (wasActive != saved.isActive() ? (saved.isActive() ? " — reactivated" : " — deactivated") : "");
        auditLogService.record("PAYMENT_METHOD_UPDATED", description, "PaymentMethod", saved.getId());
        return PaymentMethodDto.from(saved);
    }

    /**
     * Validates that `code` refers to an active payment method — used by OrderService/ReturnsService
     * when accepting a payment/refund at checkout/return time. Returns the stored code unchanged.
     */
    @Transactional(readOnly = true)
    public String validateActiveCodeOrThrow(String code) {
        PaymentMethodEntity pm = repository.findByCodeIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> ApiException.badRequest("Unknown or inactive payment method: " + code));
        return pm.getCode();
    }

    /** All codes flagged cash-equivalent, for RegisterService's till reconciliation and OrderService's cash/non-cash split. */
    @Transactional(readOnly = true)
    public Set<String> listCashCodes() {
        return repository.findByCashTrue().stream().map(PaymentMethodEntity::getCode).collect(Collectors.toSet());
    }

    /** Hotel module: codes whose amount goes on an in-house guest's room bill instead of being collected now. */
    @Transactional(readOnly = true)
    public Set<String> listRoomChargeCodes() {
        return repository.findByRoomChargeTrue().stream().map(PaymentMethodEntity::getCode).collect(Collectors.toSet());
    }

    /**
     * Hotel module: makes sure the "Charge to room" tender exists and switches it on or off together with the hotel
     * module (Hotel > Settings). Created the first time it is needed; an admin can still rename or reorder it.
     */
    @Transactional
    public void setRoomChargeEnabled(boolean enabled) {
        PaymentMethodEntity pm = repository.findByRoomChargeTrue().stream().findFirst().orElse(null);
        if (pm == null) {
            if (!enabled) return;
            pm = repository.findByCodeIgnoreCase("ROOM").orElse(null);
            if (pm == null) {
                pm = new PaymentMethodEntity();
                pm.setCode("ROOM");
                pm.setName("Charge to room");
                pm.setSortOrder(50);
            }
            pm.setRoomCharge(true);
        }
        if (pm.isActive() != enabled) {
            pm.setActive(enabled);
        }
        repository.save(pm);
    }

    /** All codes flagged as store-credit tender, for OrderService's credit-portion computation at checkout. */
    @Transactional(readOnly = true)
    public Set<String> listCreditCodes() {
        return repository.findByCreditTrue().stream().map(PaymentMethodEntity::getCode).collect(Collectors.toSet());
    }
}
