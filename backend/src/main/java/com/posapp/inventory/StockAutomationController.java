package com.posapp.inventory;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/stock-automation")
@PreAuthorize("hasRole('ADMIN')")
public class StockAutomationController {

    private final StockAutomationService service;

    public StockAutomationController(StockAutomationService service) {
        this.service = service;
    }

    /** "Run now" on the Inventory settings screen: does today's jobs immediately (only those switched on). */
    @PostMapping("/run")
    public StockAutomationService.RunResult run() {
        return service.runNow();
    }
}
