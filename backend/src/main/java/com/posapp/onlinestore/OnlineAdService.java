package com.posapp.onlinestore;

import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advertising on the shop (v1.43.0): staff keep advertisers and their ads; the public shop shows the ads that are switched on and inside their dates
 * in three spaces (HOME_TOP, PRODUCT, ORDER), counts how often each was shown and clicked per day and space, and staff print a report per advertiser.
 * A click goes through the server, which counts it and sends the visitor on to the advertiser's address (always the one staff saved, never one the visitor supplies).
 */
@Service
public class OnlineAdService {

    static final List<String> PLACEMENTS = List.of("HOME_TOP", "PRODUCT", "ORDER");
    static final int MAX_PER_PLACEMENT = 10;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_WIDTH = 1600;
    /** One person reloading the page does not add views: the same connection counts once per ad and space in this time. */
    private static final long VIEW_WINDOW_MS = 30L * 60 * 1000;
    private static final long CLICK_WINDOW_MS = 10L * 1000;

    private final OnlineAdRepository ads;
    private final OnlineAdvertiserRepository advertisers;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager em;

    public OnlineAdService(OnlineAdRepository ads, OnlineAdvertiserRepository advertisers) {
        this.ads = ads;
        this.advertisers = advertisers;
    }

    // ------------------------------------------------------------------ DTOs

    public record AdvertiserDto(Long id, String name, String contactName, String email, String phone, String notes, boolean active, long adCount) {
    }

    public record AdvertiserForm(String name, String contactName, String email, String phone, String notes, Boolean active) {
    }

    public record AdDto(Long id, Long advertiserId, String advertiserName, String title, List<String> placements, String linkUrl, String altText,
                        String startsOn, String endsOn, boolean active, BigDecimal fee, String feeNote, boolean feePaid, int sortOrder,
                        String fileName, long sizeBytes, String imageUrl, boolean showingNow, long views, long clicks) {
    }

    public record AdForm(Long advertiserId, String title, String placements, String linkUrl, String altText, String startsOn, String endsOn,
                         Boolean active, String fee, String feeNote, Boolean feePaid) {
    }

    /** What the public shop receives for one ad. */
    public record PublicAd(Long id, String title, String altText, String imageUrl, String clickUrl) {
    }

    public record PlacementCount(String placement, long views, long clicks) {
    }

    public record ReportRow(Long adId, Long advertiserId, String advertiserName, String title, long views, long clicks, double clickRate,
                            List<PlacementCount> byPlacement, BigDecimal fee, String feeNote, boolean feePaid) {
    }

    public record Report(String from, String to, long views, long clicks, double clickRate, List<ReportRow> rows) {
    }

    // ------------------------------------------------------------------ advertisers (staff)

    @Transactional(readOnly = true)
    public List<AdvertiserDto> advertisers() {
        List<AdvertiserDto> out = new ArrayList<>();
        for (OnlineAdvertiser a : advertisers.findAllByOrderByNameAsc()) out.add(dto(a));
        return out;
    }

    private AdvertiserDto dto(OnlineAdvertiser a) {
        return new AdvertiserDto(a.getId(), a.getName(), a.getContactName(), a.getEmail(), a.getPhone(), a.getNotes(), a.isActive(), ads.countByAdvertiserId(a.getId()));
    }

