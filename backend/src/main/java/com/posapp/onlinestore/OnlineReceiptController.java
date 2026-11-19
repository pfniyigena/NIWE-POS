package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.CustomerReceivedRequest;
import com.posapp.onlinestore.OnlineStoreDtos.ReceiptInfo;
import com.posapp.onlinestore.OnlineStoreDtos.ReceiptProof;
import com.posapp.onlinestore.OnlineStoreDtos.Track;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer's order reception on the public shop (v1.56.0): see the delivery code, and confirm that the goods arrived. */
@RestController
@RequestMapping("/api/public/store/orders")
public class OnlineReceiptController {

    private static final String TOKEN = ShopAccountController.TOKEN_HEADER;

    private final OnlineReceiptService receipts;
    private final ShopAccountService accounts;
    private final ModuleService moduleService;

    public OnlineReceiptController(OnlineReceiptService receipts, ShopAccountService accounts, ModuleService moduleService) {
        this.receipts = receipts;
        this.accounts = accounts;
        this.moduleService = moduleService;
    }

    /** The delivery code of an order, or when it was received. Needs the phone number of the order, or the sign-in token of the account that holds it. */
    @PostMapping("/{code}/receipt")
    public ReceiptInfo info(@PathVariable String code, @Valid @RequestBody(required = false) ReceiptProof proof, HttpServletRequest http,
                            @RequestHeader(value = TOKEN, required = false) String token) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return receipts.info(code, proof == null ? null : proof.phone(), http.getRemoteAddr(), accounts.optionalCustomerId(token));
    }

    /** "I received my order", with a signature when the customer draws one. Completes the order. */
    @PostMapping("/{code}/received")
    public Track received(@PathVariable String code, @Valid @RequestBody(required = false) CustomerReceivedRequest request, HttpServletRequest http,
                          @RequestHeader(value = TOKEN, required = false) String token) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return receipts.confirm(code, request, http.getRemoteAddr(), accounts.optionalCustomerId(token));
    }
}
