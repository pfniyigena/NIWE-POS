package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineProductPageService.Details;
import com.posapp.onlinestore.OnlineProductPageService.ProductDetail;
import com.posapp.onlinestore.OnlineProductPageService.SpecsRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** The product page: the public shop reads one product with its pictures, specifications, reviews and related products; staff manage pictures and specifications. */
@RestController
public class OnlineProductPageController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineProductPageService pages;
    private final ModuleService moduleService;

    public OnlineProductPageController(OnlineProductPageService pages, ModuleService moduleService) {
        this.pages = pages;
        this.moduleService = moduleService;
    }

    // ---------------------------------------------------------------- public (no login)

    @GetMapping("/api/public/store/products/{id}")
    public ProductDetail detail(@PathVariable Long id) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.detail(id);
    }

    @GetMapping("/api/public/store/product-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @RequestParam(defaultValue = "0") int w) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        OnlineStoreService.Image image = pages.image(id, w);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    // ---------------------------------------------------------------- staff

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/listings/{productId}/details")
    public Details details(@PathVariable Long productId) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.details(productId);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/listings/{productId}/specs")
    public Details saveSpecs(@PathVariable Long productId, @RequestBody SpecsRequest request) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.saveSpecs(productId, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/listings/{productId}/images")
    public Details addImage(@PathVariable Long productId, @RequestParam("file") MultipartFile file) throws IOException {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.addImage(productId, file);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/listing-images/{id}")
    public Details deleteImage(@PathVariable Long id) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.deleteImage(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/listing-images/{id}/move")
    public Details move(@PathVariable Long id, @RequestParam(defaultValue = "up") String direction) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return pages.moveImage(id, !"down".equalsIgnoreCase(direction));
    }
}
