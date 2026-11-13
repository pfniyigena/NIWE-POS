package com.posapp.onlinestore;

import com.posapp.common.ListPaging;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Online Store for staff: the order queue, the shop settings, the products on the shop and the delivery areas. */
@RestController
@RequestMapping("/api/online-store")
public class OnlineStoreController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineOrderService orders;
    private final OnlineStoreAdminService admin;
    private final OnlinePaymentService payments;
    private final OnlineReturnService returns;
    private final OnlineReviewService reviews;
    private final NewsletterService newsletter;
    private final NewsletterCampaignService campaigns;
    private final CartReminderService cartReminders;
    private final ShopLoyaltyService loyalty;

    public OnlineStoreController(OnlineOrderService orders, OnlineStoreAdminService admin, OnlinePaymentService payments, OnlineReturnService returns,
                                 OnlineReviewService reviews, NewsletterService newsletter, NewsletterCampaignService campaigns,
                                 CartReminderService cartReminders, ShopLoyaltyService loyalty) {
        this.loyalty = loyalty;
        this.cartReminders = cartReminders;
        this.reviews = reviews;
        this.newsletter = newsletter;
        this.campaigns = campaigns;
        this.returns = returns;
        this.payments = payments;
        this.orders = orders;
        this.admin = admin;
    }

    // ---------------------------------------------------------------- orders

    @PreAuthorize(VIEW)
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(orders.list(q, status), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/orders/pending-count")
    public Map<String, Long> pendingCount() {
        Map<String, Long> out = new java.util.LinkedHashMap<>(orders.pendingSummary());
        out.putAll(returns.counts());
        out.putAll(reviews.counts());
        return out;
    }

    @PreAuthorize(VIEW)
    @GetMapping("/orders/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orders.get(id);
    }

    /** Gives the money back for an order that was paid online and then cancelled or rejected. */
    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/refund")
    public OrderDto refund(@PathVariable Long id, @RequestBody(required = false) RefundRequest request) {
        return orders.refund(id, request);
    }

    /** Online payments for the reconciliation screen: what was paid, what is in the books, what is waiting, what needs a person. */
    @PreAuthorize(VIEW)
    @GetMapping("/payments")
    public Reconciliation reconciliation(@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                                         @RequestParam(required = false) String status) {
        return orders.reconciliation(from, to, status);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/payments/{id}/resolve")
    public ResponseEntity<Void> resolvePayment(@PathVariable Long id, @RequestBody ResolveRequest request) {
        orders.resolvePayment(id, request == null ? null : request.note());
        return ResponseEntity.noContent().build();
    }

    // ---- text messages

    @PreAuthorize(VIEW)
    @GetMapping("/text-providers")
    public List<TextProviderDto> textProviders() {
        return admin.textProviders();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/texts")
    public TextPage texts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.textLog(page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/texts/test")
    public TextTestResult testText(@Valid @RequestBody TextTestRequest request) {
        return admin.testText(request);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/payment-providers")
    public List<ProviderDto> paymentProviders() {
        return payments.providerList();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/accept")
    public OrderDto accept(@PathVariable Long id, @RequestBody(required = false) AcceptRequest request) {
        return orders.accept(id, request != null && request.overrideShortage());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/reject")
    public OrderDto reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return orders.reject(id, request.reason());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/pack")
    public OrderDto pack(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.pack(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/out-for-delivery")
    public OrderDto outForDelivery(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.outForDelivery(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/ready-for-pickup")
    public OrderDto readyForPickup(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.readyForPickup(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/complete")
    public OrderDto complete(@PathVariable Long id, @RequestBody(required = false) CompleteRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return orders.complete(id, request, principal.getUser());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @RequestBody(required = false) StatusNote request) {
        return orders.cancel(id, request == null ? null : request.note());
    }

    // ---------------------------------------------------------------- returns

    @PreAuthorize(VIEW)
    @GetMapping("/returns")
    public ResponseEntity<List<ReturnDto>> returnList(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(returns.list(q, status), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/returns/{id}")
    public ReturnDto returnGet(@PathVariable Long id) {
        return returns.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/returns/{id}/approve")
    public ReturnDto returnApprove(@PathVariable Long id, @RequestBody(required = false) ReturnDecision request) {
        return returns.approve(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/returns/{id}/refuse")
    public ReturnDto returnRefuse(@PathVariable Long id, @RequestBody(required = false) ReturnDecision request) {
        return returns.refuse(id, request == null ? null : request.note());
    }

    /** The goods are back: stock and books through the sale order return, and the money given back the way the customer paid. */
    @PreAuthorize(MANAGE)
    @PostMapping("/returns/{id}/process")
    public ReturnDto returnProcess(@PathVariable Long id, @RequestBody(required = false) ReturnProcess request) {
        return returns.process(id, request);
    }

    // ---------------------------------------------------------------- settings

    @PreAuthorize(VIEW)
    @GetMapping("/settings")
    public SettingsDto settings() {
        return admin.settings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsDto request) {
        return admin.update(request);
    }

    // ---------------------------------------------------------------- products on the shop

    @PreAuthorize(VIEW)
    @GetMapping("/listings")
    public List<ListingDto> listings() {
        return admin.listings();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/listings")
    public ListingDto saveListing(@Valid @RequestBody ListingRequest request) {
        return admin.saveListing(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> removeListing(@PathVariable Long id) {
        admin.removeListing(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- coupons

    @PreAuthorize(VIEW)
    @GetMapping("/coupons")
    public List<CouponDto> coupons() {
        return admin.coupons();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/coupons")
    public CouponDto createCoupon(@Valid @RequestBody CouponRequest request) {
        return admin.createCoupon(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/coupons/{id}")
    public CouponDto updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return admin.updateCoupon(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        admin.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- delivery areas

    @PreAuthorize(VIEW)
    @GetMapping("/zones")
    public List<ZoneDto> zones() {
        return admin.zones();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/zones")
    public ZoneDto createZone(@Valid @RequestBody ZoneRequest request) {
        return admin.createZone(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/zones/{id}")
    public ZoneDto updateZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return admin.updateZone(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        admin.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- front page (v1.35.0)

    @PreAuthorize(VIEW)
    @GetMapping("/page-content")
    public PageContent pageContent() {
        return admin.pageContent();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/page-content")
    public PageContent savePageContent(@Valid @RequestBody PageContent request) {
        return admin.savePageContent(request);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/reviews")
    public ResponseEntity<List<ReviewDto>> reviewList(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(reviews.list(q, status), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reviews/{id}/approve")
    public ReviewDto approveReview(@PathVariable Long id) {
        return reviews.approve(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reviews/{id}/hide")
    public ReviewDto hideReview(@PathVariable Long id) {
        return reviews.hide(id);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviews.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter")
    public ResponseEntity<List<NewsletterDto>> newsletterList(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(newsletter.list(status, q), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter/counts")
    public Map<String, Long> newsletterCounts() {
        return newsletter.counts();
    }

    /** The confirmed addresses as a CSV file. */
    @PreAuthorize(MANAGE)
    @GetMapping("/newsletter/export")
    public ResponseEntity<byte[]> newsletterExport() {
        return ResponseEntity.ok().header("Content-Type", "text/csv; charset=UTF-8").header("Content-Disposition", "attachment; filename=\"newsletter-subscribers.csv\"")
                .body(newsletter.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/newsletter/{id}")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable Long id) {
        newsletter.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Sending the newsletter (v1.38.0) ----

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter/campaigns")
    public List<CampaignDto> campaignList() {
        return campaigns.list();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter/campaigns/info")
    public Map<String, Object> campaignInfo() {
        return campaigns.info();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter/campaigns/{id}")
    public CampaignDto campaignGet(@PathVariable Long id) {
        return campaigns.get(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/newsletter/campaigns/{id}/deliveries")
    public ResponseEntity<List<DeliveryDto>> campaignDeliveries(@PathVariable Long id, @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(campaigns.deliveriesOf(id, status), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/newsletter/campaigns")
    public CampaignDto campaignCreate(@Valid @RequestBody CampaignRequest request) {
        return campaigns.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/newsletter/campaigns/{id}")
    public CampaignDto campaignUpdate(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        return campaigns.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/newsletter/campaigns/{id}")
    public ResponseEntity<Void> campaignDelete(@PathVariable Long id) {
        campaigns.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/newsletter/campaigns/{id}/test")
    public Map<String, Object> campaignTest(@PathVariable Long id, @Valid @RequestBody CampaignTestRequest request) {
        campaigns.sendTest(id, request.email());
        return Map.of("ok", true);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/newsletter/campaigns/{id}/send")
    public CampaignDto campaignSend(@PathVariable Long id) {
        return campaigns.start(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/newsletter/campaigns/{id}/cancel")
    public CampaignDto campaignCancel(@PathVariable Long id) {
        return campaigns.cancel(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/newsletter/campaigns/{id}/retry")
    public CampaignDto campaignRetry(@PathVariable Long id) {
        return campaigns.retryFailed(id);
    }

    // ---------------------------------------------------------------- abandoned carts

    @PreAuthorize(VIEW)
    @GetMapping("/carts")
    public CartList cartList(@RequestParam(required = false) String status) {
        return cartReminders.list(status);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/carts/{id}")
    public ResponseEntity<Void> cartDelete(@PathVariable Long id) {
        cartReminders.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- loyalty points

    @PreAuthorize(VIEW)
    @GetMapping("/loyalty")
    public LoyaltyList loyaltyList(@RequestParam(required = false) String query) {
        return loyalty.list(query);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/loyalty/{customerId}")
    public LoyaltyView loyaltyLedger(@PathVariable Long customerId) {
        return loyalty.ledger(customerId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/loyalty/{customerId}/adjust")
    public LoyaltyView loyaltyAdjust(@PathVariable Long customerId, @Valid @RequestBody LoyaltyAdjust request) {
        return loyalty.adjust(customerId, request);
    }
}
