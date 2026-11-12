package com.posapp.onlinestore;

import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The banner pictures of the shop's front page (v1.36.0): staff upload up to 8 pictures, put them in order, and remove them. The shop shows them
 * as a slideshow. A picture is checked by its first bytes (not just its name), and a big one is shrunk to at most 1920 pixels wide when it is added.
 */
@Service
public class OnlineBannerService {

    static final int MAX_BANNERS = 8;
    static final long MAX_BYTES = 8L * 1024 * 1024;
    static final int MAX_WIDTH = 1920;

    private final OnlineBannerRepository repository;

    public OnlineBannerService(OnlineBannerRepository repository) {
        this.repository = repository;
    }

    public record BannerDto(Long id, String fileName, long sizeBytes, int sortOrder, String url) {
    }

    @Transactional(readOnly = true)
    public List<BannerDto> list() {
        List<BannerDto> out = new ArrayList<>();
        for (Object[] r : repository.overview()) {
            Long id = ((Number) r[0]).longValue();
            out.add(new BannerDto(id, (String) r[1], ((Number) r[2]).longValue(), ((Number) r[3]).intValue(), url(id)));
        }
        return out;
    }

    /** The addresses of the pictures in the order they slide: what the public shop receives. */
    @Transactional(readOnly = true)
    public List<String> urls() {
        return repository.ids().stream().map(OnlineBannerService::url).toList();
    }

    static String url(Long id) {
        return "/api/public/store/banners/" + id;
    }

    @Transactional
    public BannerDto add(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest(I18n.t("Choose a picture to add."));
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest(I18n.t("A picture can be at most 8 MB."));
        if (repository.count() >= MAX_BANNERS) throw ApiException.badRequest(I18n.t("At most {0} banner pictures can be on the shop. Remove one first.", MAX_BANNERS));
        byte[] data = file.getBytes();
        String type = detect(data);
        if (type == null) throw ApiException.badRequest(I18n.t("The banner must be a JPEG, PNG, WebP or GIF picture."));
        // A GIF may be an animation and WebP cannot be read here: both are kept exactly as uploaded.
        if (type.equals("image/jpeg") || type.equals("image/png")) {
            OnlineStoreService.Image scaled = OnlineStoreService.scale(type, data, MAX_WIDTH);
            type = scaled.contentType();
            data = scaled.bytes();
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "banner" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        OnlineBanner b = new OnlineBanner();
        b.setFileName(name);
        b.setContentType(type);
        b.setSizeBytes(data.length);
        b.setData(data);
        b.setSortOrder(repository.maxOrder() + 1);
        b = repository.save(b);
        return new BannerDto(b.getId(), b.getFileName(), b.getSizeBytes(), b.getSortOrder(), url(b.getId()));
    }

    @Transactional
    public void delete(Long id) {
        OnlineBanner b = repository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        repository.delete(b);
    }

    /** Moves a picture one place earlier (up) or later (down) in the slideshow. */
    @Transactional
    public List<BannerDto> move(Long id, boolean up) {
        List<OnlineBanner> all = new ArrayList<>(repository.findAll());
        all.sort(java.util.Comparator.comparingInt(OnlineBanner::getSortOrder).thenComparing(OnlineBanner::getId));
        int i = -1;
        for (int k = 0; k < all.size(); k++) if (all.get(k).getId().equals(id)) i = k;
        if (i < 0) throw ApiException.notFound(I18n.t("Not found."));
        int j = up ? i - 1 : i + 1;
        if (j >= 0 && j < all.size()) java.util.Collections.swap(all, i, j);
        for (int k = 0; k < all.size(); k++) all.get(k).setSortOrder(k + 1);
        repository.saveAll(all);
        return list();
    }

    /** The picture itself, for the shop and for the staff preview. With a width the picture is shrunk to at most that many pixels. */
    @Transactional(readOnly = true)
    public OnlineStoreService.Image image(Long id, int width) {
        OnlineBanner b = repository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        int w = width <= 0 ? 0 : Math.max(64, Math.min(width, MAX_WIDTH));
        if (w == 0 || b.getContentType().equals("image/gif")) return new OnlineStoreService.Image(b.getContentType(), b.getData());
        return OnlineStoreService.scale(b.getContentType(), b.getData(), w);
    }

    /** The picture type from its first bytes, or null when it is not one of the four allowed types. */
    static String detect(byte[] d) {
        if (d == null || d.length < 12) return null;
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return "image/jpeg";
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return "image/gif";
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }
}
