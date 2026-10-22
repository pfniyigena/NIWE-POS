package com.posapp.restaurant;

import com.posapp.restaurant.dto.ExpoOrderDto;
import com.posapp.restaurant.dto.KitchenBumpRequest;
import com.posapp.restaurant.dto.KitchenSummaryDto;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Kitchen screens beyond the ticket list: the expo (pass), the station summary, bump, serve and send-back. Open to any signed-in user like the other order screens. */
@RestController
@RequestMapping("/api/restaurant/kitchen")
public class RestaurantKitchenController {

    private final RestaurantKitchenService service;

    public RestaurantKitchenController(RestaurantKitchenService service) {
        this.service = service;
    }

    @GetMapping("/expo")
    public List<ExpoOrderDto> expo() {
        return service.expo();
    }

    @GetMapping("/summary")
    public KitchenSummaryDto summary() {
        return service.summary();
    }

    @PostMapping("/orders/{id}/bump")
    public RestaurantOrderDto bump(@PathVariable Long id, @RequestBody(required = false) KitchenBumpRequest request) {
        return service.bump(id, request == null ? null : request.getStation());
    }

    @PostMapping("/orders/{id}/serve-ready")
    public RestaurantOrderDto serveReady(@PathVariable Long id) {
        return service.serveReady(id);
    }

    @PostMapping("/orders/{id}/lines/{lineId}/refire")
    public RestaurantOrderDto refire(@PathVariable Long id, @PathVariable Long lineId, @RequestBody(required = false) Map<String, String> body) {
        return service.refire(id, lineId, body == null ? null : body.get("reason"));
    }
}
