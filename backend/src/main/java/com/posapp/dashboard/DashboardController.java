package com.posapp.dashboard;

import com.posapp.dashboard.dto.DashboardSummaryDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardOverviewService overviewService;

    public DashboardController(DashboardService dashboardService, DashboardOverviewService overviewService) {
        this.dashboardService = dashboardService;
        this.overviewService = overviewService;
    }

    @GetMapping("/summary")
    public DashboardSummaryDto summary(@org.springframework.web.bind.annotation.RequestParam(required = false) Long branchId) {
        return dashboardService.summary(branchId);
    }

    /** Things to act on, sales against last week, who owes, bills to pay, top sellers. {@code branchId} narrows till sales, invoices issued and collections; stock, bills and approvals stay business-wide. */
    @GetMapping("/overview")
    public com.posapp.dashboard.DashboardOverviewDtos.Overview overview(@org.springframework.web.bind.annotation.RequestParam(required = false) Long branchId) {
        return overviewService.overview(branchId);
    }
}
