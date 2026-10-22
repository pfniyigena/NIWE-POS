package com.posapp.realestate;

import com.posapp.realestate.RePropertyService.Photo;
import com.posapp.realestate.dto.RealEstatePublicDtos.Detail;
import com.posapp.realestate.dto.RealEstatePublicDtos.EnquiryRequest;
import com.posapp.realestate.dto.RealEstatePublicDtos.EnquiryResult;
import com.posapp.realestate.dto.RealEstatePublicDtos.Info;
import com.posapp.realestate.dto.RealEstatePublicDtos.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** {@code /api/public/realestate}: the public listings website. No login (everything under /api/public/** is open, see SecurityConfig); {@link RePublicService} decides what may be seen. */
@RestController
@RequestMapping("/api/public/realestate")
public class RePublicController {

    private final RePublicService service;

    public RePublicController(RePublicService service) {
        this.service = service;
    }

    @GetMapping("/info")
    public Info info() {
        return service.info();
    }

    @GetMapping("/properties")
    public Page list(@RequestParam(required = false) ListingPurpose purpose, @RequestParam(required = false) PropertyType type, @RequestParam(required = false) String area,
                     @RequestParam(required = false) Integer bedrooms, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                     @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return service.list(purpose, type, area, bedrooms, minPrice, maxPrice, q, page, size);
    }

    @GetMapping("/properties/{id}")
    public Detail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/properties/{id}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @PathVariable Long photoId) {
        Photo p = service.photo(id, photoId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(p.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(p.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .contentType(type)
                .contentLength(p.data().length)
                .body(p.data());
    }

    @PostMapping("/properties/{id}/enquiries")
    public EnquiryResult enquire(@PathVariable Long id, @RequestBody EnquiryRequest request, HttpServletRequest http) {
        return service.enquire(id, request, http.getRemoteAddr());
    }
}
