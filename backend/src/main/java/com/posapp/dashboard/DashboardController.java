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

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryDto summary(@org.springframework.web.bind.annotation.RequestParam(required = false) Long branchId) {
        return dashboardService.summary(branchId);
    }
}
