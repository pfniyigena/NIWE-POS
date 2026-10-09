package com.posapp.returns;

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
    public List<SalesReturnDto> listReturns() {
        return returnsService.listReturns();
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
    public Map<Long, Integer> returnedQuantitiesForOrder(@PathVariable Long orderId) {
        return returnsService.returnedQuantitiesForOrder(orderId);
    }

    @PostMapping
    public SalesReturnDto processReturn(@Valid @RequestBody ProcessReturnRequest request) {
        SalesReturnDto processed = returnsService.processReturn(request);
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
