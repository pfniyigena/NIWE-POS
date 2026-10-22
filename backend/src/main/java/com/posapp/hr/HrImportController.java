package com.posapp.hr;

import com.posapp.hr.dto.HrImportDtos.ImportResult;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** {@code /api/hr/employees/import}: bring employees in from an Excel or CSV file. HR managers and admins; the same file can be checked first (dryRun) and then imported. */
@RestController
@RequestMapping("/api/hr/employees/import")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_MANAGE')")
public class HrImportController {

    private final HrImportService service;

    public HrImportController(HrImportService service) {
        this.service = service;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? service.templateCsv() : service.templateExcel();
        String name = "employees-import-template." + (csv ? "csv" : "xlsx");
        return ResponseEntity.ok().contentType(csv ? MediaType.parseMediaType("text/csv;charset=UTF-8") : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString()).body(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult run(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun,
                            @RequestParam(defaultValue = "false") boolean createDepartments) {
        return service.run(file, dryRun, createDepartments);
    }
}
