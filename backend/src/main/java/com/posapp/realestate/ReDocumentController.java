package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateDocDtos.DocumentDto;
import com.posapp.realestate.dto.RealEstateDocDtos.ValuationDto;
import com.posapp.realestate.dto.RealEstateDocRequests.DocumentRequest;
import com.posapp.realestate.dto.RealEstateDocRequests.ValuationRequest;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** {@code /api/realestate}: documents attached to Real Estate records, and valuations of properties. Same access as the rest of Real Estate. */
@RestController
@RequestMapping("/api/realestate")
public class ReDocumentController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReDocumentService documentService;
    private final ReValuationService valuationService;

    public ReDocumentController(ReDocumentService documentService, ReValuationService valuationService) {
        this.documentService = documentService;
        this.valuationService = valuationService;
    }

    // ---- documents

    @GetMapping("/documents")
    @PreAuthorize(VIEW)
    public List<DocumentDto> documents(@RequestParam(required = false) DocSubject subjectType, @RequestParam(required = false) Long subjectId, @RequestParam(required = false) ReDocCategory category,
                                       @RequestParam(required = false) String q, @RequestParam(required = false) Boolean expiryOnly) {
        return documentService.list(subjectType, subjectId, category, q, expiryOnly);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public DocumentDto upload(@AuthenticationPrincipal AppUserPrincipal me, @RequestPart(value = "file", required = false) MultipartFile file, @RequestParam(required = false) DocSubject subjectType,
                              @RequestParam(required = false) Long subjectId, @RequestParam(required = false) String name, @RequestParam(required = false) ReDocCategory category,
                              @RequestParam(required = false) String description, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) throws IOException {
        return documentService.upload(RealEstateController.actor(me), file, subjectType, subjectId, name, category, description, expiryDate);
    }

    @GetMapping("/documents/{id}/file")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        ReDocumentService.FileData f = documentService.file(id);
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

    @PutMapping("/documents/{id}")
    @PreAuthorize(MANAGE)
    public DocumentDto updateDocument(@PathVariable Long id, @RequestBody DocumentRequest r) {
        return documentService.update(id, r);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize(MANAGE)
    public void deleteDocument(@PathVariable Long id) {
        documentService.delete(id);
    }

    // ---- valuations

    @GetMapping("/valuations")
    @PreAuthorize(VIEW)
    public List<ValuationDto> valuations(@RequestParam(required = false) Long propertyId, @RequestParam(required = false) Boolean currentOnly, @RequestParam(required = false) Boolean expiredOnly) {
        return valuationService.list(propertyId, currentOnly, expiredOnly);
    }

    @GetMapping("/valuations/{id}")
    @PreAuthorize(VIEW)
    public ValuationDto valuation(@PathVariable Long id) {
        return valuationService.get(id);
    }

    @PostMapping("/valuations")
    @PreAuthorize(MANAGE)
    public ValuationDto createValuation(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ValuationRequest r) {
        return valuationService.create(RealEstateController.actor(me), r);
    }

    @PutMapping("/valuations/{id}")
    @PreAuthorize(MANAGE)
    public ValuationDto updateValuation(@PathVariable Long id, @RequestBody ValuationRequest r) {
        return valuationService.update(id, r);
    }

    @DeleteMapping("/valuations/{id}")
    @PreAuthorize(MANAGE)
    public void deleteValuation(@PathVariable Long id) {
        valuationService.delete(id);
    }
}
