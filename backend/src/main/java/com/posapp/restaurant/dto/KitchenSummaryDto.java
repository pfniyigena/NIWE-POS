package com.posapp.restaurant.dto;

import java.util.List;

/** The kitchen at a glance: how busy each station is, how many tickets are late, and how long dishes take today. */
public record KitchenSummaryDto(
        List<Station> stations,
        int waiting,
        int cooking,
        int ready,
        int late,
        int oldestMinutes,
        /** Items currently marked sold out. */
        List<String> soldOut
) {
    public record Station(String station, int waiting, int cooking, int ready, int late, int warn,
                          /** Average minutes from reaching the kitchen to ready, over dishes finished today (0 when none). */
                          double averagePrepMinutes, int doneToday) {
    }
}
