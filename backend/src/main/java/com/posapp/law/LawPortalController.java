package com.posapp.law;

import com.posapp.law.LawDocumentService.Download;
import com.posapp.law.dto.LawPortalDtos.*;
import com.posapp.law.dto.LawPortalRequests.MessageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** What a signed-in client can do. SecurityConfig lets only a portal token in here (a firm user's token gets 403). */
@RestController
@RequestMapping("/api/portal")
public class LawPortalController {

    private final LawPortalService service;

    public LawPortalController(LawPortalService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal LawPortalPrincipal me) {
        return service.me(me);
    }

    @GetMapping("/matters/{id}")
    public MatterDto matter(@AuthenticationPrincipal LawPortalPrincipal me, @PathVariable Long id) {
        return service.matter(me, id);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal LawPortalPrincipal me, @PathVariable Long id) {
        Download d = service.download(me, id);
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

    @PostMapping(value = "/matters/{id}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadDto upload(@AuthenticationPrincipal LawPortalPrincipal me, @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file,
                            @RequestParam(required = false) String name) {
        return service.upload(me, id, file, name);
    }

    @PostMapping("/matters/{id}/messages")
    public List<MessageDto> post(@AuthenticationPrincipal LawPortalPrincipal me, @PathVariable Long id, @RequestBody MessageRequest r) {
        return service.post(me, id, r);
    }

    @PostMapping("/matters/{id}/messages/read")
    public void read(@AuthenticationPrincipal LawPortalPrincipal me, @PathVariable Long id) {
        service.markRead(me, id);
    }
}
