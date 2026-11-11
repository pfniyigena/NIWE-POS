package com.posapp.restaurant;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.hotel.HotelClock;
import com.posapp.restaurant.dto.AddRestaurantLineRequest;
import com.posapp.restaurant.dto.OnlineOrderingDtos.*;
import com.posapp.restaurant.dto.OpenOffPremiseOrderRequest;
import com.posapp.restaurant.dto.OpenTableOrderRequest;
import com.posapp.settings.StoreSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orders from the QR menu at the table and from the takeaway / delivery page. A guest's order waits in {@link RestaurantOnlineOrder}; staff accept it
 * (or it is accepted by itself for a table, if the restaurant chose that) and only then is it put on a real order and sent to the kitchen. Nothing a
 * stranger sends can change a bill that is already open.
 */
@Service
public class RestaurantOnlineOrderService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_LINES = 40;
    private static final int MAX_QUANTITY = 20;
    private static final int MAX_TOTAL_QUANTITY = 60;
    private static final int MAX_TABLE_PENDING = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantOnlineOrderRepository repository;
    private final RestaurantDeliveryZoneRepository zoneRepository;
    private final DiningTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final RestaurantOptionGroupRepository optionGroupRepository;
    private final RestaurantOrderService orderService;
    private final RestaurantOrderRepository orderRepository;
    private final RestaurantSettingsService settingsService;
    private final StoreSettingsService storeSettingsService;
    private final HotelClock clock;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    public RestaurantOnlineOrderService(RestaurantOnlineOrderRepository repository, RestaurantDeliveryZoneRepository zoneRepository,
                                        DiningTableRepository tableRepository, MenuItemRepository menuItemRepository, MenuCategoryRepository categoryRepository,
                                        RestaurantOptionGroupRepository optionGroupRepository, RestaurantOrderService orderService, RestaurantOrderRepository orderRepository,
                                        RestaurantSettingsService settingsService, StoreSettingsService storeSettingsService, HotelClock clock, MailService mailService,
                                        AuditLogService auditLogService) {
        this.repository = repository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.settingsService = settingsService;
        this.storeSettingsService = storeSettingsService;
        this.clock = clock;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ helpers

    private static String digits(String phone) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    private String restaurantName() {
        String n = storeSettingsService.getSettingsEntity().getBusinessName();
        return n == null || n.isBlank() ? I18n.t("the restaurant") : n;
    }

    private boolean openNow(RestaurantSettings s) {
        LocalTime now = clock.now().toLocalTime();
        return !now.isBefore(LocalTime.parse(s.getOpenTime())) && now.isBefore(LocalTime.parse(s.getCloseTime()));
    }

    private static String clean(String v, int max, String what, boolean required) {
        String t = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            if (required) throw ApiException.badRequest(what + I18n.t(" is required"));
            return null;
        }
        if (t.length() > max) throw ApiException.badRequest(what + I18n.t(" is too long"));
        return t;
    }

    private static String newCode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) b.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return b.toString();
    }

    private static String unavailableReason(MenuItem item) {
        if (item.getProduct() == null || !item.isActive() || item.getCategory() == null || !item.getCategory().isActive()) return I18n.t("Not on the menu");
        if (item.isSoldOut()) return item.getSoldOutReason() == null || item.getSoldOutReason().isBlank() ? I18n.t("Sold out") : I18n.t("Sold out: ") + item.getSoldOutReason();
        return null;
    }

    private static BigDecimal priceOf(MenuItem item) {
        return item.getPriceOverride() != null ? item.getPriceOverride() : item.getProduct().getUnitPrice();
    }

    // ------------------------------------------------------------------ the guest's menu

    @Transactional(readOnly = true)
    public PublicMenu menu(String tableToken) {
        RestaurantSettings s = settingsService.entity();
        DiningTable table = null;
        String mode = null;
        if (tableToken != null && !tableToken.isBlank()) {
            table = tableRepository.findByQrToken(tableToken.trim()).orElseThrow(() -> ApiException.notFound(I18n.t("This QR code is not valid any more. Please ask a member of staff.")));
            if (!s.isQrEnabled()) throw ApiException.badRequest(I18n.t("Ordering from the table is not switched on. Please ask a member of staff."));
            if (!table.isActive()) throw ApiException.badRequest(I18n.t("This table is not in use. Please ask a member of staff."));
            mode = "TABLE";
        } else if (!s.isOnlineEnabled() || (!s.isOnlineTakeaway() && !s.isOnlineDelivery())) {
            throw ApiException.badRequest(I18n.t("Online ordering is not available right now."));
        }
        Map<Long, List<PublicOptionGroup>> groups = new HashMap<>();
        List<MenuItem> all = menuItemRepository.findAllByOrderBySortOrderAsc();
        if (!all.isEmpty()) {
            for (RestaurantOptionGroup g : optionGroupRepository.findByMenuItemIdInOrderBySortOrderAscIdAsc(all.stream().map(MenuItem::getId).toList())) {
                if (!g.isActive()) continue;
                List<PublicOption> opts = g.getOptions().stream().filter(RestaurantOption::isActive).map(o -> new PublicOption(o.getId(), o.getName(), o.getPriceDelta())).toList();
                if (opts.isEmpty()) continue;
                groups.computeIfAbsent(g.getMenuItem().getId(), k -> new ArrayList<>()).add(new PublicOptionGroup(g.getId(), g.getName(), g.isRequired(), g.getMaxSelect(), opts));
            }
        }
        List<PublicCategory> categories = new ArrayList<>();
        for (MenuCategory c : categoryRepository.findAllByOrderBySortOrderAscNameAsc()) {
            if (!c.isActive()) continue;
            List<PublicItem> items = new ArrayList<>();
            for (MenuItem i : all) {
                if (i.getCategory() == null || !i.getCategory().getId().equals(c.getId()) || !i.isActive() || i.getProduct() == null) continue;
                String why = unavailableReason(i);
                items.add(new PublicItem(i.getId(), i.getProduct().getName(), priceOf(i), i.getAllergens(), why == null, why, groups.getOrDefault(i.getId(), List.of())));
            }
            if (!items.isEmpty()) categories.add(new PublicCategory(c.getId(), c.getName(), items));
        }
        List<PublicZone> zones = zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(RestaurantDeliveryZone::isActive)
                .map(z -> new PublicZone(z.getId(), z.getName(), z.getFee(), z.getMinOrder(), z.getExtraMinutes())).toList();
        boolean tableMode = table != null;
        return new PublicMenu(restaurantName(), s.getOnlineMessage(), s.isOnlinePaused(), tableMode || openNow(s), s.getOpenTime(), s.getCloseTime(), mode,
                table == null ? null : table.getName(), tableMode, !tableMode && s.isOnlineTakeaway(), !tableMode && s.isOnlineDelivery(), s.getOnlineMinOrder(),
                s.getOnlinePrepMinutes(), categories, tableMode ? List.of() : zones);
    }

    // ------------------------------------------------------------------ placing an order

    private record Prepared(MenuItem item, BigDecimal quantity, String notes, List<RestaurantOption> options, BigDecimal unit) {
    }

    @Transactional
    public Track place(PublicOrderRequest in) {
        RestaurantSettings s = settingsService.entity();
        if (in.getWebsite() != null && !in.getWebsite().isBlank()) throw ApiException.badRequest(I18n.t("The order could not be placed"));
        String mode = in.getMode() == null ? "" : in.getMode().trim().toUpperCase();
        if (!Set.of("TABLE", "TAKEAWAY", "DELIVERY").contains(mode)) throw ApiException.badRequest(I18n.t("Choose how you want to order"));
        if (s.isOnlinePaused()) {
            throw ApiException.badRequest(I18n.t("We are not taking online orders right now.") + (s.getOnlineMessage() == null ? "" : " " + s.getOnlineMessage()));
        }
        DiningTable table = null;
        RestaurantDeliveryZone zone = null;
        String name = clean(in.getCustomerName(), 80, I18n.t("Your name"), !mode.equals("TABLE"));
        String phone = clean(in.getCustomerPhone(), 40, I18n.t("Your phone number"), !mode.equals("TABLE"));
        if (phone != null && (digits(phone).length() < 7 || phone.replaceAll("[0-9+\\-() ]", "").length() > 0)) throw ApiException.badRequest(I18n.t("Enter a phone number we can call"));
        String email = clean(in.getCustomerEmail(), 160, I18n.t("Your e-mail address"), false);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest(I18n.t("That e-mail address does not look right"));
        String address = null;
        switch (mode) {
            case "TABLE" -> {
                if (!s.isQrEnabled()) throw ApiException.badRequest(I18n.t("Ordering from the table is not switched on"));
                table = tableRepository.findByQrToken(in.getTableToken() == null ? "" : in.getTableToken().trim())
                        .orElseThrow(() -> ApiException.notFound(I18n.t("This QR code is not valid any more. Please ask a member of staff.")));
                if (!table.isActive()) throw ApiException.badRequest(I18n.t("This table is not in use"));
                if (repository.countByStatusAndTableId("PENDING", table.getId()) >= MAX_TABLE_PENDING) {
                    throw ApiException.badRequest(I18n.t("Several orders for this table are still waiting. Please ask a member of staff."));
                }
                if (name == null) name = table.getName();
            }
            case "TAKEAWAY" -> {
                if (!s.isOnlineEnabled() || !s.isOnlineTakeaway()) throw ApiException.badRequest(I18n.t("Takeaway orders are not available right now"));
                assertOpen(s);
            }
            default -> {
                if (!s.isOnlineEnabled() || !s.isOnlineDelivery()) throw ApiException.badRequest(I18n.t("Delivery orders are not available right now"));
                assertOpen(s);
                address = clean(in.getCustomerAddress(), 240, I18n.t("The delivery address"), true);
                List<RestaurantDeliveryZone> zones = zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(RestaurantDeliveryZone::isActive).toList();
                if (!zones.isEmpty()) {
                    if (in.getZoneId() == null) throw ApiException.badRequest(I18n.t("Choose your delivery area"));
                    zone = zones.stream().filter(z -> z.getId().equals(in.getZoneId())).findFirst().orElseThrow(() -> ApiException.badRequest(I18n.t("We do not deliver to that area")));
                }
            }
        }
        if (!mode.equals("TABLE") && repository.findByStatusOrderByCreatedAtAsc("PENDING").stream().filter(o -> digits(o.getCustomerPhone()).equals(digits(in.getCustomerPhone()))).count() >= s.getOnlineMaxPending()) {
            throw ApiException.badRequest(I18n.t("You already have ") + s.getOnlineMaxPending() + I18n.t(" orders waiting for an answer. Please wait for them or call us."));
        }

        List<PublicLine> lines = in.getLines() == null ? List.of() : in.getLines();
        if (lines.isEmpty()) throw ApiException.badRequest(I18n.t("Add something to your order first"));
        if (lines.size() > MAX_LINES) throw ApiException.badRequest(I18n.t("That is too many different dishes for one order"));
        BigDecimal totalQty = BigDecimal.ZERO;
        List<Prepared> prepared = new ArrayList<>();
        BigDecimal itemsGross = BigDecimal.ZERO;
        for (PublicLine l : lines) {
            if (l.getMenuItemId() == null) throw ApiException.badRequest(I18n.t("A dish in your order is missing"));
            if (l.getQuantity().compareTo(BigDecimal.ONE) < 0 || l.getQuantity().compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) throw ApiException.badRequest(I18n.t("Quantities must be between 1 and ") + MAX_QUANTITY);
            totalQty = totalQty.add(l.getQuantity());
            MenuItem item = menuItemRepository.findById(l.getMenuItemId()).orElseThrow(() -> ApiException.badRequest(I18n.t("A dish in your order is no longer on the menu")));
            String why = unavailableReason(item);
            if (why != null) throw ApiException.badRequest(item.getProduct() != null ? item.getProduct().getName() + ": " + why.toLowerCase() : I18n.t("A dish in your order is no longer on the menu"));
            List<RestaurantOption> picked = orderService.pickOptions(item.getId(), l.getOptionIds());
            BigDecimal opt = picked.stream().map(RestaurantOption::getPriceDelta).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unit = priceOf(item).add(opt);
            itemsGross = itemsGross.add(unit.multiply(l.getQuantity()));
            prepared.add(new Prepared(item, l.getQuantity(), clean(l.getNotes(), 200, I18n.t("A note"), false), picked, unit));
        }
        if (totalQty.compareTo(BigDecimal.valueOf(MAX_TOTAL_QUANTITY)) > 0) throw ApiException.badRequest(I18n.t("That is too many items for one order. Please call us for a large order."));

        BigDecimal fee = BigDecimal.ZERO;
        if (!mode.equals("TABLE")) {
            BigDecimal min = s.getOnlineMinOrder();
            if (zone != null && zone.getMinOrder().compareTo(min) > 0) min = zone.getMinOrder();
            if (itemsGross.compareTo(min) < 0) throw ApiException.badRequest(I18n.t("The minimum order is ") + min.stripTrailingZeros().toPlainString() + I18n.t(" (you have ") + itemsGross.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
            if (zone != null) fee = zone.getFee();
        }

        RestaurantOnlineOrder o = new RestaurantOnlineOrder();
        o.setMode(mode);
        o.setSource(mode.equals("TABLE") ? "QR" : "WEB");
        o.setCustomerName(name);
        o.setCustomerPhone(phone);
        o.setCustomerEmail(email);
        o.setCustomerAddress(address);
        o.setNotes(clean(in.getNotes(), 300, I18n.t("The note"), false));
        if (table != null) {
            o.setTableId(table.getId());
            o.setTableName(table.getName());
        }
        if (zone != null) {
            o.setZoneId(zone.getId());
            o.setZoneName(zone.getName());
        }
        o.setDeliveryFee(fee);
        for (Prepared p : prepared) {
            RestaurantOnlineOrderLine line = new RestaurantOnlineOrderLine();
            line.setOnlineOrder(o);
            line.setMenuItemId(p.item().getId());
            line.setName(p.item().getProduct().getName());
            line.setQuantity(p.quantity());
            line.setNotes(p.notes());
            line.setOptionIds(p.options().stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(",")));
            line.setOptionNames(p.options().stream().map(RestaurantOption::getName).collect(Collectors.joining(", ")));
            line.setUnitPrice(p.unit().setScale(2, RoundingMode.HALF_UP));
            line.setLineTotal(p.unit().multiply(p.quantity()).setScale(2, RoundingMode.HALF_UP));
            o.getLines().add(line);
        }
        BigDecimal[] est = estimate(mode, prepared, fee);
        o.setEstimatedSubtotal(est[0]);
        o.setEstimatedTotal(est[1]);
        String code;
        do {
            code = newCode();
        } while (repository.findByCode(code).isPresent());
        o.setCode(code);
        RestaurantOnlineOrder saved = repository.save(o);
        auditLogService.record("RESTAURANT_ONLINE_ORDER_PLACED", "Online order " + code + " (" + mode.toLowerCase() + ") from " + (name == null ? "a guest" : name), "RestaurantOnlineOrder", saved.getId());
        if (mode.equals("TABLE") && s.isOnlineAutoAcceptTables()) {
            try {
                acceptInternal(saved, null, null, true, "automatic");
            } catch (RuntimeException e) {
                // stays pending for staff to look at
            }
        }
        return track(saved);
    }

    private void assertOpen(RestaurantSettings s) {
        if (!openNow(s)) throw ApiException.badRequest(I18n.t("We take online orders between ") + s.getOpenTime() + " and " + s.getCloseTime());
    }

    /** Subtotal and total the way the bill will be worked out: tax, service charge and rounding included. */
    private BigDecimal[] estimate(String mode, List<Prepared> prepared, BigDecimal fee) {
        RestaurantOrder tmp = new RestaurantOrder();
        tmp.setOrderType(switch (mode) {
            case "TAKEAWAY" -> RestaurantOrderType.TAKEAWAY;
            case "DELIVERY" -> RestaurantOrderType.DELIVERY;
            default -> RestaurantOrderType.DINE_IN;
        });
        orderService.applyPricingRules(tmp);
        for (Prepared p : prepared) {
            RestaurantOrderLine l = new RestaurantOrderLine();
            l.setUnitPrice(priceOf(p.item()));
            l.setOptionsPrice(p.unit().subtract(priceOf(p.item())));
            l.setQuantity(p.quantity());
            var tax = p.item().getProduct().getTaxRate();
            if (tax != null) {
                l.setTaxCode(tax.getCode());
                l.setTaxRatePercent(tax.getRatePercent());
                l.setTaxInclusive(tax.isInclusive());
            }
            tmp.getLines().add(l);
        }
        if (fee.signum() > 0) {
            RestaurantOrderLine f = new RestaurantOrderLine();
            f.setUnitPrice(fee);
            f.setQuantity(BigDecimal.ONE);
            tmp.getLines().add(f);
        }
        return new BigDecimal[]{RestaurantOrderCalculator.subtotal(tmp), RestaurantOrderCalculator.total(tmp)};
    }

    // ------------------------------------------------------------------ tracking

    @Transactional(readOnly = true)
    public Track trackByCode(String code) {
        return track(find(code));
    }

    private RestaurantOnlineOrder find(String code) {
        return repository.findByCode(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> ApiException.notFound(I18n.t("We could not find that order. Check the code.")));
    }

    private Track track(RestaurantOnlineOrder o) {
        String stage;
        String text;
        BigDecimal subtotal = o.getEstimatedSubtotal();
        BigDecimal total = o.getEstimatedTotal();
        RestaurantOrder real = o.getRestaurantOrderId() == null ? null : orderRepository.findById(o.getRestaurantOrderId()).orElse(null);
        switch (o.getStatus()) {
            case "PENDING" -> {
                stage = "WAITING";
                text = I18n.t("Waiting for the restaurant to confirm your order");
            }
            case "REJECTED" -> {
                stage = "REJECTED";
                text = I18n.t("Sorry, the restaurant could not take this order");
            }
            case "CANCELLED" -> {
                stage = "CANCELLED";
                text = I18n.t("You cancelled this order");
            }
            case "EXPIRED" -> {
                stage = "EXPIRED";
                text = I18n.t("Nobody answered in time. Please call us or order again");
            }
            default -> {
                stage = "ACCEPTED";
                text = I18n.t("Accepted. It is in the kitchen queue");
                if (real != null) {
                    if (real.getStatus() == RestaurantOrderStatus.CANCELLED) {
                        stage = "CANCELLED";
                        text = I18n.t("This order was cancelled by the restaurant");
                    } else if (real.getStatus() == RestaurantOrderStatus.CLOSED && !o.getMode().equals("TABLE")) {
                        stage = "COMPLETED";
                        text = I18n.t("Thank you! Your order is complete");
                    } else if (real.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
                        stage = "COMPLETED";
                        text = I18n.t("Delivered. Thank you!");
                    } else {
                        Set<Long> mine = ids(o.getRestaurantLineIds());
                        List<RestaurantOrderLine> theirs = real.getLines().stream().filter(l -> mine.isEmpty() ? l.getProduct() != null : mine.contains(l.getId())).toList();
                        if (!theirs.isEmpty()) {
                            boolean allServed = theirs.stream().allMatch(l -> l.getKitchenStatus() == KitchenStatus.SERVED);
                            boolean allDone = theirs.stream().allMatch(l -> l.getKitchenStatus() == KitchenStatus.READY || l.getKitchenStatus() == KitchenStatus.SERVED);
                            boolean anyCooking = theirs.stream().anyMatch(l -> l.getKitchenStatus() == KitchenStatus.PREPARING);
                            if (allServed) {
                                stage = real.getStatus() == RestaurantOrderStatus.CLOSED ? "COMPLETED" : "SERVED";
                                text = o.getMode().equals("TABLE") ? I18n.t("Served. Enjoy your meal!") : I18n.t("Handed over. Enjoy your meal!");
                            } else if (real.getDeliveryStatus() == DeliveryStatus.OUT_FOR_DELIVERY) {
                                stage = "ON_THE_WAY";
                                text = I18n.t("Your order is on its way");
                            } else if (allDone) {
                                stage = "READY";
                                text = switch (o.getMode()) {
                                    case "TABLE" -> I18n.t("Ready. It is coming to your table");
                                    case "TAKEAWAY" -> I18n.t("Ready for pick-up");
                                    default -> I18n.t("Ready. Waiting for the driver");
                                };
                            } else if (anyCooking) {
                                stage = "PREPARING";
                                text = I18n.t("Being prepared now");
                            }
                        }
                        if (real.getStatus() == RestaurantOrderStatus.OPEN && o.getMode().equals("TABLE")) {
                            // the bill is shared with the table, so show the estimate for this request only
                        } else if (real.getStatus() != RestaurantOrderStatus.OPEN || !o.getMode().equals("TABLE")) {
                            subtotal = RestaurantOrderCalculator.subtotal(real);
                            total = RestaurantOrderCalculator.total(real);
                        }
                    }
                }
            }
        }
        String first = o.getCustomerName() == null ? null : o.getCustomerName().split(" ")[0];
        List<TrackLine> lines = o.getLines().stream().map(l -> new TrackLine(l.getName(), l.getQuantity(), l.getOptionNames(), l.getNotes(), l.getLineTotal())).toList();
        return new Track(o.getCode(), o.getMode(), o.getStatus(), stage, text, o.getEtaMinutes(), first, o.getTableName(), o.getZoneName(), o.getDeliveryFee(), subtotal, total,
                o.getRejectReason(), o.getCreatedAt(), lines, restaurantName());
    }

    private static Set<Long> ids(String csv) {
        Set<Long> out = new HashSet<>();
        if (csv != null) for (String p : csv.split(",")) if (!p.isBlank()) out.add(Long.parseLong(p.trim()));
        return out;
    }

    @Transactional
    public Track cancelByGuest(String code) {
        RestaurantOnlineOrder o = find(code);
        if (!o.getStatus().equals("PENDING")) throw ApiException.badRequest(I18n.t("This order has already been answered, so it can no longer be cancelled here. Please call us."));
        o.setStatus("CANCELLED");
        o.setDecidedAt(Instant.now());
        o.setDecidedBy("guest");
        repository.save(o);
        auditLogService.record("RESTAURANT_ONLINE_ORDER_CANCELLED", "Guest cancelled online order " + o.getCode(), "RestaurantOnlineOrder", o.getId());
        return track(o);
    }

    // ------------------------------------------------------------------ the staff queue

    @Transactional(readOnly = true)
    public List<OnlineOrderDto> list(String q, String status, String mode, LocalDate from, LocalDate to) {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(o -> status == null || status.isBlank() || status.equalsIgnoreCase("ALL") || status.equalsIgnoreCase(o.getStatus()))
                .filter(o -> mode == null || mode.isBlank() || mode.equalsIgnoreCase("ALL") || mode.equalsIgnoreCase(o.getMode()))
                .filter(o -> com.posapp.common.ListPaging.between(o.getCreatedAt(), from, to))
                .filter(o -> com.posapp.common.ListPaging.has(q, o.getCode(), o.getCustomerName(), o.getCustomerPhone(), o.getTableName(), o.getCustomerAddress()))
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return repository.countByStatus("PENDING");
    }

    @Transactional(readOnly = true)
    public OnlineOrderDto get(Long id) {
        return dto(getOrThrow(id));
    }

    private RestaurantOnlineOrder getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Online order not found: ") + id));
    }

    private OnlineOrderDto dto(RestaurantOnlineOrder o) {
        String problem = null;
        if (o.getStatus().equals("PENDING")) {
            List<String> bad = new ArrayList<>();
            for (RestaurantOnlineOrderLine l : o.getLines()) {
                MenuItem item = menuItemRepository.findById(l.getMenuItemId()).orElse(null);
                if (item == null || unavailableReason(item) != null) bad.add(l.getName());
            }
            if (!bad.isEmpty()) problem = String.join(", ", bad) + (bad.size() == 1 ? " is" : " are") + I18n.t(" no longer available");
        }
        Instant end = o.getDecidedAt() != null ? o.getDecidedAt() : Instant.now();
        List<TrackLine> lines = o.getLines().stream().map(l -> new TrackLine(l.getName(), l.getQuantity(), l.getOptionNames(), l.getNotes(), l.getLineTotal())).toList();
        return new OnlineOrderDto(o.getId(), o.getCode(), o.getMode(), o.getStatus(), o.getSource(), o.getTableId(), o.getTableName(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerAddress(), o.getZoneName(), o.getDeliveryFee(), o.getNotes(), o.getEstimatedTotal(), o.getEtaMinutes(), o.getCreatedAt(),
                o.getDecidedAt(), o.getDecidedBy(), o.getRejectReason(), o.getRestaurantOrderId(), (int) Duration.between(o.getCreatedAt(), end).toMinutes(), lines, problem);
    }

    @Transactional
    public OnlineOrderDto accept(Long id, AcceptRequest request) {
        RestaurantOnlineOrder o = getOrThrow(id);
        AcceptRequest r = request == null ? new AcceptRequest() : request;
        if (r.getEtaMinutes() != null && (r.getEtaMinutes() < 1 || r.getEtaMinutes() > 480)) throw ApiException.badRequest(I18n.t("The waiting time must be between 1 and 480 minutes"));
        acceptInternal(o, r.getEtaMinutes(), r.getServerId(), r.isSkipUnavailable(), RestaurantAccess.username());
        return dto(repository.findById(id).orElseThrow());
    }

    private void acceptInternal(RestaurantOnlineOrder o, Integer eta, Long serverId, boolean skipUnavailable, String by) {
        if (!o.getStatus().equals("PENDING")) throw ApiException.badRequest(I18n.t("This order is already ") + o.getStatus().toLowerCase());
        RestaurantSettings s = settingsService.entity();
        List<RestaurantOnlineOrderLine> keep = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (RestaurantOnlineOrderLine l : o.getLines()) {
            MenuItem item = menuItemRepository.findById(l.getMenuItemId()).orElse(null);
            String why = item == null ? I18n.t("Not on the menu") : unavailableReason(item);
            if (why == null) {
                keep.add(l);
            } else if (!skipUnavailable) {
                throw ApiException.badRequest(l.getName() + I18n.t(" is no longer available (") + why.toLowerCase() + I18n.t("). Leave it out, or reject the order."));
            } else {
                dropped.add(l.getName());
            }
        }
        if (keep.isEmpty()) throw ApiException.badRequest(I18n.t("Nothing in this order is available any more. Reject it instead."));

        Long orderId;
        Set<Long> before = new HashSet<>();
        if (o.getMode().equals("TABLE")) {
            DiningTable table = tableRepository.findById(o.getTableId()).orElseThrow(() -> ApiException.badRequest(I18n.t("The table was removed")));
            if (!table.isActive()) throw ApiException.badRequest(I18n.t("Table ") + table.getName() + I18n.t(" is not in use"));
            RestaurantOrder base = orderRepository.findByTableIdAndStatusOrderByIdAsc(table.getId(), RestaurantOrderStatus.OPEN).stream()
                    .filter(x -> x.getParentOrderId() == null).findFirst().orElse(null);
            if (base == null) {
                OpenTableOrderRequest open = new OpenTableOrderRequest();
                open.setGuestCount(1);
                open.setNotes("Ordered from the table QR (" + o.getCode() + ")");
                open.setServerId(serverId);
                orderId = orderService.openOrder(table.getId(), open).id();
            } else {
                orderId = base.getId();
                base.getLines().forEach(l -> before.add(l.getId()));
            }
        } else {
            OpenOffPremiseOrderRequest open = new OpenOffPremiseOrderRequest();
            open.setCustomerName(o.getCustomerName());
            open.setCustomerPhone(o.getCustomerPhone());
            open.setCustomerAddress(o.getCustomerAddress());
            open.setNotes(o.getNotes());
            open.setServerId(serverId);
            orderId = (o.getMode().equals("DELIVERY") ? orderService.openDeliveryOrder(open) : orderService.openTakeawayOrder(open)).id();
        }
        for (RestaurantOnlineOrderLine l : keep) {
            AddRestaurantLineRequest add = new AddRestaurantLineRequest();
            add.setMenuItemId(l.getMenuItemId());
            add.setQuantity(l.getQuantity());
            add.setNotes(l.getNotes());
            add.setCourse(1);
            add.setOptionIds(ids(l.getOptionIds()).stream().sorted().toList());
            orderService.addLine(orderId, add);
        }
        RestaurantOrder real = orderRepository.findById(orderId).orElseThrow();
        real.setSource(o.getSource());
        real.setOnlineOrderCode(o.getCode());
        if (o.getMode().equals("TABLE") && real.getNotes() != null && !real.getNotes().contains(o.getCode()) && before.isEmpty()) {
            real.setNotes(real.getNotes() + " (" + o.getCode() + ")");
        }
        List<Long> mine = new ArrayList<>();
        for (RestaurantOrderLine line : real.getLines()) {
            if (before.contains(line.getId())) continue;
            mine.add(line.getId());
            if (line.getKitchenStatus() == KitchenStatus.PENDING) line.moveTo(KitchenStatus.SENT);
        }
        if (o.getDeliveryFee() != null && o.getDeliveryFee().signum() > 0) {
            RestaurantOrderLine fee = new RestaurantOrderLine();
            fee.setOrder(real);
            fee.setDescription("Delivery fee" + (o.getZoneName() == null ? "" : " — " + o.getZoneName()));
            fee.setUnitPrice(o.getDeliveryFee());
            fee.setQuantity(BigDecimal.ONE);
            fee.setCourse(1);
            fee.setUnitCost(BigDecimal.ZERO);
            fee.moveTo(KitchenStatus.SERVED);
            real.getLines().add(fee);
        }
        orderRepository.save(real);
        o.setStatus("ACCEPTED");
        o.setDecidedAt(Instant.now());
        o.setDecidedBy(by);
        o.setRestaurantOrderId(orderId);
        o.setRestaurantLineIds(mine.stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (eta != null) {
            o.setEtaMinutes(eta);
        } else if (!o.getMode().equals("TABLE")) {
            int extra = o.getZoneId() == null ? 0 : zoneRepository.findById(o.getZoneId()).map(RestaurantDeliveryZone::getExtraMinutes).orElse(0);
            o.setEtaMinutes(s.getOnlinePrepMinutes() + extra);
        }
        repository.save(o);
        auditLogService.record("RESTAURANT_ONLINE_ORDER_ACCEPTED", "Accepted online order " + o.getCode() + (dropped.isEmpty() ? "" : " without " + String.join(", ", dropped)), "RestaurantOnlineOrder", o.getId());
        notifyGuest(o, I18n.t("Your order ") + o.getCode() + I18n.t(" has been accepted"),
                I18n.t("Thank you ") + (o.getCustomerName() == null ? "" : o.getCustomerName()) + "! " + restaurantName() + I18n.t(" has accepted your order ") + o.getCode() + "."
                        + (o.getEtaMinutes() == null ? "" : I18n.t(" It should be ready in about ") + o.getEtaMinutes() + " minutes.")
                        + (dropped.isEmpty() ? "" : I18n.t(" We could not include: ") + String.join(", ", dropped) + "."));
    }

    @Transactional
    public OnlineOrderDto reject(Long id, String reason) {
        RestaurantOnlineOrder o = getOrThrow(id);
        if (!o.getStatus().equals("PENDING")) throw ApiException.badRequest(I18n.t("This order is already ") + o.getStatus().toLowerCase());
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 3) throw ApiException.badRequest(I18n.t("Say why the order cannot be taken"));
        if (why.length() > 120) throw ApiException.badRequest(I18n.t("The reason is too long (120 characters at most)"));
        o.setStatus("REJECTED");
        o.setRejectReason(why);
        o.setDecidedAt(Instant.now());
        o.setDecidedBy(RestaurantAccess.username());
        repository.save(o);
        auditLogService.record("RESTAURANT_ONLINE_ORDER_REJECTED", "Rejected online order " + o.getCode() + ": " + why, "RestaurantOnlineOrder", o.getId());
        notifyGuest(o, I18n.t("About your order ") + o.getCode(), I18n.t("Sorry ") + (o.getCustomerName() == null ? "" : o.getCustomerName()) + ", " + restaurantName() + I18n.t(" could not take your order ") + o.getCode() + ": " + why + ".");
        return dto(o);
    }

    /** Requests nobody answered in time lapse, so a guest is never left waiting for ever. */
    @Transactional
    public int expireStale() {
        RestaurantSettings s = settingsService.entity();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(s.getOnlineExpireMinutes()));
        int n = 0;
        for (RestaurantOnlineOrder o : repository.findByStatusAndCreatedAtBefore("PENDING", cutoff)) {
            o.setStatus("EXPIRED");
            o.setDecidedAt(Instant.now());
            o.setDecidedBy("system");
            repository.save(o);
            auditLogService.record("RESTAURANT_ONLINE_ORDER_EXPIRED", "Online order " + o.getCode() + " was not answered in time", "RestaurantOnlineOrder", o.getId());
            n++;
        }
        return n;
    }

    private void notifyGuest(RestaurantOnlineOrder o, String subject, String text) {
        if (o.getCustomerEmail() == null || !mailService.isConfigured()) return;
        try {
            mailService.send(o.getCustomerEmail(), subject, "<p>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>", List.of());
        } catch (RuntimeException e) {
            // an e-mail that cannot be sent never undoes the decision
        }
    }

    // ------------------------------------------------------------------ table QR codes and delivery areas

    @Transactional
    public List<TableQr> qrList() {
        List<TableQr> out = new ArrayList<>();
        for (DiningTable t : tableRepository.findAllByOrderByNameAsc()) {
            if (t.getQrToken() == null) {
                t.setQrToken(DiningTable.newToken());
                tableRepository.save(t);
            }
            out.add(new TableQr(t.getId(), t.getName(), t.getSection(), t.isActive(), t.getQrToken(), "/menu/" + t.getQrToken()));
        }
        return out;
    }

    @Transactional
    public TableQr regenerateQr(Long tableId) {
        DiningTable t = tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound(I18n.t("Table not found: ") + tableId));
        t.setQrToken(DiningTable.newToken());
        tableRepository.save(t);
        auditLogService.record("RESTAURANT_TABLE_QR_RENEWED", "Made a new QR code for table " + t.getName(), "DiningTable", t.getId());
        return new TableQr(t.getId(), t.getName(), t.getSection(), t.isActive(), t.getQrToken(), "/menu/" + t.getQrToken());
    }

    @Transactional(readOnly = true)
    public List<ZoneDto> zones() {
        return zoneRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(RestaurantOnlineOrderService::zoneDto).toList();
    }

    private static ZoneDto zoneDto(RestaurantDeliveryZone z) {
        return new ZoneDto(z.getId(), z.getName(), z.getFee(), z.getMinOrder(), z.getExtraMinutes(), z.isActive(), z.getSortOrder());
    }

    @Transactional
    public ZoneDto saveZone(Long id, ZoneRequest in) {
        RestaurantDeliveryZone z = id == null ? new RestaurantDeliveryZone() : zoneRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery area not found: ") + id));
        if (id == null || in.getName() != null) {
            String n = clean(in.getName(), 80, I18n.t("The area name"), true);
            if (zoneRepository.findAll().stream().anyMatch(x -> !x.getId().equals(z.getId()) && x.getName().equalsIgnoreCase(n))) throw ApiException.conflict(I18n.t("There is already an area called ") + n);
            z.setName(n);
        }
        if (in.getFee() != null) {
            if (in.getFee().signum() < 0 || in.getFee().compareTo(BigDecimal.valueOf(10_000_000)) > 0) throw ApiException.badRequest(I18n.t("The fee must be between 0 and 10,000,000"));
            z.setFee(in.getFee().setScale(2, RoundingMode.HALF_UP));
        }
        if (in.getMinOrder() != null) {
            if (in.getMinOrder().signum() < 0 || in.getMinOrder().compareTo(BigDecimal.valueOf(100_000_000)) > 0) throw ApiException.badRequest(I18n.t("The minimum order must be between 0 and 100,000,000"));
            z.setMinOrder(in.getMinOrder().setScale(2, RoundingMode.HALF_UP));
        }
        if (in.getExtraMinutes() != null) {
            if (in.getExtraMinutes() < 0 || in.getExtraMinutes() > 240) throw ApiException.badRequest(I18n.t("Extra minutes must be between 0 and 240"));
            z.setExtraMinutes(in.getExtraMinutes());
        }
        if (in.getActive() != null) z.setActive(in.getActive());
        if (in.getSortOrder() != null) z.setSortOrder(in.getSortOrder());
        RestaurantDeliveryZone saved = zoneRepository.save(z);
        auditLogService.record("RESTAURANT_DELIVERY_AREA_SAVED", "Saved the delivery area " + saved.getName() + " (fee " + saved.getFee() + ")", "RestaurantDeliveryZone", saved.getId());
        return zoneDto(saved);
    }

    @Transactional
    public void deleteZone(Long id) {
        RestaurantDeliveryZone z = zoneRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery area not found: ") + id));
        boolean used = repository.findAll().stream().anyMatch(o -> id.equals(o.getZoneId()));
        if (used) {
            z.setActive(false);
            zoneRepository.save(z);
        } else {
            zoneRepository.delete(z);
        }
        auditLogService.record("RESTAURANT_DELIVERY_AREA_REMOVED", (used ? "Switched off" : "Removed") + " the delivery area " + z.getName(), "RestaurantDeliveryZone", id);
    }
}
