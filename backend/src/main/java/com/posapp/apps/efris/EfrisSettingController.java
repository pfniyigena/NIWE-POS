package com.posapp.apps.efris;

import com.posapp.apps.efris.dto.EfrisLogosDto;
import com.posapp.apps.efris.dto.EfrisTerminalDto;
import com.posapp.apps.efris.dto.UpdateEfrisSettingRequest;
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
 * Task: "every terminal should be connected to the external EFRIS" — per-terminal EFRIS URL
 * configuration, ADMIN-only same as the rest of Admin &gt; Settings &gt; Apps. Deliberately its
 * own fixed path rather than routed through AppsController's generic {@code /{key}/settings}
 * endpoints: this is EFRIS-specific, structured (one row per Register) data, not something every
 * app in the catalog needs, so it doesn't belong on the generic Apps framework surface.
 * <p>
 * {@link #getLogos} is the one deliberate exception to this class's own ADMIN-only default (Task:
 * "add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the printout") — any logged-in
 * cashier can already print a completed sale's receipt (see ReceiptComponent/OrderController,
 * neither of which is ADMIN-gated), so the two branding images that print on it can't be
 * ADMIN-only either, even though every other field on this same terminal row (URL/db/uid/password)
 * still is.
 */
@RestController
@RequestMapping("/api/apps/efris")
@PreAuthorize("hasRole('ADMIN')")
public class EfrisSettingController {

    private final EfrisSettingService efrisSettingService;

    public EfrisSettingController(EfrisSettingService efrisSettingService) {
        this.efrisSettingService = efrisSettingService;
    }

    @GetMapping("/terminals")
    public List<EfrisTerminalDto> listTerminals() {
        return efrisSettingService.listTerminals();
    }

    @PutMapping("/terminals/{registerId}")
    public EfrisTerminalDto saveTerminalSettings(@PathVariable Long registerId, @Valid @RequestBody UpdateEfrisSettingRequest request) {
        return efrisSettingService.saveTerminalSettings(registerId, request);
    }

    /**
     * Task: "One Terminal can have only one EFRIS mapped with" — unmaps this terminal from EFRIS
     * Rwanda so it's either left unmapped or can be mapped to EFRIS Chad instead.
     */
    @DeleteMapping("/terminals/{registerId}")
    public EfrisTerminalDto clearTerminalSettings(@PathVariable Long registerId) {
        return efrisSettingService.clearTerminalSettings(registerId);
    }

    @GetMapping("/terminals/{registerId}/logos")
    @PreAuthorize("isAuthenticated()")
    public EfrisLogosDto getLogos(@PathVariable Long registerId) {
        return efrisSettingService.getLogosForRegister(registerId);
    }
}
