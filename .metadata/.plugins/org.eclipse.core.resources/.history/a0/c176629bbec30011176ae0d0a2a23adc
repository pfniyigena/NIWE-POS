package com.posapp.salesorder;

import com.posapp.invoice.InvoiceService;
import com.posapp.salesorder.dto.FulfillSalesOrderRequest;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only throughout, same tier as Purchase Orders — this is a back-office order-taking flow,
 * not a terminal one.
 *
 * <p>Fourier: "Once Sale order is FULFILLED it will create automatically a draft invoice" — {@link
 * #fulfill} is where that's wired in, at the controller layer rather than inside {@code
 * SalesOrderService} itself: {@code InvoiceService} already depends on {@code SalesOrderService}
 * (to read a sales order's own DTO when generating its invoice — see {@code InvoiceService
 * .generate}'s javadoc), so {@code SalesOrderService} depending on {@code InvoiceService} back
 * would be a circular Spring bean dependency; a controller composing both services has no such
 * restriction. Guarded so this only ever fires once per order — {@code getBySalesOrderIdOrNull}
 * comes back non-null the moment either this auto-draft or an admin's own "Generate invoice" click
 * has already created one, and {@code InvoiceService.generate}/{@code generateDraftFromSalesOrder}
 * both refuse a second invoice for the same order anyway (see that service's own javadoc) — this
 * check just avoids throwing on every subsequent fulfillment call against an order that's already
 * fully fulfilled and invoiced.
 */
@RestController
@RequestMapping("/api/sales-orders")
@PreAuthorize("hasRole('ADMIN')")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final InvoiceService invoiceService;

    public SalesOrderController(SalesOrderService salesOrderService, InvoiceService invoiceService) {
        this.salesOrderService = salesOrderService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public List<SalesOrderDto> listAll() {
        return salesOrderService.listAll();
    }

    @GetMapping("/{id}")
    public SalesOrderDto getById(@PathVariable Long id) {
        return salesOrderService.getById(id);
    }

    /** Fourier: "add print (PDF with A4)" — see SalesOrderService.pdf's javadoc. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = salesOrderService.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    @PostMapping
    public SalesOrderDto create(@Valid @RequestBody SalesOrderRequest request) {
        return salesOrderService.createDraft(request);
    }

    @PutMapping("/{id}")
    public SalesOrderDto update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return salesOrderService.updateDraft(id, request);
    }

    @PostMapping("/{id}/confirm")
    public SalesOrderDto confirm(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean override) {
        return salesOrderService.confirm(id, override);
    }

    @PostMapping("/{id}/cancel")
    public SalesOrderDto cancel(@PathVariable Long id) {
        return salesOrderService.cancel(id);
    }

    @PostMapping("/{id}/fulfill")
    public SalesOrderDto fulfill(@PathVariable Long id, @Valid @RequestBody FulfillSalesOrderRequest request) {
        SalesOrderDto so = salesOrderService.fulfillLines(id, request);
        // Fourier: "Once Sale order is FULFILLED it will create automatically a draft invoice" — see class javadoc.
        if ("FULFILLED".equals(so.status()) && invoiceService.getBySalesOrderIdOrNull(id) == null) {
            invoiceService.generateDraftFromSalesOrder(id);
        }
        return so;
    }
}
