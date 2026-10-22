package com.posapp.register.dto;

import com.posapp.register.PosSettings;

import java.math.BigDecimal;

public record PosSettingsDto(BigDecimal varianceTolerance, Boolean varianceNoteRequired, Boolean denominationCountRequired, BigDecimal paidOutApprovalOver,
                             BigDecimal dropAlertAmount, Boolean voidRequiresApproval, Boolean voidSameShiftOnly, Integer returnWindowDays, BigDecimal returnApprovalOver,
                             Boolean returnReasonRequired, String returnReasons,
                             BigDecimal cashierDiscountMax, Boolean discountReasonRequired, String discountReasons, Boolean priceOverrideAllowed, Boolean priceBelowCostBlocked,
                             String receiptHeader, String receiptFooter, String receiptPaper, Boolean receiptShowLogo, Boolean receiptShowQr) {
    public static PosSettingsDto from(PosSettings s) {
        return new PosSettingsDto(s.getVarianceTolerance(), s.isVarianceNoteRequired(), s.isDenominationCountRequired(), s.getPaidOutApprovalOver(), s.getDropAlertAmount(),
                s.isVoidRequiresApproval(), s.isVoidSameShiftOnly(), s.getReturnWindowDays(), s.getReturnApprovalOver(), s.isReturnReasonRequired(), s.getReturnReasons(),
                s.getCashierDiscountMax(), s.isDiscountReasonRequired(), s.getDiscountReasons(), s.isPriceOverrideAllowed(), s.isPriceBelowCostBlocked(),
                s.getReceiptHeader(), s.getReceiptFooter(), s.getReceiptPaper(), s.isReceiptShowLogo(), s.isReceiptShowQr());
    }
}
