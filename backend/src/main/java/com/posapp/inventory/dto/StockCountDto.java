package com.posapp.inventory.dto;

import com.posapp.inventory.StockCount;
import com.posapp.inventory.StockCountLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockCountDto(
        Long id,
        String countNumber,
        Long warehouseId,
        String warehouseName,
        Long categoryId,
        String categoryName,
        String notes,
        String status,
        Instant createdAt,
        Instant submittedAt,
        Instant approvedAt,
        String approvedBy,
        Long branchId,
        int lineCount,
        int countedCount,
        /** Counted lines whose quantity differs from the system's. */
        int differenceCount,
        /** Sum of (counted - system) x unit cost over counted lines. */
        BigDecimal varianceValue,
        /** Empty on the list endpoint; filled on a single count. */
        List<StockCountLineDto> lines
) {
    public static StockCountDto from(StockCount c, boolean withLines) {
        int counted = 0;
        int differences = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StockCountLine l : c.getLines()) {
            if (l.getCountedQuantity() == null) continue;
            counted++;
            int variance = l.getCountedQuantity() - l.getSystemQuantity();
            if (variance != 0) {
                differences++;
                if (l.getUnitCost() != null) value = value.add(l.getUnitCost().multiply(BigDecimal.valueOf(variance)));
            }
        }
        return new StockCountDto(
                c.getId(), c.getCountNumber(),
                c.getWarehouse() != null ? c.getWarehouse().getId() : null, c.getWarehouseName(),
                c.getCategory() != null ? c.getCategory().getId() : null, c.getCategory() != null ? c.getCategory().getName() : null,
                c.getNotes(), c.getStatus().name(), c.getCreatedAt(), c.getSubmittedAt(), c.getApprovedAt(), c.getApprovedBy(),
                c.getBranchId(), c.getLines().size(), counted, differences, value,
                withLines ? c.getLines().stream().map(StockCountLineDto::from).toList() : List.of());
    }
}
