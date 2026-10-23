package com.posapp.returns;
import java.math.BigDecimal;

import com.posapp.returns.dto.ProcessReturnRequest;
import com.posapp.returns.dto.SalesReturnDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    private final ReturnsService returnsService;

    public ReturnsController(ReturnsService returnsService) {
        this.returnsService = returnsService;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<SalesReturnDto>> listReturns(@RequestParam(required = false) String q, @RequestParam(required = false) String method,
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(returnsService.filteredReturns(q, method, registerId, from, to), page, size);
    }

    /** Task: "print out the return" — one return's own detail (including its lines) for the return receipt. */
    @GetMapping("/{id}")
    public SalesReturnDto getReturn(@PathVariable Long id) {
        return returnsService.getById(id);
    }

    /**
     * Task: "on the receipt which was returned, I should mark it as returned" — the sale receipt
     * calls this alongside its own order fetch to know which lines (and how much of each) have
     * been returned. Keyed by orderLineId; a line with nothing returned against it is simply
     * absent from the map, not present with a zero.
     */
    @GetMapping("/order/{orderId}")
    public Map<Long, BigDecimal> returnedQuantitiesForOrder(@PathVariable Long orderId) {
        return returnsService.returnedQuantitiesForOrder(orderId);
    }

    @PostMapping
    public SalesReturnDto processReturn(@Valid @RequestBody ProcessReturnRequest request, @org.springframework.security.core.annotation.AuthenticationPrincipal com.posapp.security.AppUserPrincipal principal) {
        SalesReturnDto processed = returnsService.processReturn(request, principal.getUser());
        return returnsService.submitToEfrisIfApplicable(processed.id());
    }

    /** Void a completed sale (refund it in full and keep it on record, marked voided). */
    @PostMapping("/void-sale")
    public SalesReturnDto voidSale(@Valid @RequestBody com.posapp.returns.dto.VoidSaleRequest request, @org.springframework.security.core.annotation.AuthenticationPrincipal com.posapp.security.AppUserPrincipal principal) {
        SalesReturnDto processed = returnsService.voidSale(request, principal.getUser());
        return returnsService.submitToEfrisIfApplicable(processed.id());
    }

    /**
     * Task: "The return will also integrate with EFRIS" — the return receipt's own "Retry EFRIS
     * submission" button, exact same shape as {@code OrderController.retryEfris}: just re-runs
     * {@link ReturnsService#submitToEfrisIfApplicable}, whose own guards (already fiscalized,
     * EFRIS not enabled, no connection configured) already make a repeat call safe.
     */
    @PostMapping("/{id}/efris/retry")
    public SalesReturnDto retryEfris(@PathVariable Long id) {
        return returnsService.submitToEfrisIfApplicable(id);
    }
}