    @Transactional
    public AdvertiserDto saveAdvertiser(Long id, AdvertiserForm in) {
        if (in == null) throw ApiException.badRequest(I18n.t("Enter the advertiser's name."));
        String name = clean(in.name(), 120);
        if (name == null) throw ApiException.badRequest(I18n.t("Enter the advertiser's name."));
        OnlineAdvertiser a = id == null ? new OnlineAdvertiser() : advertisers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        a.setName(name);
        a.setContactName(clean(in.contactName(), 120));
        String email = clean(in.email(), 160);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("That e-mail address does not look right."));
        a.setEmail(email);
        a.setPhone(clean(in.phone(), 40));
        a.setNotes(clean(in.notes(), 500));
        a.setActive(in.active() == null || in.active());
        return dto(advertisers.save(a));
    }

    @Transactional
    public void deleteAdvertiser(Long id) {
        OnlineAdvertiser a = advertisers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        if (ads.countByAdvertiserId(id) > 0) throw ApiException.badRequest(I18n.t("This advertiser still has ads. Delete the ads first, or just switch the advertiser off."));
        advertisers.delete(a);
    }

    // ------------------------------------------------------------------ ads (staff)

    @Transactional(readOnly = true)
    public List<AdDto> list() {
        Map<Long, long[]> totals = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> sums = em.createNativeQuery("select ad_id, sum(views), sum(clicks) from online_ad_stat group by ad_id").getResultList();
        for (Object[] r : sums) {
            totals.put(((Number) r[0]).longValue(), new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        LocalDate today = LocalDate.now();
        List<AdDto> out = new ArrayList<>();
        for (Object[] r : ads.overview()) {
            Long id = ((Number) r[0]).longValue();
            LocalDate from = (LocalDate) r[7];
            LocalDate to = (LocalDate) r[8];
            boolean active = (Boolean) r[9];
            boolean advertiserActive = (Boolean) r[16];
            boolean now = active && advertiserActive && (from == null || !from.isAfter(today)) && (to == null || !to.isBefore(today));
            long[] t = totals.getOrDefault(id, new long[]{0, 0});
            out.add(new AdDto(id, ((Number) r[1]).longValue(), (String) r[2], (String) r[3], split((String) r[4]), (String) r[5], (String) r[6],
                    from == null ? null : from.toString(), to == null ? null : to.toString(), active, (BigDecimal) r[10], (String) r[11], (Boolean) r[12],
                    ((Number) r[13]).intValue(), (String) r[14], ((Number) r[15]).longValue(), imageUrl(id), now, t[0], t[1]));
        }
        return out;
    }

    private static String imageUrl(Long id) {
        return "/api/public/store/ads/" + id + "/image";
    }

    @Transactional
    public AdDto save(Long id, AdForm in, MultipartFile file) throws IOException {
        if (in == null) throw ApiException.badRequest(I18n.t("Fill in the ad first."));
        OnlineAd a = id == null ? new OnlineAd() : ads.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        // When an ad is changed and the form carries no advertiser, the ad keeps the one it has.
        Long advertiserId = in.advertiserId() != null ? in.advertiserId() : (id != null && a.getAdvertiser() != null ? a.getAdvertiser().getId() : null);
        if (advertiserId == null) throw ApiException.badRequest(I18n.t("Choose the advertiser."));
        a.setAdvertiser(advertisers.findById(advertiserId).orElseThrow(() -> ApiException.badRequest(I18n.t("Choose the advertiser."))));
        String title = clean(in.title(), 120);
        if (title == null) throw ApiException.badRequest(I18n.t("Enter a title for the ad (staff see it, the shop does not)."));
        a.setTitle(title);
        a.setPlacements(String.join(",", parsePlacements(in.placements())));
        a.setLinkUrl(checkLink(in.linkUrl()));
        a.setAltText(clean(in.altText(), 160));
        a.setStartsOn(parseDate(in.startsOn()));
        a.setEndsOn(parseDate(in.endsOn()));
        if (a.getStartsOn() != null && a.getEndsOn() != null && a.getEndsOn().isBefore(a.getStartsOn())) throw ApiException.badRequest(I18n.t("The end date is before the start date."));
        a.setActive(in.active() == null || in.active());
        a.setFee(parseFee(in.fee()));
        a.setFeeNote(clean(in.feeNote(), 160));
        a.setFeePaid(in.feePaid() != null && in.feePaid());
        boolean hasFile = file != null && !file.isEmpty();
        if (hasFile) {
            if (file.getSize() > MAX_BYTES) throw ApiException.badRequest(I18n.t("A picture can be at most 5 MB."));
            byte[] data = file.getBytes();
            String type = OnlineBannerService.detect(data);
            if (type == null) throw ApiException.badRequest(I18n.t("The ad must be a JPEG, PNG, WebP or GIF picture."));
            if (type.equals("image/jpeg") || type.equals("image/png")) {
                OnlineStoreService.Image scaled = OnlineStoreService.scale(type, data, MAX_WIDTH);
                type = scaled.contentType();
                data = scaled.bytes();
            }
            String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "ad" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
            if (name.length() > 200) name = name.substring(name.length() - 200);
            a.setFileName(name);
            a.setContentType(type);
            a.setSizeBytes(data.length);
            a.setData(data);
        } else if (a.getData() == null) {
            throw ApiException.badRequest(I18n.t("Choose the ad's picture."));
        }
        if (id == null) a.setSortOrder(ads.maxOrder() + 1);
        OnlineAd saved = ads.save(a);
        ads.flush();
        return list().stream().filter(x -> x.id().equals(saved.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(Long id) {
        OnlineAd a = ads.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        ads.delete(a);
    }

    private static List<String> parsePlacements(String raw) {
        Set<String> chosen = new LinkedHashSet<>();
        if (raw != null) for (String p : raw.split(",")) {
            String x = p.trim().toUpperCase(java.util.Locale.ROOT);
            if (x.isEmpty()) continue;
            if (!PLACEMENTS.contains(x)) throw ApiException.badRequest(I18n.t("Choose where the ad is shown."));
            chosen.add(x);
        }
        if (chosen.isEmpty()) throw ApiException.badRequest(I18n.t("Choose where the ad is shown."));
        return PLACEMENTS.stream().filter(chosen::contains).toList();
    }

    private static List<String> split(String placements) {
        List<String> out = new ArrayList<>();
        if (placements != null) for (String p : placements.split(",")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    /** Only a plain web address: a visitor is sent there when they click, so it must not be a script or a file. */
    static String checkLink(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw ApiException.badRequest(I18n.t("Enter the address the ad links to (starting with https://)."));
        if (s.length() > 500 || !s.matches("^https?://[^\\s]+$")) throw ApiException.badRequest(I18n.t("The link must be a web address starting with http:// or https://."));
        try {
            URI u = URI.create(s);
            if (u.getHost() == null || u.getHost().isBlank()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(I18n.t("The link must be a web address starting with http:// or https://."));
        }
        return s;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(I18n.t("A date does not look right."));
        }
    }

    private static BigDecimal parseFee(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(s.trim());
            if (v.signum() < 0 || v.compareTo(new BigDecimal("999999999999")) > 0) throw new NumberFormatException();
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(I18n.t("The fee must be a number, zero or more."));
        }
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("[\\p{Cntrl}]", " ");
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** The picture, for the shop and the staff preview. */
    @Transactional(readOnly = true)
    public OnlineStoreService.Image image(Long id) {
        OnlineAd a = ads.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        return new OnlineStoreService.Image(a.getContentType(), a.getData());
    }

    // ------------------------------------------------------------------ the public shop

    /** The ads that may be shown today, by space (at most ten per space, in staff's order). */
    @Transactional(readOnly = true)
    public Map<String, List<PublicAd>> showing() {
        Map<String, List<PublicAd>> out = new LinkedHashMap<>();
        for (String p : PLACEMENTS) out.put(p, new ArrayList<>());
        for (Object[] r : ads.showing(LocalDate.now())) {
            Long id = ((Number) r[0]).longValue();
            PublicAd ad = new PublicAd(id, (String) r[1], (String) r[2], imageUrl(id), "/api/public/store/ads/" + id + "/click");
            for (String p : split((String) r[3])) {
                List<PublicAd> list = out.get(p);
                if (list != null && list.size() < MAX_PER_PLACEMENT) list.add(ad);
            }
        }
        return out;
    }

    private Map<Long, Set<String>> showingPlacements() {
        Map<Long, Set<String>> m = new LinkedHashMap<>();
        for (Object[] r : ads.showing(LocalDate.now())) m.put(((Number) r[0]).longValue(), new LinkedHashSet<>(split((String) r[3])));
        return m;
    }

    private boolean fresh(String key, long windowMs) {
        long now = System.currentTimeMillis();
        if (recent.size() > 20000) recent.entrySet().removeIf(e -> now - e.getValue() > VIEW_WINDOW_MS);
        Long last = recent.get(key);
        if (last != null && now - last < windowMs) return false;
        recent.put(key, now);
        return true;
    }

    /** The shop shows these ads in this space: counts a view for each (a connection counts once per ad and space for half an hour). */
    @Transactional
    public void seen(String placement, List<Long> ids, String ip) {
        String p = placement == null ? "" : placement.trim().toUpperCase(java.util.Locale.ROOT);
        if (!PLACEMENTS.contains(p) || ids == null || ids.isEmpty()) return;
        Map<Long, Set<String>> live = showingPlacements();
        LocalDate today = LocalDate.now();
        int n = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            if (++n > MAX_PER_PLACEMENT) break;
            if (id == null || !live.containsKey(id) || !live.get(id).contains(p)) continue;
            if (!fresh(ip + "|v|" + id + "|" + p, VIEW_WINDOW_MS)) continue;
            bump(id, today, p, 1, 0);
        }
    }

    /** A click: counts it (when the ad is showing in that space) and returns the advertiser's address to send the visitor to; null when there is no such ad. */
    @Transactional
    public String click(Long id, String placement, String ip) {
        String link = ads.activeLink(id).orElse(null);
        if (link == null) return null;
        String p = placement == null ? "" : placement.trim().toUpperCase(java.util.Locale.ROOT);
        Set<String> where = showingPlacements().get(id);
        if (where != null && where.contains(p) && fresh(ip + "|c|" + id + "|" + p, CLICK_WINDOW_MS)) bump(id, LocalDate.now(), p, 0, 1);
        return link;
    }

    private void bump(Long adId, LocalDate day, String placement, long views, long clicks) {
        em.createNativeQuery("insert into online_ad_stat (ad_id, day, placement, views, clicks) values (:ad, :day, :pl, :v, :c) "
                        + "on conflict (ad_id, day, placement) do update set views = online_ad_stat.views + :v, clicks = online_ad_stat.clicks + :c")
                .setParameter("ad", adId).setParameter("day", day).setParameter("pl", placement).setParameter("v", views).setParameter("c", clicks).executeUpdate();
    }

    // ------------------------------------------------------------------ the report

    /** Views and clicks per ad between two days (a month back to today when none is given), optionally for one advertiser. */
    @Transactional(readOnly = true)
    public Report report(String fromText, String toText, Long advertiserId) {
        LocalDate end = parseDate(toText) == null ? LocalDate.now() : parseDate(toText);
        LocalDate start = parseDate(fromText) == null ? end.minusDays(29) : parseDate(fromText);
        if (start.isAfter(end)) throw ApiException.badRequest(I18n.t("The start date is after the end date"));
        Map<Long, List<PlacementCount>> byAd = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> stats = em.createNativeQuery("select ad_id, placement, sum(views), sum(clicks) from online_ad_stat where day between :f and :t group by ad_id, placement order by ad_id, placement")
                .setParameter("f", start).setParameter("t", end).getResultList();
        for (Object[] r : stats) {
            byAd.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>()).add(new PlacementCount((String) r[1], ((Number) r[2]).longValue(), ((Number) r[3]).longValue()));
        }
        List<ReportRow> rows = new ArrayList<>();
        long tv = 0;
        long tc = 0;
        for (AdDto a : list()) {
            if (advertiserId != null && !advertiserId.equals(a.advertiserId())) continue;
            List<PlacementCount> parts = byAd.getOrDefault(a.id(), List.of());
            long v = parts.stream().mapToLong(PlacementCount::views).sum();
            long c = parts.stream().mapToLong(PlacementCount::clicks).sum();
            tv += v;
            tc += c;
            rows.add(new ReportRow(a.id(), a.advertiserId(), a.advertiserName(), a.title(), v, c, rate(c, v), parts, a.fee(), a.feeNote(), a.feePaid()));
        }
        rows.sort(java.util.Comparator.comparing(ReportRow::advertiserName, String.CASE_INSENSITIVE_ORDER).thenComparing(ReportRow::title, String.CASE_INSENSITIVE_ORDER));
        return new Report(start.toString(), end.toString(), tv, tc, rate(tc, tv), rows);
    }

    private static double rate(long clicks, long views) {
        return views <= 0 ? 0 : BigDecimal.valueOf(clicks * 100.0 / views).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
