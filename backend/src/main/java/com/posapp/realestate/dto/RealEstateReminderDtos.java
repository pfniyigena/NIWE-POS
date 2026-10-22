package com.posapp.realestate.dto;

import java.time.Instant;
import java.util.List;

/** Viewing reminders by e-mail. */
public final class RealEstateReminderDtos {

    private RealEstateReminderDtos() {}

    /** One viewing in a reminder run. {@code client} and {@code agent} are SENT, WOULD_SEND (a preview), NO_EMAIL, NOT_CONFIGURED or FAILED; empty when that person needed nothing. */
    public record ReminderLine(Long viewingId, String propertyCode, String propertyTitle, String leadName, Instant startAt, String clientEmail, String client, String agentName, String agentEmail,
                               String agent) {
    }

    public record ReminderResult(boolean preview, boolean mailConfigured, int viewings, int clientsSent, int agentsSent, int skipped, int failed, String summary, List<ReminderLine> lines) {
    }

    /** What happened when one reminder was sent by hand. */
    public record RemindOne(String status, String email, String message) {
    }
}
