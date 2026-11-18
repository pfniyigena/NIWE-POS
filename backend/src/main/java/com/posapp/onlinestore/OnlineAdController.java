package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineAdService.AdDto;
import com.posapp.onlinestore.OnlineAdService.AdForm;
import com.posapp.onlinestore.OnlineAdService.AdvertiserDto;
import com.posapp.onlinestore.OnlineAdService.AdvertiserForm;
import com.posapp.onlinestore.OnlineAdService.PublicAd;
import com.posapp.onlinestore.OnlineAdService.Report;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Advertising on the shop: staff manage advertisers and ads and read the report; the public shop reads the ads and reports views and clicks. */
@RestController
public class OnlineAdController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineAdService service;
    private final ModuleService moduleService;

    public OnlineAdController(OnlineAdService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    public record SeenRequest(String placement, List<Long> ids) {
    }

    // ------------------------------------------------------------------ staff

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/advertisers")
    public List<AdvertiserDto> advertisers() {
        return service.advertisers();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/advertisers")
    public AdvertiserDto addAdvertiser(@RequestBody AdvertiserForm in) {
        return service.saveAdvertiser(null, in);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/advertisers/{id}")
    public AdvertiserDto updateAdvertiser(@PathVariable Long id, @RequestBody AdvertiserForm in) {
        return service.saveAdvertiser(id, in);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/advertisers/{id}")
    public void deleteAdvertiser(@PathVariable Long id) {
        service.deleteAdvertiser(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/ads")
    public List<AdDto> ads() {
        return service.list();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/ads")
    public AdDto addAd(@ModelAttribute AdForm form, @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        return service.save(null, form, file);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/ads/{id}")
    public AdDto updateAd(@PathVariable Long id, @ModelAttribute AdForm form, @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        return service.save(id, form, file);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/ads/{id}")
    public void deleteAd(@PathVariable Long id) {
        service.delete(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/ads/report")
    public Report report(@RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long advertiserId) {
        return service.report(from, to, advertiserId);
    }

    // ------------------------------------------------------------------ the public shop (no login, only while the Online Store module is on)

    /** The ads to show today, by space: HOME_TOP, PRODUCT, ORDER. */
    @GetMapping("/api/public/store/ads")
    public Map<String, List<PublicAd>> showing() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.showing();
    }

    /** The shop just showed these ads in this space. */
    @PostMapping("/api/public/store/ads/seen")
    public ResponseEntity<Void> seen(@RequestBody SeenRequest in, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        if (in != null) service.seen(in.placement(), in.ids(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/public/store/ads/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        OnlineStoreService.Image image = service.image(id);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    /** A click on an ad: counted, then the visitor is sent to the advertiser's address that staff saved. */
    @GetMapping("/api/public/store/ads/{id}/click")
    public ResponseEntity<Void> click(@PathVariable Long id, @RequestParam(name = "p", defaultValue = "") String placement, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        String link = service.click(id, placement, http.getRemoteAddr());
        if (link == null) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(link)).cacheControl(CacheControl.noStore()).build();
    }
}
