package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineDriverAccessService.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The driver's private link (v1.59.0). Public side: no staff login, the secret in the address plus the PIN. Staff side: make, show, re-PIN and switch off.
 */
@RestController
public class OnlineDriverLinkController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    static final String SESSION = "X-Driver-Session";

    private final OnlineDriverAccessService access;
    private final ModuleService moduleService;

    public OnlineDriverLinkController(OnlineDriverAccessService access, ModuleService moduleService) {
        this.access = access;
        this.moduleService = moduleService;
    }

    // ---- staff

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/drivers/{id}/access")
    public DriverAccess create(@PathVariable Long id) {
        return access.create(id);
    }

    @PreAuthorize(MANAGE)
    @GetMapping("/api/online-store/drivers/{id}/access")
    public DriverAccess link(@PathVariable Long id) {
        return access.link(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/drivers/{id}/access/pin")
    public DriverAccess newPin(@PathVariable Long id) {
        return access.newPin(id);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/drivers/{id}/access")
    public DriverAccess off(@PathVariable Long id) {
        return access.off(id);
    }

    // ---- the driver

    @GetMapping("/api/public/driver/{token}")
    public DriverLinkInfo info(@PathVariable String token, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return access.info(token, http.getRemoteAddr());
    }

    @PostMapping("/api/public/driver/{token}/session")
    public DriverSession login(@PathVariable String token, @Valid @RequestBody PinRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return access.login(token, request.pin(), http.getRemoteAddr());
    }

    @GetMapping("/api/public/driver/{token}/orders")
    public DriverBoard jobs(@PathVariable String token, @RequestHeader(value = SESSION, required = false) String session, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return access.jobs(token, session, http.getRemoteAddr());
    }

    @PostMapping("/api/public/driver/{token}/orders/{id}/receive")
    public DriverBoard receive(@PathVariable String token, @PathVariable Long id, @Valid @RequestBody DriverReceive request,
                               @RequestHeader(value = SESSION, required = false) String session, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return access.receive(token, session, id, request, http.getRemoteAddr());
    }

    @PostMapping("/api/public/driver/{token}/returns/{id}/collect")
    public DriverBoard collect(@PathVariable String token, @PathVariable Long id, @Valid @RequestBody DriverReceive request,
                               @RequestHeader(value = SESSION, required = false) String session, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return access.collect(token, session, id, request, http.getRemoteAddr());
    }
}
