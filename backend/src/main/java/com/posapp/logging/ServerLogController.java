package com.posapp.logging;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/** v1.14.0: administrators browse and download the server's log files. */
@RestController
@RequestMapping("/api/admin/server-logs")
@PreAuthorize("hasRole('ADMIN')")
public class ServerLogController {
    private final ServerLogService service;

    public ServerLogController(ServerLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServerLogService.LogFile> files() {
        return service.list();
    }

    @GetMapping("/{name}")
    public ServerLogService.Tail tail(@PathVariable String name, @RequestParam(defaultValue = "300") int lines,
                                      @RequestParam(required = false) String level, @RequestParam(required = false) String q) {
        return service.tail(name, lines, level, q);
    }

    @GetMapping("/{name}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String name) {
        Path p = service.resolve(name);
        service.auditDownload(name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body(new FileSystemResource(p));
    }
}
