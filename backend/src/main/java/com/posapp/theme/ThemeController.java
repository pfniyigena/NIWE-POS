package com.posapp.theme;

import com.posapp.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saved colour themes. The first two are for the signed-in user; the last is open to visitors (everything under /api/public is). */
@RestController
public class ThemeController {

    private final ThemeService service;

    public ThemeController(ThemeService service) {
        this.service = service;
    }

    @GetMapping("/api/me/theme")
    public ThemeDto mine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.mine(principal.getId());
    }

    @PutMapping("/api/me/theme")
    public ThemeDto save(@RequestBody ThemeRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        boolean admin = principal.getUser().getRole() == com.posapp.user.Role.ADMIN;
        return service.saveMine(principal.getId(), request, admin);
    }

    @GetMapping("/api/public/settings/theme")
    public ThemeDto website() {
        return service.website();
    }
}
