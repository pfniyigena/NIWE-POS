package com.posapp.salessettings;

import com.posapp.salessettings.dto.SalesSettingsDto;
import com.posapp.salessettings.dto.SalesSettingsRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin > Sales > Settings — ADMIN-only, same tier as HelpDeskSettingsController and every other back-office "how this module is configured" screen. */
@RestController
@RequestMapping("/api/sales")
public class SalesSettingsController {

    private final SalesSettingsService service;

    public SalesSettingsController(SalesSettingsService service) {
        this.service = service;
    }

    /** Which of Promotions, Gift cards and Loyalty points are switched on: every signed-in user needs it to show or hide the menu entries and till buttons. */
    @GetMapping("/features")
    public SalesSettingsService.Features features() {
        return service.features();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/settings")
    public SalesSettingsDto get() {
        return service.getSettings();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public SalesSettingsDto update(@RequestBody SalesSettingsRequest request) {
        return service.updateSettings(request);
    }
}
