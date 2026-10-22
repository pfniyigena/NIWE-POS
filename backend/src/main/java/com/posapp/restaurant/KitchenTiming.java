package com.posapp.restaurant;

import java.time.Duration;
import java.time.Instant;

/** How long a kitchen ticket has been waiting against the time it should take: OK, WARN (amber) or LATE (red). */
public final class KitchenTiming {

    private KitchenTiming() {
    }

    public static int target(RestaurantOrderLine line, int defaultMinutes) {
        return line.getPrepMinutes() != null && line.getPrepMinutes() > 0 ? line.getPrepMinutes() : defaultMinutes;
    }

    /** Seconds from the ticket reaching the kitchen until now, or until it was ready once it is. */
    public static long elapsedSeconds(RestaurantOrderLine line, Instant now) {
        Instant from = line.getSentAt();
        if (from == null) return 0;
        Instant to = line.getKitchenStatus() == KitchenStatus.READY || line.getKitchenStatus() == KitchenStatus.SERVED
                ? (line.getReadyAt() != null ? line.getReadyAt() : now) : now;
        return Math.max(0, Duration.between(from, to).getSeconds());
    }

    public static String urgency(RestaurantOrderLine line, Instant now, int defaultMinutes, int warnPercent) {
        if (line.getKitchenStatus() != KitchenStatus.SENT && line.getKitchenStatus() != KitchenStatus.PREPARING) return "OK";
        long secs = elapsedSeconds(line, now);
        long target = target(line, defaultMinutes) * 60L;
        if (secs >= target) return "LATE";
        if (secs * 100 >= target * warnPercent) return "WARN";
        return "OK";
    }
}
