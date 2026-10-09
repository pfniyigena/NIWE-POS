package com.posapp.register.dto;

import java.math.BigDecimal;
import java.util.List;

public record SessionReportDto(
        SessionDto session,
        int saleCount,
        BigDecimal grossSales,
        BigDecimal taxCollected,
        BigDecimal cashIn,
        BigDecimal nonCashIn,
        BigDecimal cashRefundsOut,
        BigDecimal expectedCash,
        /**
         * Every cashier session on this shift, oldest first — Task: "The report for
         * register_session (Shift) should show all cashier_session (session report)." Ordinarily
         * just the one cashier the shift was opened for; more than one entry only when "Switch
         * cashier" (or an admin's "assign") brought a teammate onto the same till during this
         * shift. See {@link CashierSessionReportDto}'s own javadoc for what each entry means.
         */
        List<CashierSessionReportDto> cashierSessions
) {
}
