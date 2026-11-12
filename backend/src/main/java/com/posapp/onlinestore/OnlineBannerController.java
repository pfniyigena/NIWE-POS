package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineBannerService.BannerDto;
import org.springframework.http.CacheControl;
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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The banner pictures of the shop's front page: staff manage them, the public shop reads them. */
@RestController
public class OnlineBannerController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineBannerService banners;
    private final ModuleService moduleService;

    public OnlineBannerController(OnlineBannerService banners, ModuleService moduleService) {
        this.banners = banners;
        this.moduleService = moduleService;
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/banners")
    public List<BannerDto> list() {
        return banners.list();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/banners")
    public BannerDto add(@RequestParam("file") MultipartFile file) throws IOException {
        return banners.add(file);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/banners/{id}")
    public void delete(@PathVariable Long id) {
        banners.delete(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/banners/{id}/move")
    public List<BannerDto> move(@PathVariable Long id, @RequestParam(defaultValue = "up") String direction) {
        return banners.move(id, !"down".equalsIgnoreCase(direction));
    }

    /** The picture, for the public shop (no login). Only while the Online Store module is on. */
    @GetMapping("/api/public/store/banners/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @RequestParam(defaultValue = "0") int w) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        OnlineStoreService.Image image = banners.image(id, w);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }
}
