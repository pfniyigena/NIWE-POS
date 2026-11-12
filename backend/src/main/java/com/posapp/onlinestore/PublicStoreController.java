package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.CouponCheck;
import com.posapp.onlinestore.OnlineStoreDtos.CouponResult;
import com.posapp.onlinestore.OnlineStoreDtos.PayRequest;
import com.posapp.onlinestore.OnlineStoreDtos.PlaceRequest;
import com.posapp.onlinestore.OnlineStoreDtos.PublicProductPage;
import com.posapp.onlinestore.OnlineStoreDtos.PublicShop;
import com.posapp.onlinestore.OnlineStoreDtos.Track;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.posapp.onlinestore.payment.SimulatedPaymentProvider;

import java.util.concurrent.TimeUnit;

/**
 * The public shop: no login. A visitor can read what is on sale, send an order that waits for staff, and follow it by its code. It is separate from the
 * staff API under /api/online-store, answers "not available" until the module is on and the shop is switched on, and never exposes costs or other customers.
 */
@RestController
@RequestMapping("/api/public/store")
public class PublicStoreController {

    private final OnlineStoreService service;
    private final ModuleService moduleService;
    private final OnlinePaymentService payments;
    private final SimulatedPaymentProvider simulated;
    private final ShopAccountService accounts;
    private final OnlineReturnService returns;

    public PublicStoreController(OnlineStoreService service, ModuleService moduleService, OnlinePaymentService payments, SimulatedPaymentProvider simulated,
                                 ShopAccountService accounts, OnlineReturnService returns) {
        this.returns = returns;
        this.accounts = accounts;
        this.payments = payments;
        this.simulated = simulated;
        this.service = service;
        this.moduleService = moduleService;
    }

    /**
     * The address the visitor came from, for the per-connection limits. Only the connection's own address is used: a header the visitor can set
     * (X-Forwarded-For) would let a script dodge the limit. Behind a reverse proxy, set {@code server.forward-headers-strategy=native} so the
     * server itself replaces this address with the one the trusted proxy reports.
     */
    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @GetMapping("/shop")
    public PublicShop shop() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.shop();
    }

    /** The catalogue, a page at a time. page starts at 0; size is capped; q searches the name; category is a category name from the shop's list. */
    @GetMapping("/products")
    public PublicProductPage products(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "16") int size,
                                      @RequestParam(required = false) String q, @RequestParam(required = false) String category) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.products(page, size, q, category);
    }

    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @RequestParam(defaultValue = "0") int w) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        OnlineStoreService.Image image = service.image(id, w);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    /** "Apply" at checkout: what a coupon is worth on this cart. Changes nothing. */
    @PostMapping("/coupons/check")
    public CouponResult checkCoupon(@Valid @RequestBody CouponCheck request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.checkCoupon(request, ip(http), accounts.optionalCustomerId(http.getHeader(ShopAccountController.TOKEN_HEADER)));
    }

    @PostMapping("/orders")
    public Track place(@Valid @RequestBody PlaceRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        // A signed-in customer's order joins the account; a missing or stale token just makes it a guest order.
        return service.place(request, ip(http), accounts.optionalCustomerId(http.getHeader(ShopAccountController.TOKEN_HEADER)));
    }

    @GetMapping("/orders/{code}")
    public Track track(@PathVariable String code, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.trackByCode(code, ip(http));
    }

    @PostMapping("/orders/{code}/cancel")
    public Track cancel(@PathVariable String code, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.cancelByCustomer(code, ip(http));
    }

    /** What can still be returned on a completed order, and the returns already asked. */
    @GetMapping("/orders/{code}/returns")
    public OnlineStoreDtos.ReturnOptions returnOptions(@PathVariable String code, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return returns.options(code, ip(http));
    }

    /** The customer asks to return goods. Needs the phone number of the order, or the sign-in token of the account that holds it. */
    @PostMapping("/orders/{code}/returns")
    public OnlineStoreDtos.PublicReturn requestReturn(@PathVariable String code, @Valid @RequestBody OnlineStoreDtos.ReturnRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return returns.request(code, request, ip(http), accounts.optionalCustomerId(http.getHeader(ShopAccountController.TOKEN_HEADER)));
    }

    /** The customer pays again, or the other way, for an order that is waiting for its payment. */
    @PostMapping("/orders/{code}/pay")
    public Track pay(@PathVariable String code, @Valid @RequestBody PayRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.payAgain(code, request, ip(http));
    }

    /**
     * Where the payment provider tells us a payment was made or failed. Not gated by the module switch on purpose: money that arrives must always be
     * recorded. A call without a valid signature is answered 401 and changes nothing; a valid one is always answered ok.
     * POST for most providers; PUT because MTN MoMo calls back with PUT.
     */
    @RequestMapping(value = "/payments/{provider}/webhook", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PUT}, consumes = MediaType.ALL_VALUE)
    public java.util.Map<String, Object> webhook(@PathVariable String provider, @RequestBody String body, HttpServletRequest http) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        java.util.Collections.list(http.getHeaderNames()).forEach(n -> headers.put(n.toLowerCase(java.util.Locale.ROOT), http.getHeader(n)));
        payments.processWebhook(provider, body, headers, ip(http));
        return java.util.Map.of("ok", true);
    }

    // ---- the test provider's pretend payment page (404 unless the simulated provider is switched on)

    @GetMapping("/payments/simulated/{reference}")
    public SimulatedPaymentProvider.TestPayment testPayment(@PathVariable String reference) {
        return simulated.find(reference);
    }

    @PostMapping("/payments/simulated/{reference}/{decision}")
    public SimulatedPaymentProvider.TestPayment decideTestPayment(@PathVariable String reference, @PathVariable String decision, HttpServletRequest http) {
        if (!decision.equals("approve") && !decision.equals("decline")) throw com.posapp.common.ApiException.notFound("Not found.");
        SimulatedPaymentProvider.Signed signed = simulated.decide(reference, decision.equals("approve"));
        // The pretend provider calls our own webhook, signature and all, exactly as a real one would.
        payments.processWebhook(SimulatedPaymentProvider.CODE, signed.body(), java.util.Map.of("x-signature", signed.signature()), ip(http));
        return simulated.find(reference);
    }
}
