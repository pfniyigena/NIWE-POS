package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.LawDocumentService.Download;
import com.posapp.law.dto.LawDocumentDtos.*;
import com.posapp.law.dto.LawDocumentRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Documents on matters and the templates letters are made from. What a person can do with a document follows the matter's access rules and
 * the confidential mark (see {@link LawDocumentService}); the templates are managed by an admin or an Operations manager.
 */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawDocumentController {

    private final LawDocumentService service;

    public LawDocumentController(LawDocumentService service) {
        this.service = service;
    }

    private static Set<String> auth(AppUserPrincipal me) {
        return me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    private static boolean canManageTemplates(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        return auth.contains("ROLE_ADMIN") || auth.contains("AREA_OPERATIONS_MANAGE");
    }

    private static ResponseEntity<byte[]> file(Download d) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(d.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(type)
                .contentLength(d.data().length)
                .body(d.data());
    }

    // ---- documents

    @GetMapping("/matters/{id}/documents")
    public DocListDto list(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam(defaultValue = "false") boolean archived) {
        return service.list(actor(me), id, archived);
    }

    @PostMapping(value = "/matters/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocDto upload(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file,
                         @RequestParam(required = false) String name, @RequestParam(required = false) DocCategory category,
                         @RequestParam(required = false) String description, @RequestParam(defaultValue = "false") boolean confidential) {
        return service.upload(actor(me), id, file, name, category, description, confidential);
    }

    @PostMapping("/matters/{id}/documents/generate")
    public GenerateResult generate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody GenerateRequest r) {
        return service.generate(actor(me), id, r);
    }

    @GetMapping("/documents/{id}/versions")
    public VersionsDto versions(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.versions(actor(me), id);
    }

    @PostMapping(value = "/documents/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocDto newVersion(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file,
                             @RequestParam(required = false) String note, @RequestParam(defaultValue = "false") boolean keepLocked) {
        return service.newVersion(actor(me), id, file, note, keepLocked);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam(required = false) Integer version) {
        return file(service.download(actor(me), id, version));
    }

    @PutMapping("/documents/{id}")
    public DocDto update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DocUpdateRequest r) {
        return service.update(actor(me), id, r);
    }

    @PutMapping("/documents/{id}/flags")
    public DocDto flags(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DocFlagsRequest r) {
        return service.setFlags(actor(me), id, r);
    }

    @PostMapping("/documents/{id}/review")
    public DocDto review(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ReviewRequest r) {
        return service.review(actor(me), id, r);
    }

    @PostMapping("/documents/{id}/checkout")
    public DocDto checkout(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.checkout(actor(me), id);
    }

    @PostMapping("/documents/{id}/release")
    public DocDto release(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.release(actor(me), id);
    }

    @PostMapping("/documents/{id}/archive")
    public DocDto archive(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.archive(actor(me), id, true);
    }

    @PostMapping("/documents/{id}/restore")
    public DocDto restore(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.archive(actor(me), id, false);
    }

    // ---- templates

    @GetMapping("/doc-templates")
    public List<TemplateDto> templates(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.templates(activeOnly);
    }

    @GetMapping("/doc-templates/placeholders")
    public List<PlaceholderDto> placeholders() {
        return service.placeholders();
    }

    @PostMapping("/doc-templates")
    public TemplateDto createTemplate(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody TemplateRequest r) {
        return service.saveTemplate(actor(me), canManageTemplates(me), null, r);
    }

    @PutMapping("/doc-templates/{id}")
    public TemplateDto updateTemplate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TemplateRequest r) {
        return service.saveTemplate(actor(me), canManageTemplates(me), id, r);
    }

    @PostMapping(value = "/doc-templates/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateDto templateFile(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.templateFile(actor(me), canManageTemplates(me), id, file);
    }

    @GetMapping("/doc-templates/{id}/file")
    public ResponseEntity<byte[]> templateFileDownload(@PathVariable Long id) {
        return file(service.templateFileDownload(id));
    }

    @DeleteMapping("/doc-templates/{id}")
    public void deleteTemplate(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.deleteTemplate(canManageTemplates(me), id);
    }
}
