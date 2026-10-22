package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.CheckLineSelection;
import com.posapp.restaurant.dto.RestaurantOrderDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Working with the bills at a table: splitting a bill into separate checks, moving items to another bill, moving a whole bill to another
 * table, and recording receipt prints. Nothing here changes what has been sold or what the kitchen has been told: lines keep their
 * prices, options, courses and kitchen progress when they move.
 */
@Service
public class RestaurantCheckService {

    private final RestaurantOrderRepository orderRepository;
    private final DiningTableRepository tableRepository;
    private final RestaurantVoidLogRepository voidLogRepository;
    private final AuditLogService auditLogService;

    public RestaurantCheckService(RestaurantOrderRepository orderRepository, DiningTableRepository tableRepository,
                                  RestaurantVoidLogRepository voidLogRepository, AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.voidLogRepository = voidLogRepository;
        this.auditLogService = auditLogService;
    }

    private RestaurantOrder open(Long id) {
        RestaurantOrder o = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        if (o.getStatus() != RestaurantOrderStatus.OPEN) throw ApiException.badRequest("This order is already " + o.getStatus());
        return o;
    }

    /** Resolves the request into line -> quantity to move, checking every line belongs to the order. */
    private Map<RestaurantOrderLine, Integer> resolve(RestaurantOrder order, CheckLineSelection sel) {
        Map<RestaurantOrderLine, Integer> out = new LinkedHashMap<>();
        if (sel != null && sel.getSeats() != null) {
            for (RestaurantOrderLine l : order.getLines()) {
                if (l.getSeat() != null && sel.getSeats().contains(l.getSeat())) out.put(l, l.getQuantity());
            }
        }
        if (sel != null && sel.getLines() != null) {
            for (CheckLineSelection.Item it : sel.getLines()) {
                if (it.getLineId() == null) throw ApiException.badRequest("Every selected item needs its line id");
                RestaurantOrderLine l = order.getLines().stream().filter(x -> x.getId().equals(it.getLineId())).findFirst()
                        .orElseThrow(() -> ApiException.badRequest("Line " + it.getLineId() + " is not on this order"));
                int q = it.getQuantity() == null ? l.getQuantity() : it.getQuantity();
                if (q < 1 || q > l.getQuantity()) {
                    throw ApiException.badRequest("You can move 1 to " + l.getQuantity() + " of " + l.getDescription());
                }
                out.put(l, q);
            }
        }
        if (out.isEmpty()) throw ApiException.badRequest("Choose the items to move");
        int remaining = 0;
        for (RestaurantOrderLine l : order.getLines()) remaining += l.getQuantity() - out.getOrDefault(l, 0);
        if (remaining < 0) throw ApiException.badRequest("Invalid selection");
        return out;
    }

    private static RestaurantOrderLine copyOf(RestaurantOrderLine l, int qty) {
        RestaurantOrderLine c = new RestaurantOrderLine();
        c.setProduct(l.getProduct());
        c.setDescription(l.getDescription());
        c.setUnitPrice(l.getUnitPrice());
        c.setUnitCost(l.getUnitCost());
        c.setTaxCode(l.getTaxCode());
        c.setTaxRatePercent(l.getTaxRatePercent());
        c.setTaxInclusive(l.getTaxInclusive());
        c.setQuantity(qty);
        c.setNotes(l.getNotes());
        c.setDiscountPercent(l.getDiscountPercent());
        c.setDiscountReason(l.getDiscountReason());
        c.setDiscountBy(l.getDiscountBy());
        c.setComped(l.isComped());
        c.setKitchenStatus(l.getKitchenStatus());
        c.setStation(l.getStation());
        c.setOptionsPrice(l.getOptionsPrice());
        c.setCourse(l.getCourse());
        c.setSeat(l.getSeat());
        c.setAllergens(l.getAllergens());
        c.setMenuItemId(l.getMenuItemId());
        c.setPrepMinutes(l.getPrepMinutes());
        c.setSentAt(l.getSentAt());
        c.setPreparingAt(l.getPreparingAt());
        c.setReadyAt(l.getReadyAt());
        c.setServedAt(l.getServedAt());
        c.setRemakeCount(l.getRemakeCount());
        for (RestaurantOrderLineOption o : l.getOptions()) {
            RestaurantOrderLineOption co = new RestaurantOrderLineOption();
            co.setLine(c);
            co.setOptionId(o.getOptionId());
            co.setGroupName(o.getGroupName());
            co.setOptionName(o.getOptionName());
            co.setPriceDelta(o.getPriceDelta());
            c.getOptions().add(co);
        }
        return c;
    }

