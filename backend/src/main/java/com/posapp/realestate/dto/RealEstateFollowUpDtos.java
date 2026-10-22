package com.posapp.realestate.dto;

import com.posapp.realestate.dto.RealEstateDtos.LeadRow;

import java.time.LocalDate;
import java.util.List;

/** The follow-up board and the morning e-mail. */
public final class RealEstateFollowUpDtos {

    private RealEstateFollowUpDtos() {}

    /** Open leads of one agent (or of everyone) by when the next call is due. {@code quiet} leads have had no contact for the stale period and are not already listed as due. */
    public record Board(Long agentId, String agentName, boolean all, int upcomingDays, int staleDays, boolean mailEnabled, List<LeadRow> overdue, List<LeadRow> today, List<LeadRow> upcoming,
                        List<LeadRow> quiet) {
    }

    /** One agent's part of a morning e-mail run. {@code status} is SENT, WOULD_SEND (a preview), NO_EMAIL, NOT_CONFIGURED or FAILED. */
    public record DigestAgent(Long userId, String name, String email, int overdue, int today, int quiet, String status) {
    }

    public record DigestResult(boolean preview, boolean mailConfigured, int agents, int sent, int skipped, int failed, int unassigned, String summary, List<DigestAgent> details) {
    }

    public record FollowUpRequest(LocalDate nextFollowUp) {
    }
}
