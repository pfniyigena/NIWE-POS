package com.posapp.tracking;

import com.posapp.tracking.dto.BatchDto;
import com.posapp.tracking.dto.SerialDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/products/{productId}/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BatchDto> batches(@PathVariable Long productId) {
        return trackingService.batchesForProduct(productId);
    }

    @GetMapping("/products/{productId}/serials")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SerialDto> serials(@PathVariable Long productId) {
        return trackingService.serialsForProduct(productId);
    }

    @GetMapping("/serials")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SerialDto> searchSerials(@RequestParam(required = false) String q) {
        return trackingService.searchSerials(q);
    }
}
