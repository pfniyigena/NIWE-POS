package com.posapp.dashboard;

import com.posapp.catalog.CatalogService;
import com.posapp.customer.CustomerRepository;
import com.posapp.dashboard.dto.DashboardSummaryDto;
import com.posapp.order.OrderService;
import com.posapp.order.SessionTotals;
import com.posapp.register.RegisterSessionRepository;
import com.posapp.register.SessionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final OrderService orderService;
    private final RegisterSessionRepository sessionRepository;
    private final CatalogService catalogService;
    private final CustomerRepository customerRepository;
    private final int lowStockThreshold;

    public DashboardService(OrderService orderService,
                             RegisterSessionRepository sessionRepository,
                             CatalogService catalogService,
                             CustomerRepository customerRepository,
                             @Value("${app.pos.low-stock-threshold:10}") int lowStockThreshold) {
        this.orderService = orderService;
        this.sessionRepository = sessionRepository;
        this.catalogService = catalogService;
        this.customerRepository = customerRepository;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto summary() {
        return summary(null);
    }

    /** {@code branchId} (null = every branch) narrows today's sales figures to one branch; stock, customers and sessions stay business-wide. */
    @Transactional(readOnly = true)
    public DashboardSummaryDto summary(Long branchId) {
        SessionTotals today = orderService.computeTodayTotals(branchId);
        long openSessions = sessionRepository.countByStatus(SessionStatus.OPEN);
        long totalCustomers = customerRepository.count();
        var lowStock = catalogService.listLowStockPrimary();
        long totalActiveProducts = catalogService.countActiveProducts();

        return new DashboardSummaryDto(
                today.saleCount(),
                today.grossSales(),
                openSessions,
                totalActiveProducts,
                totalCustomers,
                lowStock
        );
    }
}
