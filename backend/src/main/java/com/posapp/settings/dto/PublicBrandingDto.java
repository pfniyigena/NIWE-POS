package com.posapp.settings.dto;

import com.posapp.settings.StoreSettings;

/**
 * Fourier: "it is better to have a home page with look nice like a website" — the one sliver of
 * {@link StoreSettings} safe to show an anonymous visitor on the new public home page (see
 * {@code PublicSettingsController}). Deliberately NOT {@link StoreSettingsDto}: that record also
 * carries SMTP host/username/notification-email and other back-office configuration that has no
 * business being reachable without a login.
 */
public record PublicBrandingDto(String businessName, String logoDataUrl) {
    public static PublicBrandingDto from(StoreSettings s) {
        return new PublicBrandingDto(s.getBusinessName(), s.getLogoDataUrl());
    }
}
