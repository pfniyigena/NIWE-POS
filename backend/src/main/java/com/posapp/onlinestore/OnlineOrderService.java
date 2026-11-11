package com.posapp.onlinestore;

import com.posapp.user.AppUser;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.credit.dto.RecordInvoicePaymentRequest;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.dto.FulfillSalesOrderRequest;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import com.posapp.salesorder.dto.SalesOrderRequest;
import com.posapp.settings.StoreSettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Staff side of the shop: accept or reject what customers ordered, move the order along and complete it. Accepting makes a confirmed sale order
 * (that is what holds the stock); completing hands the goods over from that sale order, issues the invoice and, for cash, records the payment, so
 * accounting, stock and reports see an online sale exactly like any other sale.
 */
@Service
public class OnlineOrderService {

    private static final Set<String> OPEN = Set.of("PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");

    private final OnlineOrderRepository repository;
    private final OnlineStoreService storeService;
    private final CustomerRepository customerRepository;
    private final SalesOrderService salesOrderService;
    private final InvoiceService invoiceService;
    private final StoreSettingsService storeSettingsService;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    public OnlineOrderService(OnlineOrderRepository repository, OnlineStoreService storeService, CustomerRepository customerRepository,
                              SalesOrderService salesOrderService, InvoiceService invoiceService, StoreSettingsService storeSettingsService,
                              MailService mailService, AuditLogService auditLogService) {
        this.repository = repository;
        this.storeService = storeService;
        this.customerRepository = customerRepository;
        this.salesOrderService = salesOrderService;
        this.invoiceService = invoiceService;
        this.storeSettingsService = storeSettingsService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private OnlineOrder getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Online order not found")));
    }

    private void expect(OnlineOrder o, String... allowed) {
        for (String a : allowed) if (o.getStatus().equals(a)) return;
        throw ApiException.badRequest(I18n.t("This order is {0}, so that is not possible now", o.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ')));
    }

    private void move(OnlineOrder o, String status, String note) {
        o.setStatus(status);
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(status);
        ev.setNote(note);
        ev.setByUser(user());
        o.getEvents().add(ev);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<OrderDto> list(String q, String status) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> wanted == null || (wanted.equals("OPEN") ? OPEN.contains(o.getStatus()) : o.getStatus().equals(wanted)))
                .filter(o -> needle.isEmpty() || o.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || o.getCustomerName().toLowerCase(Locale.ROOT).contains(needle) || o.getCustomerPhone().contains(needle))
                .map(OnlineOrderService::dto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return dto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return repository.countByStatus("PLACED");
    }

    // ------------------------------------------------------------------ decisions

    @Transactional
    public OrderDto accept(Long id, boolean overrideShortage) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "PLACED");
        OnlineStoreSettings s = storeService.settings();
        BigDecimal fee = o.getDeliveryFee() == null ? BigDecimal.ZERO : o.getDeliveryFee();
        if (fee.signum() > 0 && s.getDeliveryProductId() == null) {
            throw ApiException.badRequest(I18n.t("Choose the service product that carries the delivery fee in the Online Store settings, then accept the order."));
        }
        Customer customer = customerFor(o);
        SalesOrderRequest req = new SalesOrderRequest();
        req.setCustomerId(customer.getId());
        req.setNotes(I18n.t("Online order {0}", o.getCode()) + " (" + o.getDeliveryMethod().toLowerCase(Locale.ROOT) + ")"
                + (o.getCustomerAddress() == null ? "" : ", " + o.getCustomerAddress()) + (o.getNotes() == null ? "" : ": " + o.getNotes()));
        List<SalesOrderRequest.LineEntry> lines = new ArrayList<>();
        for (OnlineOrderLine l : o.getLines()) {
            SalesOrderRequest.LineEntry e = new SalesOrderRequest.LineEntry();
            e.setProductId(l.getProductId());
            e.setQuantityOrdered(l.getQuantity());
            e.setUnitPrice(l.getBasePrice());
            lines.add(e);
        }
        if (fee.signum() > 0) {
            SalesOrderRequest.LineEntry e = new SalesOrderRequest.LineEntry();
            e.setProductId(s.getDeliveryProductId());
            e.setQuantityOrdered(BigDecimal.ONE);
            e.setUnitPrice(fee);
            lines.add(e);
        }
        req.setLines(lines);
        SalesOrderDto so = salesOrderService.createDraft(req);
        // A 409 here means the stock does not cover the order; staff may accept anyway (the sale order is then over-promised) or reject.
        salesOrderService.confirm(so.id(), overrideShortage);
        o.setCustomerId(customer.getId());
        o.setSalesOrderId(so.id());
        o.setSalesOrderNumber(so.orderNumber());
        o.setDecidedAt(java.time.Instant.now());
        o.setDecidedBy(user());
        move(o, "ACCEPTED", I18n.t("Sale order {0} created", so.orderNumber()));
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_ACCEPTED", "Accepted online order " + o.getCode() + " as sale order " + so.orderNumber(), "OnlineOrder", o.getId());
        tell(o, I18n.t("Your order {0} is accepted", o.getCode()), I18n.t("Good news: your order {0} is accepted and we are preparing it.", o.getCode()));
        return dto(o);
    }

