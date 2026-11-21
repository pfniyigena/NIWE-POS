package com.posapp.salesorder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * v1.68.0: a module that sells stock outside the sale orders (the online shop) tells Inventory about demand that sale orders do not show yet, without
 * Inventory depending on that module: units promised to orders nobody has accepted yet, and customers waiting for a product to come back.
 */
public interface PendingDemandProvider {

    /** Units promised to orders that do not have a sale order yet, by product id. */
    Map<Long, BigDecimal> unansweredByProduct();

    /** Customers who asked to be told when the product is back, by product id. */
    Map<Long, Long> waitingByProduct();
}
