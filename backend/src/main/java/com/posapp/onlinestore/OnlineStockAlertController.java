package com.posapp.onlinestore;

import com.posapp.common.ListPaging;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertDto;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertRequest;
import com.posapp.onlinestore.OnlineStoreDtos.StockAlertResult;
import com.posapp.onlinestore.OnlineStoreDtos.StockDemandDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** "Tell me when it is back in stock" (v1.54.0): what the shop's visitors send, and what staff see and do about it. */
@RestController
public class OnlineStockAlertController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineStockAlertService alerts;
    private final ModuleService moduleService;

    public OnlineStockAlertController(OnlineStockAlertService alerts, ModuleService moduleService) {
        this.alerts = alerts;
        this.moduleService = moduleService;
    }

    // ---------------------------------------------------------------- public (no login)

    @PostMapping("/api/public/store/stock-alerts")
    public StockAlertResult request(@Valid @RequestBody StockAlertRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return alerts.request(request, ip(http));
    }

    // ---------------------------------------------------------------- staff

    /** The products customers are waiting for, most wanted first. */
    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/stock-alerts/demand")
    public List<StockDemandDto> demand() {
        return alerts.demand();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/stock-alerts")
    public ResponseEntity<List<StockAlertDto>> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long productId,
                                                    @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ListPaging.of(alerts.list(status, productId, q), page, size);
    }

    /** Runs the notice now instead of waiting for the next pass; answers how many customers were told. */
    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/stock-alerts/notify")
    public Map<String, Integer> notifyNow() {
        return Map.of("told", alerts.notifyAvailable());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/stock-alerts/{id}/told")
    public StockAlertDto told(@PathVariable Long id) {
        return alerts.told(id);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/stock-alerts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alerts.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String ip(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