    @Transactional
    public OrderDto reject(Long id, String reason) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "PLACED");
        o.setRejectReason(reason == null ? null : reason.trim());
        o.setDecidedAt(java.time.Instant.now());
        o.setDecidedBy(user());
        move(o, "REJECTED", o.getRejectReason());
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_REJECTED", "Rejected online order " + o.getCode() + ": " + o.getRejectReason(), "OnlineOrder", o.getId());
        tell(o, I18n.t("About your order {0}", o.getCode()), I18n.t("Sorry, we cannot take your order {0}: {1}", o.getCode(), o.getRejectReason()));
        return dto(o);
    }

    /** Staff cancel an order that was accepted but cannot be completed (the customer changed their mind, or nobody answered the door). Releases the stock it held. */
    @Transactional
    public OrderDto cancel(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        if (o.getSalesOrderId() != null) salesOrderService.cancel(o.getSalesOrderId());
        move(o, "CANCELLED", note == null || note.isBlank() ? I18n.t("Cancelled by staff") : note.trim());
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_CANCELLED", "Cancelled online order " + o.getCode(), "OnlineOrder", o.getId());
        tell(o, I18n.t("Your order {0} is cancelled", o.getCode()), I18n.t("Your order {0} has been cancelled.", o.getCode()));
        return dto(o);
    }

    // ------------------------------------------------------------------ moving along

    @Transactional
    public OrderDto pack(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED");
        move(o, "PACKED", note);
        repository.save(o);
        tell(o, I18n.t("Your order {0} is being prepared", o.getCode()), I18n.t("Your order {0} is packed. We will tell you when it is on its way or ready for pick-up.", o.getCode()));
        return dto(o);
    }

    @Transactional
    public OrderDto outForDelivery(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED", "PACKED");
        if (!o.getDeliveryMethod().equals("DELIVERY")) throw ApiException.badRequest(I18n.t("This is a pick-up order"));
        move(o, "OUT_FOR_DELIVERY", note);
        repository.save(o);
        tell(o, I18n.t("Your order {0} is on its way", o.getCode()), I18n.t("Your order {0} is on its way. Please keep {1} ready to pay on delivery.", o.getCode(), OnlineStoreService.money(o.getTotal())));
        return dto(o);
    }

    @Transactional
    public OrderDto readyForPickup(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED", "PACKED");
        if (!o.getDeliveryMethod().equals("PICKUP")) throw ApiException.badRequest(I18n.t("This is a delivery order"));
        move(o, "READY_FOR_PICKUP", note);
        repository.save(o);
        OnlineStoreSettings s = storeService.settings();
        tell(o, I18n.t("Your order {0} is ready", o.getCode()), I18n.t("Your order {0} is ready for pick-up. Please bring {1} to pay.", o.getCode(), OnlineStoreService.money(o.getTotal()))
                + (s.getPickupAddress() == null ? "" : " " + s.getPickupAddress()));
        return dto(o);
    }

    /** Hands the goods over: takes the stock out through the sale order, issues the invoice and records the cash. */
    @Transactional
    public OrderDto complete(Long id, CompleteRequest in, AppUser caller) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        if (o.getSalesOrderId() == null) throw ApiException.badRequest(I18n.t("This order has no sale order"));
        SalesOrderDto so = salesOrderService.getById(o.getSalesOrderId());
        FulfillSalesOrderRequest fr = new FulfillSalesOrderRequest();
        fr.setLocationId(in == null ? null : in.locationId());
        List<FulfillSalesOrderRequest.FulfillLineEntry> entries = new ArrayList<>();
        for (SalesOrderLineDto l : so.lines()) {
            BigDecimal left = l.quantityOrdered().subtract(l.quantityFulfilled());
            if (left.signum() <= 0) continue;
            FulfillSalesOrderRequest.FulfillLineEntry e = new FulfillSalesOrderRequest.FulfillLineEntry();
            e.setLineId(l.id());
            e.setQuantityFulfilled(left);
            entries.add(e);
        }
        if (!entries.isEmpty()) {
            fr.setLines(entries);
            salesOrderService.fulfillLines(so.id(), fr);
        }
        InvoiceDto inv = invoiceService.generate(so.id());
        o.setInvoiceId(inv.id());
        o.setInvoiceNumber(inv.invoiceNumber());
        boolean collect = in == null || in.collectPayment() == null || in.collectPayment();
        if (collect && inv.totalAmount() != null && inv.totalAmount().signum() > 0) {
            RecordInvoicePaymentRequest pay = new RecordInvoicePaymentRequest();
            pay.setAmount(inv.totalAmount());
            pay.setMethod(in == null || in.method() == null || in.method().isBlank() ? "CASH" : in.method().trim());
            pay.setNote(I18n.t("Online order {0}", o.getCode()));
            invoiceService.recordPayment(inv.id(), pay, caller);
        }
        move(o, "COMPLETED", I18n.t("Invoice {0}", inv.invoiceNumber()) + (collect ? "" : ", " + I18n.t("payment not collected")));
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_COMPLETED", "Completed online order " + o.getCode() + " with invoice " + inv.invoiceNumber(), "OnlineOrder", o.getId());
        tell(o, I18n.t("Thank you for your order {0}", o.getCode()), I18n.t("Thank you. Your order {0} is complete. Invoice {1}.", o.getCode(), inv.invoiceNumber()));
        return dto(o);
    }

    // ------------------------------------------------------------------ helpers

    private Customer customerFor(OnlineOrder o) {
        return customerRepository.findFirstByPhone(o.getCustomerPhone()).orElseGet(() -> {
            Customer c = new Customer();
            c.setName(o.getCustomerName());
            c.setPhone(o.getCustomerPhone());
            c.setEmail(o.getCustomerEmail());
            return customerRepository.save(c);
        });
    }

    private void tell(OnlineOrder o, String subject, String text) {
        OnlineMessages.send(mailService, storeSettingsService, o, subject, text);
    }

    static OrderDto dto(OnlineOrder o) {
        return new OrderDto(o.getId(), o.getCode(), o.getStatus(), o.getDeliveryMethod(), o.getPaymentMethod(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerAddress(), o.getZoneName(), o.getDeliveryFee(), o.getNotes(), o.getSubtotal(), o.getTotal(), o.getLanguage(),
                o.getSalesOrderId(), o.getSalesOrderNumber(), o.getInvoiceId(), o.getInvoiceNumber(), o.getRejectReason(), o.getDecidedBy(), o.getDecidedAt(),
                o.getCreatedAt(),
                o.getLines().stream().map(l -> new OrderLineDto(l.getProductId(), l.getName(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal())).toList(),
                o.getEvents().stream().map(e -> new EventDto(e.getStatus(), e.getNote(), e.getByUser(), e.getCreatedAt())).toList());
    }
}
