package com.posapp.order;

import com.posapp.apps.AppKey;
import com.posapp.apps.AppsService;
import com.posapp.apps.efris.EfrisSettingService;
import com.posapp.apps.efris.EfrisSubmissionService;
import com.posapp.apps.efris.dto.EfrisConnection;
import com.posapp.apps.efris.dto.EfrisInvoiceLine;
import com.posapp.apps.efris.dto.EfrisInvoicePayload;
import com.posapp.apps.efris.dto.EfrisSubmissionResult;
import com.posapp.apps.efrischad.EfrisChadSettingService;
import com.posapp.apps.efrischad.EfrisChadSubmissionService;
import com.posapp.apps.efrischad.dto.EfrisChadConnection;
import com.posapp.apps.efrischad.dto.EfrisChadInvoiceLine;
import com.posapp.apps.efrischad.dto.EfrisChadInvoicePayload;
import com.posapp.apps.efrischad.dto.EfrisChadSubmissionResult;
import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.order.dto.*;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.register.CashierSession;
import com.posapp.register.CashierSessionRepository;
import com.posapp.register.CashierSessionStatus;
import com.posapp.register.Register;
import com.posapp.register.SessionStatus;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import com.posapp.tax.TaxRate;
import com.posapp.user.AppUser;
import com.posapp.user.Permission;
import com.posapp.user.Role;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;
    private final PaymentMethodService paymentMethodService;
    private final CustomerCreditService customerCreditService;
    private final AppsService appsService;
    private final EfrisSettingService efrisSettingService;
    private final EfrisSubmissionService efrisSubmissionService;
    private final EfrisChadSettingService efrisChadSettingService;
    private final EfrisChadSubmissionService efrisChadSubmissionService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final AccountingService accountingService;
    private final MailService mailService;
    private final DocumentPdfService documentPdfService;
    /** Fourier: "the terminal will be registered on warehouse ... stock will be managed on Location" — see {@link #resolveSellingWarehouse}/{@link #stockQuantityFor}. */
    private final WarehouseService warehouseService;
    /** Branch filter on the dashboard's sales figures — see {@link #computeTodayTotals(Long)}. */
    private final BranchService branchService;
    /** Staff limited to certain branches only see their branches' completed sales. */
    private final com.posapp.branch.BranchAccessService branchAccessService;
    /** Stock promised on confirmed sales orders is not free to sell at the till when negative stock is off. */
    private final com.posapp.salesorder.StockReservationService stockReservationService;
    /** Validates serial numbers typed at the till. */
    private final com.posapp.tracking.TrackingService trackingService;

    public OrderService(OrderRepository orderRepository,
                         CashierSessionRepository cashierSessionRepository,
                         CustomerRepository customerRepository,
                         CatalogService catalogService,
                         PaymentMethodService paymentMethodService,
                         CustomerCreditService customerCreditService,
                         AppsService appsService,
                         EfrisSettingService efrisSettingService,
                         EfrisSubmissionService efrisSubmissionService,
                         EfrisChadSettingService efrisChadSettingService,
                         EfrisChadSubmissionService efrisChadSubmissionService,
                         StoreSettingsService storeSettingsService,
                         AuditLogService auditLogService,
                         AccountingService accountingService,
                         MailService mailService,
                         DocumentPdfService documentPdfService,
                         WarehouseService warehouseService,
                         BranchService branchService,
                         com.posapp.branch.BranchAccessService branchAccessService,
                         com.posapp.salesorder.StockReservationService stockReservationService,
                         com.posapp.tracking.TrackingService trackingService) {
        this.trackingService = trackingService;
        this.stockReservationService = stockReservationService;
        this.orderRepository = orderRepository;
        this.cashierSessionRepository = cashierSessionRepository;
        this.customerRepository = customerRepository;
        this.catalogService = catalogService;
        this.paymentMethodService = paymentMethodService;
        this.customerCreditService = customerCreditService;
        this.appsService = appsService;
        this.efrisSettingService = efrisSettingService;
        this.efrisSubmissionService = efrisSubmissionService;
        this.efrisChadSettingService = efrisChadSettingService;
        this.efrisChadSubmissionService = efrisChadSubmissionService;
        this.mailService = mailService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.accountingService = accountingService;
        this.warehouseService = warehouseService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
    }

    /**
     * Fourier: "the terminal will be registered on warehouse" — the warehouse this sale/return
     * should move stock against, resolved from the register the order's session belongs to. Null
     * (no session, no register, or a register with no warehouse assigned) all mean "use the
     * primary warehouse" — same fallback {@link com.posapp.catalog.CatalogService#adjustStock(Product,
     * int, WarehouseLocation)} already applies to a null location, so every pre-existing register
     * (none of which have ever had a warehouse to assign until this field existed) keeps behaving
     * exactly as before. Used only by {@link #assertSellingWarehouseAllowed} now — every actual
     * stock operation goes through {@link #resolveSellingLocation} instead, see that method's own
     * javadoc.
     */
    private Warehouse resolveSellingWarehouse(SaleOrder order) {
        Register register = order.getSession() != null ? order.getSession().getRegister() : null;
        return register != null ? register.getWarehouse() : null;
    }

    /**
     * Fourier: "if multi-location then sale start from location" — the specific location this
     * sale/return should move stock against, resolved from the register's own {@code
     * defaultLocation} override, falling back to its warehouse's SALES location (Fourier:
     * "location type of RECEIVING,SALES,STORAGE,DAMAGED" — only a SALES location can ever back a
     * sale; see {@code WarehouseService#resolveFallbackSalesLocation}, which throws a clear,
     * actionable error if that warehouse's SALES locations are ambiguous rather than guessing), and
     * finally to null (meaning "the primary warehouse's canonical SALES location", i.e. read/write
     * {@code Product.stockQuantity} directly) exactly like {@link #resolveSellingWarehouse} already
     * did one level up — so every pre-existing register (none of which has ever had a location to
     * assign until this field existed) keeps behaving exactly as before. Shared by {@link
     * #validateStockAvailability} and {@link #completeCheckout} via {@link #stockQuantityFor(Product,
     * WarehouseLocation)} so both agree on exactly which location this sale is against.
     */
    private WarehouseLocation resolveSellingLocation(SaleOrder order) {
        Register register = order.getSession() != null ? order.getSession().getRegister() : null;
        if (register == null) {
            return null;
        }
        WarehouseLocation location = register.getDefaultLocation();
        if (location == null) {
            Warehouse warehouse = register.getWarehouse();
            if (warehouse == null) {
                return null;
            }
            location = warehouseService.resolveFallbackSalesLocation(warehouse);
        }
        Warehouse locationWarehouse = location.getWarehouse();
        boolean isPrimaryCanonicalSalesLocation = locationWarehouse != null && locationWarehouse.isPrimary()
                && location.getId().equals(warehouseService.resolveCanonicalSalesLocation(locationWarehouse).getId());
        return isPrimaryCanonicalSalesLocation ? null : location;
    }

    /**
     * How much of {@code product} is actually available against {@code location} — null (the
     * primary warehouse's default location) reads {@code Product.stockQuantity} itself (the
     * authoritative figure there, see WarehouseService's own javadoc), any other location reads
     * {@code WarehouseService#getStockQuantity} for that location specifically. Shared by {@link
     * #validateStockAvailability} (the pre-checkout check) and {@link #completeCheckout} (the
     * stockBefore/stockAfter snapshot for Stock Movement History) so both agree on what "available"
     * means for whichever location this sale is actually against.
     */
    private int stockQuantityFor(Product product, WarehouseLocation location) {
        if (location == null) {
            return product.getStockQuantity();
        }
        return warehouseService.getStockQuantity(product, location);
    }

    /**
     * Fourier: "if yes [multi-warehouse enabled]: ... No sale can be made at default
     * location/Main warehouse. To make sale there is a need of creating another warehouse and
     * locations and Terminal will be linked to those warehouse" — enforced only while {@code
     * StoreSettings.multiWarehouseEnabled} is on; while it's off (the default), every register
     * keeps selling from the primary warehouse exactly as it always has. Deliberately called only
     * from the online {@link #checkout} path, never {@link #syncOfflineSale} — same "already
     * happened for real, never strand it" reasoning {@link #validateStockAvailability} already
     * follows for that path; an offline sale that was rung before a register got reassigned (or
     * before this setting was turned on) must still sync.
     */
    private void assertSellingWarehouseAllowed(SaleOrder order) {
        if (!storeSettingsService.getSettingsEntity().isMultiWarehouseEnabled()) {
            return;
        }
        Warehouse warehouse = resolveSellingWarehouse(order);
        if (warehouse == null || warehouse.isPrimary()) {
            throw ApiException.badRequest("Multi-warehouse is enabled — this terminal isn't linked to a warehouse yet (or is linked to the Main warehouse), and no sale can be made there. Assign it a different warehouse from Admin > Registers first.");
        }
    }

    /**
     * The terminal's own tab bar — scoped to one cashier's working session (not the whole shift),
     * so two cashiers sharing a till via separate PIN sessions each only see their own open carts.
     * {@code caller} must own {@code cashierSessionId} (or be an ADMIN) — this is what actually
     * stops one cashier's transactions from being visible/postable under another's session; the
     * frontend only ever asks for its own (via CashierSessionApiService.mine/join), but nothing
     * short of this check stops a *different* authenticated user from asking for someone else's.
     */
    @Transactional(readOnly = true)
    public List<OrderDto> listOpenOrders(Long cashierSessionId, AppUser caller) {
        CashierSession cashierSession = getCashierSessionOrThrow(cashierSessionId);
        requireOwnCashierSessionOrAdmin(cashierSession, caller);
        return orderRepository.findByCashierSessionIdAndStatusOrderByCreatedAtAsc(cashierSessionId, OrderStatus.OPEN)
                .stream().map(OrderDto::from).toList();
    }

    /**
     * While an order is still OPEN (someone's working cart), only its own cashier session's
     * cashier (or an ADMIN) may view it — see {@link #requireOwnCashierSessionOrAdmin}. Once it's
     * COMPLETED it's a receipt, and receipts are intentionally open to any logged-in user (see
     * the Receipts feature), so no ownership check applies past that point.
     */
    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId, AppUser caller) {
        SaleOrder order = getOrderOrThrow(orderId);
        if (order.getStatus() == OrderStatus.OPEN) {
            requireOwnCashierSessionOrAdmin(order.getCashierSession(), caller);
        }
        return OrderDto.from(order);
    }

    /**
     * Fourier: "sending other documents like ... receipts ... to the customer's email" — emails
     * a PDF of this completed sale's receipt. Open to any logged-in user, same reasoning as
     * {@link #getOrder}'s own "past COMPLETED, receipts are open to everyone" note — no ownership
     * check applies here either. Refused for a cart that's still OPEN (nothing's final yet) or
     * VOIDED (nothing to hand the customer). {@code overrideEmail} lets the caller send to an
     * address other than the customer's own saved one — needed for a walk-in sale, which has no
     * customer record at all to have an email on.
     */
    @Transactional
    public void emailReceipt(Long orderId, String overrideEmail) {
        SaleOrder order = getOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Only a completed sale has a receipt to email — this one is " + order.getStatus());
        }
        Customer customer = order.getCustomer();
        String email = resolveEmail(overrideEmail, customer != null ? customer.getEmail() : null);
        OrderDto dto = OrderDto.from(order);
        byte[] pdf = documentPdfService.receiptPdf(dto, storeSettingsService.getSettingsEntity(),
                order.getBranchId() != null ? branchService.receiptInfo(order.getBranchId()) : null);
        String html = "<p>Hello " + escapeHtml(dto.customerName()) + ",</p>"
                + "<p>Please find attached your receipt" + (dto.invoiceNumber() != null ? " " + dto.invoiceNumber() : "") + ".</p>";
        String attachmentName = (dto.invoiceNumber() != null ? dto.invoiceNumber() : "receipt-" + order.getId()) + ".pdf";
        mailService.send(email, "Your receipt" + (dto.invoiceNumber() != null ? " " + dto.invoiceNumber() : ""),
                html, List.of(new EmailAttachment(attachmentName, "application/pdf", pdf)));
        auditLogService.record("RECEIPT_EMAILED", "Emailed receipt for order " + order.getId() + " to " + email, "SaleOrder", order.getId());
    }

    /** Same "override wins, otherwise fall back to what's on file, otherwise say so clearly" shape QuotationService/InvoiceService's own emailing methods use. */
    private String resolveEmail(String overrideEmail, String customerEmail) {
        if (overrideEmail != null && !overrideEmail.isBlank()) return overrideEmail.trim();
        if (customerEmail != null && !customerEmail.isBlank()) return customerEmail;
        throw ApiException.badRequest("No email address on file for this sale — enter one to send");
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * A cashier's own order history on one cashier session — every status (open, completed,
     * voided), not just currently-open tabs (see {@link #listOpenOrders}). Backs the "Orders" view
     * reached from the "Sessions" screen (Change: "he need to see orders on his session"). Same
     * ownership-or-admin check as everything else scoped to a cashier session.
     */
    @Transactional(readOnly = true)
    public List<OrderDto> listAllForCashierSession(Long cashierSessionId, AppUser caller) {
        CashierSession cashierSession = getCashierSessionOrThrow(cashierSessionId);
        requireOwnCashierSessionOrAdmin(cashierSession, caller);
        return orderRepository.findByCashierSessionIdOrderByCreatedAtDesc(cashierSessionId)
                .stream().map(OrderDto::from).toList();
    }

    /**
     * Used by the "Receipts" screen — recent completed sales with their lines/payments, for
     * lookup and reprint. `sessionId` scopes the list to one till session (used when the screen
     * is opened from inside the terminal); pass null for the app-wide standalone Receipts page.
     */
    @Transactional(readOnly = true)
    public List<OrderDto> listRecentCompletedOrders(Long sessionId) {
        List<SaleOrder> orders = sessionId != null
                ? orderRepository.findTop200BySessionIdAndStatusOrderByCompletedAtDesc(sessionId, OrderStatus.COMPLETED)
                : orderRepository.findTop200ByStatusOrderByCompletedAtDesc(OrderStatus.COMPLETED);
        return orders.stream()
                .filter(o -> branchAccessService.canAccess(o.getBranchId()))
                .map(OrderDto::from).toList();
    }

    /**
     * Starts a new tab under a specific cashier's own working session — {@code cashierSessionId}
     * comes from {@code CashierSessionService.getOrStartMine}/{@code joinShift}, never a raw
     * RegisterSession id, so every tab is tied to whichever cashier is actually working it.
     * {@code caller} must own that cashier session (or be an ADMIN) — see
     * {@link #requireOwnCashierSessionOrAdmin}.
     */
    @Transactional
    public OrderDto startNewOrder(Long cashierSessionId, AppUser caller) {
        CashierSession cashierSession = getOpenCashierSessionOrThrow(cashierSessionId);
        requireOwnCashierSessionOrAdmin(cashierSession, caller);
        SaleOrder order = new SaleOrder();
        order.setSession(cashierSession.getRegisterSession());
        order.setCashierSession(cashierSession);
        order.setStatus(OrderStatus.OPEN);
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDto addLine(Long orderId, AddLineRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        Product product = catalogService.getActiveProductOrThrow(request.getProductId());
        mergeOrAddLine(order, product, request.getQuantity());
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDto addLineByBarcode(Long orderId, AddByBarcodeRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        Product product = catalogService.getByBarcodeOrThrow(request.getBarcode());
        mergeOrAddLine(order, product, Math.max(1, request.getQuantity()));
        return OrderDto.from(orderRepository.save(order));
    }

    private void mergeOrAddLine(SaleOrder order, Product product, int quantity) {
        order.getLines().stream()
                .filter(l -> l.getProduct() != null && l.getProduct().getId().equals(product.getId())
                        && BigDecimal.ZERO.compareTo(l.getDiscountPercent()) == 0)
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + quantity),
                        () -> order.getLines().add(buildLine(order, product, quantity, BigDecimal.ZERO))
                );
    }

    /**
     * Builds one new snapshot line for {@code product} — pulled out of {@link #mergeOrAddLine} so
     * {@link #syncOfflineSale} can build lines the same way, but with an explicit discount up
     * front (an offline sale is replayed whole, all at once, rather than added-then-discounted
     * across two separate calls like the normal add-line/update-line flow).
     */
    private OrderLine buildLine(SaleOrder order, Product product, int quantity, BigDecimal discountPercent) {
        OrderLine line = new OrderLine();
        line.setOrder(order);
        line.setProduct(product);
        line.setDescription(product.getName());
        line.setUnitPrice(product.getUnitPrice());
        line.setQuantity(quantity);
        line.setDiscountPercent(discountPercent == null ? BigDecimal.ZERO : discountPercent);
        // Snapshot the product's currently-assigned tax rate onto the line — see
        // OrderLine's javadoc for why this is frozen here rather than looked up live.
        TaxRate taxRate = product.getTaxRate();
        if (taxRate != null) {
            line.setTaxCode(taxRate.getCode());
            line.setTaxRatePercent(taxRate.getRatePercent());
            line.setTaxInclusive(taxRate.isInclusive());
        }
        // Same snapshot treatment for cost — see OrderLine.unitCost's javadoc.
        line.setUnitCost(product.getCostPrice());
        // Same snapshot treatment for the EFRIS commodity code — see OrderLine.unspCode's javadoc.
        line.setUnspCode(product.getUnspCode());
        // Same snapshot treatment for the goods/service flag — see OrderLine.service's javadoc.
        line.setService(product.isService());
        return line;
    }

    @Transactional
    public OrderDto updateLine(Long orderId, Long lineId, UpdateLineRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        OrderLine line = order.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Line not found on this order: " + lineId));
        if (request.getQuantity() != null) {
            if (request.getQuantity() < 1) {
                throw ApiException.badRequest("Quantity must be at least 1");
            }
            line.setQuantity(request.getQuantity());
        }
        if (request.getDiscountPercent() != null) {
            validateDiscount(request.getDiscountPercent(), caller);
            line.setDiscountPercent(request.getDiscountPercent());
        }
        if (request.getSerialNumbers() != null) {
            if (line.getProduct() == null || com.posapp.tracking.TrackingService.modeOf(line.getProduct()) != com.posapp.tracking.TrackingMode.SERIAL) {
                throw ApiException.badRequest("Serial numbers apply only to serial-numbered products");
            }
            List<String> serials = trackingService.cleanSerials(line.getProduct(), request.getSerialNumbers());
            line.setSerialNumbers(serials.isEmpty() ? null : String.join("\n", serials));
        }
        return OrderDto.from(orderRepository.save(order));
    }

    /**
     * Fourier's own two-part request: Task 1 "configure the max discount" (System settings) and
     * Task 2 "Only Admin will be allowed to gives discount." Shared between {@link #updateLine}
     * and {@link #syncOfflineSale}'s own per-line loop (the offline-sale replay path builds lines
     * whole rather than add-then-update, so it needs the identical guard — this replaces what used
     * to be that method's own separate "same 0-100 range rule" copy of the plain range check).
     * <p>
     * {@code null} is always allowed (the caller didn't touch {@code discountPercent} at all — see
     * both call sites' own null guards before this is even called). A negative value is always
     * rejected outright, whoever's asking. Raising it above zero is rejected for anyone but an
     * {@code ADMIN} — dropping a line's discount back down to (or leaving it at) zero is always
     * allowed for any cashier, since that never grants a discount, only removes one, so it isn't
     * gated by role. Even an {@code ADMIN}'s discount is capped at the store's own configured
     * {@link StoreSettings#getMaxDiscountPercent()} — a system-wide ceiling on *how much*, entirely
     * separate from *who's* allowed to apply one at all.
     * <p>
     * Fourier: "Build a full permissions system" — {@code Permission.OVERRIDE_DISCOUNT_CAP} is
     * additive on top of both rules above: a holder (admin or not) can apply a discount at all
     * <em>and</em> isn't subject to {@code maxDiscountPercent} (still clamped to the plain 0-100
     * range by the check just above). A plain {@code ADMIN} who hasn't separately been granted this
     * permission keeps today's behavior exactly — still capped at {@code maxDiscountPercent}, same
     * as every other admin — so this is purely additive, never a reduction of admin's existing
     * rights.
     */
    private void validateDiscount(BigDecimal discountPercent, AppUser caller) {
        if (discountPercent == null) {
            return;
        }
        if (discountPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("Discount must be between 0 and 100");
        }
        boolean canOverrideCap = caller.getPermissions() != null && caller.getPermissions().contains(Permission.OVERRIDE_DISCOUNT_CAP);
        if (discountPercent.compareTo(BigDecimal.ZERO) > 0 && caller.getRole() != Role.ADMIN && !canOverrideCap) {
            throw ApiException.forbidden("Only an admin can apply a discount");
        }
        if (canOverrideCap) {
            return;
        }
        BigDecimal max = storeSettingsService.getSettings().maxDiscountPercent();
        if (max == null) {
            max = BigDecimal.valueOf(100);
        }
        if (discountPercent.compareTo(max) > 0) {
            throw ApiException.badRequest("Discount cannot exceed the configured maximum of " + max + "%");
        }
    }

    @Transactional
    public OrderDto removeLine(Long orderId, Long lineId, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        boolean removed = order.getLines().removeIf(l -> l.getId().equals(lineId));
        if (!removed) {
            throw ApiException.notFound("Line not found on this order: " + lineId);
        }
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDto setCustomer(Long orderId, SetCustomerRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        if (request.getCustomerId() == null) {
            order.setCustomer(null);
        } else {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }
        return OrderDto.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDto park(Long orderId, ParkRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        order.setNote(request.getNote());
        return OrderDto.from(orderRepository.save(order));
    }

    /**
     * Cancels an open cart without completing it — e.g. a customer walks away, or a tab was
     * opened by mistake. Never touches stock (checkout is the only place that adjusts stock, and
     * this order never got there) and never assigns an invoice number. VOIDED orders drop out of
     * every OPEN-status query (tabs, totals), so the tab simply disappears from the terminal.
     */
    /** Task: "Implement an audit log of who did what and when" — voiding a sale is a curated action, see AuditLogService's javadoc. */
    @Transactional
    public OrderDto discardOrder(Long orderId, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        order.setStatus(OrderStatus.VOIDED);
        SaleOrder saved = orderRepository.save(order);
        // getInvoiceNumber() is only ever set at checkout (see completeCheckout) — a voided order
        // was, by definition, never checked out, so it's always null here; the order's own id is
        // what actually identifies it in this log.
        auditLogService.record("SALE_VOIDED", "Voided order #" + saved.getId(), "SaleOrder", saved.getId());
        return OrderDto.from(saved);
    }

    /**
     * Used by CashierSessionService when a cashier session is closed (either the cashier ending
     * their own, or an admin closing all of them): any cart that was never checked out is
     * discarded rather than left OPEN with no cashier session to surface it in a tab bar again
     * (tabs are only ever listed for an *open* cashier session — see getOpenCashierSessionOrThrow).
     * Never touches stock, same as the regular Discard action.
     */
    @Transactional
    public void discardAllOpenForCashierSession(Long cashierSessionId) {
        List<SaleOrder> open = orderRepository.findByCashierSessionIdAndStatusOrderByCreatedAtAsc(cashierSessionId, OrderStatus.OPEN);
        for (SaleOrder order : open) {
            order.setStatus(OrderStatus.VOIDED);
        }
        orderRepository.saveAll(open);
    }

    /**
     * Fourier: "Never allow any sale transaction even into POS without Customer information." The
     * terminal's cart already defaults every order to the seeded "Walk-in Customer" the moment it's
     * opened (see {@code applyDefaultCustomer} client-side, and {@code Customer.walkIn}'s own
     * javadoc for the backfill), so this should never actually trip for a sale rung through the
     * normal terminal UI — it exists to close the gap for anything that reaches this endpoint
     * without going through that flow (a direct API call, or a client bug that never called {@link
     * #setCustomer}). Deliberately checked only here, not in {@link #completeCheckout} itself — see
     * that method's own note on why {@link #syncOfflineSale} is exempt, same reasoning as {@link
     * #validateStockAvailability}'s.
     */
    @Transactional
    public OrderDto checkout(Long orderId, CheckoutRequest request, AppUser caller) {
        SaleOrder order = getOpenOrderOrThrow(orderId, caller);
        if (order.getLines().isEmpty()) {
            throw ApiException.badRequest("Cannot check out an empty cart");
        }
        if (order.getCustomer() == null) {
            throw ApiException.badRequest("A customer is required for every sale — select or add a customer on this cart before checking out.");
        }
        assertSellingWarehouseAllowed(order);
        validateStockAvailability(order);
        completeCheckout(order, request.getPayments(), request.getDueDate());
        SaleOrder saved = orderRepository.save(order);
        // Fourier: "all POS...module will be integrated when accounting is enabled" — posts a
        // balanced journal entry for this sale (revenue/tax/COGS/inventory/cash/AR) once Accounting
        // is turned on in Settings; see AccountingService.postPosSale's own javadoc. Called with the
        // saved entity (has a real id for the journal entry's sourceId) — a no-op, and never throws,
        // when accounting isn't enabled, so this is safe to call unconditionally after every sale.
        accountingService.postPosSale(saved);
        return OrderDto.from(saved);
    }

    /**
     * Task: "Inventory Setting (Example: allow negative stock)." When the store-wide {@code
     * StoreSettings.allowNegativeStock} is off, blocks a checkout that would take any product's
     * stock below zero — grouped by product id first (not checked line-by-line), since the same
     * product can legitimately appear as more than one line on a cart (see {@code mergeOrAddLine}:
     * a discounted line for a product never merges with a non-discounted one for the same product),
     * and it's the *total* being sold that has to fit, not any single line's share of it.
     * <p>
     * Deliberately called only from the online {@link #checkout} path, not from the shared {@link
     * #completeCheckout} both it and {@link #syncOfflineSale} call — an offline sale already
     * happened for real by the time it syncs (the customer walked out with the goods), so rejecting
     * that sync over a stock check that can no longer change what actually occurred would only
     * strand it; see {@link #syncOfflineSale}'s own javadoc for why stock is deliberately never
     * checked there at all, a policy this setting doesn't change.
     */
    private void validateStockAvailability(SaleOrder order) {
        if (storeSettingsService.getSettings().allowNegativeStock()) {
            return;
        }
        // Fourier: "if the item is sold, it will be reduced on the specific location" — checked
        // against whichever location this sale will actually deduct from (see
        // resolveSellingLocation's own javadoc), not always the primary warehouse's figure.
        WarehouseLocation location = resolveSellingLocation(order);
        Map<Long, Integer> requestedByProductId = new LinkedHashMap<>();
        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();
            // Fourier: "Add track stock attribute on Product" — a non-tracked product can always
            // be sold, like a service or made-to-order item; see Product.trackStock's javadoc.
            if (product == null || !product.isTrackStock()) {
                continue;
            }
            requestedByProductId.merge(product.getId(), line.getQuantity(), Integer::sum);
            productsById.putIfAbsent(product.getId(), product);
        }
        for (Map.Entry<Long, Integer> entry : requestedByProductId.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int requested = entry.getValue();
            int reserved = stockReservationService.reserved(product);
            int available = stockQuantityFor(product, location) - reserved;
            if (requested > available) {
                throw ApiException.badRequest("Not enough stock for '" + product.getName() + "' — " + Math.max(available, 0)
                        + " available" + (reserved > 0 ? " (" + reserved + " reserved for sales orders)" : "") + ", " + requested + " requested. Enable \"Allow negative stock\" in Inventory settings to sell anyway.");
            }
        }
    }

    /**
     * The actual "take payment, decrement stock, assign an invoice number, mark COMPLETED" logic —
     * shared between the normal online {@link #checkout} and {@link #syncOfflineSale}, so an
     * offline-queued sale is completed by exactly the same rules (credit-limit enforcement
     * included) as one rung online, not a separate, looser copy of them.
     */
    private void completeCheckout(SaleOrder order, List<CheckoutRequest.PaymentEntry> payments, LocalDate requestedDueDate) {
        // Which method codes represent selling on store credit (Task family: "Cash + credit
        // partial payment" / "100% credit sale") — looked up once per checkout, same pattern as
        // aggregate()'s cashCodes lookup, since this is admin-configurable rather than a fixed enum.
        Set<String> creditCodes = paymentMethodService.listCreditCodes();

        BigDecimal due = OrderCalculator.total(order);
        BigDecimal tendered = BigDecimal.ZERO;
        BigDecimal creditPortion = BigDecimal.ZERO;
        for (CheckoutRequest.PaymentEntry entry : payments) {
            String method = paymentMethodService.validateActiveCodeOrThrow(entry.getMethod());
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setMethod(method);
            payment.setAmount(entry.getAmount());
            order.getPayments().add(payment);
            tendered = tendered.add(entry.getAmount());
            if (creditCodes.contains(method)) {
                creditPortion = creditPortion.add(entry.getAmount());
            }
        }
        if (tendered.compareTo(due) < 0) {
            throw ApiException.badRequest("Payments (" + tendered + ") do not cover the amount due (" + due + ")");
        }

        // Task: "Credit limit enforcement" — any credit-flagged tender requires a customer on the
        // order and enough headroom under their limit; a no-op when creditPortion is zero.
        if (creditPortion.compareTo(BigDecimal.ZERO) > 0) {
            Customer customer = order.getCustomer();
            if (customer == null) {
                throw ApiException.badRequest("A customer must be selected to sell on credit");
            }
            customerCreditService.assertWithinLimit(customer, creditPortion);
            order.setCreditAmount(creditPortion);
            // Task: "Optional due date/payment terms" — explicit request value wins, else fall
            // back to the customer's own default term, else no due date at all.
            LocalDate dueDate = requestedDueDate;
            if (dueDate == null && customer.getDefaultCreditTermDays() != null) {
                dueDate = LocalDate.now().plusDays(customer.getDefaultCreditTermDays());
            }
            order.setDueDate(dueDate);
        }

        // Fourier: "the terminal will be registered on warehouse ... if the item is sold, it will
        // be reduced on the specific location" — resolved once for the whole sale, same location
        // validateStockAvailability just checked against.
        WarehouseLocation sellingLocation = resolveSellingLocation(order);
        // Warehouse filter on Stock Movement History: null = Main warehouse (see SaleOrder.stockWarehouseId).
        order.setStockWarehouseId(sellingLocation != null ? sellingLocation.getWarehouse().getId() : null);
        order.setBranchId(warehouseService.branchIdOf(sellingLocation != null ? sellingLocation.getWarehouse() : null));
        String invoiceNumber = branchService.prefixedNumber(order.getBranchId(), "POS-" + System.currentTimeMillis());
        for (OrderLine line : order.getLines()) {
            // Fourier: "Add track stock attribute on Product" — a non-tracked product's stock is
            // never deducted at all (see Product.trackStock's javadoc), so its line simply gets no
            // stockBefore/stockAfter snapshot, the same "no snapshot means no row" treatment
            // StockMovementRow already gives a line whose product was null.
            if (line.getProduct() != null && line.getProduct().isTrackStock()) {
                // Task 6: "Any transaction affecting stock should be shown to Stock Movement
                // History (Pos sales...)" — snapshot before/after the same way StockReceipt does,
                // so ReportService.stockMovementHistory has a structured POS-sale row to show.
                int stockBefore = stockQuantityFor(line.getProduct(), sellingLocation);
                adjustForSale(line, sellingLocation, invoiceNumber);
                line.setStockBefore(stockBefore);
                line.setStockAfter(stockQuantityFor(line.getProduct(), sellingLocation));
            }
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setInvoiceNumber(invoiceNumber);
        order.setCompletedAt(java.time.Instant.now());
    }

    /**
     * Takes a sold line out of stock. A serial-numbered line may carry the serial numbers typed at the
     * till (they must match the quantity and be in stock at the selling location); without them the
     * oldest in stock go. Batch products always sell earliest-expiry-first.
     */
    private void adjustForSale(OrderLine line, WarehouseLocation sellingLocation, String invoiceNumber) {
        Product product = line.getProduct();
        java.util.Map<Long, List<String>> chosen = null;
        if (com.posapp.tracking.TrackingService.modeOf(product) == com.posapp.tracking.TrackingMode.SERIAL
                && line.getSerialNumbers() != null && !line.getSerialNumbers().isBlank()) {
            chosen = java.util.Map.of(product.getId(), List.of(line.getSerialNumbers().split("\\R")));
        }
        com.posapp.tracking.TrackingContext.begin(invoiceNumber, chosen);
        try {
            catalogService.adjustStock(product, -line.getQuantity(), sellingLocation);
        } finally {
            com.posapp.tracking.TrackingContext.clear();
        }
    }

    /**
     * Replays one whole sale that was rung offline (Task: "local queuing of sales that sync once
     * connectivity returns") — the terminal built this entirely from its own cached catalog and a
     * simplified local price/tax estimate, since none of the normal create-order/add-line/checkout
     * round trips are reachable without a connection. This one call does the equivalent of all of
     * those at once, using {@code request.getQueuedAt()} to remember when it actually happened.
     *
     * <p>Idempotent by {@code clientTempId}: the terminal may retry this exact request (lost
     * connectivity again mid-request, or never saw the first response) without double-selling.
     *
     * <p>Deliberately does NOT require the cashier session to still be open — a sale that was rung
     * hours ago while genuinely open shouldn't get stranded in the offline queue forever just
     * because the till was closed for the night in the meantime; only ownership is checked.
     *
     * <p>Stock is never checked at checkout at all, online or offline (see {@link #completeCheckout}
     * — {@code adjustStock} always just applies the delta), so there's nothing to reconcile there.
     * The one thing that actually can drift between "rung offline" and "synced" is price/tax, since
     * the terminal was pricing lines from a cached snapshot that may be stale by sync time — see
     * the {@code syncNote} comparison below, which is what "sync it anyway, flag for review" (the
     * chosen policy) actually flags.
     */
    @Transactional
    public OrderDto syncOfflineSale(OfflineSaleRequest request, AppUser caller) {
        SaleOrder existing = orderRepository.findByClientTempId(request.getClientTempId()).orElse(null);
        if (existing != null) {
            return OrderDto.from(existing);
        }

        CashierSession cashierSession = getCashierSessionOrThrow(request.getCashierSessionId());
        requireOwnCashierSessionOrAdmin(cashierSession, caller);

        SaleOrder order = new SaleOrder();
        order.setSession(cashierSession.getRegisterSession());
        order.setCashierSession(cashierSession);
        order.setStatus(OrderStatus.OPEN);
        order.setClientTempId(request.getClientTempId());
        order.setOfflineQueuedAt(request.getQueuedAt());

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }

        for (OfflineSaleRequest.LineEntry entry : request.getLines()) {
            // Same validateDiscount rule as updateLine — a discount is only ever set client-side
            // here (there's no separate offline "update line" step), so it needs the identical
            // range/role/max-percent guard. See validateDiscount's own javadoc.
            BigDecimal discountPercent = entry.getDiscountPercent();
            validateDiscount(discountPercent, caller);
            Product product = catalogService.getProductForOfflineSync(entry.getProductId());
            order.getLines().add(buildLine(order, product, entry.getQuantity(), discountPercent));
        }
        if (order.getLines().isEmpty()) {
            throw ApiException.badRequest("Cannot sync an offline sale with no lines");
        }

        completeCheckout(order, request.getPayments(), request.getDueDate());

        if (request.getClientEstimatedTotal() != null) {
            BigDecimal finalTotal = OrderCalculator.total(order);
            BigDecimal diff = finalTotal.subtract(request.getClientEstimatedTotal()).abs();
            if (diff.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                order.setSyncNote(
                        "Synced from offline queue (rung " + request.getQueuedAt() + "). The terminal's offline "
                                + "estimate was " + request.getClientEstimatedTotal() + "; the final total is "
                                + finalTotal + " — a price or tax rate likely changed since this device's catalog "
                                + "was last synced. Review this sale's lines/tax against current product settings.");
            }
        }

        SaleOrder saved = orderRepository.save(order);
        // See checkout()'s own comment on postPosSale — called here too, with the now-saved entity,
        // so an offline-synced sale posts to the ledger exactly the same as one rung online.
        accountingService.postPosSale(saved);
        return OrderDto.from(saved);
    }

    /**
     * Fourier's own request: "Send to order data to EFRIS as json" — attempted once, right after a
     * sale completes (called separately from {@link #checkout}/{@link #syncOfflineSale}, its own
     * transaction, so an outbound HTTP call to EFRIS never holds the checkout transaction open).
     * Deliberately never throws: EFRIS being uninstalled, disabled, unconfigured for this terminal,
     * unreachable, or outright rejecting the invoice are all just reasons this returns the order
     * with nothing (or {@link SaleOrder#getEfrisError()}) changed — "sale still completes, flagged
     * for review" (Fourier's own answer when asked how a submission failure should be handled)
     * means checkout itself must never depend on this succeeding.
     *
     * <p>Task: "if EFRIS Chad is set then call configured with the shared sample request" — this now
     * tries EFRIS Rwanda first, then EFRIS Chad, each only when that app is enabled <em>and</em> this
     * order's own terminal has a connection configured for it. "One Terminal can have only one EFRIS
     * mapped with" (enforced by {@code EfrisSettingService}/{@code EfrisChadSettingService} at save
     * time) guarantees at most one of the two ever actually has a connection to try, so this is a
     * dispatch, not a real race — whichever integration this terminal is mapped to is the one that
     * runs. {@code SaleOrder}'s fiscal columns ({@code sdcId} etc.) stay Rwanda-specific; EFRIS Chad
     * only ever sets the generic {@code efrisSubmittedAt}/{@code efrisResponse}/{@code efrisError}
     * fields, since its response isn't parsed into named fields yet (see
     * {@code EfrisChadSubmissionResult}'s javadoc).</p>
     */
    @Transactional
    public OrderDto submitToEfrisIfApplicable(Long orderId) {
        SaleOrder order = getOrderOrThrow(orderId);
        try {
            if (order.getStatus() != OrderStatus.COMPLETED) {
                return OrderDto.from(order);
            }
            if (order.getEfrisSubmittedAt() != null && order.getEfrisError() == null) {
                // Already successfully submitted, to whichever EFRIS integration this terminal
                // uses — a retried offline-sync request (see syncOfflineSale's clientTempId
                // idempotency) must never resubmit the same sale twice. (Covers Rwanda's own
                // sdcId-set case too: a successful Rwanda submission always clears efrisError.)
                return OrderDto.from(order);
            }
            Register register = order.getSession() != null ? order.getSession().getRegister() : null;
            if (register == null) {
                return OrderDto.from(order);
            }

            if (appsService.isEnabled(AppKey.EFRIS_RW)) {
                Optional<EfrisConnection> connection = efrisSettingService.getConnectionForRegister(register.getId());
                if (connection.isPresent()) {
                    return submitToEfrisRwanda(order, connection.get());
                }
            }
            if (appsService.isEnabled(AppKey.EFRIS_TD)) {
                Optional<EfrisChadConnection> connection = efrisChadSettingService.getConnectionForRegister(register.getId());
                if (connection.isPresent()) {
                    return submitToEfrisChad(order, connection.get());
                }
            }
            // Neither EFRIS integration is both enabled and configured for this terminal — not an
            // error, just nothing to do.
            return OrderDto.from(order);
        } catch (Exception e) {
            // Belt-and-suspenders: buildEfrisPayload/submit are already defensive, but nothing here
            // may ever propagate and roll back a sale that already completed.
            log.warn("Unexpected error submitting order {} to EFRIS", orderId, e);
            order.setEfrisSubmittedAt(Instant.now());
            order.setEfrisError("Unexpected error submitting to EFRIS: " + e.getMessage());
            return OrderDto.from(orderRepository.save(order));
        }
    }

    private OrderDto submitToEfrisRwanda(SaleOrder order, EfrisConnection connection) {
        EfrisSubmissionResult result = efrisSubmissionService.submit(connection, buildEfrisPayload(order));
        order.setEfrisSubmittedAt(Instant.now());
        order.setEfrisResponse(result.rawResponse());
        if (result.success()) {
            order.setSdcId(result.sdcId());
            order.setSdcDate(result.sdcDate());
            order.setSdcReceiptNumber(result.sdcReceiptNumber());
            order.setInternalData(result.internalData());
            order.setSignatureData(result.signatureData());
            order.setCisNumber(result.cisNumber());
            order.setCisDate(result.cisDate());
            order.setMrcData(result.mrcData());
            order.setEfrisError(null);
        } else {
            order.setEfrisError(result.errorMessage());
        }
        return OrderDto.from(orderRepository.save(order));
    }

    /**
     * Task: "if EFRIS Chad is set then call configured with the shared sample request" — EFRIS
     * Chad's own side of {@link #submitToEfrisIfApplicable}'s dispatch. On success, reuses the
     * same generic SDC/CIS fiscal columns Rwanda's own submission populates ({@code sdcId}, {@code
     * mrcData}, {@code sdcReceiptNumber}, {@code sdcDate}, {@code signatureData}, {@code
     * internalData}, {@code cisNumber}, {@code cisDate}) — per Fourier's exact {@code sdc_data}
     * split spec (see {@code EfrisChadSubmissionResult}'s javadoc) plus his two follow-up
     * corrections (Task 1: {@code cisDate} is the {@code cis_date} string this same request sent,
     * not anything parsed from the response; Task 2: {@code cisNumber} comes from the response's
     * {@code response_data.invoice_number}) — since these are the same conceptual SDC/CIS fiscal
     * fields, not Rwanda-specific ones. Because the receipt template's "SDC INFORMATION"/CIS block
     * is already gated generically on these same columns (not on which integration produced them),
     * a successful Chad submission renders that block too, with no frontend changes needed.
     */
    private OrderDto submitToEfrisChad(SaleOrder order, EfrisChadConnection connection) {
        // Branch identity: a branch with its own EFRIS branch code submits under it instead of the terminal's.
        EfrisChadConnection branchConnection = new EfrisChadConnection(connection.url(), connection.basicAuthUsername(), connection.basicPassword(),
                connection.sdcNumber(), branchService.effectiveEfrisBranchCode(order.getBranchId(), connection.branchCode()),
                connection.mrc(), connection.integrationKey());
        EfrisChadSubmissionResult result = efrisChadSubmissionService.submit(branchConnection, buildEfrisChadPayload(order));
        order.setEfrisSubmittedAt(Instant.now());
        order.setEfrisResponse(result.rawResponse());
        if (result.success()) {
            order.setSdcId(result.sdcId());
            order.setMrcData(result.mrc());
            order.setSdcReceiptNumber(result.sdcReceiptNumber());
            order.setSdcDate(result.sdcDate());
            order.setSignatureData(result.signatureData());
            order.setInternalData(result.internalData());
            order.setCisNumber(result.cisNumber());
            order.setCisDate(result.cisDate());
            order.setEfrisError(null);
        } else {
            order.setEfrisError(result.errorMessage());
        }
        return OrderDto.from(orderRepository.save(order));
    }

    /**
     * Fourier's own default: "Default tax code make it B" — EFRIS's {@code tax_label} field is
     * required on every sale line, but {@code OrderLine.taxCode} is null for a tax-exempt line (or
     * one predating this app's tax-rate feature), so this is what stands in for it rather than
     * sending EFRIS a null. {@code "B"} is RRA's own standard-rate category letter — the right
     * default for a store whose sales are, by default, standard-rated.
     */
    private static final String DEFAULT_EFRIS_TAX_CODE = "B";

    /**
     * Reduces a completed sale to exactly what EFRIS's {@code api.invoice/create_api} needs — see
     * {@code EfrisInvoicePayload}'s javadoc for why this stays a plain record built here rather
     * than {@code apps.efris} reaching back into {@code order}'s own entities.
     */
    private EfrisInvoicePayload buildEfrisPayload(SaleOrder order) {
        Customer customer = order.getCustomer();
        List<EfrisInvoiceLine> lines = order.getLines().stream()
                .map(line -> new EfrisInvoiceLine(
                        line.getDescription(),
                        resolveProductCode(line.getProduct()),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getTaxRatePercent(),
                        line.getTaxCode() != null && !line.getTaxCode().isBlank() ? line.getTaxCode() : DEFAULT_EFRIS_TAX_CODE,
                        line.getUnspCode(),
                        line.getDiscountPercent()
                ))
                .toList();
        return new EfrisInvoicePayload(
                "S",
                branchService.effectiveTaxId(order.getBranchId(), storeSettingsService.getSettings().taxId()),
                customer != null ? customer.getTaxId() : null,
                customer != null ? customer.getName() : null,
                customer != null ? customer.getPhone() : null,
                null,
                order.getInvoiceNumber(),
                "0",
                resolvePaymentMode(order),
                lines
        );
    }

    /**
     * Task: "if EFRIS Chad is set then call configured with the shared sample request" — Chad's
     * own equivalent of {@link #buildEfrisPayload}, mirroring its shape with Chad's own distinct
     * field set. Sale-only, deliberately — see {@code EfrisChadInvoicePayload}'s javadoc for why
     * returns aren't submitted to EFRIS Chad yet.
     */
    private EfrisChadInvoicePayload buildEfrisChadPayload(SaleOrder order) {
        Customer customer = order.getCustomer();
        List<OrderLine> orderLines = order.getLines();
        List<EfrisChadInvoiceLine> lines = new java.util.ArrayList<>(orderLines.size());
        for (int i = 0; i < orderLines.size(); i++) {
            OrderLine line = orderLines.get(i);
            lines.add(new EfrisChadInvoiceLine(
                    resolveProductCode(line.getProduct()),
                    line.getDescription(),
                    line.isService(),
                    line.getUnitPrice(),
                    line.getQuantity(),
                    line.getUnspCode(),
                    line.getTaxRatePercent(),
                    line.getTaxCode(),
                    line.getDiscountPercent(),
                    i + 1
            ));
        }
        var settings = storeSettingsService.getSettings();
        return new EfrisChadInvoicePayload(
                settings.businessName(),
                branchService.effectiveTaxId(order.getBranchId(), settings.taxId()),
                customer != null ? customer.getName() : null,
                customer != null ? customer.getTaxId() : null,
                customer != null ? customer.getPhone() : null,
                order.getInvoiceNumber(),
                resolveChadPaymentMode(order),
                order.getCompletedAt(),
                lines
        );
    }

    /**
     * Task: "if EFRIS Chad is set then call configured with the shared sample request" — Fourier's
     * own choice when asked: each distinct {@code Payment.method} code, upper-cased and prefixed
     * {@code "PAYMENT_METHOD_"} (matching his sample's {@code "PAYMENT_METHOD_CASH"} exactly for a
     * cash sale), joined the same comma-separated way {@link #resolvePaymentMode} already does for
     * Rwanda. Null for a sale with no payments at all.
     */
    private String resolveChadPaymentMode(SaleOrder order) {
        String modes = order.getPayments().stream()
                .map(Payment::getMethod)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(code -> "PAYMENT_METHOD_" + code.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(","));
        return modes.isBlank() ? null : modes;
    }

    /**
     * This sale's payment method code(s), for the EFRIS payload's {@code payment_mode} field —
     * see {@code EfrisInvoicePayload.paymentMode}'s javadoc. Distinct {@code Payment.method}
     * codes, in the order they were first tendered at checkout, joined with a comma for a
     * split-tender sale. Null for a sale with no payments at all (shouldn't happen for a
     * completed sale, but handled the same way every other "nothing to report" case here is).
     * Public (not private, like everything else this class builds an EFRIS payload from) because
     * {@code ReturnsService.buildEfrisPayload} reuses it too — a return's own EFRIS submission
     * reports the *original* sale's payment mode, since nothing new was tendered at return time.
     */
    public String resolvePaymentMode(SaleOrder order) {
        String modes = order.getPayments().stream()
                .map(Payment::getMethod)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return modes.isBlank() ? null : modes;
    }

    /**
     * Prefers the product's own admin-assigned code over its SKU — same fallback a
     * receipt/barcode lookup would use. Public because {@code ReturnsService.buildEfrisPayload}
     * reuses it too, for exactly the same reason {@link #resolvePaymentMode} is.
     */
    public String resolveProductCode(Product product) {
        if (product == null) {
            return null;
        }
        if (product.getCode() != null && !product.getCode().isBlank()) {
            return product.getCode();
        }
        return product.getSku();
    }

    @Transactional(readOnly = true)
    public OrderDto findByInvoiceNumber(String invoiceNumber) {
        return OrderDto.from(getCompletedOrderByInvoiceNumber(invoiceNumber));
    }

    /** Used by ReturnsService: needs the entity (lines + product refs), not just the DTO. */
    @Transactional(readOnly = true)
    public SaleOrder getCompletedOrderByInvoiceNumber(String invoiceNumber) {
        SaleOrder order = orderRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> ApiException.notFound("No sale found matching invoice '" + invoiceNumber + "'"));
        return requireCompleted(order);
    }

    /** Used by ReturnsService once it already has the orderId (e.g. from the process-return request body). */
    @Transactional(readOnly = true)
    public SaleOrder getCompletedOrderById(Long orderId) {
        return requireCompleted(getOrderOrThrow(orderId));
    }

    private SaleOrder requireCompleted(SaleOrder order) {
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Order was never completed: " + order.getId());
        }
        return order;
    }

    /** Used by RegisterService to build the till-close / Z-report totals. */
    @Transactional(readOnly = true)
    public SessionTotals computeSessionTotals(Long sessionId) {
        return aggregate(orderRepository.findBySessionIdAndStatusOrderByCreatedAtAsc(sessionId, OrderStatus.COMPLETED));
    }

    /**
     * Same aggregation as {@link #computeSessionTotals}, scoped to one cashier's own working
     * session instead of the whole shift — Task: "The report for register_session (Shift) should
     * show all cashier_session (session report)." Used by RegisterService to break the Z-report
     * down per cashier when more than one person worked the shift (the "Switch cashier" case).
     */
    @Transactional(readOnly = true)
    public SessionTotals computeCashierSessionTotals(Long cashierSessionId) {
        return aggregate(orderRepository.findByCashierSessionIdAndStatusOrderByCreatedAtAsc(cashierSessionId, OrderStatus.COMPLETED));
    }

    /**
     * Used by DashboardService for the admin overview. "Today" is UTC-midnight-to-now — a
     * deliberate simplification (no per-store timezone setting yet), noted in the README.
     */
    @Transactional(readOnly = true)
    public SessionTotals computeTodayTotals() {
        return computeTodayTotals(null);
    }

    /** Same, narrowed to one branch's sales (null = every branch). A sale with no branch (older than branches) is head office's. */
    @Transactional(readOnly = true)
    public SessionTotals computeTodayTotals(Long branchId) {
        java.time.Instant startOfDay = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        List<SaleOrder> completed = orderRepository.findByStatusAndCompletedAtGreaterThanEqual(OrderStatus.COMPLETED, startOfDay);
        if (branchId != null) {
            Long headquartersId = branchService.headquartersId();
            completed = completed.stream()
                    .filter(o -> (o.getBranchId() != null ? o.getBranchId() : headquartersId).equals(branchId))
                    .toList();
        }
        return aggregate(completed);
    }

    private SessionTotals aggregate(List<SaleOrder> completed) {
        // Looked up once per call (not per payment) — which method codes currently count as
        // cash-in-drawer is admin-configurable now (see PaymentMethodEntity.cash), not a fixed enum.
        Set<String> cashCodes = paymentMethodService.listCashCodes();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cashIn = BigDecimal.ZERO;
        BigDecimal nonCashIn = BigDecimal.ZERO;
        for (SaleOrder order : completed) {
            gross = gross.add(OrderCalculator.total(order));
            tax = tax.add(OrderCalculator.taxTotal(order));
            for (Payment payment : order.getPayments()) {
                if (cashCodes.contains(payment.getMethod())) {
                    cashIn = cashIn.add(payment.getAmount());
                } else {
                    nonCashIn = nonCashIn.add(payment.getAmount());
                }
            }
        }
        return new SessionTotals(completed.size(), gross, tax, cashIn, nonCashIn);
    }

    // ---- internal helpers ----

    SaleOrder getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + orderId));
    }

    /** Every mutation of an open cart also enforces that {@code caller} owns it — see {@link #requireOwnCashierSessionOrAdmin}. */
    private SaleOrder getOpenOrderOrThrow(Long orderId, AppUser caller) {
        SaleOrder order = getOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.OPEN) {
            throw ApiException.badRequest("This order is no longer open: " + orderId);
        }
        requireOwnCashierSessionOrAdmin(order.getCashierSession(), caller);
        return order;
    }

    /** Validates a cashier-session id for starting/listing tabs — checked at both levels (the cashier's own session, and the shift it belongs to). */
    private CashierSession getOpenCashierSessionOrThrow(Long cashierSessionId) {
        CashierSession cashierSession = getCashierSessionOrThrow(cashierSessionId);
        if (cashierSession.getStatus() != CashierSessionStatus.OPEN) {
            throw ApiException.badRequest("This cashier session has ended: " + cashierSessionId);
        }
        if (cashierSession.getRegisterSession().getStatus() != SessionStatus.OPEN) {
            throw ApiException.badRequest("This register session is closed: " + cashierSession.getRegisterSession().getId());
        }
        return cashierSession;
    }

    private CashierSession getCashierSessionOrThrow(Long cashierSessionId) {
        return cashierSessionRepository.findById(cashierSessionId)
                .orElseThrow(() -> ApiException.notFound("Cashier session not found: " + cashierSessionId));
    }

    /**
     * The actual enforcement behind "a user's transactions are mapped to one session belonging
     * to that user, and nobody else's": every read/write scoped to a cashier session or an order
     * checks this before doing anything, so one cashier can never list, start, or mutate a tab
     * under a *different* cashier's session just by knowing or guessing its id — only that
     * session's own cashier, an ADMIN, or a teammate already working the very same shift (see the
     * "Switch cashier" carve-out below) may. {@code cashierSession} may be {@code null} (a
     * pre-existing order from before cashier sessions existed); in that case only an ADMIN may
     * act on it, since ownership can't be determined. Mirrors
     * {@code CashierSessionService.requireOwnSessionOrAdmin}'s owner-or-admin check.
     *
     * <p>Bug (Fourier: "When Switch cashier, the user is not able to sale in terminal (reason no
     * selling cart available for him)"): "Switch cashier" (terminal.component.ts) is designed to
     * re-identify who a sale belongs to by PIN alone, deliberately *without* logging the browser
     * out of its original JWT — see that component's own class doc comment. So the instant a
     * cashier switches to a teammate's PIN, every subsequent order call is made with {@code
     * cashierSessionId} = the teammate's session but {@code caller} = the browser's original JWT
     * identity, which is neither that session's own cashier nor (usually) an ADMIN — this check
     * rejected every one of those calls with 403, silently leaving the terminal with no tabs and
     * no way to start one. The fix: also allow a caller who themselves has their own open cashier
     * session on the *same* register session (shift) as the one being acted on — i.e. anyone
     * already legitimately signed on to this shift can act on a teammate's session on that same
     * shift once switched to it, which is exactly what "Switch cashier" is for. This still refuses
     * a cashier with no open session anywhere on this shift, preserving the original "unrelated
     * cashier guessing another session's id" protection.
     */
    private void requireOwnCashierSessionOrAdmin(CashierSession cashierSession, AppUser caller) {
        boolean isOwnSession = cashierSession != null
                && cashierSession.getCashier() != null
                && cashierSession.getCashier().getId().equals(caller.getId());
        if (isOwnSession || caller.getRole() == Role.ADMIN) {
            return;
        }
        boolean sharesThisShift = cashierSession != null
                && cashierSession.getRegisterSession() != null
                && cashierSessionRepository.findByRegisterSessionIdAndCashierIdAndStatus(
                        cashierSession.getRegisterSession().getId(), caller.getId(), CashierSessionStatus.OPEN)
                        .isPresent();
        if (!sharesThisShift) {
            throw ApiException.forbidden("This session does not belong to you");
        }
    }
}
