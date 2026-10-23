package com.posapp.theme;

import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** Saves the chosen colour theme in the database: one per user, and one for the public website. */
@Service
public class ThemeService {

    static final List<String> IDS = List.of("navy", "charcoal", "black", "slate", "forest", "plum", "warm", "white", "paper", "grey", "sky", "custom");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final UserThemeRepository users;
    private final SiteThemeRepository site;

    public ThemeService(UserThemeRepository users, SiteThemeRepository site) {
        this.users = users;
        this.site = site;
    }

    /** The user's own theme, or null when they have never chosen one. */
    @Transactional(readOnly = true)
    public ThemeDto mine(Long userId) {
        return users.findByAppUserId(userId).map(t -> new ThemeDto(t.getTheme(), t.getCustomBg(), t.getCustomAccent())).orElse(null);
    }

    /** The public website's theme, or null when none was set (the app default then applies). */
    @Transactional(readOnly = true)
    public ThemeDto website() {
        return site.findAll().stream().findFirst().map(t -> new ThemeDto(t.getTheme(), t.getCustomBg(), t.getCustomAccent())).orElse(null);
    }

    @Transactional
    public ThemeDto saveMine(Long userId, ThemeRequest r, boolean admin) {
        ThemeDto clean = validate(r);
        UserTheme t = users.findByAppUserId(userId).orElseGet(() -> {
            UserTheme n = new UserTheme();
            n.setAppUserId(userId);
            return n;
        });
        t.setTheme(clean.theme());
        t.setCustomBg(clean.customBg());
        t.setCustomAccent(clean.customAccent());
        users.save(t);
        if (admin && Boolean.TRUE.equals(r.applyToWebsite())) {
            SiteTheme s = site.findAll().stream().findFirst().orElseGet(SiteTheme::new);
            s.setTheme(clean.theme());
            s.setCustomBg(clean.customBg());
            s.setCustomAccent(clean.customAccent());
            site.save(s);
        }
        return clean;
    }

    private static ThemeDto validate(ThemeRequest r) {
        if (r == null || r.theme() == null || !IDS.contains(r.theme())) {
            throw ApiException.badRequest("Choose one of the available themes.");
        }
        if (!"custom".equals(r.theme())) {
            return new ThemeDto(r.theme(), null, null);
        }
        if (r.customBg() == null || r.customAccent() == null || !HEX.matcher(r.customBg()).matches() || !HEX.matcher(r.customAccent()).matches()) {
            throw ApiException.badRequest("Custom colours must look like #1a2b3c.");
        }
        return new ThemeDto("custom", r.customBg().toLowerCase(), r.customAccent().toLowerCase());
    }
}
