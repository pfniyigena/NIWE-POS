package com.posapp.inventory;

import com.posapp.inventory.dto.StockAdjustmentDto;
import com.posapp.inventory.dto.StockAdjustmentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only for listing/logging, same as before Fourier's "Build a full permissions system" —
 * unlike Restaurant Wastage, a general Inventory correction was never meant to be staff-loggable
 * (see this class's own earlier javadoc: "same tier as deactivating a product"), so that part is
 * back to a plain class-level {@code @PreAuthorize}. {@link #confirm}/{@link #reject} below keep
 * their own additive override, letting an ADMIN delegate just the sign-off step — not the logging
 * step — to someone granted {@code Permission.APPROVE_STOCK_ADJUSTMENT}, a maker-checker split
 * between whoever logs a correction and whoever approves it.
 */
@RestController
@RequestMapping("/api/stock-adjustments")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;

    public StockAdjustmentController(StockAdjustmentService stockAdjustmentService) {
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @GetMapping
    public List<StockAdjustmentDto> listAll() {
        return stockAdjustmentService.listAll();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public StockAdjustmentDto create(@Valid @RequestBody StockAdjustmentRequest request) {
        return stockAdjustmentService.create(request);
    }

    /** ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT — moves a PENDING entry to CONFIRMED, which is what actually applies the stock change and posts to Accounting. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_ADJUSTMENT')")
    @PostMapping("/{id}/confirm")
    public StockAdjustmentDto confirm(@PathVariable Long id) {
        return stockAdjustmentService.confirm(id);
    }

    /** ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT — discards a PENDING entry (e.g. logged in error) with no stock/Accounting impact. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_ADJUSTMENT')")
    @PostMapping("/{id}/reject")
    public StockAdjustmentDto reject(@PathVariable Long id) {
        return stockAdjustmentService.reject(id);
    }
}
