package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantSettings;

import java.math.BigDecimal;

public record RestaurantSettingsDto(
        BigDecimal serviceChargePercent,
        boolean serviceChargeDineIn,
        boolean serviceChargeTakeaway,
        boolean serviceChargeDelivery,
        BigDecimal serviceChargeVatPercent,
        BigDecimal discountLimitPercent,
        boolean tipsEnabled,
        String tipPresets,
        BigDecimal roundingUnit,
        String notePresets,
        String openTime,
        String closeTime,
        Integer slotMinutes,
        Integer turnMinutes,
        Integer bookingDaysAhead,
        BigDecimal depositPerGuest,
        Integer depositRefundHours,
        Integer reminderHours,
        Integer graceMinutes,
        Integer noShowBlockCount,
        String reminderTemplate,
        Integer defaultPrepMinutes,
        Integer kitchenWarnPercent,
        Boolean onlineEnabled,
        Boolean qrEnabled,
        Boolean onlineTakeaway,
        Boolean onlineDelivery,
        Boolean onlinePaused,
        Boolean onlineAutoAcceptTables,
        Integer onlinePrepMinutes,
        BigDecimal onlineMinOrder,
        Integer onlineMaxPending,
        Integer onlineExpireMinutes,
        String onlineMessage,
        Boolean publicEnabled
) {
    public static RestaurantSettingsDto from(RestaurantSettings s) {
        return new RestaurantSettingsDto(s.getServiceChargePercent(), s.isServiceChargeDineIn(), s.isServiceChargeTakeaway(), s.isServiceChargeDelivery(),
                s.getServiceChargeVatPercent(), s.getDiscountLimitPercent(), s.isTipsEnabled(), s.getTipPresets(), s.getRoundingUnit(), s.getNotePresets(),
                s.getOpenTime(), s.getCloseTime(), s.getSlotMinutes(), s.getTurnMinutes(), s.getBookingDaysAhead(), s.getDepositPerGuest(),
                s.getDepositRefundHours(), s.getReminderHours(), s.getGraceMinutes(), s.getNoShowBlockCount(), s.getReminderTemplate(),
                s.getDefaultPrepMinutes(), s.getKitchenWarnPercent(), s.isOnlineEnabled(), s.isQrEnabled(), s.isOnlineTakeaway(), s.isOnlineDelivery(), s.isOnlinePaused(),
                s.isOnlineAutoAcceptTables(), s.getOnlinePrepMinutes(), s.getOnlineMinOrder(), s.getOnlineMaxPending(), s.getOnlineExpireMinutes(), s.getOnlineMessage(), s.isPublicEnabled());
    }
}
