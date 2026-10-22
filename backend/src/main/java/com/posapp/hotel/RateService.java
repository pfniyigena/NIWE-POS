package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.QuoteDto;
import com.posapp.hotel.dto.HotelDtos.QuoteNightDto;
import com.posapp.hotel.dto.HotelDtos.RatePlanDto;
import com.posapp.hotel.dto.HotelDtos.RateRuleDto;
import com.posapp.hotel.dto.HotelRequests.RatePlanRequest;
import com.posapp.hotel.dto.HotelRequests.RateRuleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The hotel's price list: rate rules (seasons, weekends, long stays), rate plans (room only, bed and breakfast, corporate), the night-by-night
 * price of a stay, the quote shown while booking and the rate calendar. A booking keeps the prices it was sold at, so changing a rule never
 * changes a booking that already exists.
 */
@Service
public class RateService {

    private static final Set<String> DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final int MAX_CALENDAR_DAYS = 92;

    /** One priced night: the room price (before tax and extra-guest charge) and the rule that set it, if any. */
    public record NightPrice(LocalDate date, BigDecimal rate, String ruleName) {
    }

    private final RatePlanRepository planRepository;
    private final RateRuleRepository ruleRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final HotelSettingsService settingsService;
    private final AuditLogService auditLogService;

    public RateService(RatePlanRepository planRepository, RateRuleRepository ruleRepository, RoomTypeRepository roomTypeRepository,
                       BookingRepository bookingRepository, RoomRepository roomRepository, HotelSettingsService settingsService,
                       AuditLogService auditLogService) {
        this.roomRepository = roomRepository;
        this.planRepository = planRepository;
        this.ruleRepository = ruleRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.bookingRepository = bookingRepository;
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- pricing

    /**
     * The price of every night in [from, to). Base = the room's own rate if it has one, else the room type's rate. The winning rule for the night
     * (see {@link RateRule}) then replaces it (FIXED — not for a room with its own rate), or moves it by a percent or an amount. The rate plan,
     * if any, moves the result again. Never below zero; rounded to cents.
     */
    @Transactional(readOnly = true)
    public List<NightPrice> price(RoomType type, Room room, RatePlan plan, LocalDate from, LocalDate to) {
        int stay = (int) Math.max(1, ChronoUnit.DAYS.between(from, to));
        List<RateRule> rules = ruleRepository.findByActiveTrue().stream()
                .filter(r -> r.getRoomType() == null || r.getRoomType().getId().equals(type.getId()))
                .filter(r -> r.getMinNights() <= stay).toList();
        boolean ownRate = room != null && room.getRateOverride() != null;
        BigDecimal base = ownRate ? room.getRateOverride() : type.getBaseRate();
        List<NightPrice> out = new ArrayList<>();
        for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
            final LocalDate n = night;
            RateRule rule = rules.stream().filter(r -> r.coversDay(n)).max(RateService::precedence).orElse(null);
            BigDecimal rate = base;
            String label = null;
            if (rule != null) {
                switch (rule.getMode()) {
                    case FIXED -> {
                        if (!ownRate) {
                            rate = rule.getValue();
                            label = rule.getName();
                        }
                    }
                    case PERCENT -> {
                        rate = base.multiply(BigDecimal.ONE.add(rule.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
                        label = rule.getName();
                    }
                    case AMOUNT -> {
                        rate = base.add(rule.getValue());
                        label = rule.getName();
                    }
                }
            }
            if (plan != null) {
                rate = rate.multiply(BigDecimal.ONE.add(nz(plan.getAdjustPercent()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))).add(nz(plan.getAdjustAmount()));
                label = label == null ? plan.getName() : label + " · " + plan.getName();
            }
            if (rate.signum() < 0) rate = BigDecimal.ZERO;
            out.add(new NightPrice(night, HotelFolioService.money(rate), label));
        }
        return out;
    }

    /** Highest priority first; then a rule for one room type over "all room types"; then the one that starts later; then the newer one. */
    private static int precedence(RateRule a, RateRule b) {
        return Comparator.<RateRule>comparingInt(RateRule::getPriority)
                .thenComparingInt(r -> r.getRoomType() != null ? 1 : 0)
                .thenComparing(RateRule::getStartDate)
                .thenComparing(RateRule::getId, Comparator.nullsFirst(Comparator.naturalOrder())).compare(a, b);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** What the stay would cost, night by night, with extra-guest charge and tax — the same figures a booking made now would get. */
    @Transactional(readOnly = true)
    public QuoteDto quote(Long roomTypeId, Long roomId, LocalDate from, LocalDate to, Long ratePlanId, Integer adults, Integer children) {
        if (from == null || to == null || !to.isAfter(from)) throw ApiException.badRequest("Departure must be after arrival");
        if (ChronoUnit.DAYS.between(from, to) > 365) throw ApiException.badRequest("A stay cannot be longer than a year");
        RoomType type = roomTypeRepository.findById(roomTypeId).orElseThrow(() -> ApiException.badRequest("Room type not found: " + roomTypeId));
        Room room = roomId == null ? null : roomRepository.findById(roomId).orElseThrow(() -> ApiException.badRequest("Room not found: " + roomId));
        if (room != null && !room.getRoomType().getId().equals(type.getId())) {
            throw ApiException.badRequest("Room " + room.getNumber() + " is a " + room.getRoomType().getName() + ", not a " + type.getName());
        }
        RatePlan plan = ratePlanId == null ? null : planRepository.findById(ratePlanId).orElseThrow(() -> ApiException.badRequest("Rate plan not found: " + ratePlanId));
        long nights = ChronoUnit.DAYS.between(from, to);
        if (plan != null && nights < plan.getMinNights()) {
            throw ApiException.badRequest("The rate plan '" + plan.getName() + "' needs a stay of at least " + plan.getMinNights() + " nights");
        }
        int guests = (adults == null ? 1 : adults) + (children == null ? 0 : children);
        BigDecimal extra = HotelFolioService.money(nz(type.getExtraPersonRate()).multiply(BigDecimal.valueOf(Math.max(0, guests - type.getBaseOccupancy()))));
        BigDecimal pct = settingsService.taxPercent();
        List<QuoteNightDto> items = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal rates = BigDecimal.ZERO;
        for (NightPrice p : price(type, room, plan, from, to)) {
            BigDecimal n = HotelFolioService.money(p.rate().add(extra));
            BigDecimal t = HotelFolioService.taxFor(n, pct);
            items.add(new QuoteNightDto(p.date(), p.rate(), extra, n, t, n.add(t), p.ruleName()));
            net = net.add(n);
            tax = tax.add(t);
            rates = rates.add(p.rate());
        }
        return new QuoteDto(type.getId(), type.getName(), from, to, (int) nights, plan == null ? null : plan.getId(), plan == null ? null : plan.getName(),
                items, net, tax, net.add(tax), rates.divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP));
    }

    /** The nightly room price for every day in [from, to] for one room type (priced as one-night stays, so long-stay rules are not shown). */
    @Transactional(readOnly = true)
    public List<NightPrice> calendarPrices(RoomType type, LocalDate from, LocalDate toInclusive, RatePlan plan) {
        if (from == null || toInclusive == null || toInclusive.isBefore(from)) throw ApiException.badRequest("Choose a date range");
        if (ChronoUnit.DAYS.between(from, toInclusive) + 1 > MAX_CALENDAR_DAYS) throw ApiException.badRequest("Choose a range of at most " + MAX_CALENDAR_DAYS + " days");
        List<NightPrice> out = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
            out.addAll(price(type, null, plan, day, day.plusDays(1)));
        }
        return out;
    }

    RoomType type(Long id) {
        return roomTypeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room type not found: " + id));
    }

    RatePlan planOrNull(Long id) {
        if (id == null) return null;
        return planRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Rate plan not found: " + id));
    }

    // ---------------------------------------------------------------- rate plans

    @Transactional(readOnly = true)
    public List<RatePlanDto> listPlans() {
        return planRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public RatePlanDto createPlan(RatePlanRequest r) {
        String name = r.name().trim();
        if (planRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("A rate plan named '" + name + "' already exists");
        RatePlan plan = apply(new RatePlan(), r, name);
        RatePlan saved = planRepository.save(plan);
        auditLogService.record("RATE_PLAN_CREATED", "Created rate plan " + saved.getName(), "RatePlan", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public RatePlanDto updatePlan(Long id, RatePlanRequest r) {
        RatePlan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Rate plan not found: " + id));
        String name = r.name().trim();
        if (planRepository.existsByNameIgnoreCaseAndIdNot(name, id)) throw ApiException.conflict("A rate plan named '" + name + "' already exists");
        RatePlan saved = planRepository.save(apply(plan, r, name));
        auditLogService.record("RATE_PLAN_UPDATED", "Updated rate plan " + saved.getName(), "RatePlan", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deletePlan(Long id) {
        RatePlan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Rate plan not found: " + id));
        if (bookingRepository.countByRatePlanId(id) > 0) {
            throw ApiException.conflict("Bookings were sold under this plan — set it inactive instead of deleting it");
        }
        planRepository.delete(plan);
        auditLogService.record("RATE_PLAN_DELETED", "Deleted rate plan " + plan.getName(), "RatePlan", id);
    }

    private RatePlan apply(RatePlan plan, RatePlanRequest r, String name) {
        BigDecimal pct = r.adjustPercent() == null ? BigDecimal.ZERO : r.adjustPercent();
        BigDecimal amt = r.adjustAmount() == null ? BigDecimal.ZERO : r.adjustAmount();
        if (pct.compareTo(BigDecimal.valueOf(-100)) <= 0 || pct.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw ApiException.badRequest("The percentage must be between -100 and 1000");
        }
        int min = r.minNights() == null ? 1 : r.minNights();
        if (min < 1 || min > 365) throw ApiException.badRequest("Minimum nights must be between 1 and 365");
        plan.setName(name);
        plan.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        plan.setAdjustPercent(pct.setScale(2, RoundingMode.HALF_UP));
        plan.setAdjustAmount(HotelFolioService.money(amt));
        plan.setMinNights(min);
        plan.setActive(r.active() == null || r.active());
        return plan;
    }

    private RatePlanDto toDto(RatePlan p) {
        return new RatePlanDto(p.getId(), p.getName(), p.getDescription(), p.getAdjustPercent(), p.getAdjustAmount(), p.getMinNights(), p.isActive(),
                bookingRepository.countByRatePlanId(p.getId()));
    }

    // ---------------------------------------------------------------- rate rules

    @Transactional(readOnly = true)
    public List<RateRuleDto> listRules() {
        return ruleRepository.findAllByOrderByStartDateDescIdDesc().stream().map(RateService::toDto).toList();
    }

    @Transactional
    public RateRuleDto createRule(RateRuleRequest r) {
        RateRule saved = ruleRepository.save(apply(new RateRule(), r));
        auditLogService.record("RATE_RULE_CREATED", "Created rate rule " + saved.getName(), "RateRule", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public RateRuleDto updateRule(Long id, RateRuleRequest r) {
        RateRule rule = ruleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Rate rule not found: " + id));
        RateRule saved = ruleRepository.save(apply(rule, r));
        auditLogService.record("RATE_RULE_UPDATED", "Updated rate rule " + saved.getName(), "RateRule", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteRule(Long id) {
        RateRule rule = ruleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Rate rule not found: " + id));
        ruleRepository.delete(rule);
        auditLogService.record("RATE_RULE_DELETED", "Deleted rate rule " + rule.getName(), "RateRule", id);
    }

    private RateRule apply(RateRule rule, RateRuleRequest r) {
        RateRuleMode mode;
        try {
            mode = RateRuleMode.valueOf(r.mode().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown rule type: " + r.mode() + " (use FIXED, PERCENT or AMOUNT)");
        }
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) throw ApiException.badRequest("The end date cannot be before the start date");
        BigDecimal value = r.value();
        if (mode == RateRuleMode.FIXED && value.signum() < 0) throw ApiException.badRequest("A fixed rate cannot be negative");
        if (mode == RateRuleMode.PERCENT && (value.compareTo(BigDecimal.valueOf(-100)) <= 0 || value.compareTo(BigDecimal.valueOf(1000)) > 0)) {
            throw ApiException.badRequest("The percentage must be between -100 and 1000");
        }
        int min = r.minNights() == null ? 1 : r.minNights();
        if (min < 1 || min > 365) throw ApiException.badRequest("Minimum nights must be between 1 and 365");
        int priority = r.priority() == null ? 0 : r.priority();
        if (priority < -1000 || priority > 1000) throw ApiException.badRequest("Priority must be between -1000 and 1000");
        RoomType type = r.roomTypeId() == null ? null : roomTypeRepository.findById(r.roomTypeId()).orElseThrow(() -> ApiException.badRequest("Room type not found: " + r.roomTypeId()));
        rule.setName(r.name().trim());
        rule.setRoomType(type);
        rule.setStartDate(r.startDate());
        rule.setEndDate(r.endDate());
        rule.setDaysOfWeek(normaliseDays(r.daysOfWeek()));
        rule.setMode(mode);
        rule.setValue(value.setScale(2, RoundingMode.HALF_UP));
        rule.setMinNights(min);
        rule.setPriority(priority);
        rule.setActive(r.active() == null || r.active());
        return rule;
    }

    private static String normaliseDays(String days) {
        if (days == null || days.isBlank()) return null;
        List<String> out = new ArrayList<>();
        for (String d : days.split(",")) {
            String code = d.trim().toUpperCase();
            if (code.isEmpty()) continue;
            if (!DAYS.contains(code)) throw ApiException.badRequest("Unknown day: " + d.trim() + " (use MON, TUE, WED, THU, FRI, SAT, SUN)");
            if (!out.contains(code)) out.add(code);
        }
        return out.isEmpty() || out.size() == 7 ? null : String.join(",", out);
    }

    private static RateRuleDto toDto(RateRule r) {
        return new RateRuleDto(r.getId(), r.getName(), r.getRoomType() == null ? null : r.getRoomType().getId(), r.getRoomType() == null ? null : r.getRoomType().getName(),
                r.getStartDate(), r.getEndDate(), r.getDaysOfWeek(), r.getMode().name(), r.getValue(), r.getMinNights(), r.getPriority(), r.isActive());
    }
}
