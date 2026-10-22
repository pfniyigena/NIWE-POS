package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateImportDtos.ImportResult;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** {@code /api/realestate/import}: bring leads and properties in from an Excel or CSV file. Managers and admins; the same file can be checked first (dryRun) and then imported. */
@RestController
@RequestMapping("/api/realestate/import")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class ReImportController {

    private final ReImportService service;

    public ReImportController(ReImportService service) {
        this.service = service;
    }

    @GetMapping("/{kind}/template")
    public ResponseEntity<byte[]> template(@PathVariable String kind, @RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? service.templateCsv(kind) : service.templateExcel(kind);
        String name = kind + "-import-template." + (csv ? "csv" : "xlsx");
        return ResponseEntity.ok().contentType(csv ? MediaType.parseMediaType("text/csv;charset=UTF-8") : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString()).body(body);
    }

    @PostMapping(value = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult run(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String kind, @RequestParam("file") MultipartFile file,
                            @RequestParam(defaultValue = "true") boolean dryRun, @RequestParam(defaultValue = "false") boolean listThem) {
        return service.run(RealEstateController.actor(me), kind, file, dryRun, listThem);
    }
}
