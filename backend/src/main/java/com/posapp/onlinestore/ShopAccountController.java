package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer accounts on the public shop: no staff login. The sign-in code goes by e-mail or, with texting on, by text message; the session token is sent in the {@code X-Shop-Token} header.
 * Everything here answers "not available" until the module and the shop are on and Accounts is switched on in the shop settings.
 */
@RestController
@RequestMapping("/api/public/store/account")
public class ShopAccountController {

    public static final String TOKEN_HEADER = "X-Shop-Token";

    private final ShopAccountService accounts;
    private final ModuleService moduleService;

    public ShopAccountController(ShopAccountService accounts, ModuleService moduleService) {
        this.accounts = accounts;
        this.moduleService = moduleService;
    }

    private void on() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
    }

    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @PostMapping("/code")
    public AccountCodeResult code(@Valid @RequestBody AccountCodeRequest request, HttpServletRequest http) {
        on();
        return accounts.requestCode(request, ip(http));
    }

    @PostMapping("/verify")
    public AccountSession verify(@Valid @RequestBody AccountVerifyRequest request, HttpServletRequest http) {
        on();
        return accounts.verify(request, ip(http));
    }

    @GetMapping("/me")
    public AccountProfile me(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        on();
        return accounts.me(token);
    }

    @PutMapping("/me")
    public AccountProfile update(@RequestHeader(value = TOKEN_HEADER, required = false) String token, @Valid @RequestBody AccountProfileRequest request) {
        on();
        return accounts.update(token, request);
    }

    @GetMapping("/orders")
    public List<AccountOrder> orders(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        on();
        return accounts.orders(token);
    }

    @GetMapping("/orders/{code}/reorder")
    public Reorder reorder(@RequestHeader(value = TOKEN_HEADER, required = false) String token, @PathVariable String code) {
        on();
        return accounts.reorder(token, code);
    }

    /** Texts a code to the phone number saved on the account. */
    @PostMapping("/phone/code")
    public ResponseEntity<Void> phoneCode(@RequestHeader(value = TOKEN_HEADER, required = false) String token, HttpServletRequest http) {
        on();
        accounts.requestPhoneCode(token, ip(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/phone/verify")
    public AccountProfile phoneVerify(@RequestHeader(value = TOKEN_HEADER, required = false) String token, @Valid @RequestBody PhoneVerifyRequest request, HttpServletRequest http) {
        on();
        return accounts.verifyPhone(token, request, ip(http));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        on();
        accounts.logout(token);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        on();
        accounts.deleteAccount(token);
        return ResponseEntity.noContent().build();
    }
}
