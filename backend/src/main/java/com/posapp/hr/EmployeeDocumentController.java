package com.posapp.hr;

import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDocDtos.*;
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
import java.util.List;

/** Employee documents. Viewing needs the HR area; adding, changing and deleting need HR manage. Under /api/hr/employees so the Employees license gate applies. */
@RestController
@RequestMapping("/api/hr/employees")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
public class EmployeeDocumentController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HR_MANAGE')";

    private final EmployeeDocumentService service;

    public EmployeeDocumentController(EmployeeDocumentService service) {
        this.service = service;
    }

    /** Every document (filters: search, category, state = EXPIRED / SOON / WITH_EXPIRY, one employee). */
    @GetMapping("/documents")
    public List<EmployeeDocumentDto> search(@RequestParam(required = false) String q, @RequestParam(required = false) EmployeeDocCategory category,
                                            @RequestParam(required = false) String state, @RequestParam(required = false) Long employeeId) {
        return service.search(q, category, state, employeeId);
    }

    @GetMapping("/documents/summary")
    public DocumentSummaryDto summary() {
        return service.summary();
    }

    @GetMapping("/{id}/documents")
    public List<EmployeeDocumentDto> forEmployee(@PathVariable Long id) {
        return service.forEmployee(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeDocumentDto upload(@PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file, @RequestParam(required = false) String name,
                                      @RequestParam(required = false) EmployeeDocCategory category, @RequestParam(required = false) String description,
                                      @RequestParam(required = false) String expiryDate, @RequestParam(defaultValue = "false") boolean shared) throws IOException {
        LocalDate expiry = null;
        if (expiryDate != null && !expiryDate.isBlank()) {
            try {
                expiry = LocalDate.parse(expiryDate.trim());
            } catch (java.time.format.DateTimeParseException e) {
                throw ApiException.badRequest("The expiry date must be a date like 2027-03-31.");
            }
        }
        return service.upload(id, file, name, category, description, expiry, shared);
    }

    @GetMapping("/documents/{docId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long docId) {
        return download(service.file(docId));
    }

    static ResponseEntity<byte[]> download(EmployeeDocumentService.FileData f) {
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
    @PutMapping("/documents/{docId}")
    public EmployeeDocumentDto update(@PathVariable Long docId, @RequestBody EmployeeDocumentRequest request) {
        return service.update(docId, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/documents/{docId}")
    public void delete(@PathVariable Long docId) {
        service.delete(docId);
    }
}
