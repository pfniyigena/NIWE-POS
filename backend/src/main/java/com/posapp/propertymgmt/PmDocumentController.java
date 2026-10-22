package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtDocumentDtos.DocumentDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDocumentDtos.DocumentRequest;
import com.posapp.security.AppUserPrincipal;
import com.posapp.common.ApiException;
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

/** {@code /api/propertymgmt/documents}: files attached to Property Management records. Operations viewers read and download; Operations managers and admins add, change and delete. */
@RestController
@RequestMapping("/api/propertymgmt/documents")
public class PmDocumentController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmDocumentService service;

    public PmDocumentController(PmDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public List<DocumentDto> list(@RequestParam(required = false) PmDocSubject subjectType, @RequestParam(required = false) Long subjectId, @RequestParam(required = false) PmDocCategory category,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Boolean expiryOnly) {
        return service.list(subjectType, subjectId, category, q, expiryOnly);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public DocumentDto upload(@AuthenticationPrincipal AppUserPrincipal me, @RequestPart(value = "file", required = false) MultipartFile file, @RequestParam(required = false) PmDocSubject subjectType,
                              @RequestParam(required = false) Long subjectId, @RequestParam(required = false) String name, @RequestParam(required = false) PmDocCategory category,
                              @RequestParam(required = false) String description, @RequestParam(required = false) String expiryDate) throws IOException {
        LocalDate expiry = null;
        if (expiryDate != null && !expiryDate.isBlank()) {
            try {
                expiry = LocalDate.parse(expiryDate.trim());
            } catch (java.time.format.DateTimeParseException e) {
                throw ApiException.badRequest("The expiry date must be a date like 2027-03-31.");
            }
        }
        return service.upload(PropertyManagementController.actor(me), file, subjectType, subjectId, name, category, description, expiry);
    }

    @GetMapping("/{id}/file")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        PmDocumentService.FileData f = service.file(id);
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

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public DocumentDto update(@PathVariable Long id, @RequestBody DocumentRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
