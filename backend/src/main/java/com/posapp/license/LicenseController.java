package com.posapp.license;

import com.posapp.common.ApiException;
import com.posapp.license.LicenseDtos.InstallRequest;
import com.posapp.license.LicenseDtos.LicenseStatus;
import com.posapp.license.LicenseDtos.ModuleRequestDto;
import com.posapp.license.LicenseDtos.RequestModuleRequest;
import com.posapp.license.LicenseDtos.RequestResult;
import com.posapp.modules.ModuleKey;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Settings > Modules, license part. Everything here is admin only. */
@RestController
@RequestMapping("/api/license")
@PreAuthorize("hasRole('ADMIN')")
public class LicenseController {

    private final LicenseService licenseService;
    private final ModuleRequestService requestService;

    public LicenseController(LicenseService licenseService, ModuleRequestService requestService) {
        this.licenseService = licenseService;
        this.requestService = requestService;
    }

    @GetMapping
    public LicenseStatus status() {
        return licenseService.status();
    }

    @PostMapping
    public LicenseStatus install(@RequestBody InstallRequest request, Authentication auth) {
        if (request == null || request.license() == null) {
            throw ApiException.badRequest("license is required");
        }
        return licenseService.install(request.license(), auth.getName());
    }

    @GetMapping("/requests")
    public List<ModuleRequestDto> requests() {
        return requestService.list();
    }

    @PostMapping("/requests")
    public RequestResult request(@RequestBody RequestModuleRequest request, Authentication auth) {
        if (request == null || request.module() == null || request.module().isBlank()) {
            throw ApiException.badRequest("module is required");
        }
        ModuleKey key;
        try {
            key = ModuleKey.valueOf(request.module().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown module: " + request.module());
        }
        return requestService.create(key, request.note(), auth.getName());
    }

    @DeleteMapping("/requests/{id}")
    public ModuleRequestDto withdraw(@PathVariable Long id, Authentication auth) {
        return requestService.withdraw(id, auth.getName());
    }
}
