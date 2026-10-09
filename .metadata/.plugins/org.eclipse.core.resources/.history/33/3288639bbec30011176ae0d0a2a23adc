package com.posapp.settings;

import com.posapp.email.EmailDocumentRequest;
import com.posapp.settings.dto.BusinessDetailsRequest;
import com.posapp.settings.dto.EmailSettingsRequest;
import com.posapp.settings.dto.InventorySettingsRequest;
import com.posapp.settings.dto.StoreSettingsDto;
import com.posapp.settings.dto.SystemSettingsRequest;
import com.posapp.warehouse.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin > Settings > Business Details / System settings. Writes stay `ADMIN`-only, like every
 * other "how the store is configured" screen (`TaxRateController`, `PaymentMethodController`'s
 * admin endpoints) — a cashier has no reason to change these. The **read**, as of the twenty-
 * fourth pass (Task 1/3: business name/logo on the receipt printout and the topbar "POS" label),
 * is open to any logged-in user instead — both of those are rendered on screens a cashier sees
 * just as often as an admin (every receipt, every page's topbar), so this controller dropped its
 * class-level `@PreAuthorize` in favor of a method-level one on just the two `PUT`s, the same
 * "open read, gated write" shape `PaymentMethodController`/`CustomerCreditController` already use.
 */
@RestController
@RequestMapping("/api/settings")
public class StoreSettingsController {

    private final StoreSettingsService settingsService;

    /** Validates the restaurant kitchen location here rather than in StoreSettingsService, which WarehouseService itself depends on (no circular bean). */
    private final WarehouseService warehouseService;

    public StoreSettingsController(StoreSettingsService settingsService, WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public StoreSettingsDto get() {
        return settingsService.getSettings();
    }

    @PutMapping("/business")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsDto updateBusinessDetails(@Valid @RequestBody BusinessDetailsRequest request) {
        return settingsService.updateBusinessDetails(request);
    }

    @PutMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsDto updateSystemSettings(@Valid @RequestBody SystemSettingsRequest request) {
        return settingsService.updateSystemSettings(request);
    }

    /** Task: "Inventory Setting (Example: allow negative stock)" — a third independent form on the same settings row, see StoreSettingsService.updateInventorySettings's javadoc. */
    @PutMapping("/inventory")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsDto updateInventorySettings(@Valid @RequestBody InventorySettingsRequest request) {
        if (request.getRestaurantLocationId() != null) {
            warehouseService.getUsableLocationOrThrow(request.getRestaurantLocationId());
        }
        return settingsService.updateInventorySettings(request);
    }

    /** Fourier: "Configure smtp for sending email" — a fifth independent form on the same settings row, see StoreSettingsService.updateEmailSettings's javadoc. */
    @PutMapping("/email")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreSettingsDto updateEmailSettings(@Valid @RequestBody EmailSettingsRequest request) {
        return settingsService.updateEmailSettings(request);
    }

    /** Fourier: "add an Email Test button for easy testing after set up email configurations" — see StoreSettingsService.sendTestEmail's javadoc. */
    @PostMapping("/email/test")
    @PreAuthorize("hasRole('ADMIN')")
    public void sendTestEmail(@RequestBody EmailDocumentRequest request) {
        settingsService.sendTestEmail(request.getEmail());
    }
}
