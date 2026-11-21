package com.posapp.onlinestore;

import com.posapp.onlinestore.OnlineShopStock.SaveRequest;
import com.posapp.onlinestore.OnlineShopStock.StockSetup;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Shop settings: the Sales location the shop sells from (v1.65.0). */
@RestController
public class OnlineShopStockController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineShopStock service;

    public OnlineShopStockController(OnlineShopStock service) {
        this.service = service;
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/shop-stock")
    public StockSetup get() {
        return service.setup();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/shop-stock")
    public StockSetup save(@RequestBody SaveRequest request) {
        return service.save(request);
    }
}
