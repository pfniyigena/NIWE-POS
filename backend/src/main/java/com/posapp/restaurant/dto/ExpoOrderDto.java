package com.posapp.restaurant.dto;


import java.math.BigDecimal;import java.time.Instant;
import java.util.List;

/** One order on the expo screen: everything the kitchen is making for it, so the pass can see when it is complete. */
public record ExpoOrderDto(
        Long orderId,
        String label,
        String orderType,
        int guestCount,
        String serverName,
        Instant oldestSentAt,
        int oldestMinutes,
        /** Every dish that has gone to the kitchen is ready to be taken out. */
        boolean allReady,
        int readyCount,
        int cookingCount,
        /** Dishes still held back for a later course. */
        int heldCount,
        /** Stations that still have something cooking. */
        List<String> waitingOn,
        /** The worst urgency among the dishes still cooking: OK, WARN or LATE. */
        String urgency,
        String guestAllergens,
        List<Line> lines
) {
    public record Line(Long lineId, String description, BigDecimal quantity, String station, String status, int course, Integer seat,
                       List<String> options, String notes, String urgency, int elapsedMinutes, int targetMinutes, String allergyConflict) {
    }
}
