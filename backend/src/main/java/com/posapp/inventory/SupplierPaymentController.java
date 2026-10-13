package com.posapp.inventory;

import com.posapp.inventory.dto.OutstandingPurchaseOrderDto;
import com.posapp.inventory.dto.RecordSupplierPaymentRequest;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Add Supplier payment" — the accounts-payable mirror of {@code
 * com.posapp.credit.CustomerCreditController}. Per-supplier actions are nested under {@code
 * /api/suppliers/{supplierId}}, same shape as the customer side; every write here is ADMIN-only,
 * same "who settles" convention already established for customer payments.
 */
@RestController
@RequestMapping("/api/suppliers/{supplierId}")
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    public SupplierPaymentController(SupplierPaymentService supplierPaymentService) {
        this.supplierPaymentService = supplierPaymentService;
    }

    /** Supplier balance at a glance — mirrors CreditSummaryDto's use on the customer side, open to any logged-in user. */
    @GetMapping("/outstanding-balance")
    public java.math.BigDecimal outstandingBalance(@PathVariable Long supplierId) {
        return supplierPaymentService.outstandingBalance(supplierId);
    }

    @GetMapping("/outstanding-orders")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
    public List<OutstandingPurchaseOrderDto> outstandingOrders(@PathVariable Long supplierId) {
        return supplierPaymentService.listOutstandingOrders(supplierId);
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE')")
    public SupplierPaymentDto recordPayment(@PathVariable Long supplierId,
                                             @Valid @RequestBody RecordSupplierPaymentRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return supplierPaymentService.recordPayment(supplierId, request, principal.getUser());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW')")
    public List<SupplierPaymentDto> paymentHistory(@PathVariable Long supplierId) {
        return supplierPaymentService.paymentHistory(supplierId);
    }
}
