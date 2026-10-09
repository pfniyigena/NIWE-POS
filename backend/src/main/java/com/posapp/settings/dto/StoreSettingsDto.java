package com.posapp.settings.dto;

import com.posapp.settings.StoreSettings;

import java.math.BigDecimal;

/** Full read view of the singleton store settings row — backs both the Business Details and System settings screens. */
public record StoreSettingsDto(
        Long id,
        String businessName,
        String address,
        String phone,
        String email,
        String taxId,
        String logoDataUrl,
        String currencyCode,
        String currencySymbol,
        String timeZone,
        String dateFormat,
        /** Task 1: "configure the max discount" — see StoreSettings.maxDiscountPercent's javadoc. */
        BigDecimal maxDiscountPercent,
        /** Fourier: "discount should be activate in the setting or configuration ... Either on line or global" — one of "NONE"/"PER_LINE"/"GLOBAL", see StoreSettings.discountMode's javadoc. */
        String discountMode,
        /** Task: "Inventory Setting (Example: allow negative stock)" — see StoreSettings.allowNegativeStock's javadoc. */
        boolean allowNegativeStock,
        /** Fourier: "Product Packaging & Breakdown ... configured as we did for chart of accounts. If not activated ignore packaging" — see StoreSettings.productBreakdownEnabled's javadoc. */
        boolean productBreakdownEnabled,
        /** Fourier: "Add allow multi-warehouse in inventory settings" — see StoreSettings.multiWarehouseEnabled's javadoc. */
        boolean multiWarehouseEnabled,
        /** Multi-warehouse phase 4 — see StoreSettings.restaurantLocationId's javadoc. */
        Long restaurantLocationId,
        /** Fourier: "Configure smtp for sending email" — see StoreSettings.smtpEnabled's javadoc. */
        boolean smtpEnabled,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        /** Never the actual password — see StoreSettings.smtpPassword's javadoc. Just tells the admin UI whether one is already saved. */
        boolean smtpPasswordSet,
        String smtpFromEmail,
        String smtpFromName,
        boolean smtpUseSsl,
        /** Fourier's own opt-in escape hatch for a real certificate-hostname mismatch — see StoreSettings.smtpSkipCertCheck's javadoc. */
        boolean smtpSkipCertCheck,
        /** Fourier: "Wiring a handful of business events to that same mailer" — see StoreSettings.notificationsEmail's javadoc. */
        String notificationsEmail,
        /** WEIGHTED_AVERAGE / LAST_PURCHASE / MANUAL — see StoreSettings.costingMethod's javadoc. */
        String costingMethod
) {
    public static StoreSettingsDto from(StoreSettings s) {
        return new StoreSettingsDto(
                s.getId(),
                s.getBusinessName(),
                s.getAddress(),
                s.getPhone(),
                s.getEmail(),
                s.getTaxId(),
                s.getLogoDataUrl(),
                s.getCurrencyCode(),
                s.getCurrencySymbol(),
                s.getTimeZone(),
                s.getDateFormat(),
                s.getMaxDiscountPercent(),
                s.getDiscountMode().name(),
                s.isAllowNegativeStock(),
                s.isProductBreakdownEnabled(),
                s.isMultiWarehouseEnabled(),
                s.getRestaurantLocationId(),
                s.isSmtpEnabled(),
                s.getSmtpHost(),
                s.getSmtpPort(),
                s.getSmtpUsername(),
                s.getSmtpPassword() != null && !s.getSmtpPassword().isBlank(),
                s.getSmtpFromEmail(),
                s.getSmtpFromName(),
                s.isSmtpUseSsl(),
                s.isSmtpSkipCertCheck(),
                s.getNotificationsEmail(),
                s.getCostingMethod() != null ? s.getCostingMethod() : "WEIGHTED_AVERAGE"
        );
    }
}
