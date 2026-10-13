package com.posapp.warehouse;

import com.posapp.warehouse.dto.StockTransferDto;
import com.posapp.warehouse.dto.StockTransferReceiveRequest;
import com.posapp.warehouse.dto.StockTransferRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only for listing/logging, same tier as {@code StockAdjustmentController} — see that
 * class's own javadoc for why. {@link #confirm}/{@link #reject} keep their own additive override,
 * letting an ADMIN delegate just the sign-off step to someone granted {@code
 * Permission.APPROVE_STOCK_TRANSFER}.
 */
@RestController
@RequestMapping("/api/stock-transfers")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @GetMapping
    public List<StockTransferDto> listAll() {
        return stockTransferService.listAll();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public StockTransferDto create(@Valid @RequestBody StockTransferRequest request) {
        return stockTransferService.create(request);
    }

    /** ADMIN or PERM_APPROVE_STOCK_TRANSFER — moves a PENDING entry to CONFIRMED, which is what actually moves the stock. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_TRANSFER')")
    @PostMapping("/{id}/confirm")
    public StockTransferDto confirm(@PathVariable Long id) {
        return stockTransferService.confirm(id);
    }

    /** ADMIN or PERM_APPROVE_STOCK_TRANSFER — the receiving branch confirms an in-transit transfer arrived (possibly short). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_TRANSFER')")
    @PostMapping("/{id}/receive")
    public StockTransferDto receive(@PathVariable Long id, @Valid @RequestBody StockTransferReceiveRequest request) {
        return stockTransferService.receive(id, request);
    }

    /** ADMIN or PERM_APPROVE_STOCK_TRANSFER — discards a PENDING entry (e.g. logged in error) with no stock impact. */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_TRANSFER')")
    @PostMapping("/{id}/reject")
    public StockTransferDto reject(@PathVariable Long id) {
        return stockTransferService.reject(id);
    }
}
