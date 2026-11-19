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
