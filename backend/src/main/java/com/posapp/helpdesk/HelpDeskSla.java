package com.posapp.helpdesk;

import java.time.Duration;
import java.time.Instant;

/** Pure rules for "is this ticket on time?" — used by the lists, the overview, the reports and the nightly check. */
public final class HelpDeskSla {

    private HelpDeskSla() {
    }

    public static boolean terminal(HelpDeskStatus status) {
        return status == HelpDeskStatus.RESOLVED || status == HelpDeskStatus.CLOSED || status == HelpDeskStatus.MERGED;
    }

    /** null = no deadline; OK, PAUSED (waiting on the customer), BREACHED, MET, MISSED. */
    public static String responseState(HelpDeskTicket t, Instant now) {
        Instant due = t.getFirstResponseDueAt();
        if (due == null || t.getStatus() == HelpDeskStatus.MERGED) return null;
        if (t.getFirstRespondedAt() != null) return t.getFirstRespondedAt().isAfter(due) ? "MISSED" : "MET";
        if (terminal(t.getStatus())) return "MISSED";
        if (t.getWaitingSince() != null) return "PAUSED";
        return now.isAfter(due) ? "BREACHED" : "OK";
    }

    /** null = no deadline; OK, DUE_SOON, PAUSED, BREACHED, MET, MISSED. */
    public static String resolutionState(HelpDeskTicket t, Instant now, int warnHours) {
        Instant due = t.getResolutionDueAt();
        if (due == null || t.getStatus() == HelpDeskStatus.MERGED) return null;
        if (t.getStatus() == HelpDeskStatus.RESOLVED || t.getStatus() == HelpDeskStatus.CLOSED) {
            Instant at = t.getResolvedAt() != null ? t.getResolvedAt() : t.getClosedAt();
            if (at == null) return null;
            return at.isAfter(due) ? "MISSED" : "MET";
        }
        if (t.getWaitingSince() != null) return "PAUSED";
        if (now.isAfter(due)) return "BREACHED";
        return Duration.between(now, due).toHours() < warnHours || Duration.between(now, due).compareTo(Duration.ofHours(warnHours)) <= 0 ? "DUE_SOON" : "OK";
    }

    public static boolean overdue(HelpDeskTicket t, Instant now) {
        return "BREACHED".equals(responseState(t, now)) || "BREACHED".equals(resolutionState(t, now, 0));
    }

    public static boolean dueSoon(HelpDeskTicket t, Instant now, int warnHours) {
        return !overdue(t, now) && "DUE_SOON".equals(resolutionState(t, now, warnHours));
    }
}
