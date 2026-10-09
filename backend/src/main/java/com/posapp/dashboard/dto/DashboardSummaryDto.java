package com.posapp.dashboard.dto;

import com.posapp.catalog.dto.ProductDto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryDto(
        int todaySalesCount,
        BigDecimal todaySalesTotal,
        long openSessionsCount,
        long totalActiveProducts,
        long totalCustomers,
        List<ProductDto> lowStockProducts
) {
}
