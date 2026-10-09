package com.posapp.settings;

import com.posapp.settings.dto.PublicBrandingDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fourier: "it is better to have a home page with look nice like a website" — the new public,
 * no-login home page needs the business name/logo to brand itself with, but {@code
 * StoreSettingsController}'s own {@code GET /api/settings} still requires a logged-in user (see
 * its own javadoc — "open" there only ever meant "any logged-in user", never anonymous), and
 * returns far more than a homepage should ever see (SMTP host/username, notification email,
 * etc). This is the one branding sliver carved out for that, under {@code /api/public/**} —
 * same "the only unauthenticated endpoints in the app" namespace {@code PublicReservationController}
 * and {@code PublicHelpDeskController} already use (see {@code SecurityConfig}'s own comment on
 * that matcher).
 */
@RestController
@RequestMapping("/api/public/settings")
public class PublicSettingsController {

    private final StoreSettingsService settingsService;

    public PublicSettingsController(StoreSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/branding")
    public PublicBrandingDto branding() {
        return settingsService.getPublicBranding();
    }
}