    /** Moves the selected quantities from one order to another (whole lines move, part lines are copied and reduced). */
    private void move(RestaurantOrder from, RestaurantOrder to, Map<RestaurantOrderLine, Integer> picks) {
        for (Map.Entry<RestaurantOrderLine, Integer> e : picks.entrySet()) {
            RestaurantOrderLine l = e.getKey();
            int q = e.getValue();
            if (q == l.getQuantity()) {
                from.getLines().remove(l);
                l.setOrder(to);
                to.getLines().add(l);
            } else {
                RestaurantOrderLine c = copyOf(l, q);
                c.setOrder(to);
                to.getLines().add(c);
                l.setQuantity(l.getQuantity() - q);
            }
        }
    }

    private void releaseTableIfIdle(DiningTable table, Long exceptOrderId) {
        if (table == null) return;
        boolean others = orderRepository.findByTableIdAndStatusOrderByIdAsc(table.getId(), RestaurantOrderStatus.OPEN).stream()
                .anyMatch(o -> !o.getId().equals(exceptOrderId));
        if (!others) {
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }
    }

    /** Splits the chosen items off into a new check at the same table; returns the new check. */
    @Transactional
    public RestaurantOrderDto split(Long orderId, CheckLineSelection sel) {
        RestaurantOrder source = open(orderId);
        if (source.getOrderType() != RestaurantOrderType.DINE_IN || source.getTable() == null) {
            throw ApiException.badRequest("Only a table's bill can be split into separate checks");
        }
        if (!source.getPayments().isEmpty()) throw ApiException.badRequest("This bill already has payments — settle it instead of splitting it");
        Map<RestaurantOrderLine, Integer> picks = resolve(source, sel);
        int left = 0;
        for (RestaurantOrderLine l : source.getLines()) left += l.getQuantity() - picks.getOrDefault(l, 0);
        if (left == 0) throw ApiException.badRequest("Leave at least one item on the original check");

        BigDecimal totalAfterOwn = BigDecimal.ZERO, movedAfterOwn = BigDecimal.ZERO;
        for (RestaurantOrderLine l : source.getLines()) {
            BigDecimal a = RestaurantOrderCalculator.lineGross(l).subtract(RestaurantOrderCalculator.lineOwnDiscount(l));
            totalAfterOwn = totalAfterOwn.add(a);
            Integer q = picks.get(l);
            if (q != null) movedAfterOwn = movedAfterOwn.add(a.multiply(BigDecimal.valueOf(q)).divide(BigDecimal.valueOf(l.getQuantity()), 6, RoundingMode.HALF_UP));
        }

        RestaurantOrder t = new RestaurantOrder();
        t.setTable(source.getTable());
        t.setOrderType(RestaurantOrderType.DINE_IN);
        t.setGuestCount(1);
        t.setServer(source.getServer());
        t.setNotes(source.getNotes());
        t.setServiceChargePercent(source.getServiceChargePercent());
        t.setServiceChargeVatPercent(source.getServiceChargeVatPercent());
        t.setServiceChargeWaived(source.isServiceChargeWaived());
        t.setRoundingUnit(source.getRoundingUnit());
        t.setFiredCourse(source.getFiredCourse());
        t.setGuestAllergens(source.getGuestAllergens());
        t.setStockWarehouseId(source.getStockWarehouseId());
        t.setBranchId(source.getBranchId());
        t.setDiscountPercent(source.getDiscountPercent());
        t.setDiscountReason(source.getDiscountReason());
        t.setDiscountBy(source.getDiscountBy());
        BigDecimal amount = source.getDiscountAmount() == null ? BigDecimal.ZERO : source.getDiscountAmount();
        if (amount.signum() > 0 && totalAfterOwn.signum() > 0) {
            BigDecimal share = amount.multiply(movedAfterOwn).divide(totalAfterOwn, 2, RoundingMode.HALF_UP);
            t.setDiscountAmount(share);
            source.setDiscountAmount(amount.subtract(share));
        }
        Long root = source.getParentOrderId() != null ? source.getParentOrderId() : source.getId();
        if (source.getCheckLabel() == null) source.setCheckLabel("Check 1");
        t.setParentOrderId(root);
        t.setCheckLabel("Check " + (orderRepository.countByParentOrderId(root) + 2));
        move(source, t, picks);
        orderRepository.save(source);
        RestaurantOrder saved = orderRepository.save(t);
        auditLogService.record("RESTAURANT_CHECK_SPLIT", "Split " + saved.getCheckLabel() + " off " + source.displayLabel() + " (" + picks.size() + " item" + (picks.size() == 1 ? "" : "s") + ")",
                "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Moves the chosen items onto another open bill; an emptied bill is closed and its table freed. Returns the receiving bill. */
    @Transactional
    public RestaurantOrderDto transfer(Long orderId, CheckLineSelection sel) {
        RestaurantOrder source = open(orderId);
        if (sel == null || sel.getTargetOrderId() == null) throw ApiException.badRequest("Choose the bill to move the items to");
        if (sel.getTargetOrderId().equals(orderId)) throw ApiException.badRequest("Choose a different bill");
        RestaurantOrder target = open(sel.getTargetOrderId());
        if (source.getOrderType() != target.getOrderType()) throw ApiException.badRequest("Items can only move between bills of the same kind");
        if (!source.getPayments().isEmpty() || !target.getPayments().isEmpty()) throw ApiException.badRequest("A bill with payments cannot take part in a move");
        Map<RestaurantOrderLine, Integer> picks = resolve(source, sel);
        move(source, target, picks);
        if (source.getLines().isEmpty()) {
            source.setStatus(RestaurantOrderStatus.CANCELLED);
            source.setClosedAt(java.time.Instant.now());
            source.setCancelReason("All items moved to " + target.displayLabel());
            source.setCancelledBy(RestaurantAccess.username());
            releaseTableIfIdle(source.getTable(), source.getId());
        }
        orderRepository.save(source);
        RestaurantOrder saved = orderRepository.save(target);
        auditLogService.record("RESTAURANT_ITEMS_MOVED", "Moved " + picks.size() + " item" + (picks.size() == 1 ? "" : "s") + " from " + source.displayLabel() + " to " + saved.displayLabel(),
                "RestaurantOrder", saved.getId());
        return RestaurantOrderDto.from(saved);
    }

    /** Moves a whole table bill to another free table. */
    @Transactional
    public RestaurantOrderDto moveTable(Long orderId, Long tableId) {
        RestaurantOrder order = open(orderId);
        if (order.getOrderType() != RestaurantOrderType.DINE_IN || order.getTable() == null) throw ApiException.badRequest("Only a table's bill can be moved to another table");
        if (tableId == null) throw ApiException.badRequest("Choose the table");
        DiningTable to = tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound("Table not found: " + tableId));
        if (to.getId().equals(order.getTable().getId())) throw ApiException.badRequest("The bill is already at " + to.getName());
        if (to.getStatus() != TableStatus.AVAILABLE
                || !orderRepository.findByTableIdAndStatusOrderByIdAsc(to.getId(), RestaurantOrderStatus.OPEN).isEmpty()) {
            throw ApiException.badRequest("Table " + to.getName() + " is not free");
        }
        DiningTable from = order.getTable();
        String fromName = from.getName();
        order.setTable(to);
        to.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(to);
        orderRepository.save(order);
        releaseTableIfIdle(from, order.getId());
        auditLogService.record("RESTAURANT_TABLE_MOVED", "Moved the bill from table " + fromName + " to table " + to.getName(), "RestaurantOrder", order.getId());
        return RestaurantOrderDto.from(order);
    }

    /** Records a print of a settled bill's receipt; from the second print the receipt is a copy and the copy is logged. */
    @Transactional
    public RestaurantOrderDto recordPrint(Long orderId) {
        RestaurantOrder order = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found: " + orderId));
        if (order.getStatus() != RestaurantOrderStatus.CLOSED) throw ApiException.badRequest("Only a settled bill has a receipt");
        order.setPrintCount(order.getPrintCount() + 1);
        RestaurantOrder saved = orderRepository.save(order);
        if (saved.getPrintCount() > 1) {
            RestaurantVoidLog log = new RestaurantVoidLog();
            log.setEntryType("REPRINT");
            log.setOrderId(saved.getId());
            log.setOrderLabel(saved.displayLabel());
            log.setDescription("Receipt " + saved.getTicketNumber());
            log.setQuantity(saved.getPrintCount());
            log.setAmount(RestaurantOrderCalculator.total(saved));
            log.setReason("Receipt copy " + (saved.getPrintCount() - 1));
            log.setByUser(RestaurantAccess.username());
            voidLogRepository.save(log);
            auditLogService.record("RESTAURANT_RECEIPT_REPRINTED", "Printed a copy of receipt " + saved.getTicketNumber(), "RestaurantOrder", saved.getId());
        }
        return RestaurantOrderDto.from(saved);
    }
}
