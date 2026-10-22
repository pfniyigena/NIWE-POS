package com.posapp.realestate;

import com.posapp.realestate.RePrintService.Printed;
import com.posapp.realestate.dto.RealEstatePrintDtos.PaymentRow;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@code /api/realestate/print}: the printed papers of Real Estate as A4 PDFs (offer letter, reservation letter, sale agreement, receipts, project price list, property
 * flyer). Anyone who can see Real Estate can print them.
 */
@RestController
@RequestMapping("/api/realestate/print")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class RePrintController {

    private final RePrintService service;

    public RePrintController(RePrintService service) {
        this.service = service;
    }

    private static ResponseEntity<byte[]> pdf(Printed p) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    @GetMapping("/deals/{id}/offer")
    public ResponseEntity<byte[]> dealOffer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.dealOffer(RealEstateController.actor(me), id));
    }

    @GetMapping("/deals/{id}/reservation")
    public ResponseEntity<byte[]> dealReservation(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.dealReservation(RealEstateController.actor(me), id));
    }

    @GetMapping("/deals/{id}/agreement")
    public ResponseEntity<byte[]> dealAgreement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.dealAgreement(RealEstateController.actor(me), id));
    }

    @GetMapping("/deals/{id}/payments")
    public List<PaymentRow> dealPayments(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.dealPayments(RealEstateController.actor(me), id);
    }

    @GetMapping("/deals/{id}/payments/{paymentId}/receipt")
    public ResponseEntity<byte[]> dealReceipt(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long paymentId) {
        return pdf(service.dealReceipt(RealEstateController.actor(me), id, paymentId));
    }

    @GetMapping("/unit-sales/{id}/reservation")
    public ResponseEntity<byte[]> saleReservation(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.saleReservation(RealEstateController.actor(me), id));
    }

    @GetMapping("/unit-sales/{id}/agreement")
    public ResponseEntity<byte[]> saleAgreement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.saleAgreement(RealEstateController.actor(me), id));
    }

    @GetMapping("/unit-sales/{id}/payments")
    public List<PaymentRow> salePayments(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.salePayments(RealEstateController.actor(me), id);
    }

    @GetMapping("/unit-sales/{id}/payments/{paymentId}/receipt")
    public ResponseEntity<byte[]> saleReceipt(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long paymentId) {
        return pdf(service.saleReceipt(RealEstateController.actor(me), id, paymentId));
    }

    @GetMapping("/unit-sales/{id}/statement")
    public ResponseEntity<byte[]> saleStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.statement(RealEstateController.actor(me), id));
    }

    @GetMapping("/projects/{id}/price-list")
    public ResponseEntity<byte[]> priceList(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean all) {
        return pdf(service.priceList(id, all));
    }

    @GetMapping("/properties/{id}/flyer")
    public ResponseEntity<byte[]> flyer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(service.flyer(RealEstateController.actor(me), id));
    }
}
