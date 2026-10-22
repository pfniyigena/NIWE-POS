package com.posapp.restaurant;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.restaurant.dto.RestaurantSettingsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RestaurantSettingsService {

    private final RestaurantSettingsRepository repository;
    private final AuditLogService auditLogService;

    public RestaurantSettingsService(RestaurantSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Read-write on purpose: the first call on a new database creates the row. */
    @Transactional
    public RestaurantSettings entity() {
        List<RestaurantSettings> all = repository.findAll();
        return all.isEmpty() ? repository.save(new RestaurantSettings()) : all.get(0);
    }

    @Transactional
    public RestaurantSettingsDto get() {
        return RestaurantSettingsDto.from(entity());
    }

    @Transactional
    public RestaurantSettingsDto update(RestaurantSettingsDto in) {
        RestaurantSettings s = entity();
        s.setServiceChargePercent(pct("Service charge", in.serviceChargePercent(), 50));
        s.setServiceChargeDineIn(in.serviceChargeDineIn());
        s.setServiceChargeTakeaway(in.serviceChargeTakeaway());
        s.setServiceChargeDelivery(in.serviceChargeDelivery());
        s.setServiceChargeVatPercent(pct("VAT on the service charge", in.serviceChargeVatPercent(), 100));
        s.setDiscountLimitPercent(pct("Discount limit", in.discountLimitPercent() == null ? BigDecimal.valueOf(10) : in.discountLimitPercent(), 100));
        s.setTipsEnabled(in.tipsEnabled());
        s.setTipPresets(presets(in.tipPresets()));
        BigDecimal unit = in.roundingUnit() == null ? BigDecimal.ZERO : in.roundingUnit();
        if (unit.signum() < 0 || unit.compareTo(BigDecimal.valueOf(100000)) > 0) {
            throw ApiException.badRequest("Rounding must be between 0 and 100000");
        }
        s.setRoundingUnit(unit);
        if (in.notePresets() != null) s.setNotePresets(notes(in.notePresets()));
        // booking rules: a missing value keeps what is stored, so older screens never wipe them
        String open = in.openTime() == null ? s.getOpenTime() : hhmm("Opening time", in.openTime());
        String close = in.closeTime() == null ? s.getCloseTime() : hhmm("Closing time", in.closeTime());
        if (java.time.LocalTime.parse(close).compareTo(java.time.LocalTime.parse(open)) <= 0) {
            throw ApiException.badRequest("Closing time must be after opening time");
        }
        s.setOpenTime(open);
        s.setCloseTime(close);
        if (in.slotMinutes() != null) s.setSlotMinutes(range("Time between bookable slots", in.slotMinutes(), 5, 240));
        if (in.turnMinutes() != null) s.setTurnMinutes(range("Table turn time", in.turnMinutes(), 15, 480));
        if (in.bookingDaysAhead() != null) s.setBookingDaysAhead(range("Days ahead", in.bookingDaysAhead(), 1, 365));
        if (in.depositPerGuest() != null) {
            if (in.depositPerGuest().signum() < 0 || in.depositPerGuest().compareTo(BigDecimal.valueOf(10_000_000)) > 0) throw ApiException.badRequest("Deposit must be between 0 and 10,000,000 per guest");
            s.setDepositPerGuest(in.depositPerGuest());
        }
        if (in.depositRefundHours() != null) s.setDepositRefundHours(range("Deposit refund window", in.depositRefundHours(), 0, 720));
        if (in.reminderHours() != null) s.setReminderHours(range("Reminder hours", in.reminderHours(), 0, 720));
        if (in.graceMinutes() != null) s.setGraceMinutes(range("Grace period", in.graceMinutes(), 0, 240));
        if (in.noShowBlockCount() != null) s.setNoShowBlockCount(range("No-show limit", in.noShowBlockCount(), 0, 20));
        if (in.defaultPrepMinutes() != null) s.setDefaultPrepMinutes(range("Usual preparation time", in.defaultPrepMinutes(), 1, 240));
        if (in.kitchenWarnPercent() != null) s.setKitchenWarnPercent(range("Warning level", in.kitchenWarnPercent(), 10, 100));
        if (in.onlineEnabled() != null) s.setOnlineEnabled(in.onlineEnabled());
        if (in.qrEnabled() != null) s.setQrEnabled(in.qrEnabled());
        if (in.onlineTakeaway() != null) s.setOnlineTakeaway(in.onlineTakeaway());
        if (in.onlineDelivery() != null) s.setOnlineDelivery(in.onlineDelivery());
        if (in.onlinePaused() != null) s.setOnlinePaused(in.onlinePaused());
        if (in.onlineAutoAcceptTables() != null) s.setOnlineAutoAcceptTables(in.onlineAutoAcceptTables());
        if (in.onlinePrepMinutes() != null) s.setOnlinePrepMinutes(range("Usual time for online orders", in.onlinePrepMinutes(), 5, 480));
        if (in.onlineMaxPending() != null) s.setOnlineMaxPending(range("Waiting orders per phone", in.onlineMaxPending(), 1, 20));
        if (in.onlineExpireMinutes() != null) s.setOnlineExpireMinutes(range("Minutes before an unanswered order lapses", in.onlineExpireMinutes(), 5, 240));
        if (in.onlineMinOrder() != null) {
            if (in.onlineMinOrder().signum() < 0 || in.onlineMinOrder().compareTo(BigDecimal.valueOf(100_000_000)) > 0) throw ApiException.badRequest("The minimum order must be between 0 and 100,000,000");
            s.setOnlineMinOrder(in.onlineMinOrder());
        }
        if (in.onlineMessage() != null) {
            String m = in.onlineMessage().trim();
            if (m.length() > 200) throw ApiException.badRequest("The message for online guests is too long (200 characters at most)");
            s.setOnlineMessage(m.isEmpty() ? null : m);
        }
        if (in.reminderTemplate() != null) {
            String t = in.reminderTemplate().trim();
            if (t.isEmpty()) throw ApiException.badRequest("The reminder message cannot be empty");
            if (t.length() > 600) throw ApiException.badRequest("The reminder message is too long (600 characters at most)");
            s.setReminderTemplate(t);
        }
        RestaurantSettings saved = repository.save(s);
        auditLogService.record("RESTAURANT_SETTINGS_UPDATED", "Updated the restaurant pricing rules", "RestaurantSettings", saved.getId());
        return RestaurantSettingsDto.from(saved);
    }

    private static BigDecimal pct(String what, BigDecimal v, int max) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        if (x.signum() < 0 || x.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw ApiException.badRequest(what + " must be between 0 and " + max + " percent");
        }
        return x;
    }

    private static int range(String what, int v, int min, int max) {
        if (v < min || v > max) throw ApiException.badRequest(what + " must be between " + min + " and " + max);
        return v;
    }

    private static String hhmm(String what, String raw) {
        try {
            java.time.LocalTime t = java.time.LocalTime.parse(raw.trim(), java.time.format.DateTimeFormatter.ofPattern("H:mm"));
            return String.format("%02d:%02d", t.getHour(), t.getMinute());
        } catch (Exception e) {
            throw ApiException.badRequest(what + " must look like 09:30");
        }
    }

    private static String notes(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 40) throw ApiException.badRequest("A quick note can be at most 40 characters");
            if (out.length() > 0) out.append(',');
            out.append(t);
        }
        if (out.length() > 600) throw ApiException.badRequest("Too many quick notes");
        return out.toString();
    }

    private static String presets(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            BigDecimal v;
            try {
                v = new BigDecimal(t);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Tip choices must be numbers separated by commas, for example 5,10,15");
            }
            if (v.signum() <= 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw ApiException.badRequest("Each tip choice must be between 0 and 100 percent");
            }
            if (out.length() > 0) out.append(',');
            out.append(v.stripTrailingZeros().toPlainString());
        }
        if (out.length() > 60) throw ApiException.badRequest("Too many tip choices");
        return out.toString();
    }
}
