package com.posapp.apps.efrischad;

import com.posapp.apps.efrischad.dto.EfrisChadLogosDto;
import com.posapp.apps.efrischad.dto.EfrisChadTerminalDto;
import com.posapp.apps.efrischad.dto.UpdateEfrisChadSettingRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Task: "Now I want to add new app (EFRIS Chad)..." — per-terminal EFRIS Chad connection
 * configuration, ADMIN-only, same tier as every other Settings screen. Deliberately its own fixed
 * path (mirroring {@code EfrisSettingController}, Rwanda's equivalent) rather than routed through
 * {@code AppsController}'s generic {@code /{key}/settings} endpoints — this is structured, one row
 * per Register, not something every app in the catalog needs.
 *
 * <p>{@link #getLogos} mirrors {@code EfrisSettingController#getLogos} (Rwanda's equivalent) — the
 * one deliberate exception to this class's own ADMIN-only default, added once Task 3 ("Add also
 * Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting") gave EFRIS Chad its own two
 * letterhead images: any logged-in cashier can already print a completed sale's receipt, so the
 * branding images that print on it can't be ADMIN-only either, even though every other field on
 * this same terminal row still is.</p>
 */
@RestController
@RequestMapping("/api/apps/efris-chad")
@PreAuthorize("hasRole('ADMIN')")
public class EfrisChadSettingController {

    private final EfrisChadSettingService efrisChadSettingService;

    public EfrisChadSettingController(EfrisChadSettingService efrisChadSettingService) {
        this.efrisChadSettingService = efrisChadSettingService;
    }

    @GetMapping("/terminals")
    public List<EfrisChadTerminalDto> listTerminals() {
        return efrisChadSettingService.listTerminals();
    }

    @PutMapping("/terminals/{registerId}")
    public EfrisChadTerminalDto saveTerminalSettings(@PathVariable Long registerId, @Valid @RequestBody UpdateEfrisChadSettingRequest request) {
        return efrisChadSettingService.saveTerminalSettings(registerId, request);
    }

    /**
     * Task: "One Terminal can have only one EFRIS mapped with" — unmaps this terminal from EFRIS
     * Chad so it's either left unmapped or can be mapped to EFRIS Rwanda instead.
     */
    @DeleteMapping("/terminals/{registerId}")
    public EfrisChadTerminalDto clearTerminalSettings(@PathVariable Long registerId) {
        return efrisChadSettingService.clearTerminalSettings(registerId);
    }

    @GetMapping("/terminals/{registerId}/logos")
    @PreAuthorize("isAuthenticated()")
    public EfrisChadLogosDto getLogos(@PathVariable Long registerId) {
        return efrisChadSettingService.getLogosForRegister(registerId);
    }
}
