package com.posapp.backup;

import com.posapp.backup.dto.BackupFileDto;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin > Data > Backup & Restore (Fourier: "no backup/restore ... anywhere in the backend").
 * ADMIN-only end to end, same tier as Reports/Audit Log — this is "what if the server dies"
 * territory, not something any cashier role needs to see. Restoring is the one genuinely
 * dangerous endpoint here: it replaces the *entire* current database with whatever the chosen
 * backup holds. The frontend gates it behind a strongly-worded confirm() (same confirm-dialog
 * convention Stock Adjustment confirm/reject already use for a destructive action), and the UI
 * recommends restarting the app right after — see BackupService's own javadoc for why.
 * <p>
 * While a restore is running, both {@code restoreFromStored} and {@code restoreFromUpload} below
 * put the whole app into maintenance mode (see BackupService's {@code beginRestore}/
 * {@code MaintenanceModeService}/{@code MaintenanceModeFilter}) — every other request, from any
 * user, gets a flat 503 until the restore finishes.
 */
@RestController
@RequestMapping("/api/backups")
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public List<BackupFileDto> list() {
        return backupService.listBackups();
    }

    @PostMapping
    public BackupFileDto createNow() {
        return backupService.createBackup();
    }

    @GetMapping("/{filename}/download")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        byte[] content = backupService.readBackup(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    @PostMapping("/{filename}/restore")
    public void restoreFromStored(@PathVariable String filename) {
        backupService.restoreFromStoredBackup(filename);
    }

    /** Restoring from a file the admin just uploaded (e.g. downloaded earlier, or brought over from another machine) rather than one already sitting in app.backup.directory. */
    @PostMapping(value = "/restore-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void restoreFromUpload(@RequestParam("file") MultipartFile file) {
        backupService.restoreFromUploadedFile(file);
    }

    @DeleteMapping("/{filename}")
    public void delete(@PathVariable String filename) {
        backupService.deleteBackup(filename);
    }
}
