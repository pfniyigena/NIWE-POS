package com.posapp.credit;

import com.posapp.credit.dto.CustomerPaymentDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Task: "Payment receipt" — reprinting a specific past payment's receipt by its own id. Also
 * backs the new store-wide Customer Payments list screen (Task: "Add Sale order and Customer
 * payment... Let have Sale as Menu"), mirroring the accounts-payable side's {@code
 * SupplierPaymentListController}.
 */
@RestController
@RequestMapping("/api/customer-payments")
public class CustomerPaymentController {

    private final CustomerCreditService customerCreditService;

    public CustomerPaymentController(CustomerCreditService customerCreditService) {
        this.customerCreditService = customerCreditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_BILLING_VIEW')")
    public List<CustomerPaymentDto> allPayments() {
        return customerCreditService.paymentHistory();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_BILLING_VIEW')")
    public CustomerPaymentDto getReceipt(@PathVariable Long id) {
        return customerCreditService.getPaymentReceipt(id);
    }

    /** Fourier: "Change payment receipt printout. Make a PDF with A4" — see CustomerCreditService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_BILLING_VIEW')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = customerCreditService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
