package com.posapp.onlinestore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.PublicProduct;
import com.posapp.onlinestore.OnlineStoreDtos.PublicReview;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The product page of the shop (v1.44.0): one product with a gallery of pictures, a specifications table, its approved reviews and related products.
 * Staff add up to {@value #MAX_IMAGES} extra pictures (besides the product's own) and write the specification rows from Shop settings. Reading is public;
 * nothing here changes an order or the stock.
 */
@Service
public class OnlineProductPageService {

    static final int MAX_IMAGES = 5;
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_WIDTH = 1600;
    static final int MAX_SPECS = 20;
    static final int MAX_LABEL = 60;
    static final int MAX_VALUE = 200;
    static final int REVIEWS_SHOWN = 20;
    static final int RELATED = 4;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OnlineStoreService store;
    private final OnlineFrontService front;
    private final OnlineShopSearch search;
    private final OnlineListingRepository listings;
    private final OnlineListingImageRepository images;
    private final OnlineReviewRepository reviews;
    private final AuditLogService audit;
    private final OnlineVariants variants;

    public OnlineProductPageService(OnlineStoreService store, OnlineFrontService front, OnlineListingRepository listings, OnlineListingImageRepository images,
                                    OnlineReviewRepository reviews, AuditLogService audit, OnlineShopSearch search, OnlineVariants variants) {
        this.search = search;
        this.variants = variants;
        this.store = store;
        this.front = front;
        this.listings = listings;
        this.images = images;
        this.reviews = reviews;
        this.audit = audit;
    }

    public record SpecRow(String label, String value) {
    }

    public record ImageDto(Long id, String fileName, long sizeBytes, int sortOrder, String url) {
    }

    /** What staff edit for a product: its extra pictures and its specification rows. */
    public record Details(List<ImageDto> images, List<SpecRow> specs, boolean hasMainImage, int maxImages) {
    }

    public record SpecsRequest(List<SpecRow> specs) {
    }

    /** stars[0] is how many gave 1 star, stars[4] how many gave 5. */
    public record RatingSummary(double average, int count, int[] stars) {
    }

    /** images: the pictures of the gallery in order (the product's own first), as addresses a width can be added to with ?w=. */
    public record ProductDetail(PublicProduct product, String brand, List<String> images, List<SpecRow> specs, RatingSummary rating, List<PublicReview> reviews,
                                List<PublicProduct> related, List<PublicProduct> bought, List<OnlineVariants.Option> options, List<VariantDto> variants,
                                Long selectedVariantId) {
    }

    /**
     * One option of a product with variants, as a customer chooses it: id is the product that goes into the cart, values are its option values in the order of
     * the options, pictures are shown first when this option is chosen (empty: the product's own gallery).
     */
    public record VariantDto(Long id, String label, List<String> values, java.math.BigDecimal price, java.math.BigDecimal wasPrice, String stock, String sku,
                             List<String> pictures) {
    }

    // ------------------------------------------------------------------ the public page

    @Transactional(readOnly = true)
    public ProductDetail detail(Long productId) {
        OnlineStoreSettings s = store.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        OnlineListing l = shown(productId, s);
        Product self = l.getProduct();
        Long group = self.getVariantParentId();
        List<OnlineListing> rows = group == null ? List.of() : variants.rows(group);
        boolean grouped = !rows.isEmpty();
        PublicProduct product = front.withRatings(List.of(store.publicProduct(l, s))).get(0);
        // the gallery belongs to the product as a whole: the template's listing (a product without one uses the first option's)
        OnlineListing galleryOwner = grouped ? listings.findByProductId(group).orElse(l) : l;
        List<String> pictures = new ArrayList<>();
        if (product.imageUrl() != null) pictures.add(product.imageUrl());
        for (Long id : images.ids(galleryOwner.getId())) pictures.add(url(id));

        List<Long> members = new ArrayList<>();
        members.add(self.getId());
        if (grouped) {
            members.add(group);
            for (OnlineListing r : listings.findAllGroupRows(group)) members.add(r.getProduct().getId());
        }
        int[] stars = new int[5];
        for (Object[] r : reviews.breakdown(members)) {
            int n = ((Number) r[0]).intValue();
            if (n >= 1 && n <= 5) stars[n - 1] = ((Number) r[1]).intValue();
        }
        int count = 0;
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            count += stars[i];
            sum += stars[i] * (i + 1);
        }
        RatingSummary rating = new RatingSummary(count == 0 ? 0 : Math.round(sum * 10.0 / count) / 10.0, count, stars);
        List<PublicReview> shownReviews = reviews.findShownForProduct(members, PageRequest.of(0, REVIEWS_SHOWN)).stream()
                .map(r -> new PublicReview(r.getProductName(), r.getReviewerName(), r.getRating(), r.getReviewText(), r.getCreatedAt())).toList();
        Product brandOf = grouped ? variants.template(group).orElse(self) : self;
        String brand = brandOf.getBrand() == null ? null : brandOf.getBrand().getName();

        List<OnlineVariants.Option> options = List.of();
        List<VariantDto> options2 = List.of();
        Long selected = null;
        if (grouped) {
            Product template = variants.template(group).orElse(null);
            options = OnlineVariants.options(template);
            boolean consistent = OnlineVariants.consistent(options, rows);
            if (!consistent) {
                List<String> labels = new ArrayList<>();
                for (OnlineListing r : rows) labels.add(r.getProduct().getVariantLabel() == null ? r.getProduct().getName() : r.getProduct().getVariantLabel());
                options = List.of(new OnlineVariants.Option(I18n.t("Option"), labels));
            }
            String templateIcon = template == null ? null : template.getIconDataUrl();
            final List<VariantDto> built = new ArrayList<>();
            for (OnlineListing r : rows) {
                Product vp = r.getProduct();
                PublicProduct v = store.publicVariant(r, s);
                List<String> own = new ArrayList<>();
                String icon = vp.getIconDataUrl();
                if (icon != null && !icon.isBlank() && !icon.equals(templateIcon)) own.add(v.imageUrl());
                for (Long id : images.ids(r.getId())) own.add(url(id));
                String label = vp.getVariantLabel() == null ? vp.getName() : vp.getVariantLabel();
                built.add(new VariantDto(vp.getId(), label, consistent ? OnlineVariants.values(vp) : List.of(label), v.price(), v.wasPrice(), v.stock(), vp.getSku(), own));
            }
            Long wanted = self.getId();
            selected = rows.stream().map(r -> r.getProduct().getId()).filter(wanted::equals).findFirst()
                    .orElseGet(() -> built.stream().filter(v -> !"SOLD_OUT".equals(v.stock())).map(VariantDto::id).findFirst().orElse(built.get(0).id()));
            options2 = built;
        }
        Long selfId = product.id();
        return new ProductDetail(product, brand, pictures, readSpecs(l.getSpecs()), rating, shownReviews, related(l, s, selfId), bought(members, s, selfId), options, options2, selected);
    }

    /** Products often bought in the same orders (up to 3), left out when they are gone or sold out. A product with options is not offered here (it needs a choice first). */
    private List<PublicProduct> bought(List<Long> members, OnlineStoreSettings s, Long selfId) {
        List<PublicProduct> picked = new ArrayList<>();
        for (Long id : search.boughtTogether(members, 12)) {
            listings.findByProductId(id).filter(OnlineListing::isListed).filter(x -> x.getProduct().isActive()).ifPresent(x -> {
                PublicProduct card = store.publicProduct(x, s);
                if (picked.size() < 3 && card.variants() == 0 && !card.id().equals(selfId) && !"SOLD_OUT".equals(card.stock())
                        && picked.stream().noneMatch(c -> c.id().equals(card.id()))) picked.add(card);
            });
        }
        return front.withRatings(picked);
    }

    /** The products with these ids (comma separated, at most 12), in the same order, for "recently viewed". */
    @Transactional(readOnly = true)
    public List<PublicProduct> recent(String ids) {
        return pick(ids, 12);
    }

    /** The products with these ids (comma separated, at most 60), in the same order, for the wishlist page. */
    @Transactional(readOnly = true)
    public List<PublicProduct> wished(String ids) {
        return pick(ids, 60);
    }

    private List<PublicProduct> pick(String ids, int max) {
        OnlineStoreSettings s = store.settings();
        if (!s.isEnabled()) throw ApiException.notFound(I18n.t("This shop is not available."));
        List<PublicProduct> found = new ArrayList<>();
        for (String part : ids == null ? new String[0] : ids.split(",")) {
            if (found.size() >= max) break;
            try {
                browse(Long.parseLong(part.trim()), s).map(l -> store.publicProduct(l, s)).ifPresent(card -> {
                    if (found.stream().noneMatch(c -> c.id().equals(card.id()))) found.add(card);
                });
            } catch (NumberFormatException ignored) {
                // a bad id is simply skipped
            }
        }
        return front.withRatings(found);
    }

    /** Other products of the same category first, then any others, never the product itself and each product once. */
    private List<PublicProduct> related(OnlineListing l, OnlineStoreSettings s, Long selfId) {
        List<PublicProduct> picked = new ArrayList<>();
        Product base = l.getProduct().getVariantParentId() == null ? l.getProduct() : variants.template(l.getProduct().getVariantParentId()).orElse(l.getProduct());
        String category = base.getCategory() == null ? "" : base.getCategory().getName();
        if (!category.isEmpty()) {
            for (OnlineListing o : listings.findShopPage("", category, PageRequest.of(0, RELATED * 8)).getContent()) addRelated(picked, o, s, selfId);
        }
        if (picked.size() < RELATED) {
            for (OnlineListing o : listings.findShopPage("", "", PageRequest.of(0, RELATED * 8)).getContent()) addRelated(picked, o, s, selfId);
        }
        return front.withRatings(picked);
    }

    private void addRelated(List<PublicProduct> picked, OnlineListing o, OnlineStoreSettings s, Long selfId) {
        if (picked.size() >= RELATED) return;
        PublicProduct card = store.publicProduct(o, s);
        if (!card.id().equals(selfId) && picked.stream().noneMatch(c -> c.id().equals(card.id()))) picked.add(card);
    }

    /** The listing a visitor lands on for an id: the product itself, or, for a product with variants (its template's id), the first option that can be bought. */
    private Optional<OnlineListing> browse(Long id, OnlineStoreSettings s) {
        Optional<OnlineListing> own = listings.findByProductId(id).filter(OnlineListing::isListed);
        if (own.isPresent() && own.get().getProduct().isActive()) return own;
        List<OnlineListing> rows = variants.rows(id);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(variants.lead(rows, r -> !"SOLD_OUT".equals(store.publicVariant(r, s).stock())));
    }

    private OnlineListing shown(Long productId, OnlineStoreSettings s) {
        return browse(productId, s).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
    }

    static String url(Long imageId) {
        return "/api/public/store/product-images/" + imageId;
    }

    /** One gallery picture for the public shop; with a width it is shrunk to at most that many pixels. Only for products that are on the shop. */
    @Transactional(readOnly = true)
    public OnlineStoreService.Image image(Long imageId, int width) {
        OnlineListingImage i = images.findById(imageId).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        OnlineListing l = listings.findById(i.getListingId()).filter(OnlineListing::isListed)
                .filter(x -> x.getProduct().isActive() || (x.getProduct().getVariantAttributes() != null && !x.getProduct().getVariantAttributes().isBlank()))
                .orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        int w = width <= 0 ? 0 : Math.max(64, Math.min(width, MAX_WIDTH));
        if (w == 0 || i.getContentType().equals("image/gif")) return new OnlineStoreService.Image(i.getContentType(), i.getData());
        return OnlineStoreService.scale(i.getContentType(), i.getData(), w);
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public Details details(Long productId) {
        OnlineListing l = listing(productId);
        return details(l);
    }

    private Details details(OnlineListing l) {
        List<ImageDto> list = new ArrayList<>();
        for (Object[] r : images.overview(l.getId())) {
            Long id = ((Number) r[0]).longValue();
            list.add(new ImageDto(id, (String) r[1], ((Number) r[2]).longValue(), ((Number) r[3]).intValue(), url(id)));
        }
        String icon = l.getProduct().getIconDataUrl();
        return new Details(list, readSpecs(l.getSpecs()), icon != null && !icon.isBlank(), MAX_IMAGES);
    }

    private OnlineListing listing(Long productId) {
        return listings.findByProductId(productId).orElseThrow(() -> ApiException.notFound(I18n.t("Put the product on the shop first.")));
    }

    @Transactional
    public Details saveSpecs(Long productId, SpecsRequest in) {
        OnlineListing l = listing(productId);
        l.setSpecs(writeSpecs(check(in == null ? null : in.specs())));
        listings.save(l);
        audit.record("ONLINE_LISTING_SAVED", "Changed the specifications of " + l.getProduct().getName(), "OnlineListing", l.getId());
        return details(l);
    }

    /** Trims the rows, drops the empty ones and refuses a row with only a name or only a value, too many rows or too long texts. */
    static List<SpecRow> check(List<SpecRow> in) {
        List<SpecRow> out = new ArrayList<>();
        if (in == null) return out;
        for (SpecRow r : in) {
            String label = r == null || r.label() == null ? "" : r.label().trim();
            String value = r == null || r.value() == null ? "" : r.value().trim();
            if (label.isEmpty() && value.isEmpty()) continue;
            if (label.isEmpty() || value.isEmpty()) throw ApiException.badRequest(I18n.t("Each specification needs a name and a value."));
            if (label.length() > MAX_LABEL) throw ApiException.badRequest(I18n.t("A specification name can be at most {0} characters.", MAX_LABEL));
            if (value.length() > MAX_VALUE) throw ApiException.badRequest(I18n.t("A specification value can be at most {0} characters.", MAX_VALUE));
            out.add(new SpecRow(label, value));
        }
        if (out.size() > MAX_SPECS) throw ApiException.badRequest(I18n.t("At most {0} specifications can be written for a product.", MAX_SPECS));
        return out;
    }

    private static String writeSpecs(List<SpecRow> rows) {
        if (rows.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(rows);
        } catch (IOException e) {
            throw ApiException.badRequest(I18n.t("The specifications could not be saved."));
        }
    }

    static List<SpecRow> readSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<SpecRow>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }

    @Transactional
    public Details addImage(Long productId, MultipartFile file) throws IOException {
        OnlineListing l = listing(productId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest(I18n.t("Choose a picture to add."));
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest(I18n.t("A picture can be at most 5 MB."));
        if (images.countByListingId(l.getId()) >= MAX_IMAGES) {
            throw ApiException.badRequest(I18n.t("At most {0} extra pictures can be added to a product. Remove one first.", MAX_IMAGES));
        }
        byte[] data = file.getBytes();
        String type = OnlineBannerService.detect(data);
        if (type == null) throw ApiException.badRequest(I18n.t("The picture must be a JPEG, PNG, WebP or GIF picture."));
        if (type.equals("image/jpeg") || type.equals("image/png")) {
            OnlineStoreService.Image scaled = OnlineStoreService.scale(type, data, MAX_WIDTH);
            type = scaled.contentType();
            data = scaled.bytes();
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "picture" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        OnlineListingImage i = new OnlineListingImage();
        i.setListingId(l.getId());
        i.setFileName(name);
        i.setContentType(type);
        i.setSizeBytes(data.length);
        i.setData(data);
        i.setSortOrder(images.maxOrder(l.getId()) + 1);
        images.save(i);
        audit.record("ONLINE_LISTING_SAVED", "Added a picture to " + l.getProduct().getName() + " on the online shop", "OnlineListing", l.getId());
        return details(l);
    }

    @Transactional
    public Details deleteImage(Long imageId) {
        OnlineListingImage i = images.findById(imageId).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        OnlineListing l = listings.findById(i.getListingId()).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        images.delete(i);
        images.flush();
        audit.record("ONLINE_LISTING_SAVED", "Removed a picture of " + l.getProduct().getName() + " on the online shop", "OnlineListing", l.getId());
        return details(l);
    }

    /** Moves a picture one place earlier (up) or later (down) in the gallery. */
    @Transactional
    public Details moveImage(Long imageId, boolean up) {
        OnlineListingImage one = images.findById(imageId).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        OnlineListing l = listings.findById(one.getListingId()).orElseThrow(() -> ApiException.notFound(I18n.t("Not found.")));
        List<OnlineListingImage> all = new ArrayList<>(images.findByListingIdOrderBySortOrderAscIdAsc(l.getId()));
        int i = -1;
        for (int k = 0; k < all.size(); k++) if (all.get(k).getId().equals(imageId)) i = k;
        int j = up ? i - 1 : i + 1;
        if (i >= 0 && j >= 0 && j < all.size()) java.util.Collections.swap(all, i, j);
        for (int k = 0; k < all.size(); k++) all.get(k).setSortOrder(k + 1);
        images.saveAll(all);
        return details(l);
    }
}
