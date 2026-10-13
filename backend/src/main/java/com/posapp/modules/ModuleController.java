package com.posapp.modules;

import com.posapp.common.ApiException;
import com.posapp.modules.ModuleService.ModuleDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Reading is open to every signed-in user (the sidebar needs it); switching a module is admin-only. */
@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    public record ModuleToggleRequest(Boolean enabled) {
    }

    private final ModuleService service;

    public ModuleController(ModuleService service) {
        this.service = service;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<ModuleDto> list() {
        return service.list();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{key}")
    public List<ModuleDto> set(@PathVariable String key, @RequestBody ModuleToggleRequest request) {
        if (request == null || request.enabled() == null) {
            throw ApiException.badRequest("enabled is required");
        }
        ModuleKey moduleKey;
        try {
            moduleKey = ModuleKey.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown module: " + key);
        }
        return service.setEnabled(moduleKey, request.enabled());
    }
}
