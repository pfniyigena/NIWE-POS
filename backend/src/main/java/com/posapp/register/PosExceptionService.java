package com.posapp.register;

import com.posapp.branch.BranchAccessService;
import com.posapp.common.ListPaging;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.SaleOrder;
import com.posapp.register.dto.PosExceptionDto;
import com.posapp.returns.SalesReturn;
import com.posapp.returns.SalesReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The log a manager reads to see what was undone at the tills: sales voided after payment, returns (with who approved them) and carts dropped
 * before they were paid. Types: VOID, RETURN, DROPPED_CART.
 */
@Service
public class PosExceptionService {

    private final OrderRepository orderRepository;
    private final SalesReturnRepository returnRepository;
    private final BranchAccessService branchAccessService;
    private final com.posapp.order.ReceiptPrintService receiptPrintService;

    public PosExceptionService(OrderRepository orderRepository, SalesReturnRepository returnRepository, BranchAccessService branchAccessService, com.posapp.order.ReceiptPrintService receiptPrintService) {
        this.receiptPrintService = receiptPrintService;
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.branchAccessService = branchAccessService;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String invoice(SaleOrder o) {
        return o.getInvoiceNumber() != null ? o.getInvoiceNumber() : "#" + o.getId();
    }

    @Transactional(readOnly = true)
    public List<PosExceptionDto> filtered(String q, String type, Long registerId, LocalDate from, LocalDate to) {
        boolean all = type == null || type.isBlank() || type.equalsIgnoreCase("ALL");
        List<PosExceptionDto> out = new ArrayList<>();
        boolean wantVoid = all || type.equalsIgnoreCase("VOID");
        boolean wantDiscount = all || type.equalsIgnoreCase("DISCOUNT");
        boolean wantPrice = all || type.equalsIgnoreCase("PRICE_OVERRIDE");
        if (wantVoid || wantDiscount || wantPrice) {
            for (SaleOrder o : orderRepository.findByStatusOrderByCompletedAtDesc(OrderStatus.COMPLETED)) {
                if (!branchAccessService.canAccess(o.getBranchId())) continue;
                if (wantVoid && o.getVoidedAt() != null) {
                    out.add(row("VOID", o.getVoidedAt(), invoice(o), o, null, o.getVoidedBy(), scale(OrderCalculator.total(o)), o.getVoidReason(), o.getVoidApprovedBy()));
                }
                if (wantDiscount) discountRow(o).ifPresent(out::add);
                if (wantPrice) priceRow(o).ifPresent(out::add);
            }
        }
        if (all || type.equalsIgnoreCase("REPRINT")) {
            for (var rp : receiptPrintService.reprints()) {
                SaleOrder o = orderRepository.findById(rp.getOrderId()).orElse(null);
                if (o == null || !branchAccessService.canAccess(o.getBranchId())) continue;
                out.add(row("REPRINT", rp.getCreatedAt(), invoice(o), o, null, rp.getPrintedBy(), scale(OrderCalculator.total(o)), "Copy " + rp.getCopyNumber(), null));
            }
        }
        if (all || type.equalsIgnoreCase("RETURN")) {
            for (SalesReturn r : returnRepository.findAll()) {
                if (r.isVoidReturn() || r.getOrder() == null || !branchAccessService.canAccess(r.getOrder().getBranchId())) continue;
                out.add(row("RETURN", r.getReturnDate(), r.getReturnNumber() != null ? r.getReturnNumber() : "R-" + r.getId(), r.getOrder(), r.getId(), r.getProcessedBy(),
                        scale(r.getRefundTotal()), r.getReason(), r.getApprovedBy()));
            }
        }
        if (all || type.equalsIgnoreCase("DROPPED_CART")) {
            for (SaleOrder o : orderRepository.findByStatusOrderByUpdatedAtDesc(OrderStatus.VOIDED)) {
                if (o.getLines().isEmpty() || !branchAccessService.canAccess(o.getBranchId())) continue;
                var cs = o.getCashierSession();
                var cashier = cs != null ? cs.getCashier() : (o.getSession() != null ? o.getSession().getCashier() : null);
                out.add(row("DROPPED_CART", o.getUpdatedAt(), "#" + o.getId(), o, null, cashier == null ? null : cashier.getFullName(), scale(OrderCalculator.total(o)), o.getNote(), null));
            }
        }
        return out.stream()
                .filter(e -> registerId == null || registerId.equals(e.registerId()))
                .filter(e -> ListPaging.between(e.at(), from, to))
                .filter(e -> ListPaging.has(q, e.ref(), e.customer(), e.by(), e.registerName(), e.reason(), e.approvedBy()))
                .sorted(Comparator.comparing(PosExceptionDto::at, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static String cashierOf(SaleOrder o) {
        var cs = o.getCashierSession();
        var c = cs != null ? cs.getCashier() : (o.getSession() != null ? o.getSession().getCashier() : null);
        return c == null ? null : c.getFullName();
    }

    private static String joined(java.util.Collection<String> parts) {
        return String.join("; ", new java.util.LinkedHashSet<>(parts.stream().filter(x -> x != null && !x.isBlank()).toList()));
    }

    /** A sale where the cashier gave a discount (on a line or on the whole sale): the value taken off, why, and who approved it. */
    private java.util.Optional<PosExceptionDto> discountRow(SaleOrder o) {
        BigDecimal value = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        List<String> approvers = new ArrayList<>();
        BigDecimal orderPct = o.getOrderDiscountPercent() == null ? BigDecimal.ZERO : o.getOrderDiscountPercent();
        for (var l : o.getLines()) {
            BigDecimal d = l.getDiscountPercent() == null ? BigDecimal.ZERO : l.getDiscountPercent();
            if (d.signum() > 0) {
                BigDecimal gross = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()));
                value = value.add(gross.multiply(d).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                reasons.add(l.getDiscountReason());
                approvers.add(l.getDiscountApprovedBy());
            }
        }
        if (orderPct.signum() > 0) {
            value = value.add(OrderCalculator.orderDiscountValue(o));
            reasons.add(o.getOrderDiscountReason());
            approvers.add(o.getOrderDiscountApprovedBy());
        }
        if (value.signum() <= 0) return java.util.Optional.empty();
        String ref = invoice(o);
        return java.util.Optional.of(row("DISCOUNT", o.getCompletedAt(), ref, o, null, cashierOf(o), scale(value), joined(reasons), joined(approvers)));
    }

    /** A sale where a price was changed by hand: the amount taken off the normal price (negative when raised), why, and who approved it. */
    private java.util.Optional<PosExceptionDto> priceRow(SaleOrder o) {
        BigDecimal value = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        List<String> approvers = new ArrayList<>();
        boolean any = false;
        for (var l : o.getLines()) {
            if (!l.isPriceOverridden() || l.getOriginalUnitPrice() == null) continue;
            any = true;
            value = value.add(l.getOriginalUnitPrice().subtract(l.getUnitPrice()).multiply(BigDecimal.valueOf(l.getQuantity())));
            reasons.add(l.getPriceOverrideReason());
            approvers.add(l.getPriceOverrideApprovedBy());
        }
        if (!any) return java.util.Optional.empty();
        return java.util.Optional.of(row("PRICE_OVERRIDE", o.getCompletedAt(), invoice(o), o, null, cashierOf(o), scale(value), joined(reasons), joined(approvers)));
    }

    private PosExceptionDto row(String type, Instant at, String ref, SaleOrder o, Long returnId, String by, BigDecimal amount, String reason, String approvedBy) {
        RegisterSession s = o.getSession();
        var reg = s == null ? null : s.getRegister();
        return new PosExceptionDto(type, at, ref, o.getId(), returnId, o.getCustomer() != null ? o.getCustomer().getName() : "Walk-in Customer", by,
                reg == null ? "" : reg.getName(), reg == null ? null : reg.getId(), amount, reason, approvedBy, s == null ? null : s.getId());
    }
}
