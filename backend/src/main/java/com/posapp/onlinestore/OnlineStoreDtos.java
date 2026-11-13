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
    public record PublicProduct(Long id, String name, String description, BigDecimal price, String category, String imageUrl, String stock,
                                 BigDecimal wasPrice, double rating, int ratingCount) {
    }

    /** A link of the footer (label and address) written by staff in Shop settings. */
    public record PublicLink(String label, String url) {
    }

    /** The front page's wording. Anything empty is simply not shown. */
    public record PublicPage(String announcementText, String announcementLink, String heroTitle, String heroSubtitle, String heroButtonText,
                             String heroButtonLink, String heroImageUrl, String aboutText, String contactEmail, String openingHours,
                             List<PublicLink> footerLinks, List<PublicLink> socialLinks) {
    }

    public record PublicReview(String productName, String reviewerName, int rating, String text, Instant at) {
    }

    /** The rows of the front page, chosen by staff, the banner pictures (addresses, in sliding order) and the best of the approved reviews. */
    public record PublicHome(List<PublicProduct> featured, List<PublicProduct> bestSellers, List<PublicProduct> offers, List<PublicProduct> newArrivals,
                             List<String> banners, List<PublicReview> reviews, Double ratingAverage, long ratingCount) {
    }

    public record PublicShop(String name, String intro, String phone, String pickupAddress, boolean open, String message,
                             boolean pickup, boolean delivery, boolean cash, BigDecimal minOrder, BigDecimal freeDeliveryOver,
                             List<PublicZone> zones, List<String> categories, boolean payOnline, List<String> payMethods, boolean accounts, String currency, boolean coupons,
                             String textChannel, boolean cartReminders, boolean loyalty, int loyaltyMaxPercent, PublicPage page) {
    }

    /** One page of the catalogue. page starts at 0. */
    public record PublicProductPage(List<PublicProduct> items, int page, int size, long total, int totalPages) {
    }

    public record PublicLine(@NotNull Long productId, @NotNull BigDecimal quantity) {
    }

    public record PlaceRequest(@Size(max = 80) String name, @Size(max = 40) String phone, @Size(max = 160) String email,
                               @Size(max = 240) String address, String method, Long zoneId, @Size(max = 300) String notes,
                               String language, String website, @Valid List<PublicLine> lines,
                               String payment, @Size(max = 40) String payPhone, @Size(max = 40) String couponCode, Boolean textUpdates, Integer pointsToUse) {
    }

    /** "Apply" at checkout: only asks what the code is worth now; the order asks again when it is placed. */
    public record CouponCheck(@Size(max = 40) String code, String method, Long zoneId, @Size(max = 40) String phone, @Valid List<PublicLine> lines) {
    }

    /** discount comes off the items, feeWaived off the delivery fee; type is PERCENT, FIXED or FREE_DELIVERY. */
    public record CouponResult(String code, String type, BigDecimal discount, BigDecimal feeWaived) {
    }

    public record TrackLine(String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record TrackStep(String status, String note, Instant at) {
    }

    /**
     * What the customer sees about payment. method is COD or ONLINE; status is NONE, AWAITING, FAILED, PAID, REFUND_DUE or REFUNDED.
     * redirectUrl is where to send the customer to finish paying (while a try is pending); canRetry means "Try again" may be shown.
     */
    public record PaymentInfo(String method, String status, String channel, String redirectUrl, boolean canRetry, Instant expiresAt, BigDecimal paidAmount) {
    }

    public record Track(String code, String status, String deliveryMethod, String name, String zoneName, BigDecimal subtotal,
                        BigDecimal deliveryFee, BigDecimal total, String rejectReason, Instant placedAt,
                        List<TrackLine> lines, List<TrackStep> history, PaymentInfo payment,
                        String couponCode, BigDecimal discount, BigDecimal feeWaived, BigDecimal loyaltyDiscount, int pointsEarned) {
    }

    /** The customer pays again (or with another way) for an order that is waiting for payment. */
    public record PayRequest(String payment, @Size(max = 40) String payPhone) {
    }

    // ---------------------------------------------------------------- customer accounts

    /** identifier is an e-mail address or a phone number already saved on an account. */
    public record AccountCodeRequest(@Size(max = 160) String identifier, String language) {
    }

    /** Always sent = true, whether or not an account exists. */
    public record AccountCodeResult(boolean sent) {
    }

    public record AccountVerifyRequest(@Size(max = 160) String identifier, @Size(max = 12) String code) {
    }

    public record AccountProfile(String email, String phone, String name, String address, String language, boolean phoneVerified, int points) {
    }

    /** The code that proves the phone number saved on the account. */
    public record PhoneVerifyRequest(@Size(max = 12) String code) {
    }

    public record AccountSession(String token, AccountProfile profile) {
    }

    public record AccountProfileRequest(@Size(max = 80) String name, @Size(max = 40) String phone, @Size(max = 240) String address, String language) {
    }

    public record AccountOrder(String code, String status, String deliveryMethod, BigDecimal total, Instant placedAt, int items, String paymentStatus) {
    }

    public record ReorderLine(PublicProduct product, BigDecimal quantity) {
    }

    /** lines are on the shop now at today's price; unavailable names the products that are gone or sold out. */
    public record Reorder(List<ReorderLine> lines, List<String> unavailable) {
    }

    // ---------------------------------------------------------------- loyalty points

    /** kind: EARN, SPEND, SPEND_BACK, RET_BACK, RET_TAKE or ADJUST; points is signed. */
    public record LoyaltyEntryDto(Long id, String kind, int points, int balanceAfter, String orderCode, String note, Instant at) {
    }

    /** What a customer (or staff looking at one customer) sees: the balance, the rules and the latest changes. */
    public record LoyaltyView(boolean enabled, int points, int pointsPer100, int maxPercent, List<LoyaltyEntryDto> entries) {
    }

    public record LoyaltyCustomerDto(Long id, String name, String email, String phone, int points) {
    }

    public record LoyaltyList(long customersWithPoints, long pointsOutstanding, List<LoyaltyCustomerDto> items) {
    }

    public record LoyaltyAdjust(@NotNull Integer points, @Size(max = 200) String reason) {
    }

    // ---------------------------------------------------------------- abandoned carts

    /** The cart a visitor has so far, sent when they type an e-mail address at checkout (nothing is kept unless the shop has the reminders on). */
    public record CartSave(@Size(max = 160) String email, @Size(max = 2) String language, @Size(max = 100) String website, @Valid List<PublicLine> lines) {
    }

    public record CartToken(@Size(max = 64) String token) {
    }

    /** The cart behind the link of a reminder, as the shop has it today. */
    public record RestoredCart(List<ReorderLine> lines, List<String> unavailable, String email) {
    }

    public record CartDto(Long id, String email, String status, int items, int quantity, Instant savedAt, int reminders, Instant firstReminderAt,
                          Instant secondReminderAt, Instant recoveredAt, String lastError) {
    }

    public record CartList(long waiting, long reminded, long done, long recovered, long stopped, long expired, List<CartDto> items) {
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
                           String paymentStatus, String paymentChannel, String paymentReference, BigDecimal paidAmount, Instant paidAt,
                           Instant refundedAt, String refundReference, String couponCode, BigDecimal discountAmount, BigDecimal feeWaived,
                           BigDecimal refundedTotal, List<OrderLineDto> lines, List<EventDto> events,
                           int loyaltyPointsUsed, BigDecimal loyaltyDiscount, int loyaltyPointsEarned) {
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
                              Long deliveryProductId, String deliveryProductName, String notifyEmail,
                              int unansweredAlertMinutes, boolean onlinePaymentsEnabled, String paymentProvider, int paymentExpiryMinutes, boolean accountsEnabled,
                              String textProvider, String textChannel, String textCountryCode, int textDailyLimit, String notifyPhone, int returnWindowDays,
                              boolean cartRemindersEnabled, boolean loyaltyEnabled, int loyaltyPointsPer100, int loyaltyMaxPercent) {
    }

    // ---------------------------------------------------------------- online payments (staff)

    public record ProviderDto(String code, String label, boolean available, List<String> methods) {
    }

    /** manual: the refund was made outside the system and reference says which; otherwise the provider is asked to refund. */
    public record RefundRequest(boolean manual, @Size(max = 80) String reference, @Size(max = 160) String note) {
    }

    public record ResolveRequest(@Size(max = 160) String note) {
    }

    /** flag is REVIEW, REFUND_DUE, STUCK_PENDING or empty: what needs a person. */
    public record PaymentRow(Long id, Long orderId, String orderCode, Instant createdAt, String provider, String reference, String providerReference,
                             String method, String phone, BigDecimal amount, String currency, String status, String failureReason,
                             String orderStatus, String orderPaymentStatus, String flag) {
    }

    /** paid = confirmed payments in the period; inBooks = of those, on completed orders (recorded as invoice payments); held = on orders not completed yet;
     *  refundDue / refunded = money to give back / given back. */
    public record ReconSummary(BigDecimal paid, BigDecimal inBooks, BigDecimal held, BigDecimal refundDue, BigDecimal refunded,
                               long toReview, long failed, long stuck) {
    }

    public record Reconciliation(ReconSummary summary, List<PaymentRow> rows) {
    }

    // ---------------------------------------------------------------- text messages (staff)

    public record TextProviderDto(String code, String label, boolean available, List<String> channels) {
    }

    public record TextMessageDto(Long id, Instant at, String channel, String to, String purpose, String body, String status, String provider, String error, Long orderId) {
    }

    public record TextPage(List<TextMessageDto> items, int page, int size, long total, int totalPages) {
    }

    public record TextTestRequest(@Size(max = 40) String phone, @Size(max = 200) String text) {
    }

    /** status is SENT, FAILED or SKIPPED; error says why not. */
    public record TextTestResult(String status, String error, String to) {
    }

    public record ListingDto(Long id, Long productId, String productName, String sku, String category, BigDecimal unitPrice, boolean listed,
                             String webTitle, String webDescription, BigDecimal onlinePrice, int sortOrder, boolean hasImage, String stock,
                             boolean featured, boolean bestSeller, boolean specialOffer, boolean newArrival, StockInfo stockInfo) {
    }

    /**
     * Why a product shows the stock it shows: units in sellable warehouse locations, minus units promised on confirmed sales orders (reserved), minus the shop's
     * safety buffer, is what the shop may still sell. tracked = false: stock is not tracked for the product, so the shop always shows it as in stock (the other
     * figures are then null).
     */
    public record StockInfo(boolean tracked, BigDecimal inWarehouses, BigDecimal reserved, int buffer, BigDecimal sellable) {
    }

    public record ListingRequest(@NotNull Long productId, Boolean listed, @Size(max = 160) String webTitle, @Size(max = 2000) String webDescription,
                                 BigDecimal onlinePrice, Integer sortOrder, Boolean featured, Boolean bestSeller, Boolean specialOffer, Boolean newArrival) {
    }

    public record ZoneDto(Long id, String name, BigDecimal fee, BigDecimal minOrder, boolean active, int sortOrder) {
    }

    /** used = orders holding a use of it (not rejected or cancelled). */
    public record CouponDto(Long id, String code, String description, String type, BigDecimal value, BigDecimal maxDiscount, BigDecimal minOrder,
                            java.time.LocalDate validFrom, java.time.LocalDate validUntil, Integer maxUses, boolean oncePerCustomer, boolean active, long used) {
    }

    public record CouponRequest(@NotNull @Size(min = 3, max = 40) String code, @Size(max = 160) String description, @NotNull String type, BigDecimal value,
                                BigDecimal maxDiscount, BigDecimal minOrder, java.time.LocalDate validFrom, java.time.LocalDate validUntil,
                                Integer maxUses, Boolean oncePerCustomer, Boolean active) {
    }

    public record ZoneRequest(@NotNull @Size(min = 1, max = 80) String name, BigDecimal fee, BigDecimal minOrder, Boolean active, Integer sortOrder) {
    }

    // ---------------------------------------------------------------- returns (public)

    /** What the customer sees about one return; the lines show what they are sending back and what it is worth. */
    public record PublicReturnLine(String name, BigDecimal quantity, BigDecimal amount) {
    }

    public record PublicReturn(String code, String status, String reason, String staffNote, BigDecimal refundAmount, String refundMethod,
                               String refundReference, Instant requestedAt, Instant decidedAt, Instant refundedAt, List<PublicReturnLine> lines) {
    }

    /** returnable is what may still be asked back on the line (what was bought, less what other returns already hold). */
    public record ReturnOptionLine(Long lineId, String name, BigDecimal quantity, BigDecimal unitPaid, BigDecimal returnable) {
    }

    /** allowed is false with a message saying why not; lastDay is the end of the return period. */
    public record ReturnOptions(boolean allowed, String message, int windowDays, Instant lastDay, List<ReturnOptionLine> lines, List<PublicReturn> returns) {
    }

    public record ReturnLineRequest(@NotNull Long lineId, @NotNull BigDecimal quantity) {
    }

    /** phone is the number on the order (not needed by a signed-in customer whose account holds the order). */
    public record ReturnRequest(@Size(max = 40) String phone, @NotNull @Size(min = 3, max = 300) String reason,
                                @NotNull @Size(min = 1, max = 40) List<ReturnLineRequest> lines) {
    }

    // ---------------------------------------------------------------- returns (staff)

    public record ReturnLineDto(Long lineId, String name, BigDecimal quantity, BigDecimal amount) {
    }

    public record ReturnDto(Long id, String code, Long orderId, String orderCode, String customerName, String customerPhone, String customerEmail,
                            String status, String reason, String staffNote, BigDecimal refundAmount, String refundMethod, String refundReference,
                            String paymentMethod, String paymentStatus, Instant requestedAt, String decidedBy, Instant decidedAt, String refundedBy,
                            Instant refundedAt, String salesOrderReturnNumber, List<ReturnLineDto> lines) {
    }

    public record ReturnDecision(@Size(max = 300) String note) {
    }

    /** manual = the refund was made outside (mobile money, the provider's dashboard); reference is then required for money paid online. */
    public record ReturnProcess(Long locationId, boolean manual, @Size(max = 80) String reference, @Size(max = 300) String note) {
    }

    // ---------------------------------------------------------------- front page (v1.35.0)

    /** Staff's wording of the front page (Shop settings > Front page). */
    public record PageContent(@Size(max = 200) String announcementText, @Size(max = 300) String announcementLink, @Size(max = 120) String heroTitle,
                              @Size(max = 300) String heroSubtitle, @Size(max = 40) String heroButtonText, @Size(max = 300) String heroButtonLink,
                              @Size(max = 300) String heroImageUrl, @Size(max = 400) String aboutText, @Size(max = 160) String contactEmail,
                              @Size(max = 200) String openingHours, @Size(max = 1200) String footerLinks, @Size(max = 800) String socialLinks) {
    }

    // reviews

    public record ReviewItem(Long productId, String name, Integer rating, String text, String status) {
    }

    public record ReviewOptions(boolean canReview, String why, List<ReviewItem> items) {
    }

    public record ReviewRequest(@Size(max = 40) String phone, Long productId, int rating, @Size(max = 1000) String text, @Size(max = 40) String name) {
    }

    public record ReviewDto(Long id, String orderCode, Long productId, String productName, String reviewerName, int rating, String text, String status,
                            Instant createdAt, String moderatedBy) {
    }

    // newsletter

    /** website is a hidden field real visitors leave empty; a robot that fills it is ignored. */
    public record NewsletterRequest(@Size(max = 160) String email, @Size(max = 2) String language, @Size(max = 100) String website) {
    }

    public record NewsletterToken(@Size(max = 64) String token) {
    }

    public record NewsletterDto(Long id, String email, String language, String status, Instant createdAt, Instant confirmedAt, Instant unsubscribedAt) {
    }

    public record CampaignRequest(@Size(max = 160) String subject, @Size(max = 5000) String body, @Size(max = 60) String buttonLabel, @Size(max = 300) String buttonLink,
                                  @Size(max = 3) String audience) {
    }

    public record CampaignTestRequest(@Size(max = 160) String email) {
    }

    public record CampaignDto(Long id, String subject, String body, String buttonLabel, String buttonLink, String audience, String status, int recipients, int sent, int failed,
                              int skipped, int pending, String createdBy, Instant createdAt, Instant startedAt, Instant finishedAt, String lastError) {
    }

    public record DeliveryDto(Long id, String email, String language, String status, String error, Instant sentAt) {
    }
}
