package com.posapp.paymentmethod;

import com.posapp.paymentmethod.dto.PaymentMethodDto;
import com.posapp.paymentmethod.dto.PaymentMethodRequest;
import com.posapp.paymentmethod.dto.UpdatePaymentMethodRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    /** Active methods for the checkout/refund dropdowns — open to any logged-in user. */
    @GetMapping
    public List<PaymentMethodDto> listActive() {
        return paymentMethodService.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentMethodDto> listAll() {
        return paymentMethodService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentMethodDto create(@Valid @RequestBody PaymentMethodRequest request) {
        return paymentMethodService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentMethodDto update(@PathVariable Long id, @Valid @RequestBody UpdatePaymentMethodRequest request) {
        return paymentMethodService.update(id, request);
    }
}
