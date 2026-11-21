package com.posapp.accounting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Journal descriptions for what comes from the Online Store (v1.58.2): the number of the online order is added at the end of the description, like
 * "Payment received from Ada (online order 9FMMY2MC)", so an accountant looking at a posting can find the order. Looked up by the sale order (or invoice)
 * the posting belongs to; returns an empty text for anything that does not come from an online order. Read with plain SQL on purpose: accounting does not
 * depend on the Online Store classes, and a failed lookup never stops a posting.
 */
@Component
public class OnlineOrderRefs {

    private static final Logger log = LoggerFactory.getLogger(OnlineOrderRefs.class);

    private final JdbcTemplate jdbc;

    public OnlineOrderRefs(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** " (online order CODE)" for the online order made from this sale order, else "". */
    public String forSalesOrder(Long salesOrderId) {
        return salesOrderId == null ? "" : lookup("select code from online_order where sales_order_id = ?", salesOrderId);
    }

    /** " (online order CODE)" for the online order of this invoice (through its sale order, or the invoice itself), else "". */
    public String forInvoice(Long invoiceId, Long salesOrderId) {
        String viaSale = forSalesOrder(salesOrderId);
        if (!viaSale.isEmpty() || invoiceId == null) return viaSale;
        return lookup("select code from online_order where invoice_id = ?", invoiceId);
    }

    /** What accounting needs to know about the online order of an invoice: how it was paid and how much online money is still held for it (null for anything else). */
    public record OnlineMoney(Long orderId, boolean prepaid, String channel, java.math.BigDecimal depositLeft, String deliveryMethod) {
    }

    public OnlineMoney moneyFor(Long invoiceId, Long salesOrderId) {
        try {
            java.util.List<OnlineMoney> found = jdbc.query(
                    "select id, payment_method, coalesce(clearing_channel, payment_channel), deposit_left, delivery_method from online_order where sales_order_id = ? or invoice_id = ? order by id limit 1",
                    (rs, i) -> new OnlineMoney(rs.getLong(1), "ONLINE".equals(rs.getString(2)), rs.getString(3),
                            rs.getBigDecimal(4) == null ? java.math.BigDecimal.ZERO : rs.getBigDecimal(4), rs.getString(5)),
                    salesOrderId == null ? -1L : salesOrderId, invoiceId == null ? -1L : invoiceId);
            return found.isEmpty() ? null : found.get(0);
        } catch (DataAccessException e) {
            log.warn("Could not look up the online payment of an invoice: {}", e.getMessage());
            return null;
        }
    }

    private String lookup(String sql, Long id) {
        try {
            java.util.List<String> codes = jdbc.queryForList(sql, String.class, id);
            return codes.isEmpty() || codes.get(0) == null ? "" : " (online order " + codes.get(0) + ")";
        } catch (DataAccessException e) {
            log.warn("Could not look up the online order for a posting description: {}", e.getMessage());
            return "";
        }
    }
}
