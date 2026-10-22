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

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
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
