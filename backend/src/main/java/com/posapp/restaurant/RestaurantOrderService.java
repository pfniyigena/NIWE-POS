package com.posapp.restaurant;
import com.posapp.common.Qty;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.manufacturing.Bom;
import com.posapp.manufacturing.BomService;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.restaurant.dto.AddRestaurantLineRequest;
import com.posapp.restaurant.dto.AssignDriverRequest;
import com.posapp.restaurant.dto.KitchenTicketDto;
import com.posapp.restaurant.dto.MergeOrdersRequest;
import com.posapp.restaurant.dto.OpenOffPremiseOrderRequest;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.restaurant.dto.RestaurantCheckoutRequest;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import com.posapp.restaurant.dto.StaffOptionDto;
import com.posapp.restaurant.dto.UpdateRestaurantLineRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dine-in order-taking + kitchen + checkout flow (Fourier: "add Restaurant management in our
 * ERP ... enabled on request as we did for Accounting module"). Deliberately self-contained
 * rather than routed through {@code com.posapp.order.OrderService} — see RestaurantOrder's own
 * class javadoc — but mirrors that service's checkout mechanics closely: stock is decremented the
 * same way (respecting {@code StoreSettings.allowNegativeStock}, and only for {@code
 * Product.trackStock} lines), and a balanced journal entry is posted through {@code
 * AccountingService.postRestaurantSale} the same way a POS sale posts through {@code
 * postPosSale} — so a restaurant sale shows up in Inventory/Accounting exactly like any other
 * sale once checked out, even though the order-taking flow itself is its own screen.
 *
 * <p>Recipes / ingredient consumption (Fourier: "Recipes / ingredient consumption"): when a menu
 * item's product has an active {@code Bom} (set up the same way as any manufactured product,
 * via Admin &gt; Manufacturing &gt; Bill of materials — no separate Restaurant recipe UI), a line
 * consumes its recipe's ingredients instead of the menu item's own stock, and COGS is rolled up
 * from ingredient cost instead of the item's own {@code costPrice}. This mirrors {@code
 * ManufacturingOrderService.produce()}'s stock-validation/consumption pattern exactly. A menu item
 * with no active Bom keeps today's behavior unchanged (decrement its own stock if tracked, cost
 * from its own costPrice). Recipe quantities are plain whole numbers in whatever unit the
 * ingredient Product's own stock is tracked in — there is no unit-conversion (kg/g/L/mL) system,
 * same constraint the Manufacturing module already has (Fourier's own worked example — "Chicken
 * Brochette", 250 g chicken/50 g onion/20 ml oil/5 g spices/100 g charcoal per unit sold — works
 * exactly like this today, as long as each ingredient Product's own stock is tracked in that same
 * unit; there's no per-ingredient unit field to convert from, e.g. purchasing chicken by the kg but
 * consuming it by the gram).
 *
 * <p>Fourier: "Restaurant should impact Inventory Module" — checkout doesn't just mutate {@code
 * Product.stockQuantity} silently, it leaves the same kind of structured, dated trail every other
 * stock-changing event in this app does: a recipe-backed line's ingredients are snapshotted onto
 * {@link RestaurantOrderConsumption} rows (before/after stock, unit cost — mirrors {@code
 * ManufacturingOrderConsumption}), and a non-recipe line's own product gets a {@code
 * RestaurantOrderLine.stockBefore}/{@code stockAfter} snapshot (mirrors {@code
 * com.posapp.order.OrderLine}). {@code ReportService.stockMovementHistory} reads both, so a
 * restaurant sale shows up in the Inventory module's own Stock Movement History report — "Restaurant
 * sale" for a line whose product's own stock was decremented, "Restaurant ingredient consumption"
 * for each ingredient a recipe-backed line consumed — exactly like a POS sale or a Manufacturing run
 * already do, not just as a silent change to the number on the product's own record.
 */
@Service
public class RestaurantOrderService {

    private final RestaurantOrderRepository orderRepository;
    private final DiningTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;
    private final PaymentMethodService paymentMethodService;
    private final AccountingService accountingService;
    private final StoreSettingsService storeSettingsService;
    private final AuditLogService auditLogService;
    private final BomService bomService;
    /** Multi-warehouse phase 4 — the configured kitchen location (null = primary warehouse stock). */
    private final WarehouseService warehouseService;
    /** Hotel module: puts a bill on an in-house guest's room bill, and finds in-house guests for room service. */
    private final com.posapp.hotel.HotelFolioService hotelFolioService;
    private final RestaurantSettingsService settingsService;
    private final RestaurantOptionGroupRepository optionGroupRepository;
    private final RestaurantVoidLogRepository voidLogRepository;

    public static final int MAX_COURSE = 5;

    private final ReservationRepository reservationRepository;
    private final RestaurantCashService cashService;

    public RestaurantOrderService(RestaurantOrderRepository orderRepository, DiningTableRepository tableRepository,
                                   MenuItemRepository menuItemRepository, UserRepository userRepository,
                                   CatalogService catalogService, PaymentMethodService paymentMethodService,
                                   AccountingService accountingService, StoreSettingsService storeSettingsService,
                                   AuditLogService auditLogService, BomService bomService,
                                   WarehouseService warehouseService, com.posapp.hotel.HotelFolioService hotelFolioService,
                                   RestaurantSettingsService settingsService, RestaurantOptionGroupRepository optionGroupRepository,
                                   RestaurantVoidLogRepository voidLogRepository, ReservationRepository reservationRepository,
                                   RestaurantCashService cashService) {
        this.cashService = cashService;
        this.reservationRepository = reservationRepository;
        this.voidLogRepository = voidLogRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.settingsService = settingsService;
        this.hotelFolioService = hotelFolioService;
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.catalogService = catalogService;
        this.paymentMethodService = paymentMethodService;
        this.accountingService = accountingService;
        this.storeSettingsService = storeSettingsService;
        this.auditLogService = auditLogService;
        this.bomService = bomService;
        this.warehouseService = warehouseService;
    }

