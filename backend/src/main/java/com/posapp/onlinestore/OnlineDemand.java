package com.posapp.onlinestore;

import com.posapp.salesorder.PendingDemandProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * v1.68.0: what the online shop knows that Inventory does not see yet: the units promised to orders that wait for a payment or for staff (no sale order yet),
 * and the customers who asked to be told when a product is back. Reorder and the stock report use them.
 */
@Component
public class OnlineDemand implements PendingDemandProvider {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private OnlineStockAlertRepository alerts;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> unansweredByProduct() {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Object[] r : em.createQuery("select l.productId, sum(l.quantity) from OnlineOrderLine l where l.onlineOrder.status in ('AWAITING_PAYMENT', 'PLACED') group by l.productId", Object[].class).getResultList()) {
            out.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> waitingByProduct() {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] r : alerts.demand()) out.put(((Number) r[0]).longValue(), ((Number) r[2]).longValue());
        return out;
    }
}
