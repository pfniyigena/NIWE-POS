package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.PlaceRequest;
import com.posapp.onlinestore.OnlineStoreDtos.PublicProductPage;
import com.posapp.onlinestore.OnlineStoreDtos.PublicShop;
import com.posapp.onlinestore.OnlineStoreDtos.Track;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * The public shop: no login. A visitor can read what is on sale, send an order that waits for staff, and follow it by its code. It is separate from the
 * staff API under /api/online-store, answers "not available" until the module is on and the shop is switched on, and never exposes costs or other customers.
 */
@RestController
@RequestMapping("/api/public/store")
public class PublicStoreController {

    private final OnlineStoreService service;
    private final ModuleService moduleService;

    public PublicStoreController(OnlineStoreService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    /**
     * The address the visitor came from, for the per-connection limits. Only the connection's own address is used: a header the visitor can set
     * (X-Forwarded-For) would let a script dodge the limit. Behind a reverse proxy, set {@code server.forward-headers-strategy=native} so the
     * server itself replaces this address with the one the trusted proxy reports.
     */
    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @GetMapping("/shop")
    public PublicShop shop() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.shop();
    }

    /** The catalogue, a page at a time. page starts at 0; size is capped; q searches the name; category is a category name from the shop's list. */
    @GetMapping("/products")
    public PublicProductPage products(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size,
                                      @RequestParam(required = false) String q, @RequestParam(required = false) String category) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.products(page, size, q, category);
    }

    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        OnlineStoreService.Image image = service.image(id);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    @PostMapping("/orders")
    public Track place(@Valid @RequestBody PlaceRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.place(request, ip(http));
    }

    @GetMapping("/orders/{code}")
    public Track track(@PathVariable String code, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.trackByCode(code, ip(http));
    }

    @PostMapping("/orders/{code}/cancel")
    public Track cancel(@PathVariable String code, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.cancelByCustomer(code, ip(http));
    }
}
