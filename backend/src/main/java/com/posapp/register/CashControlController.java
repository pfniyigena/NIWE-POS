package com.posapp.register;

import com.posapp.common.ListPaging;
import com.posapp.register.dto.CashMovementDto;
import com.posapp.register.dto.CashMovementRequest;
import com.posapp.register.dto.CashSummaryDto;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cash paid in, paid out and dropped into the safe during a shift, and counts made along the way. Open to anyone working on the shift (the
 * service checks), with a manager's PIN for a large paid-out; voiding an entry is for managers.
 */
@RestController
@RequestMapping("/api")
public class CashControlController {

    private final CashControlService service;

    public CashControlController(CashControlService service) {
        this.service = service;
    }

    @GetMapping("/sessions/{id}/cash")
    public CashSummaryDto summary(@PathVariable Long id) {
        return service.summary(id);
    }

    @PostMapping("/sessions/{id}/cash-movements")
    public CashMovementDto record(@PathVariable Long id, @RequestBody CashMovementRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.record(id, request, principal.getUser());
    }

    @PostMapping("/cash-movements/{id}/void")
    public CashMovementDto voidMovement(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AppUserPrincipal principal) {
        return service.voidMovement(id, body == null ? null : body.get("reason"), principal.getUser());
    }

    /** The reasons offered for each kind of movement, so the till shows the same choices the server accepts. */
    @GetMapping("/cash-movements/reasons")
    public Map<String, Map<String, String>> reasons() {
        return Map.of("PAID_IN", CashControlService.IN_REASONS, "PAID_OUT", CashControlService.OUT_REASONS, "DROP", CashControlService.DROP_REASONS);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_SALES_MANAGE')")
    @GetMapping("/cash-movements")
    public ResponseEntity<List<CashMovementDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long registerId,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(service.filtered(q, status, registerId, from, to), page, size);
    }
}
