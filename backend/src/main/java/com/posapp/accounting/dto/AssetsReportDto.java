package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin &gt; Accounting &gt; Reports &gt; Assets — a simple listing of Asset-type accounts and their as-of-date balances, reusing BalanceSheetDto's own asset computation (Fourier's own scoping choice over a full fixed-asset register with depreciation). */
public record AssetsReportDto(
        Instant asOf,
        List<AccountAmountDto> assets,
        BigDecimal totalAssets
) {
}
