package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskSettingsDto;
import com.posapp.helpdesk.dto.HelpDeskSettingsRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin > Help Desk > Settings — ADMIN-only, same tier as HelpDeskCategoryController and every other back-office "how this module is configured" screen. */
@RestController
@RequestMapping("/api/helpdesk/settings")
@PreAuthorize("hasRole('ADMIN')")
public class HelpDeskSettingsController {

    private final HelpDeskSettingsService service;

    public HelpDeskSettingsController(HelpDeskSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public HelpDeskSettingsDto get() {
        return service.getSettings();
    }

    @PutMapping
    public HelpDeskSettingsDto update(@RequestBody HelpDeskSettingsRequest request) {
        return service.updateSettings(request);
    }
}
