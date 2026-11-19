package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * What search engines and link-preview crawlers read (v1.53.0): small HTML pages for the shop, a product and an information page, the sitemap and
 * robots.txt. Public, read-only. The web server sends crawlers here (docs/online-store-seo.md); a visitor never sees these pages.
 */
@RestController
public class OnlineSeoController {

    private final OnlineSeoService seo;
    private final ModuleService moduleService;

    public OnlineSeoController(OnlineSeoService seo, ModuleService moduleService) {
        this.seo = seo;
        this.moduleService = moduleService;
    }

    @GetMapping(value = "/api/public/store/seo/shop", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> shop(HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return cached(MediaType.TEXT_HTML, seo.shopPage(origin(http)));
    }

    @GetMapping(value = "/api/public/store/seo/product/{id}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> product(@PathVariable Long id, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return cached(MediaType.TEXT_HTML, seo.productPage(id, origin(http)));
    }

    @GetMapping(value = "/api/public/store/seo/info/{slug}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> info(@PathVariable String slug, @RequestParam(defaultValue = "en") String lang, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return cached(MediaType.TEXT_HTML, seo.infoPage(slug, lang, origin(http)));
    }

    @GetMapping(value = "/api/public/store/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap(HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return cached(MediaType.APPLICATION_XML, seo.sitemap(origin(http)));
    }

    @GetMapping(value = "/api/public/store/robots.txt", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> robots(HttpServletRequest http) {
        return cached(MediaType.TEXT_PLAIN, seo.robots(origin(http)));
    }

    private static ResponseEntity<String> cached(MediaType type, String body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic()).contentType(type).body(body);
    }

    /** The address the request came in on (behind a proxy: the forwarded host and protocol), used only when the app's own address is not set. */
    private static String origin(HttpServletRequest http) {
        String proto = http.getHeader("X-Forwarded-Proto");
        String host = http.getHeader("X-Forwarded-Host");
        if (host == null) host = http.getHeader("Host");
        if (host == null) return "";
        return (proto == null ? http.getScheme() : proto) + "://" + host;
    }
}
