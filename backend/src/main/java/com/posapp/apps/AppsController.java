package com.posapp.apps;

import com.posapp.apps.dto.AppDto;
import com.posapp.common.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task: "This will work as a plugin (or apps) to be installed and configured" — ADMIN-only, same
 * tier as every other Settings screen (Business Details, System settings, Payment methods,
 * Taxes): installing/configuring an integration is a store-configuration action, not something a
 * cashier does.
 */
@RestController
@RequestMapping("/api/apps")
@PreAuthorize("hasRole('ADMIN')")
public class AppsController {

    private final AppsService appsService;

    public AppsController(AppsService appsService) {
        this.appsService = appsService;
    }

    @GetMapping
    public List<AppDto> list() {
        return appsService.list();
    }

    @PostMapping("/{key}/install")
    public AppDto install(@PathVariable String key) {
        return appsService.install(parseKey(key));
    }

    @PostMapping("/{key}/uninstall")
    public void uninstall(@PathVariable String key) {
        appsService.uninstall(parseKey(key));
    }

    @PostMapping("/{key}/enable")
    public AppDto enable(@PathVariable String key) {
        return appsService.setEnabled(parseKey(key), true);
    }

    @PostMapping("/{key}/disable")
    public AppDto disable(@PathVariable String key) {
        return appsService.setEnabled(parseKey(key), false);
    }

    private AppKey parseKey(String key) {
        try {
            return AppKey.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Unknown app: " + key);
        }
    }
}
