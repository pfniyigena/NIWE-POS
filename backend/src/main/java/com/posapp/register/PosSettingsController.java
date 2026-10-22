package com.posapp.register;

import com.posapp.register.dto.PosSettingsDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/settings")
public class PosSettingsController {

    private final PosSettingsService service;

    public PosSettingsController(PosSettingsService service) {
        this.service = service;
    }

    /** Any signed-in user: the till reads the rules (limits, reminders). */
    @GetMapping
    public PosSettingsDto get() {
        return service.get();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PutMapping
    public PosSettingsDto update(@RequestBody PosSettingsDto in) {
        return service.update(in);
    }
}
