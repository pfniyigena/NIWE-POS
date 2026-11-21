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

    /** v1.65.0: the stock location a delivery is taken from when nobody chose one. */
    @org.springframework.beans.factory.annotation.Autowired
    private OnlineShopStock shopStock;

    private final ShopLoyaltyService loyalty;
    private final OnlineDriverRepository drivers;
    @org.springframework.beans.factory.annotation.Autowired
    private OnlineSlotService slotService;

    private static final Set<String> OPEN = Set.of("PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");

    private final OnlineOrderRepository repository;
    private final OnlineStoreService storeService;
    private final CustomerRepository customerRepository;
    private final SalesOrderService salesOrderService;
    private final InvoiceService invoiceService;
    private final StoreSettingsService storeSettingsService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final OnlinePaymentService payments;

    public OnlineOrderService(OnlineOrderRepository repository, OnlineStoreService storeService, CustomerRepository customerRepository,
                              SalesOrderService salesOrderService, InvoiceService invoiceService, StoreSettingsService storeSettingsService,
                              MailService mailService, AuditLogService auditLogService, OnlinePaymentService payments, ShopLoyaltyService loyalty,
                              OnlineDriverRepository drivers) {
        this.drivers = drivers;
        this.loyalty = loyalty;
        this.payments = payments;
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
        throw ApiException.badRequest(I18n.t("This order is {0}, so that is not possible now", statusWord(o.getStatus())));
    }

    private void move(OnlineOrder o, String status, String note) {
        move(o, status, note, user());
    }

    private void move(OnlineOrder o, String status, String note, String by) {
        o.setStatus(status);
        if (note != null && note.length() > 200) note = note.substring(0, 197) + "...";
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(status);
        ev.setNote(note);
        ev.setByUser(by);
        o.getEvents().add(ev);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<OrderDto> list(String q, String status, Long driverId) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> wanted == null || (wanted.equals("OPEN") ? OPEN.contains(o.getStatus()) : wanted.equals("REFUND_DUE") ? o.getPaymentStatus().equals("REFUND_DUE") || (o.getRefundDifference() != null && o.getRefundDifference().signum() > 0) : o.getStatus().equals(wanted)))
                .filter(o -> driverId == null || (driverId == 0 ? o.getDriverId() == null && "DELIVERY".equals(o.getDeliveryMethod()) : driverId.equals(o.getDriverId())))
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

    /** How many orders wait for an answer, how many of them for longer than the alert time (0 = no alert time), and that time in minutes. */
    @Transactional
    public java.util.Map<String, Long> pendingSummary() {
        int minutes = storeService.settings().getUnansweredAlertMinutes();
        long overdue = minutes > 0 ? repository.countByStatusAndCreatedAtBefore("PLACED", java.time.Instant.now().minusSeconds(60L * minutes)) : 0;
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>(payments.attentionCounts());
        out.put("pending", repository.countByStatus("PLACED"));
        // the numbers on the other tabs of Online orders
        out.put("open", repository.countByStatusIn(OPEN));
        out.put("completed", repository.countByStatus("COMPLETED"));
        out.put("rejected", repository.countByStatus("REJECTED"));
        out.put("cancelled", repository.countByStatus("CANCELLED"));
        out.put("all", repository.count());
        out.put("overdue", overdue);
        out.put("alertMinutes", (long) minutes);
        return out;
    }

    // ------------------------------------------------------------------ decisions

    /**
     * The sale order line(s) of an order line. With no coupon discount it is one line at the list price. With one, the line price is lowered so the
     * invoice total equals what the customer pays: prices have 2 decimals, so when the discounted line total does not divide evenly over the quantity,
     * the line is split in two prices one cent apart (for example 3 x 1,333.33 would be 3,999.99, so it becomes 2 x 1,333.33 + 1 x 1,333.34 = 4,000.00).
     */
    static void saleLines(List<SalesOrderRequest.LineEntry> out, OnlineOrderLine l) {
        BigDecimal disc = l.getDiscountAmount() == null ? BigDecimal.ZERO : l.getDiscountAmount();
        BigDecimal gross = l.getLineTotal();
        if (disc.signum() <= 0 || gross == null || gross.signum() <= 0) {
            out.add(saleLine(l.getProductId(), l.getQuantity(), l.getBasePrice()));
            return;
        }
        BigDecimal qty = l.getQuantity();
        // What the customer pays for the line, in the same terms as the sale order (list price, tax added by the sale order if it is not inclusive).
        BigDecimal target = l.getBasePrice().multiply(qty).multiply(gross.subtract(disc)).divide(gross, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal unit = target.divide(qty, 2, java.math.RoundingMode.DOWN);
        BigDecimal rest = target.subtract(unit.multiply(qty));
        int dearer = rest.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        BigDecimal dearerQty = BigDecimal.valueOf(dearer);
        if (dearer <= 0 || dearerQty.compareTo(qty) >= 0) {
            out.add(saleLine(l.getProductId(), qty, dearer <= 0 ? unit : unit.add(new BigDecimal("0.01"))));
            return;
        }
        out.add(saleLine(l.getProductId(), qty.subtract(dearerQty), unit));
        out.add(saleLine(l.getProductId(), dearerQty, unit.add(new BigDecimal("0.01"))));
    }

    private static SalesOrderRequest.LineEntry saleLine(Long productId, BigDecimal qty, BigDecimal unitPrice) {
        SalesOrderRequest.LineEntry e = new SalesOrderRequest.LineEntry();
        e.setProductId(productId);
        e.setQuantityOrdered(qty);
        e.setUnitPrice(unitPrice);
        return e;
    }

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
        for (OnlineOrderLine l : o.getLines()) saleLines(lines, l);
        if (fee.signum() > 0) {
            SalesOrderRequest.LineEntry e = new SalesOrderRequest.LineEntry();
            e.setProductId(s.getDeliveryProductId());
            e.setQuantityOrdered(BigDecimal.ONE);
            e.setUnitPrice(fee);
            // v1.59.1: the fee is what the customer pays; the tax chosen for the area is the part of it that is tax (included), so invoice = order total
            if (o.getDeliveryTaxRateId() != null) {
                e.setTaxRateId(o.getDeliveryTaxRateId());
                e.setTaxIncluded(true);
            }
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
        tell(o, OnlineMessages.m("Your order {0} is accepted", o.getCode()), OnlineStoreService.withSlot(OnlineMessages.m("Good news: your order {0} is accepted and we are preparing it.", o.getCode()), o));
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
        boolean refundDue = payments.afterClosed(o);
        loyalty.giveBackSpent(o);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_REJECTED", "Rejected online order " + o.getCode() + ": " + o.getRejectReason(), "OnlineOrder", o.getId());
        tell(o, OnlineMessages.m("About your order {0}", o.getCode()), refundDue
                ? OnlineMessages.m("Sorry, we cannot take your order {0}: {1}. We will refund {2} to the account you paid with.", o.getCode(), o.getRejectReason(), OnlineStoreService.money(o.getPaidAmount()))
                : OnlineMessages.m("Sorry, we cannot take your order {0}: {1}", o.getCode(), o.getRejectReason()));
        return dto(o);
    }

    /** Staff cancel an order that was accepted but cannot be completed (the customer changed their mind, or nobody answered the door). Releases the stock it held. */
    @Transactional
    public OrderDto cancel(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "AWAITING_PAYMENT", "PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        if (o.getSalesOrderId() != null) salesOrderService.cancel(o.getSalesOrderId());
        move(o, "CANCELLED", note == null || note.isBlank() ? I18n.t("Cancelled by staff") : note.trim());
        boolean refundDue = payments.afterClosed(o);
        loyalty.giveBackSpent(o);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_CANCELLED", "Cancelled online order " + o.getCode(), "OnlineOrder", o.getId());
        tell(o, OnlineMessages.m("Your order {0} is cancelled", o.getCode()), refundDue
                ? OnlineMessages.m("Your order {0} has been cancelled. We will refund {1} to the account you paid with.", o.getCode(), OnlineStoreService.money(o.getPaidAmount()))
                : OnlineMessages.m("Your order {0} has been cancelled.", o.getCode()));
        return dto(o);
    }

    // ------------------------------------------------------------------ moving along

    @Transactional
    public OrderDto pack(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED");
        move(o, "PACKED", note);
        repository.save(o);
        tell(o, OnlineMessages.m("Your order {0} is being prepared", o.getCode()), OnlineMessages.m("Your order {0} is packed. We will tell you when it is on its way or ready for pick-up.", o.getCode()));
        return dto(o);
    }

    @Transactional
    public OrderDto outForDelivery(Long id, String note, Long driverId) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED", "PACKED");
        if (!o.getDeliveryMethod().equals("DELIVERY")) throw ApiException.badRequest(I18n.t("This is a pick-up order"));
        // v1.64.0: when windows are compulsory, an order whose visit failed needs a new window first (the customer chooses one, or staff use Change time)
        OnlineSlotService.SlotSettings ss = slotService.slotSettings();
        if (ss.enabled() && ss.required() && o.getDeliveryAttempts() > 0 && o.getSlotDate() == null) {
            throw ApiException.badRequest(I18n.t("Choose a new delivery time first: the customer can choose one on his order page, or use Change time."));
        }
        if (driverId != null) setDriver(o, driverId);
        // when the shop has drivers, somebody must be taking the order
        if (o.getDriverId() == null && drivers.countByActiveTrue() > 0) throw ApiException.badRequest(I18n.t("Choose the driver who takes this order."));
        move(o, "OUT_FOR_DELIVERY", note);
        repository.save(o);
        OnlineMessages.Msg text = paid(o) ? OnlineMessages.m("Your order {0} is on its way. It is already paid.", o.getCode())
                : OnlineMessages.m("Your order {0} is on its way. Please keep {1} ready to pay on delivery.", o.getCode(), OnlineStoreService.money(o.getTotal()));
        if (o.getDriverName() != null) {
            text = text.then(o.getDriverPhone() != null ? OnlineMessages.m("Your driver is {0}, phone {1}.", o.getDriverName(), o.getDriverPhone())
                    : OnlineMessages.m("Your driver is {0}.", o.getDriverName()));
        }
        String code = receiptCode(o);
        text = text.then(OnlineMessages.m("Your delivery code is {0}. Give it to the driver only when you hold your goods.", code));
        repository.save(o);
        tellCode(o, OnlineMessages.m("Your order {0} is on its way", o.getCode()), text, code);
        return dto(o);
    }

    /** Chooses (or, with null, removes) the driver of a delivery order that is not delivered yet. */
    @Transactional
    public OrderDto assignDriver(Long id, Long driverId) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY");
        if (!"DELIVERY".equals(o.getDeliveryMethod())) throw ApiException.badRequest(I18n.t("This is a pick-up order"));
        String before = o.getDriverName();
        setDriver(o, driverId);
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setNote(o.getDriverName() == null ? I18n.t("Driver removed") : I18n.t("Driver: {0}", o.getDriverName()));
        ev.setByUser(user());
        o.getEvents().add(ev);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_DRIVER", "Online order " + o.getCode() + ": driver " + (o.getDriverName() == null ? "removed (was " + before + ")" : o.getDriverName()), "OnlineOrder", o.getId());
        return dto(o);
    }

    /**
     * Staff move a delivery order to another delivery window (or back to "as soon as possible"): the customer asked, a visit failed, the shop is late.
     * The limit of the window does not stop staff; the customer is told.
     */
    @Transactional
    public OrderDto changeSlot(Long id, SlotChange in) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY");
        if (!"DELIVERY".equals(o.getDeliveryMethod())) throw ApiException.badRequest(I18n.t("This is a pick-up order"));
        OnlineSlotService.Pick pick = slotService.staffPick(in == null ? null : in.slotId(), in == null ? null : in.slotDate());
        String before = OnlineSlotService.label(o.getSlotDate(), o.getSlotStart(), o.getSlotEnd());
        o.setSlotDate(pick == null ? null : pick.date());
        o.setSlotStart(pick == null ? null : pick.start());
        o.setSlotEnd(pick == null ? null : pick.end());
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setNote(pick == null ? I18n.t("Delivery time removed: as soon as possible") : I18n.t("Delivery time: {0}", OnlineSlotService.label(pick.date(), pick.start(), pick.end())));
        ev.setByUser(user());
        o.getEvents().add(ev);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_SLOT", "Online order " + o.getCode() + ": delivery time " + (pick == null ? "removed" : OnlineSlotService.label(pick.date(), pick.start(), pick.end())) + (before.isEmpty() ? "" : " (was " + before + ")"), "OnlineOrder", o.getId());
        if (pick == null) {
            tell(o, OnlineMessages.m("About the delivery of your order {0}", o.getCode()), OnlineMessages.m("The delivery time of your order {0} is no longer fixed. We will bring it as soon as possible.", o.getCode()));
        } else {
            tell(o, OnlineMessages.m("About the delivery of your order {0}", o.getCode()),
                    OnlineMessages.m("The delivery of your order {0} is planned on {1}, between {2} and {3}.", o.getCode(), OnlineSlotService.d(pick.date()), OnlineSlotService.t(pick.start()), OnlineSlotService.t(pick.end())));
        }
        return dto(o);
    }

    /** An order status in words, in the language of the person reading (used inside error messages). */
    static String statusWord(String status) {
        return switch (status == null ? "" : status) {
            case "AWAITING_PAYMENT" -> I18n.t("waiting for payment");
            case "PLACED" -> I18n.t("just placed");
            case "ACCEPTED" -> I18n.t("accepted");
            case "PACKED" -> I18n.t("packed");
            case "OUT_FOR_DELIVERY" -> I18n.t("out for delivery");
            case "READY_FOR_PICKUP" -> I18n.t("ready for pick-up");
            case "COMPLETED" -> I18n.t("completed");
            case "CANCELLED" -> I18n.t("cancelled");
            case "REJECTED" -> I18n.t("rejected");
            default -> status == null ? "" : status.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private void setDriver(OnlineOrder o, Long driverId) {
        if (driverId == null) {
            o.setDriverId(null);
            o.setDriverName(null);
            o.setDriverPhone(null);
            o.setDriverAssignedAt(null);
            return;
        }
        OnlineDriver d = drivers.findById(driverId).orElseThrow(() -> ApiException.notFound(I18n.t("Driver not found")));
        if (!d.isActive() && !driverId.equals(o.getDriverId())) throw ApiException.badRequest(I18n.t("This driver is switched off."));
        o.setDriverId(d.getId());
        o.setDriverName(d.getName());
        o.setDriverPhone(d.getPhone());
        o.setDriverAssignedAt(java.time.Instant.now());
    }

    @Transactional
    public OrderDto readyForPickup(Long id, String note) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "ACCEPTED", "PACKED");
        if (!o.getDeliveryMethod().equals("PICKUP")) throw ApiException.badRequest(I18n.t("This is a delivery order"));
        move(o, "READY_FOR_PICKUP", note);
        repository.save(o);
        OnlineStoreSettings s = storeService.settings();
        String pickCode = receiptCode(o);
        tellCode(o, OnlineMessages.m("Your order {0} is ready", o.getCode()), (paid(o) ? OnlineMessages.m("Your order {0} is ready for pick-up. It is already paid.", o.getCode())
                : OnlineMessages.m("Your order {0} is ready for pick-up. Please bring {1} to pay.", o.getCode(), OnlineStoreService.money(o.getTotal())))
                .plus(s.getPickupAddress() == null ? "" : " " + s.getPickupAddress())
                .then(OnlineMessages.m("Your pick-up code is {0}. Give it at the counter only when you collect your goods.", pickCode)), pickCode);
        repository.save(o);
        return dto(o);
    }

    /** Hands the goods over: takes the stock out through the sale order, issues the invoice and records the cash. */
    @Transactional
    public OrderDto complete(Long id, CompleteRequest in, AppUser caller) {
        return finish(getOrThrow(id), in, caller, user(), null, in == null ? null : in.kept());
    }

    /**
     * The goods are in the customer's hands and the shop did not press "Complete" itself (v1.56.0). Checks the way it was confirmed, keeps who and when
     * (and the signature), then completes like staff do: stock out, invoice, and the cash recorded as collected for an order paid on delivery.
     * Wrong codes are counted even though the request is refused, so the counter survives the error (noRollbackFor).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto receive(Long id, ReceiveRequest in, AppUser caller) {
        return receiveAs(getOrThrow(id), in, caller, user());
    }

    /**
     * A driver with a private link and PIN (v1.59.0, no staff login) gives the customer's code or signature. Only an order assigned to him; the stock
     * location and partial delivery are not his to choose (a partial or failed delivery is recorded by staff).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto receiveByDriver(Long id, OnlineDriver driver, ReceiveRequest in) {
        OnlineOrder o = getOrThrow(id);
        if (driver == null || !driver.getId().equals(o.getDriverId())) throw ApiException.badRequest(I18n.t("This order is not yours to deliver."));
        return receiveAs(o, in, null, "Driver: " + driver.getName());
    }

    private OrderDto receiveAs(OnlineOrder o, ReceiveRequest in, AppUser caller, String by) {
        expect(o, "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        // v1.59.0: staff and drivers hand over with the customer's code only; the customer signs only on his own page (receiveByCustomer)
        String code = in == null || in.code() == null ? "" : in.code().trim();
        if (code.isEmpty()) throw ApiException.badRequest(I18n.t("Type the customer's delivery code."));
        checkCode(o, code);
        o.setReceivedVia("CODE");
        o.setReceivedBy(by);
        o.setReceivedAt(java.time.Instant.now());
        o.setSignature(null);
        o.setSignedName(null);
        String how = I18n.t("received: the customer's code was typed");
        try {
            return finish(o, new CompleteRequest(in == null ? null : in.locationId(), true, "CASH", null), caller, by, how, in == null ? null : in.kept());
        } catch (ApiException e) {
            // refused (no sale order, say): the order is not received, whatever noRollbackFor keeps
            o.setReceivedVia(null);
            o.setReceivedBy(null);
            o.setReceivedAt(null);
            o.setSignature(null);
            o.setSignedName(null);
            throw e;
        }
    }

    /** The customer tapped "I received my order" (the caller has already proved the order is theirs). */
    @Transactional
    public OrderDto receiveByCustomer(Long id, String signature, String signedName) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        o.setReceivedVia("CUSTOMER");
        o.setReceivedBy("customer");
        o.setReceivedAt(java.time.Instant.now());
        o.setSignature(cleanSignature(signature));
        o.setSignedName(signedName == null || signedName.isBlank() ? null : signedName.trim());
        return finish(o, new CompleteRequest(null, true, "CASH", null), null, "customer", I18n.t("received: the customer confirmed it"));
    }

    /** The drawn signature of an order as PNG bytes, or null. */
    @Transactional(readOnly = true)
    public byte[] signaturePng(Long id) {
        String sig = getOrThrow(id).getSignature();
        return sig == null ? null : java.util.Base64.getDecoder().decode(sig.substring(sig.indexOf(',') + 1));
    }

    /**
     * Staff make a new delivery / pick-up code (the customer lost it, never got the message, or the code was blocked after wrong tries). The old code stops
     * working, the wrong-code counter starts again, and the customer gets the new code the same way as the first one. Staff never see the code.
     */
    @Transactional
    public OrderDto reissueCode(Long id) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        String code = o.reissueReceiptCode();
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setNote(I18n.t("A new code was sent to the customer"));
        ev.setByUser(user());
        o.getEvents().add(ev);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_CODE_REISSUED", "Re-issued the " + ("DELIVERY".equals(o.getDeliveryMethod()) ? "delivery" : "pick-up") + " code of online order " + o.getCode(), "OnlineOrder", o.getId());
        boolean delivery = "DELIVERY".equals(o.getDeliveryMethod());
        tellCode(o, OnlineMessages.m("Your new code for order {0}", o.getCode()), delivery
                ? OnlineMessages.m("Your new delivery code for order {0} is {1}. The old code no longer works. Give it to the driver only when you hold your goods.", o.getCode(), code)
                : OnlineMessages.m("Your new pick-up code for order {0} is {1}. The old code no longer works. Give it at the counter only when you collect your goods.", o.getCode(), code), code);
        return dto(o);
    }

    private String receiptCode(OnlineOrder o) {
        return o.ensureReceiptCode();
    }

    private void checkCode(OnlineOrder o, String typed) {
        java.time.Instant now = java.time.Instant.now();
        if (o.getReceiptFailures() >= 5 && o.getReceiptFailedAt() != null && o.getReceiptFailedAt().plusSeconds(1800).isAfter(now)) {
            throw ApiException.badRequest(I18n.t("Too many wrong codes. Ask the customer to confirm in the shop, have the customer sign, or try again in half an hour."));
        }
        if (o.getReceiptCode() != null && o.getReceiptCode().equals(typed)) {
            o.setReceiptFailures(0);
            o.setReceiptFailedAt(null);
            return;
        }
        o.setReceiptFailures(o.getReceiptFailedAt() != null && o.getReceiptFailedAt().plusSeconds(1800).isBefore(now) ? 1 : o.getReceiptFailures() + 1);
        o.setReceiptFailedAt(now);
        repository.save(o);
        throw ApiException.badRequest(I18n.t("That code is not right."));
    }

    /** A drawn signature as sent by the browser: a PNG data URL of a sensible size. Anything else is refused; empty means no signature. */
    static String cleanSignature(String in) {
        if (in == null || in.isBlank()) return null;
        String prefix = "data:image/png;base64,";
        if (!in.startsWith(prefix) || in.length() > 200_000) throw ApiException.badRequest(I18n.t("The signature could not be read. Please sign again."));
        try {
            byte[] png = java.util.Base64.getDecoder().decode(in.substring(prefix.length()));
            if (png.length < 100 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') throw new IllegalArgumentException("not a png");
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(I18n.t("The signature could not be read. Please sign again."));
        }
        return in;
    }

    private OrderDto finish(OnlineOrder o, CompleteRequest in, AppUser caller, String by, String how) {
        return finish(o, in, caller, by, how, null);
    }

    /** What a partial delivery left out: the part of the coupon/points discount that belonged to the goods not delivered, and the net value of the goods delivered. */
    private record Partial(BigDecimal undeliveredDiscount, BigDecimal deliveredNet, String summary) {
    }

    /**
     * The customer kept only some of the goods (v1.58.0). Checks the quantities kept per product, writes them on the order lines and lowers the sale order, so
     * the invoice and the stock cover only what stayed with the customer. Returns null when everything was kept (a normal delivery).
     */
    private Partial applyKept(OnlineOrder o, SalesOrderDto so, List<KeptLine> kept) {
        java.util.Map<Long, BigDecimal> ordered = new java.util.LinkedHashMap<>();
        for (OnlineOrderLine l : o.getLines()) ordered.merge(l.getProductId(), l.getQuantity(), BigDecimal::add);
        java.util.Map<Long, BigDecimal> keep = new java.util.LinkedHashMap<>(ordered);
        for (KeptLine k : kept) {
            if (k == null || k.productId() == null || k.quantity() == null || !ordered.containsKey(k.productId())) throw ApiException.badRequest(I18n.t("That item is not in this order."));
            if (k.quantity().signum() < 0 || k.quantity().compareTo(ordered.get(k.productId())) > 0) throw ApiException.badRequest(I18n.t("The quantity delivered cannot be more than the quantity ordered."));
            keep.put(k.productId(), k.quantity());
        }
        boolean all = ordered.entrySet().stream().allMatch(e -> keep.get(e.getKey()).compareTo(e.getValue()) == 0);
        if (all) return null;
        if (keep.values().stream().allMatch(q -> q.signum() == 0)) throw ApiException.badRequest(I18n.t("Nothing was delivered. Use \"Delivery failed\" instead."));
        BigDecimal undeliveredDiscount = BigDecimal.ZERO;
        BigDecimal deliveredNet = BigDecimal.ZERO;
        StringBuilder summary = new StringBuilder();
        java.util.Map<Long, BigDecimal> left = new java.util.LinkedHashMap<>(keep);
        for (OnlineOrderLine l : o.getLines()) {
            BigDecimal rem = left.get(l.getProductId());
            BigDecimal d = rem.min(l.getQuantity());
            left.put(l.getProductId(), rem.subtract(d));
            l.setDeliveredQuantity(d);
            BigDecimal disc = l.getDiscountAmount() == null ? BigDecimal.ZERO : l.getDiscountAmount();
            BigDecimal ratioKept = l.getQuantity().signum() == 0 ? BigDecimal.ZERO : d.divide(l.getQuantity(), 8, java.math.RoundingMode.HALF_UP);
            deliveredNet = deliveredNet.add(l.getLineTotal().subtract(disc).multiply(ratioKept));
            undeliveredDiscount = undeliveredDiscount.add(disc.multiply(BigDecimal.ONE.subtract(ratioKept)));
            if (d.compareTo(l.getQuantity()) < 0) {
                if (summary.length() > 0) summary.append("; ");
                summary.append(d.stripTrailingZeros().toPlainString()).append('/').append(l.getQuantity().stripTrailingZeros().toPlainString()).append(" ").append(l.getName());
            }
        }
        java.util.Map<Long, BigDecimal> newQty = new java.util.LinkedHashMap<>();
        java.util.Map<Long, BigDecimal> rest = new java.util.LinkedHashMap<>(keep);
        for (SalesOrderLineDto sl : so.lines()) {
            if (!rest.containsKey(sl.productId())) continue;
            BigDecimal q = rest.get(sl.productId()).min(sl.quantityOrdered());
            rest.put(sl.productId(), rest.get(sl.productId()).subtract(q));
            newQty.put(sl.id(), q);
        }
        salesOrderService.reduceQuantities(so.id(), newQty);
        return new Partial(undeliveredDiscount.setScale(2, java.math.RoundingMode.HALF_UP), deliveredNet.setScale(2, java.math.RoundingMode.HALF_UP), summary.toString());
    }

    /** The lines a completion is going to take out of stock: everything left, or (partial delivery) only the part the customer keeps. */
    private static List<FulfillSalesOrderRequest.FulfillLineEntry> planned(SalesOrderDto so, List<KeptLine> kept) {
        java.util.Map<Long, BigDecimal> rest = new java.util.HashMap<>();
        if (kept != null) for (KeptLine k : kept) if (k != null && k.productId() != null && k.quantity() != null) rest.merge(k.productId(), k.quantity(), BigDecimal::add);
        List<FulfillSalesOrderRequest.FulfillLineEntry> out = new ArrayList<>();
        for (SalesOrderLineDto l : so.lines()) {
            BigDecimal left = l.quantityOrdered().subtract(l.quantityFulfilled());
            if (left.signum() <= 0) continue;
            BigDecimal take = left;
            if (!rest.isEmpty() && rest.containsKey(l.productId())) {
                take = left.min(rest.get(l.productId()).max(BigDecimal.ZERO));
                rest.put(l.productId(), rest.get(l.productId()).subtract(take));
            }
            if (take.signum() <= 0) continue;
            FulfillSalesOrderRequest.FulfillLineEntry e = new FulfillSalesOrderRequest.FulfillLineEntry();
            e.setLineId(l.id());
            e.setQuantityFulfilled(take);
            out.add(e);
        }
        return out;
    }

    private OrderDto finish(OnlineOrder o, CompleteRequest in, AppUser caller, String by, String how, List<KeptLine> kept) {
        expect(o, "OUT_FOR_DELIVERY", "READY_FOR_PICKUP");
        if (o.getSalesOrderId() == null) throw ApiException.badRequest(I18n.t("This order has no sale order"));
        SalesOrderDto so = salesOrderService.getById(o.getSalesOrderId());
        // v1.65.0: the stock check comes first and for what is going to be taken (the kept part of a partial delivery), so a refusal ("not enough
        // stock at the location") is a clear message and leaves nothing half done; the location is the shop's own when nobody chose one.
        FulfillSalesOrderRequest fr = new FulfillSalesOrderRequest();
        try {
            fr.setLocationId(shopStock.locationFor(o, in == null ? null : in.locationId()));
            fr.setLines(planned(so, kept));
            if (!fr.getLines().isEmpty()) salesOrderService.checkFulfilment(so.id(), fr);
        } catch (ApiException e) {
            if (caller != null) throw e;
            // the driver and the customer get a plain sentence; the reason is written in the order's history for staff
            OnlineOrderEvent ev = new OnlineOrderEvent();
            ev.setOnlineOrder(o);
            ev.setStatus(o.getStatus());
            String why = e.getMessage() == null ? "" : e.getMessage();
            ev.setNote(I18n.t("The hand-over could not be recorded: {0}", why.length() > 200 ? why.substring(0, 200) : why));
            ev.setByUser(by);
            o.getEvents().add(ev);
            repository.save(o);
            throw ApiException.badRequest(I18n.t("We could not record this delivery just now. Please call the shop."));
        }
        Partial partial = kept == null || kept.isEmpty() ? null : applyKept(o, so, kept);
        if (partial != null) so = salesOrderService.getById(o.getSalesOrderId());
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
        // No e-mail yet: it goes out below, after the payment is recorded, so the invoice the customer receives is not marked unpaid.
        InvoiceDto inv = invoiceService.generateWithoutEmail(so.id());
        o.setInvoiceId(inv.id());
        o.setInvoiceNumber(inv.invoiceNumber());
        boolean prepaid = paid(o);
        // Paid online: the money is already ours, so record it against the invoice with the way the customer paid, whatever staff ticked.
        boolean collect = prepaid || in == null || in.collectPayment() == null || in.collectPayment();
        String differs = "";
        if (prepaid && inv.totalAmount() != null && inv.totalAmount().signum() > 0) {
            BigDecimal amount = o.getPaidAmount().min(inv.totalAmount());
            RecordInvoicePaymentRequest pay = new RecordInvoicePaymentRequest();
            pay.setAmount(amount);
            pay.setMethod(o.getPaymentChannel());
            pay.setNote(I18n.t("Online payment {0}", o.getPaymentReference()));
            invoiceService.recordPayment(inv.id(), pay, caller);
            if (partial != null && o.getPaidAmount().compareTo(inv.totalAmount()) > 0) {
                o.setRefundDifference(o.getPaidAmount().subtract(inv.totalAmount()));
                differs = ", " + I18n.t("customer paid online {0}, invoice {1}: give back {2}", OnlineStoreService.money(o.getPaidAmount()), OnlineStoreService.money(inv.totalAmount()), OnlineStoreService.money(o.getRefundDifference()));
            } else if (o.getPaidAmount().compareTo(inv.totalAmount()) != 0) {
                differs = ", " + I18n.t("paid online {0} but the invoice is {1}: check the difference", OnlineStoreService.money(o.getPaidAmount()), OnlineStoreService.money(inv.totalAmount()));
            }
        } else if (collect && inv.totalAmount() != null && inv.totalAmount().signum() > 0) {
            RecordInvoicePaymentRequest pay = new RecordInvoicePaymentRequest();
            pay.setAmount(inv.totalAmount());
            pay.setMethod(in == null || in.method() == null || in.method().isBlank() ? "CASH" : in.method().trim());
            pay.setNote(I18n.t("Online order {0}", o.getCode()));
            invoiceService.recordPayment(inv.id(), pay, caller);
            // cash taken by a driver is cash he must hand in (Online Store > Driver cash)
            if ("DELIVERY".equals(o.getDeliveryMethod()) && "CASH".equalsIgnoreCase(pay.getMethod())) o.setCashCollected(inv.totalAmount());
        }
        if (partial != null) o.setDeliveredTotal(inv.totalAmount());
        invoiceService.emailIssued(inv.id());
        move(o, "COMPLETED", I18n.t("Invoice {0}", inv.invoiceNumber()) + (how == null ? "" : ", " + how)
                + (partial == null ? "" : ", " + I18n.t("delivered in part: {0}", partial.summary())) + (collect ? "" : ", " + I18n.t("payment not collected")) + differs, by);
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_COMPLETED", "Completed online order " + o.getCode() + " with invoice " + inv.invoiceNumber(), "OnlineOrder", o.getId());
        if (partial != null) loyalty.giveBackPart(o, partial.undeliveredDiscount());
        int earned = partial == null ? loyalty.earn(o) : loyalty.earn(o, partial.deliveredNet());
        OnlineMessages.Msg thanks = partial != null
                ? OnlineMessages.m("Thank you. Your order {0} is complete, with part of the goods delivered. Invoice {1}.", o.getCode(), inv.invoiceNumber())
                : earned > 0 ? OnlineMessages.m("Thank you. Your order {0} is complete. Invoice {1}. You earned {2} loyalty points.", o.getCode(), inv.invoiceNumber(), earned)
                : OnlineMessages.m("Thank you. Your order {0} is complete. Invoice {1}.", o.getCode(), inv.invoiceNumber());
        if (partial != null && earned > 0) thanks = thanks.then(OnlineMessages.m("You earned {0} loyalty points.", earned));
        if (partial != null && o.getRefundDifference() != null && o.getRefundDifference().signum() > 0) {
            thanks = thanks.then(OnlineMessages.m("We will refund {0} to the account you paid with.", OnlineStoreService.money(o.getRefundDifference())));
        }
        tell(o, OnlineMessages.m("Thank you for your order {0}", o.getCode()), thanks);
        return dto(o);
    }

    // ------------------------------------------------------------------ failed delivery (v1.58.0)

    /**
     * The driver came and could not hand the goods over (customer absent, refused, wrong address). The order goes back to PACKED with the reason and the
     * number of tries; the stock stays reserved, the driver stays on the order, and staff send it out again or cancel it.
     */
    @Transactional
    public OrderDto deliveryFailed(Long id, String reason) {
        OnlineOrder o = getOrThrow(id);
        expect(o, "OUT_FOR_DELIVERY");
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) throw ApiException.badRequest(I18n.t("Write the reason the order could not be delivered."));
        if (why.length() > 160) why = why.substring(0, 160);
        o.setDeliveryAttempts(o.getDeliveryAttempts() + 1);
        o.setLastFailureReason(why);
        o.setLastFailureAt(java.time.Instant.now());
        move(o, "PACKED", I18n.t("Delivery failed (attempt {0}): {1}", o.getDeliveryAttempts(), why));
        // v1.64.0: the window of the failed visit is freed at once; the customer is asked to choose a new one (when delivery windows are on)
        boolean rebook = slotService.enabled();
        if (o.getSlotDate() != null) {
            String old = OnlineSlotService.label(o.getSlotDate(), o.getSlotStart(), o.getSlotEnd());
            o.setSlotDate(null);
            o.setSlotStart(null);
            o.setSlotEnd(null);
            OnlineOrderEvent ev = new OnlineOrderEvent();
            ev.setOnlineOrder(o);
            ev.setStatus(o.getStatus());
            ev.setNote(I18n.t("Delivery time {0} freed: waiting for a new time", old));
            ev.setByUser(user());
            o.getEvents().add(ev);
        }
        repository.save(o);
        auditLogService.record("ONLINE_ORDER_DELIVERY_FAILED", "Delivery of online order " + o.getCode() + " failed (attempt " + o.getDeliveryAttempts() + "): " + why, "OnlineOrder", o.getId());
        tell(o, OnlineMessages.m("About the delivery of your order {0}", o.getCode()), rebook
                ? OnlineMessages.m("We could not deliver your order {0}: {1}. Please open your order page and choose a new delivery time. If you prefer, we will contact you to arrange it.", o.getCode(), why)
                : OnlineMessages.m("We could not deliver your order {0}: {1}. We will contact you to arrange a new delivery.", o.getCode(), why));
        return dto(o);
    }

    /** Gives back what an order paid online and delivered in part cost more than what was delivered (the invoice). */
    @Transactional
    public OrderDto refundDifference(Long id, RefundRequest in) {
        OnlineOrder o = getOrThrow(id);
        BigDecimal due = o.getRefundDifference();
        if (due == null || due.signum() <= 0) throw ApiException.badRequest(I18n.t("There is nothing left to refund on this order"));
        String ref = payments.refundPart(o, due, in != null && in.manual(), in == null ? null : in.reference(), in == null ? null : in.note());
        o.setRefundDifference(null);
        repository.save(o);
        tell(o, OnlineMessages.m("Your refund for order {0}", o.getCode()),
                OnlineMessages.m("We refunded {0} for the goods of your order {1} that were not delivered. Reference: {2}.", OnlineStoreService.money(due), o.getCode(), ref));
        return dto(o);
    }

    // ------------------------------------------------------------------ money paid online

    /** Gives the customer's money back on an order that was paid online and then cancelled or rejected. */
    @Transactional
    public OrderDto refund(Long id, RefundRequest in) {
        return dto(payments.refund(id, in != null && in.manual(), in == null ? null : in.reference(), in == null ? null : in.note()));
    }

    @Transactional(readOnly = true)
    public Reconciliation reconciliation(java.time.LocalDate from, java.time.LocalDate to, String status) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate end = to == null ? java.time.LocalDate.now(zone) : to;
        java.time.LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) throw ApiException.badRequest(I18n.t("The start date is after the end date"));
        return payments.reconciliation(start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant(), status);
    }

    @Transactional
    public void resolvePayment(Long paymentId, String note) {
        payments.resolve(paymentId, note);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean paid(OnlineOrder o) {
        return "PAID".equals(o.getPaymentStatus()) && o.getPaidAmount() != null;
    }

    private Customer customerFor(OnlineOrder o) {
        return customerRepository.findFirstByPhone(o.getCustomerPhone()).orElseGet(() -> {
            Customer c = new Customer();
            c.setName(o.getCustomerName());
            c.setPhone(o.getCustomerPhone());
            c.setEmail(o.getCustomerEmail());
            return customerRepository.save(c);
        });
    }

    private void tell(OnlineOrder o, OnlineMessages.Msg subject, OnlineMessages.Msg text) {
        OnlineMessages.send(mailService, storeSettingsService, o, subject, text);
    }

    /** A message with the delivery / pick-up code: by e-mail and by SMS (see {@link OnlineMessages#sendCode}); the order's history says how each went. */
    private void tellCode(OnlineOrder o, OnlineMessages.Msg subject, OnlineMessages.Msg text, String code) {
        String how = OnlineMessages.sendCode(mailService, storeSettingsService, o, subject, text, code);
        OnlineOrderEvent ev = new OnlineOrderEvent();
        ev.setOnlineOrder(o);
        ev.setStatus(o.getStatus());
        ev.setNote(how);
        ev.setByUser(user());
        o.getEvents().add(ev);
        repository.save(o);
    }

    static OrderDto dto(OnlineOrder o) {
        return new OrderDto(o.getId(), o.getCode(), o.getStatus(), o.getDeliveryMethod(), o.getPaymentMethod(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerAddress(), o.getZoneName(), o.getDeliveryFee(), o.getNotes(), o.getSubtotal(), o.getTotal(), o.getLanguage(),
                o.getSalesOrderId(), o.getSalesOrderNumber(), o.getInvoiceId(), o.getInvoiceNumber(), o.getRejectReason(), o.getDecidedBy(), o.getDecidedAt(),
                o.getCreatedAt(),
                o.getPaymentStatus(), o.getPaymentChannel(), o.getPaymentReference(), o.getPaidAmount(), o.getPaidAt(), o.getRefundedAt(), o.getRefundReference(), o.getCouponCode(), o.getDiscountAmount(), o.getFeeWaived(), o.getRefundedTotal(),
                o.getLines().stream().map(l -> new OrderLineDto(l.getProductId(), l.getName(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal(), l.getDeliveredQuantity())).toList(),
                o.getEvents().stream().map(e -> new EventDto(e.getStatus(), e.getNote(), e.getByUser(), e.getCreatedAt())).toList(),
                o.getLoyaltyPointsUsed(), o.getLoyaltyDiscount(), o.getLoyaltyPointsEarned(), o.getDriverId(), o.getDriverName(), o.getDriverPhone(),
                o.getReceivedAt(), o.getReceivedVia(), o.getReceivedBy(), o.getSignedName(), o.getSignature() != null, o.getReceiptCode() != null,
                o.getDeliveryAttempts(), o.getLastFailureReason(), o.getLastFailureAt(), o.getDeliveredTotal(), o.getRefundDifference(),
                OnlineSlotService.d(o.getSlotDate()), OnlineSlotService.t(o.getSlotStart()), OnlineSlotService.t(o.getSlotEnd()), o.getEtaDaysMin(), o.getEtaDaysMax());
    }
}
