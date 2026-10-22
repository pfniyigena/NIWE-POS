package com.posapp.hotel;

import com.posapp.common.ApiException;
import com.posapp.common.ExportFiles;
import com.posapp.common.ListPaging;
import com.posapp.hotel.dto.GuestDtos.*;
import com.posapp.hotel.dto.GuestRequests.MergeRequest;
import com.posapp.hotel.dto.HotelDtos.FolioDto;
import com.posapp.hotel.dto.HotelDtos.GuestDto;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The guest register (HO-6): a guest's history and spend, duplicates and merging, ID document uploads, the registration card and
 * confirmation letter, and the police / immigration register. Reading needs the Hotel area (view); changing things needs manage.
 */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class GuestRegisterController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final GuestProfileService profileService;
    private final GuestDocumentService documentService;
    private final GuestService guestService;
    private final HotelFolioService folioService;
    private final HotelSettingsService settingsService;
    private final HotelPdf pdf;
    private final StoreSettingsService storeSettingsService;
    private final ReportExportService exportService;

    public GuestRegisterController(GuestProfileService profileService, GuestDocumentService documentService, GuestService guestService, HotelFolioService folioService,
                                   HotelSettingsService settingsService, HotelPdf pdf, StoreSettingsService storeSettingsService, ReportExportService exportService) {
        this.profileService = profileService;
        this.documentService = documentService;
        this.guestService = guestService;
        this.folioService = folioService;
        this.settingsService = settingsService;
        this.pdf = pdf;
        this.storeSettingsService = storeSettingsService;
        this.exportService = exportService;
    }

    // ---- history, duplicates, merge

    @GetMapping("/guests/{id}/profile")
    public GuestProfileDto profile(@PathVariable Long id) {
        return profileService.profile(id);
    }

    @GetMapping("/guests/duplicates")
    public List<DuplicateClusterDto> duplicates() {
        return profileService.duplicates();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/guests/{id}/merge")
    public MergeResultDto merge(@PathVariable Long id, @RequestBody MergeRequest request) {
        return profileService.merge(id, request == null ? null : request.duplicateId());
    }

    // ---- ID documents

    @GetMapping("/guests/{id}/documents")
    public List<GuestDocumentDto> documents(@PathVariable Long id) {
        return documentService.list(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/guests/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GuestDocumentDto upload(@PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file, @RequestParam(required = false) String docType,
                                   @RequestParam(required = false) String name, @RequestParam(required = false) String docNumber,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) throws IOException {
        return documentService.upload(id, file, docType, name, docNumber, expiryDate);
    }

    @GetMapping("/guest-documents/{id}/file")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id) {
        GuestDocumentService.FileData f = documentService.file(id);
        MediaType type;
        try {
            type = MediaType.parseMediaType(f.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(type)
                .contentLength(f.data().length)
                .body(f.data());
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/guest-documents/{id}")
    public void deleteDocument(@PathVariable Long id) {
        documentService.delete(id);
    }

    // ---- printed documents

    @GetMapping("/bookings/{id}/registration-card/pdf")
    public ResponseEntity<byte[]> registrationCard(@PathVariable Long id) {
        FolioDto folio = folioService.folio(id);
        if ("CANCELLED".equals(folio.booking().status()) || "NO_SHOW".equals(folio.booking().status())) {
            throw ApiException.badRequest("A cancelled booking has no registration card");
        }
        GuestDto g = guestService.get(folio.booking().guestId());
        byte[] bytes = pdf.registrationCard(folio.booking(), g, settingsService.get(), storeSettingsService.getSettingsEntity());
        return pdfResponse(bytes, folio.booking().bookingNumber() + "-registration-card.pdf");
    }

    @GetMapping("/bookings/{id}/confirmation/pdf")
    public ResponseEntity<byte[]> confirmation(@PathVariable Long id) {
        FolioDto folio = folioService.folio(id);
        String status = folio.booking().status();
        if (!"BOOKED".equals(status) && !"CHECKED_IN".equals(status)) {
            throw ApiException.badRequest("A confirmation can be printed only for a booking that is waiting for the guest or in the house");
        }
        GuestDto g = guestService.get(folio.booking().guestId());
        byte[] bytes = pdf.confirmationLetter(folio, g, settingsService.get(), storeSettingsService.getSettingsEntity());
        return pdfResponse(bytes, folio.booking().bookingNumber() + "-confirmation.pdf");
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(bytes);
    }

    // ---- police / immigration register

    @GetMapping("/guest-register")
    public ResponseEntity<List<RegisterRowDto>> register(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @RequestParam(required = false) String q, @RequestParam(required = false) String nationality,
                                                         @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(profileService.register(from, to, q, nationality), page, size);
    }

    @GetMapping("/guest-register/export")
    public ResponseEntity<byte[]> registerExport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String q, @RequestParam(required = false) String nationality,
                                                 @RequestParam(defaultValue = "xlsx") String format) {
        List<RegisterRowDto> rows = profileService.register(from, to, q, nationality);
        List<List<String>> body = new ArrayList<>();
        int n = 0;
        for (RegisterRowDto r : rows) {
            body.add(List.of(String.valueOf(++n), HotelExportController.s(r.fullName()), HotelExportController.s(r.nationality()), HotelExportController.s(r.idType()),
                    HotelExportController.s(r.idNumber()), HotelExportController.s(r.dateOfBirth()), HotelExportController.s(r.address()), HotelExportController.s(r.profession()),
                    HotelExportController.s(r.arrival()), HotelExportController.s(r.departure()), HotelExportController.s(r.roomNumber()),
                    HotelExportController.s(r.bookingNumber()), ""));
        }
        String period = (from == null ? "" : from.toString()) + (to == null ? "" : " to " + to);
        return ExportFiles.file(exportService, HotelExportController.title("Guest register", period.isBlank() ? null : period, HotelExportController.search(q)), "guest-register",
                List.of("No.", "Full name", "Nationality", "ID type", "ID number", "Date of birth", "Address", "Profession", "Arrival", "Departure", "Room", "Booking", "Signature"),
                body, format);
    }
}