    @Transactional(readOnly = true)
    public List<RestaurantOrderDto> listOpen() {
        return orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN).stream()
                .map(RestaurantOrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantOrderDto getById(Long id) {
        return RestaurantOrderDto.from(getOrThrow(id));
    }

    /** Restaurant Receipts screen (lookup + reprint) — most recent checked-out orders, newest first. Cancelled orders don't appear here (nothing was ever sold on one), same as the POS terminal's own Receipts list only showing COMPLETED sales. */
    @Transactional(readOnly = true)
    public List<RestaurantOrderDto> listClosed() {
        return orderRepository.findTop200ByStatusOrderByClosedAtDesc(RestaurantOrderStatus.CLOSED).stream()
                .map(RestaurantOrderDto::from)
                .toList();
    }

    /**
     * The Receipts screen's feed with search and filters: CLOSED orders by default, CANCELLED ones with {@code status=CANCELLED},
     * or both with {@code ALL}. With no filter at all the list stays capped at the newest 200, as before. {@code q} searches the
     * ticket number, table or customer, phone, server, room and notes; {@code method} keeps orders paid (even partly) with it.
     */
    @Transactional(readOnly = true)
    public List<RestaurantOrderDto> closedFiltered(String q, String status, String type, String method, Long serverId,
                                                   java.time.LocalDate from, java.time.LocalDate to, boolean capped) {
        String st = status == null || status.isBlank() ? "CLOSED" : status.trim().toUpperCase();
        List<RestaurantOrder> source = new ArrayList<>();
        if (!st.equals("CANCELLED")) source.addAll(orderRepository.findByStatusOrderByClosedAtDesc(RestaurantOrderStatus.CLOSED));
        if (st.equals("CANCELLED") || st.equals("ALL")) source.addAll(orderRepository.findByStatusOrderByClosedAtDesc(RestaurantOrderStatus.CANCELLED));
        if (st.equals("ALL")) source.sort(Comparator.comparing(RestaurantOrder::getClosedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<RestaurantOrderDto> out = new ArrayList<>();
        for (RestaurantOrder o : source) {
            if (!com.posapp.common.ListPaging.between(o.getClosedAt(), from, to)) continue;
            if (type != null && !type.isBlank() && !o.getOrderType().name().equalsIgnoreCase(type.trim())) continue;
            if (serverId != null && (o.getServer() == null || !serverId.equals(o.getServer().getId()))) continue;
            if (method != null && !method.isBlank() && o.getPayments().stream().noneMatch(p -> method.trim().equalsIgnoreCase(p.getMethod()))) continue;
            if (!com.posapp.common.ListPaging.has(q, o.getTicketNumber(), o.displayLabel(), o.getCustomerName(), o.getCustomerPhone(),
                    o.getServer() != null ? o.getServer().getFullName() : null, o.getRoomLabel(), o.getNotes())) continue;
            out.add(RestaurantOrderDto.from(o));
            if (capped && out.size() >= 200) break;
        }
        return out;
    }

    /** Opens a new tab at {@code tableId} — refused if that table already has one open, same "one open order per table" invariant a real floor manager would expect. */
    @Transactional
    public RestaurantOrderDto openOrder(Long tableId, OpenTableOrderRequest request) {
        DiningTable table = tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound("Table not found: " + tableId));
        if (!orderRepository.findByTableIdAndStatusOrderByIdAsc(tableId, RestaurantOrderStatus.OPEN).isEmpty()) {
            throw ApiException.badRequest("Table " + table.getName() + " already has an open order");
        }
        RestaurantOrder order = new RestaurantOrder();
        order.setTable(table);
        order.setGuestCount(request.getGuestCount() > 0 ? request.getGuestCount() : 1);
        order.setNotes(request.getNotes());
        if (request.getServerId() != null) {
            order.setServer(userRepository.findById(request.getServerId()).orElse(null));
        }
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);
        applyPricingRules(order);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_OPENED", "Opened order at table " + table.getName(), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /**
     * Fourier: "Delivery & takeaway" — opens a TAKEAWAY order with no table, a customer name
     * standing in for "which table" everywhere else in this service. Otherwise identical to
     * {@link #openOrder}: everything downstream (adding lines, sending to the kitchen, checkout)
     * works unchanged since it's null-safe on {@code table} already.
     */
    @Transactional
    public RestaurantOrderDto openTakeawayOrder(OpenOffPremiseOrderRequest request) {
        RestaurantOrder order = newOffPremiseOrder(request, RestaurantOrderType.TAKEAWAY);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_OPENED", "Opened takeaway order for " + saved.getCustomerName(), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /**
     * Fourier: "Delivery & takeaway" — opens a DELIVERY order with no table; unlike takeaway, an
     * address is required (nothing to hand a driver otherwise) and the order starts life on the
     * deliveries board with {@code deliveryStatus = AWAITING_DRIVER}.
     */
    @Transactional
    public RestaurantOrderDto openDeliveryOrder(OpenOffPremiseOrderRequest request) {
        if (request.getCustomerAddress() == null || request.getCustomerAddress().isBlank()) {
            throw ApiException.badRequest("A delivery address is required");
        }
        RestaurantOrder order = newOffPremiseOrder(request, RestaurantOrderType.DELIVERY);
        order.setDeliveryStatus(DeliveryStatus.AWAITING_DRIVER);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_OPENED", "Opened delivery order for " + saved.getCustomerName(), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /**
     * Hotel module: opens a room-service order for a checked-in guest. It runs through the kitchen like a takeaway order and, at checkout,
     * is charged to the guest's room bill (the cashier can still take payment another way if the guest prefers to pay at the door).
     */
    @Transactional
    public RestaurantOrderDto openRoomServiceOrder(com.posapp.restaurant.dto.OpenRoomServiceOrderRequest request) {
        com.posapp.hotel.dto.HotelDtos.InHouseDto guest = hotelFolioService.inHouseGuest(request.getBookingId());
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderType(RestaurantOrderType.TAKEAWAY);
        order.setCustomerName(guest.guestName());
        order.setGuestCount(1);
        order.setNotes(request.getNotes());
        order.setRoomBookingId(guest.bookingId());
        order.setRoomLabel(guest.roomNumber() + " — " + guest.guestName());
        if (request.getServerId() != null) {
            order.setServer(userRepository.findById(request.getServerId()).orElse(null));
        }
        applyPricingRules(order);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_OPENED", "Opened room service order for room " + guest.roomNumber(), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    private RestaurantOrder newOffPremiseOrder(OpenOffPremiseOrderRequest request, RestaurantOrderType type) {
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderType(type);
        order.setCustomerName(request.getCustomerName());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setCustomerAddress(type == RestaurantOrderType.DELIVERY ? request.getCustomerAddress() : null);
        order.setGuestCount(request.getGuestCount() > 0 ? request.getGuestCount() : 1);
        order.setNotes(request.getNotes());
        if (request.getServerId() != null) {
            order.setServer(userRepository.findById(request.getServerId()).orElse(null));
        }
        applyPricingRules(order);
        return order;
    }

    /** Copies the restaurant's pricing rules onto a new order, so a later change to the settings never touches a tab that is already open. */
    void applyPricingRules(RestaurantOrder order) {
        RestaurantSettings st = settingsService.entity();
        boolean charged = switch (order.getOrderType()) {
            case DINE_IN -> st.isServiceChargeDineIn();
            case TAKEAWAY -> st.isServiceChargeTakeaway();
            case DELIVERY -> st.isServiceChargeDelivery();
        };
        order.setServiceChargePercent(charged ? st.getServiceChargePercent() : BigDecimal.ZERO);
        order.setServiceChargeVatPercent(st.getServiceChargeVatPercent());
        order.setRoundingUnit(st.getRoundingUnit());
    }

    // ---- Discounts and the service charge ----

    private static BigDecimal grossOf(RestaurantOrder order) {
        BigDecimal g = BigDecimal.ZERO;
        for (RestaurantOrderLine l : order.getLines()) g = g.add(RestaurantOrderCalculator.lineGross(l));
        return g;
    }

    /** A discount bigger than the restaurant's limit (as a share of the whole bill) needs a manager. */
    private void assertWithinDiscountLimit(RestaurantOrder order) {
        BigDecimal gross = grossOf(order);
        if (gross.signum() <= 0) return;
        BigDecimal pct = RestaurantOrderCalculator.discountTotal(order).multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP);
        BigDecimal limit = settingsService.entity().getDiscountLimitPercent();
        if (pct.compareTo(limit) > 0 && !RestaurantAccess.isApprover()) {
            throw ApiException.forbidden("A discount of " + pct.stripTrailingZeros().toPlainString() + "% is more than the " + limit.stripTrailingZeros().toPlainString()
                    + "% you may give — ask a manager to approve it");
        }
    }

    private static String cleanReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() > 200) throw ApiException.badRequest("The reason is too long (200 characters at most)");
        return r;
    }

    /** Sets, changes or removes the discount on the whole order — a percentage or a fixed amount, with a reason. */
    @Transactional
    public RestaurantOrderDto setOrderDiscount(Long orderId, com.posapp.restaurant.dto.OrderDiscountRequest request) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        BigDecimal percent = request.getPercent() == null ? BigDecimal.ZERO : request.getPercent();
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("A discount must be between 0 and 100 percent");
        }
        if (amount.signum() < 0) {
            throw ApiException.badRequest("A discount amount cannot be negative");
        }
        if (percent.signum() > 0 && amount.signum() > 0) {
            throw ApiException.badRequest("Give either a percentage or an amount, not both");
        }
        String reason = cleanReason(request.getReason());
        BigDecimal before = RestaurantOrderCalculator.discountTotal(order);
        if (percent.signum() > 0 || amount.signum() > 0) {
            if (reason.isEmpty()) throw ApiException.badRequest("Say why the discount is given");
            BigDecimal afterOwn = BigDecimal.ZERO;
            for (RestaurantOrderLine l : order.getLines()) afterOwn = afterOwn.add(RestaurantOrderCalculator.lineGross(l).subtract(RestaurantOrderCalculator.lineOwnDiscount(l)));
            if (amount.compareTo(afterOwn) > 0) throw ApiException.badRequest("The discount is more than the bill");
            order.setDiscountPercent(percent.setScale(2, RoundingMode.HALF_UP));
            order.setDiscountAmount(amount.setScale(2, RoundingMode.HALF_UP));
            order.setDiscountReason(reason);
            order.setDiscountBy(RestaurantAccess.username());
        } else {
            order.setDiscountPercent(BigDecimal.ZERO);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setDiscountReason(null);
            order.setDiscountBy(null);
        }
        if (RestaurantOrderCalculator.discountTotal(order).compareTo(before) > 0) {
            assertWithinDiscountLimit(order);
        }
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_DISCOUNT", (percent.signum() > 0 || amount.signum() > 0
                ? "Gave a discount of " + (percent.signum() > 0 ? percent.stripTrailingZeros().toPlainString() + "%" : amount.toPlainString()) + " on " + safeTableName(saved) + " (" + reason + ")"
                : "Removed the discount on " + safeTableName(saved)), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Waives or restores the service charge on one order. Waiving it needs a manager. */
    @Transactional
    public RestaurantOrderDto setServiceChargeWaived(Long orderId, boolean waived) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        if (waived && !RestaurantAccess.isApprover()) {
            throw ApiException.forbidden("Only a manager can waive the service charge");
        }
        order.setServiceChargeWaived(waived);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_SERVICE_CHARGE", (waived ? "Waived" : "Restored") + " the service charge on " + safeTableName(saved), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** The deliveries board's own feed — every still-open DELIVERY order, oldest first, same filter-in-service convention as listKitchenTickets. */
    @Transactional(readOnly = true)
    public List<RestaurantOrderDto> listDeliveries() {
        return orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN).stream()
                .filter(o -> o.getOrderType() == RestaurantOrderType.DELIVERY)
                .map(RestaurantOrderDto::from)
                .toList();
    }

    /** Driver-picker options for the deliveries board — see StaffOptionDto's javadoc for why this doesn't just reuse the ADMIN-only /api/users. */
    @Transactional(readOnly = true)
    public List<StaffOptionDto> listActiveStaff() {
        return userRepository.findByActiveTrue().stream().map(StaffOptionDto::from).toList();
    }

    /** Assigns/reassigns a driver on a DELIVERY order — auto-advances AWAITING_DRIVER to OUT_FOR_DELIVERY, since assigning a driver is what dispatching means; a reassignment leaves an already-in-flight status alone. */
    @Transactional
    public RestaurantOrderDto assignDriver(Long orderId, AssignDriverRequest request) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        if (order.getOrderType() != RestaurantOrderType.DELIVERY) {
            throw ApiException.badRequest("Only delivery orders can have a driver assigned");
        }
        AppUser driver = userRepository.findById(request.getDriverId())
                .orElseThrow(() -> ApiException.notFound("User not found: " + request.getDriverId()));
        order.setDriver(driver);
        if (order.getDeliveryStatus() == DeliveryStatus.AWAITING_DRIVER) {
            order.setDeliveryStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        }
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_DELIVERY_DRIVER_ASSIGNED", "Assigned " + driver.getFullName() + " to deliver " + saved.getCustomerName() + "'s order", "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Advances a DELIVERY order's own hand-off status — no enforced sequence, same "staff judgment call" convention as setLineKitchenStatus. */
    @Transactional
    public RestaurantOrderDto setDeliveryStatus(Long orderId, String status) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        if (order.getOrderType() != RestaurantOrderType.DELIVERY) {
            throw ApiException.badRequest("Only delivery orders have a delivery status");
        }
        DeliveryStatus newStatus;
        try {
            newStatus = DeliveryStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown delivery status: " + status);
        }
        order.setDeliveryStatus(newStatus);
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    /** Adds one menu item to an open order — snapshots the product's current name/price/tax exactly like OrderService.addLine, so a later product edit never rewrites an already-ordered line. */
    @Transactional
    public RestaurantOrderDto addLine(Long orderId, AddRestaurantLineRequest request) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> ApiException.notFound("Menu item not found: " + request.getMenuItemId()));
        Product product = menuItem.getProduct();
        if (menuItem.isSoldOut()) {
            throw ApiException.badRequest(product.getName() + " is sold out" + (menuItem.getSoldOutReason() == null ? "" : " (" + menuItem.getSoldOutReason() + ")"));
        }
        RestaurantOrderLine line = new RestaurantOrderLine();
        line.setOrder(order);
        line.setProduct(product);
        line.setDescription(product.getName());
        line.setUnitPrice(menuItem.getPriceOverride() != null ? menuItem.getPriceOverride() : product.getUnitPrice());
        line.setUnitCost(product.getCostPrice());
        line.setStation(menuItem.getStation());
        line.setPrepMinutes(menuItem.getPrepMinutes());
        line.setQuantity(request.getQuantity().signum() > 0 ? request.getQuantity() : BigDecimal.ONE);
        line.setNotes(request.getNotes());
        line.setMenuItemId(menuItem.getId());
        line.setSeat(checkSeat(request.getSeat()));
        line.setAllergens(menuItem.getAllergens());
        line.setCourse(request.getCourse() != null ? checkCourse(request.getCourse()) : (menuItem.getDefaultCourse() <= 0 ? 1 : menuItem.getDefaultCourse()));
        applyOptions(line, pickOptions(menuItem.getId(), request.getOptionIds()));
        if (product.getTaxRate() != null) {
            line.setTaxCode(product.getTaxRate().getCode());
            line.setTaxRatePercent(product.getTaxRate().getRatePercent());
            line.setTaxInclusive(product.getTaxRate().isInclusive());
        }
        order.getLines().add(line);
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    @Transactional
    public RestaurantOrderDto updateLine(Long orderId, Long lineId, UpdateRestaurantLineRequest request) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        if (request.getQuantity() != null) {
            if (request.getQuantity().signum() <= 0) {
                throw ApiException.badRequest("Quantity must be at least 1 — remove the line instead to take it off the bill");
            }
            line.setQuantity(request.getQuantity());
        }
        if (request.getNotes() != null) {
            line.setNotes(request.getNotes());
        }
        if (request.getSeat() != null) {
            line.setSeat(request.getSeat() == 0 ? null : checkSeat(request.getSeat()));
        }
        if (request.getCourse() != null) {
            if (line.getKitchenStatus() != KitchenStatus.PENDING) {
                throw ApiException.badRequest("This line has already been sent to the kitchen, so its course can no longer be changed");
            }
            line.setCourse(checkCourse(request.getCourse()));
        }
        if (request.getOptionIds() != null) {
            if (line.getKitchenStatus() != KitchenStatus.PENDING) {
                throw ApiException.badRequest("This line has already been sent to the kitchen — take it off and add it again to change its options");
            }
            if (line.getMenuItemId() == null) {
                throw ApiException.badRequest("This line was ordered before options existed — take it off and add it again");
            }
            applyOptions(line, pickOptions(line.getMenuItemId(), request.getOptionIds()));
        }
        if (request.getDiscountPercent() != null) {
            if (line.isComped() && !RestaurantAccess.isApprover()) {
                throw ApiException.forbidden("This item was given free by a manager — only a manager can change it");
            }
            line.setComped(false);
            BigDecimal p = request.getDiscountPercent();
            if (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw ApiException.badRequest("A discount must be between 0 and 100 percent");
            }
            BigDecimal old = line.getDiscountPercent() == null ? BigDecimal.ZERO : line.getDiscountPercent();
            if (p.signum() > 0) {
                String reason = request.getDiscountReason() != null ? cleanReason(request.getDiscountReason()) : (line.getDiscountReason() == null ? "" : line.getDiscountReason());
                if (reason.isEmpty()) throw ApiException.badRequest("Say why the discount is given");
                line.setDiscountReason(reason);
                line.setDiscountBy(RestaurantAccess.username());
            } else {
                line.setDiscountReason(null);
                line.setDiscountBy(null);
            }
            line.setDiscountPercent(p.setScale(2, RoundingMode.HALF_UP));
            if (p.compareTo(old) > 0) assertWithinDiscountLimit(order);
            auditLogService.record("RESTAURANT_DISCOUNT", (p.signum() > 0 ? "Gave " + p.stripTrailingZeros().toPlainString() + "% off " + line.getDescription() + " (" + line.getDiscountReason() + ")"
                    : "Removed the discount on " + line.getDescription()) + " at " + safeTableName(order), "RestaurantOrder", order.getId());
        }
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    @Transactional
    public RestaurantOrderDto removeLine(Long orderId, Long lineId, String reason) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        if (line.getKitchenStatus() != KitchenStatus.PENDING) {
            String why = reason == null ? "" : cleanReason(reason);
            if (why.isEmpty()) throw ApiException.badRequest("Say why the item is being voided — it has already gone to the kitchen");
            if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Voiding an item that has gone to the kitchen needs a manager's approval");
            BigDecimal value = RestaurantOrderCalculator.lineNetTotal(line).add(RestaurantOrderCalculator.lineTax(line));
            logVoid("VOID", order, line.getDescription(), line.getQuantity(), value, why, true);
            auditLogService.record("RESTAURANT_LINE_VOIDED", "Voided " + Qty.str(line.getQuantity()) + " × " + line.getDescription() + " at " + safeTableName(order) + " (" + why + ")", "RestaurantOrder", order.getId());
        }
        order.getLines().remove(line);
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    /** Fourier: "Kitchen Order Tickets / Kitchen Display" — flips every still-PENDING line to SENT in one go, i.e. "fire this round to the kitchen." Lines already past PENDING (a second round sent earlier) are left alone. */
    @Transactional
    public RestaurantOrderDto sendToKitchen(Long orderId) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        for (RestaurantOrderLine line : order.getLines()) {
            if (line.getKitchenStatus() == KitchenStatus.PENDING && line.getCourse() <= order.getFiredCourse()) {
                line.moveTo(KitchenStatus.SENT);
            }
        }
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_SENT_TO_KITCHEN", "Sent order at table " + safeTableName(saved) + " to the kitchen", "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    private Integer checkSeat(Integer seat) {
        if (seat == null) return null;
        if (seat < 1 || seat > 40) throw ApiException.badRequest("Seat must be between 1 and 40");
        return seat;
    }

    /** Gives one item away free (a 100% discount with a reason). Only a manager may. */
    @Transactional
    public RestaurantOrderDto compLine(Long orderId, Long lineId, String reason) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Giving an item free needs a manager's approval");
        String why = reason == null ? "" : cleanReason(reason);
        if (why.isEmpty()) throw ApiException.badRequest("Say why the item is given free");
        BigDecimal value = RestaurantOrderCalculator.lineGross(line);
        line.setDiscountPercent(BigDecimal.valueOf(100).setScale(2));
        line.setDiscountReason("Comp: " + why);
        line.setDiscountBy(RestaurantAccess.username());
        line.setComped(true);
        RestaurantOrder saved = orderRepository.save(order);
        logVoid("COMP", saved, line.getDescription(), line.getQuantity(), value, why, true);
        auditLogService.record("RESTAURANT_LINE_COMPED", "Comped " + Qty.str(line.getQuantity()) + " × " + line.getDescription() + " at " + safeTableName(saved) + " (" + why + ")", "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Takes a comp back (the item is charged again). */
    @Transactional
    public RestaurantOrderDto uncompLine(Long orderId, Long lineId) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Only a manager can take a comp back");
        if (!line.isComped()) throw ApiException.badRequest("This item was not given free");
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setDiscountReason(null);
        line.setDiscountBy(null);
        line.setComped(false);
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_LINE_COMPED", "Took back the comp on " + line.getDescription() + " at " + safeTableName(saved), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Releases a course (and everything before it) to the kitchen: its waiting lines go out now. */
    @Transactional
    public RestaurantOrderDto fireCourse(Long orderId, int course) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        checkCourse(course);
        int fired = 0;
        for (RestaurantOrderLine line : order.getLines()) {
            if (line.getKitchenStatus() == KitchenStatus.PENDING && line.getCourse() <= course) {
                line.moveTo(KitchenStatus.SENT);
                fired++;
            }
        }
        if (fired == 0) {
            throw ApiException.badRequest("There is nothing waiting for course " + course + " on this order");
        }
        order.setFiredCourse(Math.max(order.getFiredCourse(), course));
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_COURSE_FIRED", "Fired course " + course + " (" + fired + " item" + (fired == 1 ? "" : "s") + ") at " + safeTableName(saved), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Records the allergens the guests at this order cannot have, so the kitchen sees them and conflicting items are flagged. */
    @Transactional
    public RestaurantOrderDto setGuestAllergens(Long orderId, String allergens) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        order.setGuestAllergens(RestaurantAllergens.normalize(allergens));
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_GUEST_ALLERGENS", saved.getGuestAllergens() == null ? "Cleared the guest allergens at " + safeTableName(saved)
                : "Guest allergens at " + safeTableName(saved) + ": " + saved.getGuestAllergens(), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    private int checkCourse(int course) {
        if (course < 1 || course > MAX_COURSE) throw ApiException.badRequest("Course must be between 1 and " + MAX_COURSE);
        return course;
    }

    /** Checks the picked option ids against the item's option groups and returns the chosen options. */
    List<RestaurantOption> pickOptions(Long menuItemId, List<Long> ids) {
        List<RestaurantOptionGroup> groups = optionGroupRepository.findByMenuItemIdOrderBySortOrderAscIdAsc(menuItemId).stream().filter(RestaurantOptionGroup::isActive).toList();
        List<Long> wanted = ids == null ? List.of() : ids.stream().distinct().toList();
        List<RestaurantOption> picked = new ArrayList<>();
        for (Long id : wanted) {
            RestaurantOption found = null;
            for (RestaurantOptionGroup g : groups) {
                for (RestaurantOption o : g.getOptions()) if (o.getId().equals(id) && o.isActive()) found = o;
            }
            if (found == null) throw ApiException.badRequest("Option " + id + " is not available for this item (it may have been removed from the menu)");
            picked.add(found);
        }
        for (RestaurantOptionGroup g : groups) {
            long count = picked.stream().filter(o -> o.getGroup().getId().equals(g.getId())).count();
            if (g.isRequired() && count == 0) throw ApiException.badRequest("Choose an option for \"" + g.getName() + "\"");
            if (g.getMaxSelect() > 0 && count > g.getMaxSelect()) {
                throw ApiException.badRequest("\"" + g.getName() + "\": choose at most " + g.getMaxSelect());
            }
        }
        return picked;
    }

    private void applyOptions(RestaurantOrderLine line, List<RestaurantOption> picked) {
        line.getOptions().clear();
        BigDecimal sum = BigDecimal.ZERO;
        for (RestaurantOption o : picked) {
            RestaurantOrderLineOption lo = new RestaurantOrderLineOption();
            lo.setLine(line);
            lo.setOptionId(o.getId());
            lo.setGroupName(o.getGroup().getName());
            lo.setOptionName(o.getName());
            lo.setPriceDelta(o.getPriceDelta());
            line.getOptions().add(lo);
            sum = sum.add(o.getPriceDelta());
        }
        line.setOptionsPrice(sum);
    }

    /** The Kitchen Display's own feed — every line currently SENT/PREPARING/READY across every open order, oldest order first, so the kitchen always works its queue in the order tickets actually arrived. */
    @Transactional(readOnly = true)
    public List<KitchenTicketDto> listKitchenTickets() {
        List<RestaurantOrder> openOrders = orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN);
        List<KitchenTicketDto> tickets = new ArrayList<>();
        java.time.Instant now = java.time.Instant.now();
        RestaurantSettings cfg = settingsService.entity();
        for (RestaurantOrder order : openOrders) {
            for (RestaurantOrderLine line : order.getLines()) {
                if (line.getKitchenStatus() == KitchenStatus.SENT || line.getKitchenStatus() == KitchenStatus.PREPARING
                        || line.getKitchenStatus() == KitchenStatus.READY) {
                    tickets.add(KitchenTicketDto.from(order, line, now, cfg.getDefaultPrepMinutes(), cfg.getKitchenWarnPercent()));
                }
            }
        }
        tickets.sort(Comparator.comparing(KitchenTicketDto::orderOpenedAt));
        return tickets;
    }

    /** Advances one line's own kitchen ticket status — no enforced sequence, matching a real kitchen's own judgment calls (an item sent back to PREPARING after being marked READY too soon, say). */
    @Transactional
    public RestaurantOrderDto setLineKitchenStatus(Long orderId, Long lineId, String status) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        KitchenStatus newStatus;
        try {
            newStatus = KitchenStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown kitchen status: " + status);
        }
        line.moveTo(newStatus);
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    /**
     * Settles the bill — one or more payments (Fourier: "split & merge bills" — a table can pay
     * with more than one tender), decrements stock for every stock-tracked line exactly like
     * OrderService.completeCheckout, then posts to Accounting the same way a POS sale does (see
     * class javadoc) and frees the table back to AVAILABLE.
     */
    @Transactional
    public RestaurantOrderDto checkout(Long orderId, RestaurantCheckoutRequest request) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        if (order.getLines().isEmpty()) {
            throw ApiException.badRequest("Cannot check out an order with no items");
        }
        BigDecimal due = RestaurantOrderCalculator.total(order);
        BigDecimal tip = request.getTip() == null ? BigDecimal.ZERO : request.getTip().setScale(2, RoundingMode.HALF_UP);
        if (tip.signum() < 0) {
            throw ApiException.badRequest("A tip cannot be negative");
        }
        if (tip.signum() > 0 && !settingsService.entity().isTipsEnabled()) {
            throw ApiException.badRequest("Tips are switched off in the restaurant settings");
        }
        // A reservation deposit already paid comes off the bill; any part the bill does not use goes back to the guest.
        BigDecimal deposit = order.getDepositApplied() == null ? BigDecimal.ZERO : order.getDepositApplied();
        BigDecimal depositUsed = deposit.min(due.max(BigDecimal.ZERO));
        BigDecimal depositLeft = deposit.subtract(depositUsed);
        BigDecimal payable = due.add(tip).subtract(depositUsed);
        // Hotel module: the "Charge to room" tender puts its amount on an in-house guest's bill instead of collecting it now.
        java.util.Set<String> roomCodes = paymentMethodService.listRoomChargeCodes();
        java.util.Set<String> cashCodes = paymentMethodService.listCashCodes();
        List<String> methods = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal tendered = BigDecimal.ZERO;
        for (RestaurantCheckoutRequest.PaymentEntry entry : request.getPayments()) {
            methods.add(paymentMethodService.validateActiveCodeOrThrow(entry.getMethod()));
            amounts.add(entry.getAmount());
            tendered = tendered.add(entry.getAmount());
        }
        if (tendered.compareTo(payable) < 0) {
            throw ApiException.badRequest("Payments (" + tendered + ") do not cover the amount due (" + payable + ")");
        }
        // More than the bill was handed over: the surplus is change, which only cash can be (it comes back out of the cash taken).
        BigDecimal surplus = tendered.subtract(payable);
        if (surplus.signum() > 0) {
            BigDecimal cashTaken = BigDecimal.ZERO;
            for (int i = 0; i < methods.size(); i++) if (cashCodes.contains(methods.get(i))) cashTaken = cashTaken.add(amounts.get(i));
            if (surplus.compareTo(cashTaken) > 0) {
                throw ApiException.badRequest("Payments (" + tendered + ") are more than the amount due (" + payable + "); only cash can be given back as change");
            }
            BigDecimal left = surplus;
            for (int i = methods.size() - 1; i >= 0 && left.signum() > 0; i--) {
                if (!cashCodes.contains(methods.get(i))) continue;
                BigDecimal cut = left.min(amounts.get(i));
                amounts.set(i, amounts.get(i).subtract(cut));
                left = left.subtract(cut);
            }
        }
        BigDecimal roomPortion = BigDecimal.ZERO;
        for (int i = 0; i < methods.size(); i++) {
            if (amounts.get(i).signum() <= 0) continue;
            if (roomCodes.contains(methods.get(i))) roomPortion = roomPortion.add(amounts.get(i));
            RestaurantPayment payment = new RestaurantPayment();
            payment.setOrder(order);
            payment.setMethod(methods.get(i));
            payment.setAmount(amounts.get(i));
            order.getPayments().add(payment);
        }
        order.setTipAmount(tip);
        order.setChangeGiven(surplus);
        order.setDepositApplied(depositUsed);
        tendered = payable;
        Long roomBookingId = request.getRoomBookingId() != null ? request.getRoomBookingId() : order.getRoomBookingId();
        if (roomPortion.signum() > 0) {
            if (roomBookingId == null) {
                throw ApiException.badRequest("Choose the in-house guest whose room the bill is charged to");
            }
            if (roomPortion.compareTo(payable) > 0) {
                throw ApiException.badRequest("The amount charged to the room cannot be more than the bill");
            }
            hotelFolioService.inHouseGuest(roomBookingId); // fails early, before stock moves, if the guest has left
        }
        boolean allowNegativeStock = storeSettingsService.getSettings().allowNegativeStock();
        if (!allowNegativeStock) {
            validateStockAvailability(order);
        }
        // Multi-warehouse phase 4: resolved once for the whole order; null = primary warehouse stock.
        WarehouseLocation kitchenLocation = warehouseService.getRestaurantLocationOrNull();
        order.setStockWarehouseId(kitchenLocation != null ? kitchenLocation.getWarehouse().getId() : null);
        order.setBranchId(warehouseService.branchIdOf(kitchenLocation != null ? kitchenLocation.getWarehouse() : null));
        BigDecimal cogs = BigDecimal.ZERO;
        for (RestaurantOrderLine line : order.getLines()) {
            Product product = line.getProduct();
            if (product == null) {
                continue;
            }
            Bom recipe = bomService.findActiveForProduct(product.getId());
            if (recipe != null) {
                // Fourier: "the restaurant can automatically calculate ingredient consumption...
                // Also Restaurant should impact Inventory Module" — one RestaurantOrderConsumption
                // row per ingredient, snapshotted the same way ManufacturingOrderService.produce
                // snapshots each component it consumes, so this shows up as a dated "Restaurant
                // ingredient consumption" row in ReportService.stockMovementHistory instead of the
                // ingredient's stock just silently changing.
                for (Map.Entry<Product, BigDecimal> required : bomService.requiredComponentQuantities(recipe, line.getQuantity()).entrySet()) {
                    Product ingredient = required.getKey();
                    BigDecimal quantityConsumed = required.getValue();
                    BigDecimal unitCost = ingredient.getCostPrice() != null ? ingredient.getCostPrice() : BigDecimal.ZERO;

                    RestaurantOrderConsumption consumption = new RestaurantOrderConsumption();
                    consumption.setOrder(order);
                    consumption.setLine(line);
                    consumption.setComponent(ingredient);
                    consumption.setComponentName(ingredient.getName());
                    consumption.setQuantityConsumed(quantityConsumed);
                    consumption.setUnitCost(unitCost);
                    if (ingredient.isTrackStock()) {
                        BigDecimal stockBefore = warehouseService.stockAt(ingredient, kitchenLocation);
                        catalogService.adjustStock(ingredient, quantityConsumed.negate(), kitchenLocation);
                        consumption.setStockBefore(stockBefore);
                        consumption.setStockAfter(warehouseService.stockAt(ingredient, kitchenLocation));
                    }
                    order.getIngredientConsumptions().add(consumption);

                    cogs = cogs.add(unitCost.multiply(quantityConsumed));
                }
            } else if (product.isTrackStock()) {
                // Fourier: "Restaurant should impact Inventory Module" — snapshot before/after the
                // same way OrderLine.stockBefore/stockAfter does for a POS sale, so this shows up
                // as a "Restaurant sale" row in ReportService.stockMovementHistory.
                BigDecimal stockBefore = warehouseService.stockAt(product, kitchenLocation);
                catalogService.adjustStock(product, line.getQuantity().negate(), kitchenLocation);
                line.setStockBefore(stockBefore);
                line.setStockAfter(warehouseService.stockAt(product, kitchenLocation));
                cogs = cogs.add(RestaurantOrderCalculator.lineCost(line));
            }
        }
        order.setStatus(RestaurantOrderStatus.CLOSED);
        order.setClosedAt(Instant.now());
        order.setTicketNumber("REST-" + System.currentTimeMillis());
        releaseTableIfIdle(order.getTable(), order.getId());
        RestaurantOrder saved = orderRepository.save(order);
        BigDecimal subtotal = RestaurantOrderCalculator.subtotal(saved);
        BigDecimal taxTotal = RestaurantOrderCalculator.taxTotal(saved);
        accountingService.postRestaurantSale(saved.getId(), saved.getTicketNumber(), tendered.subtract(roomPortion), roomPortion,
                subtotal, taxTotal, cogs.setScale(2, RoundingMode.HALF_UP),
                RestaurantOrderCalculator.serviceCharge(saved), tip, RestaurantOrderCalculator.roundingAdjustment(saved), depositUsed);
        if (depositLeft.signum() > 0 && saved.getDepositReservationId() != null) {
            Reservation depositRes = reservationRepository.findById(saved.getDepositReservationId()).orElse(null);
            String code = depositRes == null ? null : depositRes.getConfirmationCode();
            accountingService.postRestaurantDeposit("REFUNDED", saved.getDepositReservationId(), code, depositLeft);
            cashService.deposit("DEPOSIT_REFUNDED", saved.getDepositReservationId(), code, depositRes == null ? null : depositRes.getDepositMethod(), depositLeft);
        }
        if (roomPortion.signum() > 0) {
            boolean roomService = saved.getRoomLabel() != null;
            saved.setRoomBookingId(roomBookingId);
            orderRepository.save(saved);
            hotelFolioService.chargeToRoom(roomBookingId, roomService ? com.posapp.hotel.ChargeType.ROOM_SERVICE : com.posapp.hotel.ChargeType.RESTAURANT,
                    (roomService ? "Room service " : "Restaurant ") + saved.getTicketNumber(), roomPortion, "RESTAURANT_ORDER", saved.getId());
        }
        auditLogService.record("RESTAURANT_ORDER_CHECKED_OUT", "Checked out order " + saved.getTicketNumber() + " (" + due + (tip.signum() > 0 ? ", tip " + tip : "") + ")", "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /**
     * Merges required quantities across every line before checking availability, so two dishes
     * sharing a recipe ingredient (or two lines of the same non-recipe item) are validated against
     * their combined demand rather than line-by-line — same reasoning as {@code
     * ManufacturingOrderService.produce()}'s own validation pass. A line whose product has an
     * active recipe checks ingredient stock instead of the menu item's own stock; a line with no
     * recipe checks the item's own stock exactly as before.
     */
    private void validateStockAvailability(RestaurantOrder order) {
        WarehouseLocation kitchenLocation = warehouseService.getRestaurantLocationOrNull();
        Map<Long, BigDecimal> requestedByProductId = new LinkedHashMap<>();
        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (RestaurantOrderLine line : order.getLines()) {
            Product product = line.getProduct();
            if (product == null) continue;
            Bom recipe = bomService.findActiveForProduct(product.getId());
            if (recipe != null) {
                for (Map.Entry<Product, BigDecimal> required : bomService.requiredComponentQuantities(recipe, line.getQuantity()).entrySet()) {
                    Product ingredient = required.getKey();
                    if (!ingredient.isTrackStock()) continue;
                    requestedByProductId.merge(ingredient.getId(), required.getValue(), BigDecimal::add);
                    productsById.putIfAbsent(ingredient.getId(), ingredient);
                }
            } else if (product.isTrackStock()) {
                requestedByProductId.merge(product.getId(), line.getQuantity(), BigDecimal::add);
                productsById.putIfAbsent(product.getId(), product);
            }
        }
        for (var entry : requestedByProductId.entrySet()) {
            Product product = productsById.get(entry.getKey());
            BigDecimal requested = entry.getValue();
            BigDecimal available = warehouseService.stockAt(product, kitchenLocation);
            if (requested.compareTo(available) > 0) {
                throw ApiException.badRequest("Not enough stock for '" + product.getName() + "' — " + available
                        + " available, " + Qty.str(requested) + " requested. Enable \"Allow negative stock\" in Inventory settings to sell anyway.");
            }
        }
    }

    /** Cancels a still-open order (e.g. a party left without ordering) — frees the table, posts no accounting entry (nothing was ever sold). */
    @Transactional
    public RestaurantOrderDto cancel(Long orderId, String reason) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        boolean sent = order.getLines().stream().anyMatch(l -> l.getKitchenStatus() != KitchenStatus.PENDING);
        String why = reason == null ? "" : cleanReason(reason);
        if (sent) {
            if (why.isEmpty()) throw ApiException.badRequest("Say why the order is being cancelled — some of it has already gone to the kitchen");
            if (!RestaurantAccess.isApprover()) throw ApiException.forbidden("Cancelling an order that has gone to the kitchen needs a manager's approval");
        }
        order.setStatus(RestaurantOrderStatus.CANCELLED);
        order.setClosedAt(Instant.now());
        order.setCancelReason(why.isEmpty() ? null : why);
        order.setCancelledBy(RestaurantAccess.username());
        releaseTableIfIdle(order.getTable(), order.getId());
        RestaurantOrder saved = orderRepository.save(order);
        if (saved.getDepositApplied() != null && saved.getDepositApplied().signum() > 0 && saved.getDepositReservationId() != null) {
            // the party left without eating: the deposit goes back to them
            Reservation res = reservationRepository.findById(saved.getDepositReservationId()).orElse(null);
            accountingService.postRestaurantDeposit("REFUNDED", saved.getDepositReservationId(), res == null ? null : res.getConfirmationCode(), saved.getDepositApplied());
            cashService.deposit("DEPOSIT_REFUNDED", saved.getDepositReservationId(), res == null ? null : res.getConfirmationCode(), res == null ? null : res.getDepositMethod(), saved.getDepositApplied());
            if (res != null) {
                res.setDepositStatus("REFUNDED");
                reservationRepository.save(res);
            }
            saved.setDepositApplied(BigDecimal.ZERO);
            orderRepository.save(saved);
        }
        if (!saved.getLines().isEmpty()) {
            logVoid("CANCEL", saved, saved.getLines().size() + " item" + (saved.getLines().size() == 1 ? "" : "s"),
                    saved.getLines().stream().map(RestaurantOrderLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add), RestaurantOrderCalculator.total(saved),
                    why.isEmpty() ? "No reason given" : why, sent);
        }
        auditLogService.record("RESTAURANT_ORDER_CANCELLED", "Cancelled order at table " + safeTableName(saved) + (why.isEmpty() ? "" : " (" + why + ")"), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Frees the table once no other check is still open on it. */
    void releaseTableIfIdle(DiningTable table, Long exceptOrderId) {
        if (table == null) return;
        boolean others = orderRepository.findByTableIdAndStatusOrderByIdAsc(table.getId(), RestaurantOrderStatus.OPEN).stream()
                .anyMatch(o -> !o.getId().equals(exceptOrderId));
        if (!others) {
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }
    }

    void logVoid(String type, RestaurantOrder order, String description, BigDecimal quantity, BigDecimal amount, String reason, boolean approvalNeeded) {
        RestaurantVoidLog log = new RestaurantVoidLog();
        log.setEntryType(type);
        log.setOrderId(order.getId());
        log.setOrderLabel(safeTableName(order));
        log.setDescription(description);
        log.setQuantity(quantity);
        log.setAmount(amount == null ? BigDecimal.ZERO : amount);
        log.setReason(reason);
        log.setByUser(RestaurantAccess.username());
        log.setApprovedBy(approvalNeeded ? RestaurantAccess.username() : null);
        voidLogRepository.save(log);
    }

    /**
     * Fourier: "split & merge bills" — the "merge" half: moves every line from {@code orderId}
     * into {@code request.targetOrderId} (e.g. two tables pushed together for one party) and
     * cancels the now-empty source, freeing its table. Payments already taken against the source
     * order block the merge — merging is meant for before anyone's paid, so a source with payments
     * already recorded gets a clear error instead of silently reassigning money already collected
     * against a different bill.
     */
    @Transactional
    public RestaurantOrderDto mergeInto(Long orderId, MergeOrdersRequest request) {
        RestaurantOrder source = getOpenOrThrow(orderId);
        RestaurantOrder target = getOpenOrThrow(request.getTargetOrderId());
        if (source.getId().equals(target.getId())) {
            throw ApiException.badRequest("Cannot merge an order into itself");
        }
        if (!source.getPayments().isEmpty()) {
            throw ApiException.badRequest("This order already has payments recorded — settle it separately instead of merging");
        }
        for (RestaurantOrderLine line : new ArrayList<>(source.getLines())) {
            source.getLines().remove(line);
            line.setOrder(target);
            target.getLines().add(line);
        }
        source.setStatus(RestaurantOrderStatus.CANCELLED);
        source.setClosedAt(Instant.now());
        releaseTableIfIdle(source.getTable(), source.getId());
        orderRepository.save(source);
        RestaurantOrder savedTarget = orderRepository.save(target);
        auditLogService.record("RESTAURANT_ORDERS_MERGED",
                "Merged table " + safeTableName(source) + "'s order into table " + safeTableName(savedTarget), "RestaurantOrder", savedTarget.getId());
        return RestaurantOrderDto.from(savedTarget);
    }

    private String safeTableName(RestaurantOrder order) {
        return order.displayLabel();
    }

    private RestaurantOrderLine findLineOrThrow(RestaurantOrder order, Long lineId) {
        return order.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Order line not found: " + lineId));
    }

    private RestaurantOrder getOrThrow(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order not found: " + id));
    }

    private RestaurantOrder getOpenOrThrow(Long id) {
        RestaurantOrder order = getOrThrow(id);
        if (order.getStatus() != RestaurantOrderStatus.OPEN) {
            throw ApiException.badRequest("This order is already " + order.getStatus());
        }
        return order;
    }
}
