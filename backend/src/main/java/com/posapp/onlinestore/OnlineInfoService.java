package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shop's information pages (v1.51.0): Terms, Privacy, Returns and Delivery. Until staff write their own, a starter text in English or French is shown
 * (the visitor's language); once staff save a text it is shown to everybody as written. {shop}, {phone}, {email} and {address} in a text are replaced by the
 * shop's own details when a visitor reads it, so they stay right when the details change.
 */
@Service
public class OnlineInfoService {

    private final OnlineInfoPageRepository pages;
    private final OnlineStoreService storeService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;

    public OnlineInfoService(OnlineInfoPageRepository pages, OnlineStoreService storeService, StoreSettingsService storeSettingsService, AuditLogService auditLogService) {
        this.pages = pages;
        this.storeService = storeService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
    }

    private static String who() {
        org.springframework.security.core.Authentication a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private static String lang(String l) {
        return "fr".equalsIgnoreCase(l) ? "fr" : "en";
    }

    private static String slug(String s) {
        String x = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (!OnlineInfoDefaults.known(x)) throw ApiException.notFound(I18n.t("Not found."));
        return x;
    }

    // ------------------------------------------------------------------ the visitor

    /** The published pages, for the footer. */
    @Transactional(readOnly = true)
    public List<InfoLink> links(String language) {
        storeService.openSettings();
        List<InfoLink> out = new ArrayList<>();
        for (String slug : OnlineInfoDefaults.SLUGS) {
            OnlineInfoPage p = pages.findBySlug(slug).orElse(null);
            if (p != null && !p.isPublished()) continue;
            out.add(new InfoLink(slug, title(p, slug, language)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public InfoPageDto page(String slugIn, String language) {
        OnlineStoreSettings s = storeService.openSettings();
        String slug = slug(slugIn);
        OnlineInfoPage p = pages.findBySlug(slug).orElse(null);
        if (p != null && !p.isPublished()) throw ApiException.notFound(I18n.t("Not found."));
        String body = p != null && p.getBody() != null ? p.getBody() : OnlineInfoDefaults.of(slug, lang(language))[1];
        return new InfoPageDto(slug, title(p, slug, language), fill(body, s));
    }

    private static String title(OnlineInfoPage p, String slug, String language) {
        return p != null && p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : OnlineInfoDefaults.of(slug, lang(language))[0];
    }

    /** The shop's details into a text; a detail the shop does not have leaves no stray separator behind. */
    String fill(String body, OnlineStoreSettings s) {
        String shop = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        String email = s.getContactEmail() != null && !s.getContactEmail().isBlank() ? s.getContactEmail() : storeSettingsService.getSettingsEntity().getEmail();
        String out = body.replace("{shop}", shop == null ? "" : shop).replace("{phone}", nz(s.getContactPhone())).replace("{email}", nz(email))
                .replace("{address}", nz(s.getPickupAddress()));
        out = out.replaceAll("(?m)(\\s·\\s)+(?=\\s·\\s)", "").replaceAll("(?m)\\s·\\s*$", "").replaceAll("(?m)^\\s·\\s", "").replaceAll("[ \\t]+\\n", "\n").replaceAll("[ \\t]{2,}", " ");
        return out.trim();
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    // ------------------------------------------------------------------ staff

    /** All four pages as staff edit them, in the language of the staff's screen for the starter texts. */
    @Transactional(readOnly = true)
    public List<InfoPageAdminDto> adminPages(String language) {
        List<InfoPageAdminDto> out = new ArrayList<>();
        for (String slug : OnlineInfoDefaults.SLUGS) {
            OnlineInfoPage p = pages.findBySlug(slug).orElse(null);
            boolean edited = p != null && p.getBody() != null;
            String[] d = OnlineInfoDefaults.of(slug, lang(language));
            out.add(new InfoPageAdminDto(slug, p != null && p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : d[0], edited ? p.getBody() : d[1],
                    p == null || p.isPublished(), edited));
        }
        return out;
    }

    @Transactional
    public InfoPageAdminDto save(String slugIn, InfoPageRequest in, String language) {
        String slug = slug(slugIn);
        String body = in.body() == null ? "" : in.body().trim();
        if (body.isEmpty()) throw ApiException.badRequest(I18n.t("Write the text of the page."));
        String title = in.title() == null ? "" : in.title().trim();
        if (title.isEmpty()) throw ApiException.badRequest(I18n.t("Write the title of the page."));
        OnlineInfoPage p = pages.findBySlug(slug).orElseGet(() -> {
            OnlineInfoPage n = new OnlineInfoPage();
            n.setSlug(slug);
            return n;
        });
        p.setTitle(title);
        p.setBody(body);
        p.setPublished(in.published() == null || in.published());
        p.setUpdatedBy(who());
        pages.save(p);
        auditLogService.record("SHOP_PAGE_SAVED", "Saved the shop page " + slug);
        return adminPages(language).stream().filter(x -> x.slug().equals(slug)).findFirst().orElseThrow();
    }

    /** Goes back to the starter text (the page stays published or not as it was). */
    @Transactional
    public InfoPageAdminDto restore(String slugIn, String language) {
        String slug = slug(slugIn);
        pages.findBySlug(slug).ifPresent(p -> {
            p.setTitle(null);
            p.setBody(null);
            p.setUpdatedBy(who());
            pages.save(p);
        });
        auditLogService.record("SHOP_PAGE_RESTORED", "Restored the starter text of the shop page " + slug);
        return adminPages(language).stream().filter(x -> x.slug().equals(slug)).findFirst().orElseThrow();
    }
}
