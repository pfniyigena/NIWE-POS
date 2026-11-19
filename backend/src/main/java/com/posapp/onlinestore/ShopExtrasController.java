package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.AddressDto;
import com.posapp.onlinestore.OnlineStoreDtos.AddressRequest;
import com.posapp.onlinestore.OnlineStoreDtos.InvoiceRequest;
import com.posapp.onlinestore.OnlineStoreDtos.PublicProduct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The public shop's saved addresses, wishlist and invoice download (v1.47.0). Answers "not available" until the module and the shop are on. */
@RestController
@RequestMapping("/api/public/store")
public class ShopExtrasController {

    private static final String TOKEN = ShopAccountController.TOKEN_HEADER;

    private final ShopExtrasService extras;
    private final ShopAccountService accounts;
    private final OnlineProductPageService pages;
    private final ModuleService moduleService;

    public ShopExtrasController(ShopExtrasService extras, ShopAccountService accounts, OnlineProductPageService pages, ModuleService moduleService) {
        this.extras = extras;
        this.accounts = accounts;
        this.pages = pages;
        this.moduleService = moduleService;
    }

    private void on() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
    }

    @GetMapping("/account/addresses")
    public List<AddressDto> addresses(@RequestHeader(value = TOKEN, required = false) String token) {
        on();
        return extras.addresses(token);
    }

    @PostMapping("/account/addresses")
    public List<AddressDto> addAddress(@RequestHeader(value = TOKEN, required = false) String token, @Valid @RequestBody AddressRequest request) {
        on();
        return extras.saveAddress(token, null, request);
    }

    @PutMapping("/account/addresses/{id}")
    public List<AddressDto> updateAddress(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        on();
        return extras.saveAddress(token, id, request);
    }

    @DeleteMapping("/account/addresses/{id}")
    public List<AddressDto> deleteAddress(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long id) {
        on();
        return extras.deleteAddress(token, id);
    }

    /** The product ids on the signed-in customer's wishlist, newest first. */
    @GetMapping("/account/wishlist")
    public List<Long> wishlist(@RequestHeader(value = TOKEN, required = false) String token) {
        on();
        return extras.wishlist(token);
    }

    @PutMapping("/account/wishlist/{productId}")
    public List<Long> addWish(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long productId) {
        on();
        return extras.addWish(token, productId);
    }

    @DeleteMapping("/account/wishlist/{productId}")
    public List<Long> removeWish(@RequestHeader(value = TOKEN, required = false) String token, @PathVariable Long productId) {
        on();
        return extras.removeWish(token, productId);
    }

    /** The hearts given as a guest in this browser: {@code {"ids":[1,2]}}. */
    @PostMapping("/account/wishlist/merge")
    public List<Long> mergeWishes(@RequestHeader(value = TOKEN, required = false) String token, @RequestBody Map<String, List<Long>> body) {
        on();
        return extras.mergeWishes(token, body == null ? null : body.get("ids"));
    }

    /** The products with these ids (comma separated, at most 60), for the wishlist page. */
    @GetMapping("/wishlist")
    public List<PublicProduct> wished(@RequestParam(required = false) String ids) {
        on();
        return pages.wished(ids);
    }

    /** The invoice of a completed order as a PDF. Needs the phone number of the order, or the sign-in token of the account that holds it. */
    @PostMapping("/orders/{code}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable String code, @Valid @RequestBody InvoiceRequest request, HttpServletRequest http,
                                          @RequestHeader(value = TOKEN, required = false) String token) {
        on();
        byte[] pdf = extras.invoicePdf(code, request.phone(), http.getRemoteAddr(), accounts.optionalCustomerId(token));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice-" + code.replaceAll("[^A-Za-z0-9]", "") + ".pdf\"").body(pdf);
    }
}
