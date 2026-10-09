package com.posapp.credit;

import com.posapp.credit.dto.CreditSummaryDto;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.credit.dto.OutstandingOrderDto;
import com.posapp.credit.dto.RecordPaymentRequest;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Every credit action for one customer, nested under {@code /api/customers/{customerId}}. Per
 * the user's explicit choice ("who settles" = ADMIN only), every write here — enabling/limiting
 * credit lives on CustomerController, but recording a payment lives here — is ADMIN-only.
 * {@link #creditSummary} is the deliberate exception: it's a read, not an action, and the
 * terminal/checkout need it for any logged-in cashier to decide whether/how much credit to offer.
 */
@RestController
@RequestMapping("/api/customers/{customerId}")
public class CustomerCreditController {

    private final CustomerCreditService customerCreditService;

    public CustomerCreditController(CustomerCreditService customerCreditService) {
        this.customerCreditService = customerCreditService;
    }

    /** Task: "Customer balance on POS screen" — open to any logged-in user. */
    @GetMapping("/credit-summary")
    public CreditSummaryDto creditSummary(@PathVariable Long customerId) {
        return customerCreditService.creditSummary(customerId);
    }

    /** Task: "Payment allocation to orders" — the Record Payment screen's allocation table. */
    @GetMapping("/outstanding-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OutstandingOrderDto> outstandingOrders(@PathVariable Long customerId) {
        return customerCreditService.listOutstandingOrders(customerId);
    }

    /** Task family: "Customer payment later" / "Partial settlement" / "Multiple payments against one order". */
    @PostMapping("/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerPaymentDto recordPayment(@PathVariable Long customerId,
                                             @Valid @RequestBody RecordPaymentRequest request,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return customerCreditService.recordPayment(customerId, request, principal.getUser());
    }

    /** Task: "Payment history". */
    @GetMapping("/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerPaymentDto> paymentHistory(@PathVariable Long customerId) {
        return customerCreditService.paymentHistory(customerId);
    }
}
