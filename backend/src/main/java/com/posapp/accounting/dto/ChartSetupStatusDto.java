package com.posapp.accounting.dto;

import com.posapp.accounting.ChartCountry;

/**
 * {@code GET /api/accounting/chart-setup}'s response — what {@code ChartSetupController} and the
 * frontend's setup wizard/admin guard need to know. {@code needsSelection} true means no account
 * exists yet, i.e. the wizard should be shown instead of the normal admin screens;
 * {@code suggestedCountry} is only ever a pre-filled suggestion (from {@code
 * app.accounting.chart-country} if nothing's been chosen yet) — never the locked-in answer once
 * {@code needsSelection} is false, since by then the real answer lives in {@code
 * StoreSettings.chartCountry} and this DTO doesn't need to repeat it.
 */
public record ChartSetupStatusDto(boolean needsSelection, ChartCountry suggestedCountry) {
}
