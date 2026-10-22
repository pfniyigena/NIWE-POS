package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Law Firm overview page: the numbers a partner looks at first, for the matters the signed-in person may see. */
public final class LawOverviewDto {

    private LawOverviewDto() {
    }

    public record Slice(String label, BigDecimal value) {
    }

    /** Something that needs a person to act. {@code kind} is DEADLINE, UPLOAD, MESSAGE or CONFLICT. */
    public record Attention(String kind, Long matterId, String matterNumber, String text, LocalDate date) {
    }

    public record Overview(int openMatters, int onHold, int openedThisMonth, int closedThisMonth, int deadlinesThisWeek, int overdueDeadlines, int overdueTasks,
                           int conflictsNotCleared, BigDecimal hoursThisMonth, BigDecimal billableHoursThisMonth, BigDecimal billedThisMonth, BigDecimal unbilledWork,
                           BigDecimal outstanding, BigDecimal outstandingOver60Days, boolean trustEnabled, BigDecimal clientMoneyHeld, boolean portalEnabled,
                           int waitingUploads, int unansweredMessages, List<Slice> openByPracticeArea, List<Slice> billedByMonth, List<Slice> outstandingByAge,
                           List<Slice> hoursByLawyer, List<Attention> attention) {
    }
}
