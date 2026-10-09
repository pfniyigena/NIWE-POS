package com.posapp.settings;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.settings.dto.BusinessDetailsRequest;
import com.posapp.settings.dto.EmailSettingsRequest;
import com.posapp.settings.dto.InventorySettingsRequest;
import com.posapp.settings.dto.PublicBrandingDto;
import com.posapp.settings.dto.StoreSettingsDto;
import com.posapp.settings.dto.SystemSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StoreSettingsService {

    private final StoreSettingsRepository repository;
    private final AuditLogService auditLogService;
    private final MailService mailService;

    public StoreSettingsService(StoreSettingsRepository repository, AuditLogService auditLogService,
                                 MailService mailService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
    }

    @Transactional(readOnly = true)
    public StoreSettingsDto getSettings() {
        return StoreSettingsDto.from(getOrCreate());
    }

    /** Fourier: "it is better to have a home page with look nice like a website" — the one unauthenticated read of this settings row, see {@code PublicSettingsController}/{@link PublicBrandingDto}'s own javadoc for why it's this narrow. */
    @Transactional(readOnly = true)
    public PublicBrandingDto getPublicBranding() {
        return PublicBrandingDto.from(getOrCreate());
    }

    /**
     * The raw entity, for internal cross-module callers that need fields {@link StoreSettingsDto}
     * deliberately never exposes (the SMTP password) or that just want the business-details fields
     * without a DTO round-trip — currently {@code MailService} and {@code DocumentPdfService}'s
     * callers (QuotationService/InvoiceService/OrderService, emailing a quotation/invoice/receipt —
     * Fourier: "sending other documents ... to the customer's email"). Read-only; callers must
     * never mutate and save what this returns — {@link #updateBusinessDetails} and friends are the
     * only writers.
     */
    @Transactional(readOnly = true)
    public StoreSettings getSettingsEntity() {
        return getOrCreate();
    }

    /** Task: "Implement an audit log of who did what and when" — settings changes are one of the curated actions, see AuditLogService's javadoc. */
    @Transactional
    public StoreSettingsDto updateBusinessDetails(BusinessDetailsRequest request) {
        StoreSettings settings = getOrCreate();
        settings.setBusinessName(request.getBusinessName());
        settings.setAddress(request.getAddress());
        settings.setPhone(request.getPhone());
        settings.setEmail(request.getEmail());
        settings.setTaxId(request.getTaxId());
        settings.setLogoDataUrl(request.getLogoDataUrl());
        StoreSettings saved = repository.save(settings);
        auditLogService.record("BUSINESS_DETAILS_UPDATED",
                "Updated business details for " + (saved.getBusinessName() != null ? saved.getBusinessName() : "the store"),
                "StoreSettings", saved.getId());
        return StoreSettingsDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — see updateBusinessDetails's identical treatment just above. */
    @Transactional
    public StoreSettingsDto updateSystemSettings(SystemSettingsRequest request) {
        StoreSettings settings = getOrCreate();
        settings.setCurrencyCode(request.getCurrencyCode());
        settings.setCurrencySymbol(request.getCurrencySymbol());
        settings.setTimeZone(request.getTimeZone());
        settings.setDateFormat(request.getDateFormat());
        settings.setMaxDiscountPercent(request.getMaxDiscountPercent());
        settings.setDiscountMode(parseDiscountMode(request.getDiscountMode()));
        StoreSettings saved = repository.save(settings);
        auditLogService.record("SYSTEM_SETTINGS_UPDATED",
                "Updated system settings (currency " + saved.getCurrencyCode() + ", time zone " + saved.getTimeZone()
                        + ", max discount " + saved.getMaxDiscountPercent() + "%, discount mode "
                        + saved.getDiscountMode() + ")",
                "StoreSettings", saved.getId());
        return StoreSettingsDto.from(saved);
    }

    /**
     * Fourier: "discount should be activate in the setting or configuration ... Either on line
     * or global" — {@code SystemSettingsRequest.discountMode} arrives as a plain string (so the
     * frontend never has to know the enum's exact Java type), so this is where it's actually
     * turned into a {@link DiscountMode} or rejected as the same kind of business-rule error
     * every other invalid input in this app gets, rather than the generic 400 a jakarta.validation
     * annotation would produce for an enum-typed field.
     */
    private DiscountMode parseDiscountMode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Discount mode is required");
        }
        try {
            return DiscountMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Discount mode must be NONE, PER_LINE, or GLOBAL");
        }
    }

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — shared validation for every per-line discount on
     * a Quotation, Sales Order, Invoice, or Purchase Order line (Bill/Refund/Credit Note never
     * call this directly — they inherit whatever already-validated value their source Sales
     * Order/Purchase Order line carries, same as {@code taxCode}). Returns the normalized,
     * never-null value to snapshot onto the line.
     * <p>
     * {@code null} or zero is always allowed, even with the feature off, since that never grants
     * a discount — only a genuinely positive value is gated. While {@link StoreSettings
     * #getDiscountMode()} isn't {@code PER_LINE} (either {@code NONE}, or {@code GLOBAL} — a
     * per-line value makes no sense once the store has switched to one discount for the whole
     * document), any positive value here is rejected outright with a clear error (never silently
     * zeroed — a silent drop would leave an admin wondering why their typed discount didn't show
     * up on the printed document). Once the mode is {@code PER_LINE}, the value is still capped
     * at the exact same store-wide {@link StoreSettings#getMaxDiscountPercent()} ceiling the POS
     * terminal's own discount already respects (see {@code OrderService.validateDiscount}) — one
     * ceiling, shared by every discount in the app, whichever document type or mode it's applied
     * through. See {@link #validateGlobalDiscount} for the {@code GLOBAL}-mode counterpart.
     */
    @Transactional(readOnly = true)
    public BigDecimal validateLineDiscount(BigDecimal discountPercent) {
        return validateDiscountForMode(discountPercent, DiscountMode.PER_LINE, "Line discounts are turned off");
    }

    /**
     * Fourier: "Except POS other module can have global discount. But again give the user to
     * configure both discount. Either on line or global" — the {@code GLOBAL}-mode counterpart
     * to {@link #validateLineDiscount}: called once per document (not once per line) with the
     * single percentage entered for the whole Quotation/Sales Order/Invoice/Purchase Order,
     * rejecting it outright (never silently zeroing it) unless {@link StoreSettings
     * #getDiscountMode()} is actually {@code GLOBAL}, and capping it at the same store-wide
     * {@link StoreSettings#getMaxDiscountPercent()} ceiling every other discount in this app
     * shares.
     */
    @Transactional(readOnly = true)
    public BigDecimal validateGlobalDiscount(BigDecimal discountPercent) {
        return validateDiscountForMode(discountPercent, DiscountMode.GLOBAL, "Global discount is turned off");
    }

    private BigDecimal validateDiscountForMode(BigDecimal discountPercent, DiscountMode requiredMode, String offMessage) {
        if (discountPercent == null || discountPercent.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (discountPercent.signum() < 0) {
            throw ApiException.badRequest("Discount must be between 0 and 100");
        }
        StoreSettings settings = getOrCreate();
        if (settings.getDiscountMode() != requiredMode) {
            throw ApiException.badRequest(offMessage + " — enable it in System settings first");
        }
        BigDecimal max = settings.getMaxDiscountPercent() == null ? BigDecimal.valueOf(100) : settings.getMaxDiscountPercent();
        if (discountPercent.compareTo(max) > 0) {
            throw ApiException.badRequest("Discount cannot exceed the configured maximum of " + max + "%");
        }
        return discountPercent;
    }

    /**
     * Task: "Inventory Setting (Example: allow negative stock)." A third split off the same
     * singleton row — same "independent form, independent request DTO/endpoint" reasoning as
     * {@link #updateBusinessDetails}/{@link #updateSystemSettings} above, so Admin > Inventory >
     * Settings never has to know about (or accidentally overwrite) currency/timezone/discount
     * fields it has nothing to do with.
     */
    @Transactional
    public StoreSettingsDto updateInventorySettings(InventorySettingsRequest request) {
        StoreSettings settings = getOrCreate();
        settings.setAllowNegativeStock(request.isAllowNegativeStock());
        settings.setProductBreakdownEnabled(request.isProductBreakdownEnabled());
        settings.setMultiWarehouseEnabled(request.isMultiWarehouseEnabled());
        settings.setRestaurantLocationId(request.getRestaurantLocationId());
        String method = request.getCostingMethod() == null || request.getCostingMethod().isBlank() ? "WEIGHTED_AVERAGE" : request.getCostingMethod().trim().toUpperCase();
        if (!method.equals("WEIGHTED_AVERAGE") && !method.equals("LAST_PURCHASE") && !method.equals("MANUAL")) {
            throw com.posapp.common.ApiException.badRequest("Costing method must be WEIGHTED_AVERAGE, LAST_PURCHASE or MANUAL");
        }
        settings.setCostingMethod(method);
        StoreSettings saved = repository.save(settings);
        auditLogService.record("INVENTORY_SETTINGS_UPDATED",
                "Updated inventory settings (allow negative stock " + (saved.isAllowNegativeStock() ? "on" : "off")
                        + ", product packaging & breakdown " + (saved.isProductBreakdownEnabled() ? "on" : "off")
                        + ", multi-warehouse " + (saved.isMultiWarehouseEnabled() ? "on" : "off")
                        + ", costing " + saved.getCostingMethod() + ")",
                "StoreSettings", saved.getId());
        return StoreSettingsDto.from(saved);
    }

    /**
     * Fourier: "Configure smtp for sending email" — a fifth independent form on the same singleton
     * row. {@code request.smtpPassword} is left blank whenever the admin isn't changing it (the
     * frontend never has the real saved value to redisplay — see StoreSettingsDto.smtpPasswordSet),
     * so only a non-blank value here overwrites what's already stored; every other field always
     * overwrites, same as the other update* methods. Never audit-logs the password itself, only
     * that email settings changed.
     */
    @Transactional
    public StoreSettingsDto updateEmailSettings(EmailSettingsRequest request) {
        StoreSettings settings = getOrCreate();
        settings.setSmtpEnabled(request.isSmtpEnabled());
        settings.setSmtpHost(request.getSmtpHost());
        settings.setSmtpPort(request.getSmtpPort() != null ? request.getSmtpPort() : 587);
        settings.setSmtpUsername(request.getSmtpUsername());
        if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
            settings.setSmtpPassword(request.getSmtpPassword());
        }
        settings.setSmtpFromEmail(request.getSmtpFromEmail());
        settings.setSmtpFromName(request.getSmtpFromName());
        settings.setSmtpUseSsl(request.isSmtpUseSsl());
        settings.setSmtpSkipCertCheck(request.isSmtpSkipCertCheck());
        settings.setNotificationsEmail(request.getNotificationsEmail());
        StoreSettings saved = repository.save(settings);
        auditLogService.record("EMAIL_SETTINGS_UPDATED",
                "Updated email settings (SMTP " + (saved.isSmtpEnabled() ? "enabled" : "disabled")
                        + (saved.getSmtpHost() != null ? ", host " + saved.getSmtpHost() : "")
                        + (saved.isSmtpSkipCertCheck() ? ", certificate check skipped" : "") + ")",
                "StoreSettings", saved.getId());
        return StoreSettingsDto.from(saved);
    }

    /**
     * Fourier: "add an Email Test button for easy testing after set up email configurations" —
     * sends a short test email through whatever SMTP settings are currently saved (exactly the
     * ones {@link #updateEmailSettings} just persisted), so an admin can confirm the connection
     * actually works right from the settings screen instead of waiting for a real password-reset
     * or document email to go out. Reuses {@code MailService.send} exactly like every other
     * document-emailing caller (QuotationService/InvoiceService/OrderService) — same one
     * consistent {@code ApiException.badRequest} error shape whether that's "not configured yet"
     * or the SMTP server itself rejecting the connection, so this button surfaces the same failure
     * message an admin would eventually hit for real.
     */
    @Transactional
    public void sendTestEmail(String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            throw ApiException.badRequest("Enter an email address to send the test to");
        }
        StoreSettings settings = getOrCreate();
        String businessName = settings.getBusinessName() != null && !settings.getBusinessName().isBlank()
                ? settings.getBusinessName() : "your store";
        String html = "<p>This is a test email from " + escapeHtml(businessName) + ".</p>"
                + "<p>If you're reading this, your SMTP settings are working correctly.</p>";
        mailService.send(toEmail.trim(), "Test email from " + businessName, html, List.of());
        auditLogService.record("EMAIL_TEST_SENT", "Sent a test email to " + toEmail.trim(), "StoreSettings", settings.getId());
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * There's exactly one settings row in practice (see the class javadoc on {@link StoreSettings});
     * this is what lets both {@code getSettings} and the update methods above treat it as a
     * singleton without a fixed/well-known id. Normally DataSeeder has already created it on first
     * run, so the create-on-demand path here is just a safety net (e.g. app.seed-demo-data=false).
     */
    private StoreSettings getOrCreate() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new StoreSettings()));
    }
}
