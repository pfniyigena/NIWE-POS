package com.posapp.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Task: "local queuing of sales that sync once connectivity returns" — the terminal's connectivity
 * check pings this rather than relying on {@code navigator.onLine} alone (that only reflects
 * whether the device has *a* network connection, e.g. to a Wi-Fi router with no real internet
 * beyond it, not whether this backend is actually reachable). Deliberately public (see
 * SecurityConfig) — a terminal that's lost its JWT-carrying session state or is checking
 * connectivity before ever logging in still needs to be able to ping this.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
