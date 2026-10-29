package com.posapp.logging;

import com.posapp.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/** v1.14.0: administrators see who signed in, and who tried and failed. */
@RestController
@RequestMapping("/api/admin/login-log")
@PreAuthorize("hasRole('ADMIN')")
public class LoginLogController {
    private final LoginLogService service;

    public LoginLogController(LoginLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<LoginEventDto> search(@RequestParam(required = false) String q, @RequestParam(required = false) String outcome,
                                              @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Instant f = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant t = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1) : null;
        return service.search(q, outcome, f, t, page, size);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }
}
