package com.posapp.register;

import com.posapp.register.dto.*;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RegisterController {

    private final RegisterService registerService;
    private final DeviceBindingService deviceBindingService;

    public RegisterController(RegisterService registerService, DeviceBindingService deviceBindingService) {
        this.registerService = registerService;
        this.deviceBindingService = deviceBindingService;
    }

    /** Is this till tied to a device, and is the device making this call the one? Open to any signed-in user (the terminal asks it before showing itself). */
    @GetMapping("/registers/{id}/device-status")
    public DeviceStatusDto deviceStatus(@PathVariable Long id) {
        return deviceBindingService.status(id);
    }

    /** Ties the till to the device making this call (the admin sits at the till PC and presses "Bind to this device"). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/registers/{id}/device-binding")
    public RegisterDto bindDevice(@PathVariable Long id, @RequestBody(required = false) BindDeviceRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        return deviceBindingService.bind(id, request, principal.getUser());
    }

    /** Frees the till so it can be bound again. Works from any device — that is how a lost or replaced PC is dealt with. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @DeleteMapping("/registers/{id}/device-binding")
    public RegisterDto unbindDevice(@PathVariable Long id) {
        return deviceBindingService.unbind(id);
    }

    @GetMapping("/registers")
    public List<RegisterDto> listRegisters() {
        return registerService.listRegisters();
    }

    /** Register add/modify is ADMIN-only (Change 4) — anyone logged in can still list registers. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/registers")
    public RegisterDto createRegister(@Valid @RequestBody RegisterRequest request) {
        return registerService.createRegister(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PutMapping("/registers/{id}")
    public RegisterDto updateRegister(@PathVariable Long id, @Valid @RequestBody RegisterRequest request) {
        return registerService.updateRegister(id, request);
    }

    /** Opening/closing a shift is ADMIN-only (Change 3) — cashiers join an already-open shift with their PIN instead (see CashierSessionController). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/registers/{id}/sessions")
    public SessionDto openSession(@PathVariable Long id,
                                   @Valid @RequestBody OpenSessionRequest request,
                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return registerService.openSession(id, request, principal.getUser());
    }

    @GetMapping("/sessions")
    public org.springframework.http.ResponseEntity<List<SessionDto>> listSessions(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long registerId, @RequestParam(required = false) Long cashierId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(registerService.filteredSessions(q, status, registerId, cashierId, from, to), page, size);
    }

    @GetMapping("/sessions/{id}")
    public SessionDto getSession(@PathVariable Long id) {
        return registerService.getSession(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_MANAGE')")
    @PostMapping("/sessions/{id}/close")
    public SessionDto closeSession(@PathVariable Long id, @Valid @RequestBody CloseSessionRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return registerService.closeSession(id, request, principal.getUser());
    }

    @GetMapping("/sessions/{id}/report")
    public SessionReportDto sessionReport(@PathVariable Long id) {
        return registerService.sessionReport(id);
    }
}
