package com.posapp.onlinestore.text;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The test sender: nothing leaves the server. A message is only written to the application log and to the shop's text log, so the whole flow
 * (consent, limits, sign-in codes, order updates) can be tried without a provider account. A number ending in 0000 is refused, to try the failure path.
 * Switched on with {@code app.online-store.text.simulated.enabled=true} (the dev profile does).
 */
@Component
public class SimulatedTextProvider implements TextProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTextProvider.class);
    private final boolean enabled;
    private final Environment environment;
    private final AtomicLong counter = new AtomicLong();

    public SimulatedTextProvider(@Value("${app.online-store.text.simulated.enabled:false}") boolean enabled, Environment environment) {
        this.enabled = enabled;
        this.environment = environment;
    }

    @PostConstruct
    void warn() {
        if (enabled && !List.of(environment.getActiveProfiles()).contains("dev")) {
            log.warn("The simulated text sender is ON but this is not the dev profile: no text message is really sent. Use it for tests only.");
        }
    }

    @Override
    public String code() {
        return "SIMULATED";
    }

    @Override
    public String label() {
        return "Test sender (nothing is really sent)";
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public boolean supports(String channel) {
        return "SMS".equals(channel) || "WHATSAPP".equals(channel);
    }

    @Override
    public Result send(String channel, String to, String body) {
        if (!enabled) return Result.failed("The test sender is switched off");
        if (to.endsWith("0000")) return Result.failed("The test sender refuses numbers ending in 0000");
        log.info("[test text] {} to {}: {}", channel, to, body);
        return Result.sent("SIM-" + counter.incrementAndGet());
    }
}
