package com.posapp.restaurant;

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

    public RestaurantOrderService(RestaurantOrderRepository orderRepository, DiningTableRepository tableRepository,
                                   MenuItemRepository menuItemRepository, UserRepository userRepository,
                                   CatalogService catalogService, PaymentMethodService paymentMethodService,
                                   AccountingService accountingService, StoreSettingsService storeSettingsService,
                                   AuditLogService auditLogService, BomService bomService,
                                   WarehouseService warehouseService) {
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

    /** Opens a new tab at {@code tableId} — refused if that table already has one open, same "one open order per table" invariant a real floor manager would expect. */
    @Transactional
    public RestaurantOrderDto openOrder(Long tableId, OpenTableOrderRequest request) {
        DiningTable table = tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound("Table not found: " + tableId));
        if (orderRepository.findByTableIdAndStatus(tableId, RestaurantOrderStatus.OPEN).isPresent()) {
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
        return order;
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
        RestaurantOrderLine line = new RestaurantOrderLine();
        line.setOrder(order);
        line.setProduct(product);
        line.setDescription(product.getName());
        line.setUnitPrice(menuItem.getPriceOverride() != null ? menuItem.getPriceOverride() : product.getUnitPrice());
        line.setUnitCost(product.getCostPrice());
        line.setStation(menuItem.getStation());
        line.setQuantity(request.getQuantity() > 0 ? request.getQuantity() : 1);
        line.setNotes(request.getNotes());
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
            if (request.getQuantity() <= 0) {
                throw ApiException.badRequest("Quantity must be at least 1 — remove the line instead to take it off the bill");
            }
            line.setQuantity(request.getQuantity());
        }
        if (request.getNotes() != null) {
            line.setNotes(request.getNotes());
        }
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    @Transactional
    public RestaurantOrderDto removeLine(Long orderId, Long lineId) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        RestaurantOrderLine line = findLineOrThrow(order, lineId);
        order.getLines().remove(line);
        RestaurantOrder saved = orderRepository.save(order);
        return RestaurantOrderDto.from(saved);
    }

    /** Fourier: "Kitchen Order Tickets / Kitchen Display" — flips every still-PENDING line to SENT in one go, i.e. "fire this round to the kitchen." Lines already past PENDING (a second round sent earlier) are left alone. */
    @Transactional
    public RestaurantOrderDto sendToKitchen(Long orderId) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        for (RestaurantOrderLine line : order.getLines()) {
            if (line.getKitchenStatus() == KitchenStatus.PENDING) {
                line.setKitchenStatus(KitchenStatus.SENT);
            }
        }
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_SENT_TO_KITCHEN", "Sent order at table " + safeTableName(saved) + " to the kitchen", "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** The Kitchen Display's own feed — every line currently SENT/PREPARING/READY across every open order, oldest order first, so the kitchen always works its queue in the order tickets actually arrived. */
    @Transactional(readOnly = true)
    public List<KitchenTicketDto> listKitchenTickets() {
        List<RestaurantOrder> openOrders = orderRepository.findByStatusOrderByOpenedAtAsc(RestaurantOrderStatus.OPEN);
        List<KitchenTicketDto> tickets = new ArrayList<>();
        for (RestaurantOrder order : openOrders) {
            for (RestaurantOrderLine line : order.getLines()) {
                if (line.getKitchenStatus() == KitchenStatus.SENT || line.getKitchenStatus() == KitchenStatus.PREPARING
                        || line.getKitchenStatus() == KitchenStatus.READY) {
                    tickets.add(KitchenTicketDto.from(order, line));
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
        line.setKitchenStatus(newStatus);
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
        BigDecimal tendered = BigDecimal.ZERO;
        for (RestaurantCheckoutRequest.PaymentEntry entry : request.getPayments()) {
            String method = paymentMethodService.validateActiveCodeOrThrow(entry.getMethod());
            RestaurantPayment payment = new RestaurantPayment();
            payment.setOrder(order);
            payment.setMethod(method);
            payment.setAmount(entry.getAmount());
            order.getPayments().add(payment);
            tendered = tendered.add(entry.getAmount());
        }
        if (tendered.compareTo(due) < 0) {
            throw ApiException.badRequest("Payments (" + tendered + ") do not cover the amount due (" + due + ")");
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
                for (Map.Entry<Product, Integer> required : bomService.requiredComponentQuantities(recipe, line.getQuantity()).entrySet()) {
                    Product ingredient = required.getKey();
                    int quantityConsumed = required.getValue();
                    BigDecimal unitCost = ingredient.getCostPrice() != null ? ingredient.getCostPrice() : BigDecimal.ZERO;

                    RestaurantOrderConsumption consumption = new RestaurantOrderConsumption();
                    consumption.setOrder(order);
                    consumption.setLine(line);
                    consumption.setComponent(ingredient);
                    consumption.setComponentName(ingredient.getName());
                    consumption.setQuantityConsumed(quantityConsumed);
                    consumption.setUnitCost(unitCost);
                    if (ingredient.isTrackStock()) {
                        int stockBefore = warehouseService.stockAt(ingredient, kitchenLocation);
                        catalogService.adjustStock(ingredient, -quantityConsumed, kitchenLocation);
                        consumption.setStockBefore(stockBefore);
                        consumption.setStockAfter(warehouseService.stockAt(ingredient, kitchenLocation));
                    }
                    order.getIngredientConsumptions().add(consumption);

                    cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(quantityConsumed)));
                }
            } else if (product.isTrackStock()) {
                // Fourier: "Restaurant should impact Inventory Module" — snapshot before/after the
                // same way OrderLine.stockBefore/stockAfter does for a POS sale, so this shows up
                // as a "Restaurant sale" row in ReportService.stockMovementHistory.
                int stockBefore = warehouseService.stockAt(product, kitchenLocation);
                catalogService.adjustStock(product, -line.getQuantity(), kitchenLocation);
                line.setStockBefore(stockBefore);
                line.setStockAfter(warehouseService.stockAt(product, kitchenLocation));
                cogs = cogs.add(RestaurantOrderCalculator.lineCost(line));
            }
        }
        order.setStatus(RestaurantOrderStatus.CLOSED);
        order.setClosedAt(Instant.now());
        order.setTicketNumber("REST-" + System.currentTimeMillis());
        DiningTable table = order.getTable();
        if (table != null) {
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }
        RestaurantOrder saved = orderRepository.save(order);
        BigDecimal subtotal = RestaurantOrderCalculator.subtotal(saved);
        BigDecimal taxTotal = RestaurantOrderCalculator.taxTotal(saved);
        accountingService.postRestaurantSale(saved.getId(), saved.getTicketNumber(), tendered, subtotal, taxTotal,
                cogs.setScale(2, RoundingMode.HALF_UP));
        auditLogService.record("RESTAURANT_ORDER_CHECKED_OUT", "Checked out order " + saved.getTicketNumber() + " (" + due + ")", "RestaurantOrder", saved.getId());
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
        Map<Long, Integer> requestedByProductId = new LinkedHashMap<>();
        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (RestaurantOrderLine line : order.getLines()) {
            Product product = line.getProduct();
            if (product == null) continue;
            Bom recipe = bomService.findActiveForProduct(product.getId());
            if (recipe != null) {
                for (Map.Entry<Product, Integer> required : bomService.requiredComponentQuantities(recipe, line.getQuantity()).entrySet()) {
                    Product ingredient = required.getKey();
                    if (!ingredient.isTrackStock()) continue;
                    requestedByProductId.merge(ingredient.getId(), required.getValue(), Integer::sum);
                    productsById.putIfAbsent(ingredient.getId(), ingredient);
                }
            } else if (product.isTrackStock()) {
                requestedByProductId.merge(product.getId(), line.getQuantity(), Integer::sum);
                productsById.putIfAbsent(product.getId(), product);
            }
        }
        for (var entry : requestedByProductId.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int requested = entry.getValue();
            int available = warehouseService.stockAt(product, kitchenLocation);
            if (requested > available) {
                throw ApiException.badRequest("Not enough stock for '" + product.getName() + "' — " + available
                        + " available, " + requested + " requested. Enable \"Allow negative stock\" in Inventory settings to sell anyway.");
            }
        }
    }

    /** Cancels a still-open order (e.g. a party left without ordering) — frees the table, posts no accounting entry (nothing was ever sold). */
    @Transactional
    public RestaurantOrderDto cancel(Long orderId) {
        RestaurantOrder order = getOpenOrThrow(orderId);
        order.setStatus(RestaurantOrderStatus.CANCELLED);
        order.setClosedAt(Instant.now());
        DiningTable table = order.getTable();
        if (table != null) {
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }
        RestaurantOrder saved = orderRepository.save(order);
        auditLogService.record("RESTAURANT_ORDER_CANCELLED", "Cancelled order at table " + safeTableName(saved), "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
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
        DiningTable sourceTable = source.getTable();
        if (sourceTable != null) {
            sourceTable.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(sourceTable);
        }
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
