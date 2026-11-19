package com.posapp.onlinestore;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gives {@link OnlineMessages} the address of the app, so the e-mails about an order can link to it (the order page for the customer, Online orders for the shop). */
@Component
class OnlineMessageLinks {

    private final String frontendUrl;

    OnlineMessageLinks(@Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @PostConstruct
    void init() {
        OnlineMessages.frontendUrl(frontendUrl);
    }
}
