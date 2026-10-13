package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.HotelSettingsDto;
import com.posapp.hotel.dto.HotelRequests.HotelSettingsRequest;
import com.posapp.paymentmethod.PaymentMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class HotelSettingsService {

    private final HotelSettingsRepository repository;
    private final BookingRepository bookingRepository;
    private final PaymentMethodService paymentMethodService;
    private final AuditLogService auditLogService;

    public HotelSettingsService(HotelSettingsRepository repository, BookingRepository bookingRepository,
                                PaymentMethodService paymentMethodService, AuditLogService auditLogService) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
        this.paymentMethodService = paymentMethodService;
        this.auditLogService = auditLogService;
    }

    /** Read-write on purpose: the first call on a new database creates the row. */
    @Transactional
    public HotelSettings entity() {
        List<HotelSettings> all = repository.findAll();
        return all.isEmpty() ? repository.save(new HotelSettings()) : all.get(0);
    }

    @Transactional
    public HotelSettingsDto get() {
        return toDto(entity());
    }

    @Transactional
    public PostingMode postingMode() {
        PostingMode mode = entity().getPostingMode();
        return mode == null ? PostingMode.NIGHTLY : mode;
    }

    @Transactional
    public BigDecimal taxPercent() {
        BigDecimal tax = entity().getTaxPercent();
        return tax == null ? BigDecimal.ZERO : tax;
    }

    @Transactional
    public HotelSettingsDto update(HotelSettingsRequest request) {
        HotelSettings settings = entity();
        if (request.postingMode() != null) {
            PostingMode mode;
            try {
                mode = PostingMode.valueOf(request.postingMode().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown accounting mode: " + request.postingMode());
            }
            if (mode != settings.getPostingMode()) {
                boolean open = !bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.BOOKED).isEmpty()
                        || !bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).isEmpty();
                if (open) {
                    throw ApiException.conflict("The accounting mode can only be changed when there are no open bookings (booked or in-house) — "
                            + "otherwise one stay would be posted two different ways. Check out or cancel them first.");
                }
                settings.setPostingMode(mode);
            }
        }
        if (request.taxPercent() != null) {
            if (request.taxPercent().signum() < 0 || request.taxPercent().compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.badRequest("Tax must be between 0 and 100 percent");
            }
            settings.setTaxPercent(request.taxPercent());
        }
        if (request.checkInTime() != null) settings.setCheckInTime(validTime(request.checkInTime(), "Check-in time"));
        if (request.checkOutTime() != null) settings.setCheckOutTime(validTime(request.checkOutTime(), "Check-out time"));
        if (request.auditTime() != null) settings.setAuditTime(validTime(request.auditTime(), "Night audit time"));
        if (request.enabled() != null) {
            settings.setEnabled(request.enabled());
            paymentMethodService.setRoomChargeEnabled(request.enabled());
        }
        HotelSettings saved = repository.save(settings);
        auditLogService.record("HOTEL_SETTINGS_UPDATED", "Hotel settings saved (" + (saved.isEnabled() ? "enabled" : "disabled")
                + ", accounting " + saved.getPostingMode() + ")", "HotelSettings", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void markAuditRan(LocalDate date, String summary) {
        HotelSettings settings = entity();
        settings.setAuditLastRun(date);
        settings.setAuditLastSummary(summary);
        repository.save(settings);
    }

    private static String validTime(String value, String label) {
        try {
            return LocalTime.parse(value.trim()).toString();
        } catch (Exception ex) {
            throw ApiException.badRequest(label + " must look like 14:00");
        }
    }

    static HotelSettingsDto toDto(HotelSettings s) {
        return new HotelSettingsDto(s.isEnabled(), (s.getPostingMode() == null ? PostingMode.NIGHTLY : s.getPostingMode()).name(),
                s.getTaxPercent() == null ? BigDecimal.ZERO : s.getTaxPercent(), s.getCheckInTime(), s.getCheckOutTime(),
                s.getAuditTime(), s.getAuditLastRun(), s.getAuditLastSummary());
    }
}
