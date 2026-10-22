package com.posapp.register;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.register.dto.PosSettingsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PosSettingsService {

    private final PosSettingsRepository repository;
    private final AuditLogService auditLogService;

    public PosSettingsService(PosSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Read-write on purpose: the first call on a new database creates the row. */
    @Transactional
    public PosSettings entity() {
        List<PosSettings> all = repository.findAll();
        return all.isEmpty() ? repository.save(new PosSettings()) : all.get(0);
    }

    @Transactional
    public PosSettingsDto get() {
        return PosSettingsDto.from(entity());
    }

    @Transactional
    public PosSettingsDto update(PosSettingsDto in) {
        PosSettings s = entity();
        if (in.varianceTolerance() != null) s.setVarianceTolerance(money("Allowed difference", in.varianceTolerance()));
        if (in.varianceNoteRequired() != null) s.setVarianceNoteRequired(in.varianceNoteRequired());
        if (in.denominationCountRequired() != null) s.setDenominationCountRequired(in.denominationCountRequired());
        if (in.paidOutApprovalOver() != null) s.setPaidOutApprovalOver(money("Paid-out limit", in.paidOutApprovalOver()));
        if (in.dropAlertAmount() != null) s.setDropAlertAmount(money("Safe drop reminder", in.dropAlertAmount()));
        if (in.voidRequiresApproval() != null) s.setVoidRequiresApproval(in.voidRequiresApproval());
        if (in.voidSameShiftOnly() != null) s.setVoidSameShiftOnly(in.voidSameShiftOnly());
        if (in.returnWindowDays() != null) {
            if (in.returnWindowDays() < 0 || in.returnWindowDays() > 3650) throw ApiException.badRequest("The return window must be between 0 and 3650 days");
            s.setReturnWindowDays(in.returnWindowDays());
        }
        if (in.returnApprovalOver() != null) s.setReturnApprovalOver(money("Return approval limit", in.returnApprovalOver()));
        if (in.returnReasonRequired() != null) s.setReturnReasonRequired(in.returnReasonRequired());
        if (in.returnReasons() != null) s.setReturnReasons(reasons(in.returnReasons()));
        if (in.cashierDiscountMax() != null) {
            if (in.cashierDiscountMax().signum() < 0 || in.cashierDiscountMax().compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("The cashier discount allowance must be between 0 and 100 percent");
            s.setCashierDiscountMax(in.cashierDiscountMax());
        }
        if (in.discountReasonRequired() != null) s.setDiscountReasonRequired(in.discountReasonRequired());
        if (in.discountReasons() != null) s.setDiscountReasons(reasons(in.discountReasons()));
        if (in.priceOverrideAllowed() != null) s.setPriceOverrideAllowed(in.priceOverrideAllowed());
        if (in.priceBelowCostBlocked() != null) s.setPriceBelowCostBlocked(in.priceBelowCostBlocked());
        if (in.receiptHeader() != null) s.setReceiptHeader(text("The receipt header", in.receiptHeader(), 300, true));
        if (in.receiptFooter() != null) s.setReceiptFooter(text("The receipt footer", in.receiptFooter(), 300, false));
        if (in.receiptPaper() != null) {
            if (!List.of("80", "58").contains(in.receiptPaper().trim())) throw ApiException.badRequest("Receipt paper must be 80 or 58 (mm)");
            s.setReceiptPaper(in.receiptPaper().trim());
        }
        if (in.receiptShowLogo() != null) s.setReceiptShowLogo(in.receiptShowLogo());
        if (in.receiptShowQr() != null) s.setReceiptShowQr(in.receiptShowQr());
        PosSettings saved = repository.save(s);
        auditLogService.record("POS_SETTINGS_UPDATED", "Updated the point-of-sale rules", "PosSettings", saved.getId());
        return PosSettingsDto.from(saved);
    }

    private static String text(String what, String raw, int max, boolean blankIsNull) {
        String t = raw.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " is too long (" + max + " characters at most)");
        return t.isEmpty() && blankIsNull ? null : t;
    }

    private static String reasons(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 40) throw ApiException.badRequest("A reason can be at most 40 characters");
            if (out.length() > 0) out.append(',');
            out.append(t);
        }
        if (out.length() > 400) throw ApiException.badRequest("Too many reasons");
        if (out.length() == 0) throw ApiException.badRequest("Give at least one reason");
        return out.toString();
    }

    private static BigDecimal money(String what, BigDecimal v) {
        if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(1_000_000_000)) > 0) throw ApiException.badRequest(what + " must be between 0 and 1,000,000,000");
        return v;
    }
}
