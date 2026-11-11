package com.posapp.onlinestore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The shapes the public shop and the staff screens exchange with the server. */
public final class OnlineStoreDtos {

    private OnlineStoreDtos() {
    }

    // ---------------------------------------------------------------- public shop

    public record PublicZone(Long id, String name, BigDecimal fee, BigDecimal minOrder) {
    }

    /** stock is IN_STOCK, LOW or SOLD_OUT: never an exact number. */
    public record PublicProduct(Long id, String name, String description, BigDecimal price, String category, String imageUrl, String stock) {
    }

    public record PublicShop(String name, String intro, String phone, String pickupAddress, boolean open, String message,
                             boolean pickup, boolean delivery, boolean cash, BigDecimal minOrder, BigDecimal freeDeliveryOver,
                             List<PublicZone> zones, List<String> categories) {
    }

    /** One page of the catalogue. page starts at 0. */
    public record PublicProductPage(List<PublicProduct> items, int page, int size, long total, int totalPages) {
    }

    public record PublicLine(@NotNull Long productId, @NotNull BigDecimal quantity) {
    }

    public record PlaceRequest(@Size(max = 80) String name, @Size(max = 40) String phone, @Size(max = 160) String email,
                               @Size(max = 240) String address, String method, Long zoneId, @Size(max = 300) String notes,
                               String language, String website, @Valid List<PublicLine> lines) {
    }

    public record TrackLine(String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record TrackStep(String status, String note, Instant at) {
    }

    public record Track(String code, String status, String deliveryMethod, String name, String zoneName, BigDecimal subtotal,
                        BigDecimal deliveryFee, BigDecimal total, String rejectReason, Instant placedAt,
                        List<TrackLine> lines, List<TrackStep> history) {
    }

    // ---------------------------------------------------------------- staff

    public record OrderLineDto(Long productId, String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record EventDto(String status, String note, String by, Instant at) {
    }

    public record OrderDto(Long id, String code, String status, String deliveryMethod, String paymentMethod, String customerName,
                           String customerPhone, String customerEmail, String customerAddress, String zoneName, BigDecimal deliveryFee,
                           String notes, BigDecimal subtotal, BigDecimal total, String language, Long salesOrderId, String salesOrderNumber,
                           Long invoiceId, String invoiceNumber, String rejectReason, String decidedBy, Instant decidedAt, Instant createdAt,
                           List<OrderLineDto> lines, List<EventDto> events) {
    }

    public record RejectRequest(@NotNull @Size(min = 1, max = 160) String reason) {
    }

    /** overrideShortage: accept even when the stock does not cover the order (the sale order is then confirmed over-promised). */
    public record AcceptRequest(boolean overrideShortage) {
    }

    public record StatusNote(@Size(max = 200) String note) {
    }

    /** locationId: the warehouse location the goods leave from (needed when multi-warehouse is on). collectPayment: record the cash now. */
    public record CompleteRequest(Long locationId, Boolean collectPayment, String method) {
    }

    public record SettingsDto(boolean enabled, boolean paused, String pauseMessage, String shopName, String introText, String contactPhone,
                              String pickupAddress, boolean pickupEnabled, boolean deliveryEnabled, boolean codEnabled, int stockBuffer,
                              BigDecimal minOrder, BigDecimal freeDeliveryOver, int maxPendingPerPhone, int maxPerIpPerHour,
                              Long deliveryProductId, String deliveryProductName, String notifyEmail) {
    }

    public record ListingDto(Long id, Long productId, String productName, String sku, String category, BigDecimal unitPrice, boolean listed,
                             String webTitle, String webDescription, BigDecimal onlinePrice, int sortOrder, boolean hasImage, String stock) {
    }

    public record ListingRequest(@NotNull Long productId, Boolean listed, @Size(max = 160) String webTitle, @Size(max = 2000) String webDescription,
                                 BigDecimal onlinePrice, Integer sortOrder) {
    }

    public record ZoneDto(Long id, String name, BigDecimal fee, BigDecimal minOrder, boolean active, int sortOrder) {
    }

    public record ZoneRequest(@NotNull @Size(min = 1, max = 80) String name, BigDecimal fee, BigDecimal minOrder, Boolean active, Integer sortOrder) {
    }
}
