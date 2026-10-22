package com.posapp.order;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Counts how often a sale's receipt is printed: the first printing is the original, every later one is a COPY (1, 2, 3...), recorded with who printed
 * it so a reprinted receipt cannot pass for an original. Only pressing Print counts; looking at the receipt on screen does not.
 */
@Service
public class ReceiptPrintService {

    public record Printed(int copyNumber, int printCount) {
    }

    private final OrderRepository orderRepository;
    private final ReceiptPrintRepository repository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public ReceiptPrintService(OrderRepository orderRepository, ReceiptPrintRepository repository, BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.repository = repository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Printed record(Long orderId, AppUser caller) {
        SaleOrder o = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Sale not found: " + orderId));
        if (o.getStatus() != OrderStatus.COMPLETED || !branchAccessService.canAccess(o.getBranchId())) throw ApiException.notFound("Sale not found: " + orderId);
        int copy = o.getPrintCount();
        ReceiptPrint p = new ReceiptPrint();
        p.setOrderId(o.getId());
        p.setCopyNumber(copy);
        p.setPrintedBy(caller == null ? null : caller.getUsername());
        repository.save(p);
        o.setPrintCount(copy + 1);
        orderRepository.save(o);
        if (copy > 0) {
            auditLogService.record("RECEIPT_REPRINT", "Reprinted the receipt of " + (o.getInvoiceNumber() == null ? "sale #" + o.getId() : o.getInvoiceNumber()) + " (copy " + copy + ")", "SaleOrder", o.getId());
        }
        return new Printed(copy, copy + 1);
    }

    @Transactional(readOnly = true)
    public List<ReceiptPrint> reprints() {
        return repository.findByCopyNumberGreaterThanOrderByCreatedAtDesc(0);
    }
}
