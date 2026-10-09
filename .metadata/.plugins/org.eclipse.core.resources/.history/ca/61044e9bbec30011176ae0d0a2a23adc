package com.posapp.accounting;

import com.posapp.accounting.dto.ChartCountrySelectionRequest;
import com.posapp.accounting.dto.ChartSetupStatusDto;
import com.posapp.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add this
 * on the screen then at first time a user select the country?" — the first-run setup wizard an
 * ADMIN is redirected to (see the frontend's {@code adminGuard}) instead of the normal admin
 * screens, for as long as {@link AccountService#isChartSetupNeeded} is true (no account exists
 * yet). ADMIN-only end to end, same as every other Accounting endpoint — a cashier never needs to
 * see or answer this, since {@code AccountingService.post}'s own lazy safety net already seeds a
 * sensible default (the configured property) if business activity happens before any admin ever
 * logs in to pick one explicitly.
 */
@RestController
@RequestMapping("/api/accounting/chart-setup")
@PreAuthorize("hasRole('ADMIN')")
public class ChartSetupController {

    private final AccountService accountService;

    public ChartSetupController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ChartSetupStatusDto status() {
        return new ChartSetupStatusDto(accountService.isChartSetupNeeded(), accountService.getDefaultChartCountrySuggestion());
    }

    /** Rejects with 409 (via {@code ApiException.conflict}) if the chart was already seeded by the time this is called — see {@code AccountService.seedDefaultChartIfNeeded(ChartCountry)}'s own javadoc. */
    @PostMapping
    public ChartSetupStatusDto select(@Valid @RequestBody ChartCountrySelectionRequest request) {
        ChartCountry country;
        try {
            country = ChartCountry.fromConfigValue(request.getCountry());
        } catch (IllegalStateException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        accountService.seedDefaultChartIfNeeded(country);
        return new ChartSetupStatusDto(false, country);
    }
}
