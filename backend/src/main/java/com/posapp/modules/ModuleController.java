package com.posapp.modules;

import com.posapp.common.ApiException;
import com.posapp.license.LicenseService;
import com.posapp.modules.ModuleService.ModuleDto;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final LicenseService licenseService;
    private final ModuleListPdf listPdf;

    public ModuleController(ModuleService service, LicenseService licenseService, ModuleListPdf listPdf) {
        this.service = service;
        this.licenseService = licenseService;
        this.listPdf = listPdf;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<ModuleDto> list() {
        return service.list();
    }

    /** Settings > Modules, Print PDF: all modules with their state, and the license. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list-pdf")
    public ResponseEntity<byte[]> listPdf() {
        byte[] bytes = listPdf.build(service.list(), licenseService.status());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("niwe-erp-modules.pdf").build().toString()).body(bytes);
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
