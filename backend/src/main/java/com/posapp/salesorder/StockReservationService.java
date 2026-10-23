package com.posapp.salesorder;
import com.posapp.common.Qty;
import java.math.BigDecimal;

import com.posapp.catalog.Product;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock promised to customers. A confirmed or partly delivered sales order reserves what is still to be
 * delivered; nothing is stored — the reserved quantity is always the sum of those orders' outstanding lines.
 * Available stock is sellable stock (every warehouse, damaged locations excluded) minus what is reserved.
 */
@Service
public class StockReservationService {

    /** Order states that hold a reservation. */
    static final List<SalesOrderStatus> RESERVING = List.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED);

    private final SalesOrderLineRepository lineRepository;
    private final WarehouseService warehouseService;

    public StockReservationService(SalesOrderLineRepository lineRepository, WarehouseService warehouseService) {
        this.lineRepository = lineRepository;
        this.warehouseService = warehouseService;
    }

    /** Quantity of this product promised on confirmed / part-delivered sales orders. */
    @Transactional(readOnly = true)
    public BigDecimal reserved(Product product) {
        return product == null ? BigDecimal.ZERO : Qty.nz(lineRepository.outstandingQuantity(product.getId(), RESERVING));
    }

    /** Reserved quantity per product id, for reports and the reorder list. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> reservedByProduct() {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : lineRepository.outstandingByProduct(RESERVING)) {
            result.put(((Number) row[0]).longValue(), Qty.dec(row[1]));
        }
        return result;
    }

    /** Sellable stock across all warehouses minus what is reserved (never below zero is not enforced — a negative figure means over-promised). */
    @Transactional(readOnly = true)
    public BigDecimal available(Product product) {
        return warehouseService.totalSellable(product).subtract(reserved(product));
    }
}
