package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivery time slots (v1.61.0). The shop sets daily windows (each with a limit of orders and the weekdays it exists), dates it does not deliver, how many days
 * ahead a window can be booked and how many hours before it starts it closes. At checkout the customer picks a day and a window (or, unless the shop makes
 * it compulsory, "as soon as possible"). A full window cannot be chosen; two customers cannot take the last place at once (the window is locked while an
 * order is placed). Only delivery orders have a window. Staff can move an order to another window whatever the limit.
 */
@Service
public class OnlineSlotService {

    static final List<String> FREE = List.of("REJECTED", "CANCELLED");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final OnlineDeliverySlotRepository slots;
    private final OnlineClosedDayRepository closed;
    private final OnlineOrderRepository orders;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final AuditLogService auditLogService;

    public OnlineSlotService(OnlineDeliverySlotRepository slots, OnlineClosedDayRepository closed, OnlineOrderRepository orders,
                             OnlineStoreSettingsRepository settingsRepository, AuditLogService auditLogService) {
        this.slots = slots;
        this.closed = closed;
        this.orders = orders;
        this.settingsRepository = settingsRepository;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ shapes

    public record SlotDto(Long id, String start, String end, int maxOrders, String days, boolean active, int sortOrder) {
    }

    public record SlotRequest(@NotNull @Size(min = 4, max = 5) String start, @NotNull @Size(min = 4, max = 5) String end, Integer maxOrders,
                              @Size(max = 7) String days, Boolean active, Integer sortOrder) {
    }

    public record SlotSettings(boolean enabled, boolean required, int daysAhead, int noticeHours) {
    }

    public record ClosedDayDto(Long id, String day, String reason) {
    }

    public record ClosedDayRequest(@NotNull @Size(min = 10, max = 10) String day, @Size(max = 80) String reason) {
    }

    /** why: FULL or SOON when it cannot be chosen. */
    public record Choice(Long id, String start, String end, boolean available, String why) {
    }

    public record Day(String date, List<Choice> slots) {
    }

    public record Board(boolean enabled, boolean required, List<Day> days) {
    }

    /** The window an order takes. */
    public record Pick(LocalDate date, LocalTime start, LocalTime end) {
    }

    // ------------------------------------------------------------------ helpers

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /** The shop's settings row (made when the shop is first opened; defaults if it does not exist yet). */
    private OnlineStoreSettings settings() {
        List<OnlineStoreSettings> all = settingsRepository.findAll();
        return all.isEmpty() ? new OnlineStoreSettings() : all.get(0);
    }

    private static LocalTime time(String s, String what) {
        try {
            String t = s == null ? "" : s.trim();
            if (t.matches("\\d:\\d\\d")) t = "0" + t;
            return LocalTime.parse(t, HM);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(I18n.t("{0}: write the time like 08:30", what));
        }
    }

    private static LocalDate date(String s) {
        try {
            return LocalDate.parse(s == null ? "" : s.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(I18n.t("That date does not look right"));
        }
    }

    private static String hm(LocalTime t) {
        return t.format(HM);
    }

    private static SlotDto dto(OnlineDeliverySlot s) {
        return new SlotDto(s.getId(), hm(s.getStartTime()), hm(s.getEndTime()), s.getMaxOrders(), s.getDays(), s.isActive(), s.getSortOrder());
    }

    static String d(LocalDate day) {
        return day == null ? null : day.toString();
    }

    static String t(LocalTime time) {
        return time == null ? null : hm(time);
    }

    /** "2026-11-24, 08:00-11:00", for papers and staff screens. */
    public static String label(LocalDate day, LocalTime start, LocalTime end) {
        if (day == null || start == null) return "";
        return day + ", " + hm(start) + (end == null ? "" : "-" + hm(end));
    }

    // ------------------------------------------------------------------ staff: windows, closed days, switches

    @Transactional(readOnly = true)
    public List<SlotDto> list() {
        return slots.findAllByOrderBySortOrderAscStartTimeAsc().stream().map(OnlineSlotService::dto).toList();
    }

    @Transactional
    public SlotDto create(SlotRequest in) {
        return save(new OnlineDeliverySlot(), in);
    }

    @Transactional
    public SlotDto update(Long id, SlotRequest in) {
        return save(slots.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery window not found"))), in);
    }

    private SlotDto save(OnlineDeliverySlot s, SlotRequest in) {
        LocalTime a = time(in.start(), I18n.t("Start"));
        LocalTime b = time(in.end(), I18n.t("End"));
        if (!b.isAfter(a)) throw ApiException.badRequest(I18n.t("The window must end after it starts."));
        String days = in.days() == null || in.days().isBlank() ? "1111111" : in.days().trim();
        if (!days.matches("[01]{7}") || !days.contains("1")) throw ApiException.badRequest(I18n.t("Choose at least one day of the week."));
        int max = in.maxOrders() == null ? 0 : in.maxOrders();
        if (max < 0 || max > 1000) throw ApiException.badRequest(I18n.t("The limit must be between 0 and 1000 orders (0 = no limit)."));
        s.setStartTime(a);
        s.setEndTime(b);
        s.setMaxOrders(max);
        s.setDays(days);
        if (in.active() != null) s.setActive(in.active());
        if (in.sortOrder() != null) s.setSortOrder(in.sortOrder());
        OnlineDeliverySlot saved = slots.save(s);
        auditLogService.record("SHOP_DELIVERY_WINDOW_SAVED", "Saved the delivery window " + hm(a) + "-" + hm(b));
        return dto(saved);
    }

    @Transactional
    public void delete(Long id) {
        OnlineDeliverySlot s = slots.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery window not found")));
        slots.delete(s);
        auditLogService.record("SHOP_DELIVERY_WINDOW_DELETED", "Deleted the delivery window " + hm(s.getStartTime()) + "-" + hm(s.getEndTime()));
    }

    @Transactional(readOnly = true)
    public SlotSettings slotSettings() {
        OnlineStoreSettings s = settings();
        return new SlotSettings(s.isSlotsEnabled(), s.isSlotsRequired(), s.getSlotDaysAhead(), s.getSlotNoticeHours());
    }

    @Transactional
    public SlotSettings saveSettings(SlotSettings in) {
        if (in.daysAhead() < 0 || in.daysAhead() > 60) throw ApiException.badRequest(I18n.t("Days ahead must be between 0 and 60."));
        if (in.noticeHours() < 0 || in.noticeHours() > 72) throw ApiException.badRequest(I18n.t("The notice must be between 0 and 72 hours."));
        OnlineStoreSettings s = settings();
        s.setSlotsEnabled(in.enabled());
        s.setSlotsRequired(in.required());
        s.setSlotDaysAhead(in.daysAhead());
        s.setSlotNoticeHours(in.noticeHours());
        settingsRepository.save(s);
        auditLogService.record("SHOP_DELIVERY_WINDOWS_SETTINGS", "Delivery windows " + (in.enabled() ? "on" : "off") + (in.required() ? ", compulsory" : "") + ", " + in.daysAhead() + " days ahead, " + in.noticeHours() + " h notice");
        return slotSettings();
    }

    @Transactional(readOnly = true)
    public List<ClosedDayDto> closedDays() {
        return closed.findAllByDayGreaterThanEqualOrderByDayAsc(LocalDate.now(zone())).stream().map(c -> new ClosedDayDto(c.getId(), c.getDay().toString(), c.getReason())).toList();
    }

    @Transactional
    public ClosedDayDto addClosed(ClosedDayRequest in) {
        LocalDate d = date(in.day());
        if (d.isBefore(LocalDate.now(zone()))) throw ApiException.badRequest(I18n.t("That date is already past."));
        if (closed.existsByDay(d)) throw ApiException.badRequest(I18n.t("That day is already closed."));
        OnlineClosedDay c = new OnlineClosedDay();
        c.setDay(d);
        c.setReason(in.reason() == null || in.reason().isBlank() ? null : in.reason().trim());
        c = closed.save(c);
        auditLogService.record("SHOP_DELIVERY_DAY_CLOSED", "No deliveries on " + d);
        return new ClosedDayDto(c.getId(), d.toString(), c.getReason());
    }

    @Transactional
    public void removeClosed(Long id) {
        OnlineClosedDay c = closed.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Closed day not found")));
        closed.delete(c);
        auditLogService.record("SHOP_DELIVERY_DAY_OPENED", "Deliveries possible again on " + c.getDay());
    }

    // ------------------------------------------------------------------ customer: what can be chosen

    /** The days and windows the customer can see at checkout. Counts are never shown, only whether a window is still free. */
    @Transactional(readOnly = true)
    public Board board() {
        OnlineStoreSettings s = settings();
        if (!s.isSlotsEnabled()) return new Board(false, false, List.of());
        List<OnlineDeliverySlot> all = slots.findAllByOrderBySortOrderAscStartTimeAsc().stream().filter(OnlineDeliverySlot::isActive).toList();
        LocalDateTime now = LocalDateTime.now(zone());
        LocalDate today = now.toLocalDate();
        LocalDate last = today.plusDays(s.getSlotDaysAhead());
        Map<String, Long> load = new HashMap<>();
        for (Object[] r : orders.slotLoad(today, last, FREE)) load.put(r[0] + "|" + r[1], (Long) r[2]);
        java.util.Set<LocalDate> off = new java.util.HashSet<>();
        closed.findAllByDayGreaterThanEqualOrderByDayAsc(today).forEach(c -> off.add(c.getDay()));
        List<Day> days = new ArrayList<>();
        for (LocalDate d = today; !d.isAfter(last); d = d.plusDays(1)) {
            if (off.contains(d)) continue;
            List<Choice> list = new ArrayList<>();
            for (OnlineDeliverySlot sl : all) {
                if (!sl.on(d.getDayOfWeek())) continue;
                String why = null;
                if (LocalDateTime.of(d, sl.getStartTime()).isBefore(now.plusHours(s.getSlotNoticeHours()))) why = "SOON";
                else if (sl.getMaxOrders() > 0 && load.getOrDefault(d + "|" + sl.getStartTime(), 0L) >= sl.getMaxOrders()) why = "FULL";
                list.add(new Choice(sl.getId(), hm(sl.getStartTime()), hm(sl.getEndTime()), why == null, why));
            }
            if (!list.isEmpty()) days.add(new Day(d.toString(), list));
        }
        return new Board(true, s.isSlotsRequired(), days);
    }

    // ------------------------------------------------------------------ placing an order

    /**
     * The window the customer asked for, checked, or null when the order has none ("as soon as possible", or windows are off, or it is a pick-up). Must run
     * in the transaction that saves the order: the window row is locked until then, so the count below cannot go stale.
     */
    @Transactional
    public Pick pick(Long slotId, String dayText, String method) {
        OnlineStoreSettings s = settings();
        if (!s.isSlotsEnabled() || !"DELIVERY".equals(method)) return null;
        if (slotId == null || dayText == null || dayText.isBlank()) {
            if (s.isSlotsRequired()) throw ApiException.badRequest(I18n.t("Choose a delivery day and time"));
            return null;
        }
        OnlineDeliverySlot sl = slots.lockById(slotId).filter(OnlineDeliverySlot::isActive).orElseThrow(() -> ApiException.badRequest(I18n.t("That delivery time is not available. Please choose another.")));
        LocalDate d = date(dayText);
        LocalDateTime now = LocalDateTime.now(zone());
        if (d.isBefore(now.toLocalDate()) || d.isAfter(now.toLocalDate().plusDays(s.getSlotDaysAhead())) || !sl.on(d.getDayOfWeek()) || closed.existsByDay(d)) {
            throw ApiException.badRequest(I18n.t("That delivery time is not available. Please choose another."));
        }
        if (LocalDateTime.of(d, sl.getStartTime()).isBefore(now.plusHours(s.getSlotNoticeHours()))) {
            throw ApiException.badRequest(I18n.t("That delivery time is too soon. Please choose a later one."));
        }
        if (sl.getMaxOrders() > 0 && orders.countBySlotDateAndSlotStartAndStatusNotIn(d, sl.getStartTime(), FREE) >= sl.getMaxOrders()) {
            throw ApiException.badRequest(I18n.t("That delivery time is full. Please choose another."));
        }
        return new Pick(d, sl.getStartTime(), sl.getEndTime());
    }

    /** Staff move an order to a window (or, with no window, back to "as soon as possible"). The limit and the notice do not stop staff, the weekday and the date do. */
    @Transactional(readOnly = true)
    public Pick staffPick(Long slotId, String dayText) {
        if (slotId == null) return null;
        OnlineDeliverySlot sl = slots.findById(slotId).orElseThrow(() -> ApiException.notFound(I18n.t("Delivery window not found")));
        LocalDate d = date(dayText);
        if (d.isBefore(LocalDate.now(zone()))) throw ApiException.badRequest(I18n.t("That date is already past."));
        if (!sl.on(d.getDayOfWeek())) throw ApiException.badRequest(I18n.t("That window does not exist on that day of the week."));
        return new Pick(d, sl.getStartTime(), sl.getEndTime());
    }
}
