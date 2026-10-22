package com.posapp.hotel;

import com.posapp.common.ApiException;
import com.posapp.common.ExportFiles;
import com.posapp.common.ListPaging;
import com.posapp.hotel.dto.HotelDtos.*;
import com.posapp.hotel.dto.HotelRequests.*;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The guest bill's extras: numbered bills and bills to companies, moving charges, the fee previews, the company ledger, and the printed
 * bill / receipt. Reading needs the Hotel area (view); changing things needs manage.
 */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HotelBillingController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final HotelFolioService folioService;
    private final CompanyLedgerService ledgerService;
    private final HotelPdf pdf;
    private final StoreSettingsService storeSettingsService;
    private final ReportExportService exportService;

    public HotelBillingController(HotelFolioService folioService, CompanyLedgerService ledgerService, HotelPdf pdf,
                                  StoreSettingsService storeSettingsService, ReportExportService exportService) {
        this.folioService = folioService;
        this.ledgerService = ledgerService;
        this.pdf = pdf;
        this.storeSettingsService = storeSettingsService;
        this.exportService = exportService;
    }

    // ---- previews

    @GetMapping("/bookings/{id}/cancel-preview")
    public CancelPreviewDto cancelPreview(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean noShow) {
        return folioService.cancelPreview(id, noShow);
    }

    @GetMapping("/bookings/{id}/checkout-preview")
    public CheckoutPreviewDto checkoutPreview(@PathVariable Long id) {
        return folioService.checkoutPreview(id);
    }

    // ---- numbered bills

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/bills")
    public FolioDto addBill(@PathVariable Long id, @RequestBody(required = false) BillRequest request) {
        return folioService.createBill(id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/bookings/{id}/bills/{billNo}")
    public FolioDto updateBill(@PathVariable Long id, @PathVariable int billNo, @RequestBody BillRequest request) {
        return folioService.updateBill(id, billNo, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/bookings/{id}/bills/{billNo}")
    public FolioDto deleteBill(@PathVariable Long id, @PathVariable int billNo) {
        return folioService.deleteBill(id, billNo);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/bills/{billNo}/room-nights")
    public FolioDto roomNights(@PathVariable Long id, @PathVariable int billNo) {
        return folioService.setRoomBill(id, billNo);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/charges/{chargeId}/assign")
    public FolioDto assign(@PathVariable Long id, @PathVariable Long chargeId, @Valid @RequestBody AssignChargeRequest request) {
        return folioService.assignCharge(id, chargeId, request.billNo());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/charges/{chargeId}/move")
    public FolioDto move(@PathVariable Long id, @PathVariable Long chargeId, @Valid @RequestBody MoveChargeRequest request) {
        return folioService.moveCharge(id, chargeId, request.targetBookingId(), request.reason());
    }

    // ---- printed documents

    @GetMapping("/bookings/{id}/bill/pdf")
    public ResponseEntity<byte[]> billPdf(@PathVariable Long id, @RequestParam(defaultValue = "0") int bill) {
        FolioDto folio = folioService.folio(id);
        if (bill < 0 || (bill > 0 && folio.bills().stream().noneMatch(b -> b.billNo() == bill))) throw ApiException.notFound("Bill " + bill + " does not exist");
        BillDto b = bill == 0 ? null : folio.bills().stream().filter(x -> x.billNo() == bill).findFirst().orElse(null);
        String number = b == null ? null : b.companyBillNumber();
        byte[] bytes = pdf.bill(folio, bill, b == null ? null : b.companyName(), number, storeSettingsService.getSettingsEntity());
        return pdfResponse(bytes, folio.booking().bookingNumber() + (bill > 0 ? "-bill" + bill : "-bill") + ".pdf");
    }

    @GetMapping("/bookings/{id}/payments/{paymentId}/receipt/pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long id, @PathVariable Long paymentId) {
        FolioDto folio = folioService.folio(id);
        FolioPaymentDto p = folio.payments().stream().filter(x -> x.id().equals(paymentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Payment not found: " + paymentId));
        byte[] bytes = pdf.receipt(p, folio.booking().bookingNumber(), folio.booking().guestName(), storeSettingsService.getSettingsEntity());
        return pdfResponse(bytes, String.format("receipt-R-%06d.pdf", p.id()));
    }

    @GetMapping("/company-bills/{id}/invoice/pdf")
    public ResponseEntity<byte[]> companyInvoicePdf(@PathVariable Long id) {
        CompanyBillDto cb = ledgerService.get(id);
        FolioDto folio = folioService.folio(cb.bookingId());
        byte[] bytes = pdf.bill(folio, cb.billNo(), cb.companyName(), cb.billNumber(), storeSettingsService.getSettingsEntity());
        return pdfResponse(bytes, cb.billNumber() + ".pdf");
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(bytes);
    }

    // ---- company ledger

    @GetMapping("/company-bills")
    public ResponseEntity<List<CompanyBillDto>> companyBills(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) Long companyId,
                                                             @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(ledgerService.list(q, status, companyId), page, size);
    }

    @GetMapping("/company-bills/export")
    public ResponseEntity<byte[]> exportCompanyBills(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long companyId, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (CompanyBillDto b : ledgerService.list(q, status, companyId)) {
            body.add(List.of(HotelExportController.s(b.billNumber()), HotelExportController.s(b.companyName()), HotelExportController.s(b.bookingNumber()),
                    HotelExportController.s(b.guestName()), HotelExportController.s(b.issuedOn()), HotelExportController.s(b.dueDate()),
                    HotelExportController.num(b.amount()), HotelExportController.num(b.paid()), HotelExportController.num(b.outstanding()),
                    b.overdue() ? "Overdue" : "OPEN".equals(b.status()) ? "Open" : "Paid"));
        }
        return ExportFiles.file(exportService, HotelExportController.title("Company bills", status == null || status.isBlank() ? null : "status " + status.toLowerCase(),
                HotelExportController.search(q)), "company-bills",
                List.of("Invoice", "Company", "Booking", "Guest", "Issued", "Due", "Amount", "Received", "Outstanding", "Status"), body, format);
    }

    @GetMapping("/company-bills/{id}")
    public CompanyBillDto companyBill(@PathVariable Long id) {
        return ledgerService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/company-bills/{id}/receipts")
    public CompanyBillDto receive(@PathVariable Long id, @Valid @RequestBody CompanyReceiptRequest request) {
        return ledgerService.receive(id, request);
    }

    @GetMapping("/companies")
    public List<CompanyDto> companies() {
        return ledgerService.companies();
    }

    @GetMapping("/company-summary")
    public List<CompanySummaryDto> companySummary() {
        return ledgerService.summary();
    }
}
