package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.realestate.dto.RealEstateDtos.SettingsDto;
import com.posapp.realestate.dto.RealEstateRequests.SettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Module settings, and the code series (PR-0001...) used by the Real Estate records. */
@Service
public class ReSettingsService {

    private final ReSettingsRepository repository;
    private final ReCounterRepository counterRepository;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;
    private final MailService mailService;

    public ReSettingsService(ReSettingsRepository repository, ReCounterRepository counterRepository, RealEstateAccess access, AuditLogService auditLogService, MailService mailService) {
        this.mailService = mailService;
        this.repository = repository;
        this.counterRepository = counterRepository;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ReSettings entity() {
        List<ReSettings> all = repository.findAll();
        return all.isEmpty() ? repository.save(new ReSettings()) : all.get(0);
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    SettingsDto dto(ReSettings s) {
        return new SettingsDto(split(s.getAreas()), s.getViewingMinutes(), s.getStaleLeadDays(), s.isWebsiteEnabled(), s.getWebsiteTitle(), s.getWebsiteIntro(), s.getWebsitePhone(),
                s.getWebsiteEmail(), s.getEnquiriesPerPhonePerDay(), s.getDefaultCommissionPercent(), s.getCommissionAccountId(), s.isFollowUpEmailEnabled(), s.getFollowUpLastRun(),
                s.getFollowUpLastSummary(), mailService.isConfigured(), s.isViewingReminderEnabled(), s.getViewingReminderHours(),
                s.getOfferTerms(), s.getReservationTerms(), s.getAgreementTerms());
    }

    private static String text(String v, int max, String what) {
        if (v == null) return null;
        String t = v.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    @Transactional
    public SettingsDto get() {
        access.on();
        ReSettings s = entity();
        return dto(s);
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        access.on();
        ReSettings s = entity();
        if (r.areas() != null) {
            List<String> list = split(r.areas());
            if (list.stream().anyMatch(a -> a.length() > 120)) throw ApiException.badRequest("An area name can be at most 120 characters.");
            s.setAreas(String.join(", ", list));
        }
        if (r.viewingMinutes() != null) {
            if (r.viewingMinutes() < 5 || r.viewingMinutes() > 480) throw ApiException.badRequest("A viewing lasts between 5 and 480 minutes.");
            s.setViewingMinutes(r.viewingMinutes());
        }
        if (r.staleLeadDays() != null) {
            if (r.staleLeadDays() < 1 || r.staleLeadDays() > 365) throw ApiException.badRequest("A lead is stale after 1 to 365 days.");
            s.setStaleLeadDays(r.staleLeadDays());
        }
        if (r.websiteTitle() != null) s.setWebsiteTitle(text(r.websiteTitle(), 120, "The website title"));
        if (r.websiteIntro() != null) s.setWebsiteIntro(text(r.websiteIntro(), 1000, "The introduction"));
        if (r.websitePhone() != null) s.setWebsitePhone(text(r.websitePhone(), 60, "The phone number"));
        if (r.websiteEmail() != null) {
            String e = text(r.websiteEmail(), 200, "The e-mail");
            if (e != null && !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest("That e-mail address does not look right.");
            s.setWebsiteEmail(e);
        }
        if (r.enquiriesPerPhonePerDay() != null) {
            if (r.enquiriesPerPhonePerDay() < 1 || r.enquiriesPerPhonePerDay() > 50) throw ApiException.badRequest("One phone number may send 1 to 50 enquiries a day.");
            s.setEnquiriesPerPhonePerDay(r.enquiriesPerPhonePerDay());
        }
        if (r.defaultCommissionPercent() != null) {
            if (r.defaultCommissionPercent().signum() < 0 || r.defaultCommissionPercent().compareTo(new java.math.BigDecimal("100")) > 0) {
                throw ApiException.badRequest("The commission is between 0 and 100 percent.");
            }
            s.setDefaultCommissionPercent(r.defaultCommissionPercent().signum() == 0 ? null : r.defaultCommissionPercent().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (r.commissionAccountId() != null) s.setCommissionAccountId(r.commissionAccountId() <= 0 ? null : r.commissionAccountId());
        if (r.websiteEnabled() != null) s.setWebsiteEnabled(r.websiteEnabled());
        if (r.followUpEmailEnabled() != null) s.setFollowUpEmailEnabled(r.followUpEmailEnabled());
        if (r.viewingReminderEnabled() != null) s.setViewingReminderEnabled(r.viewingReminderEnabled());
        if (r.viewingReminderHours() != null) {
            if (r.viewingReminderHours() < 1 || r.viewingReminderHours() > 72) throw ApiException.badRequest("A reminder goes out 1 to 72 hours before the viewing.");
            s.setViewingReminderHours(r.viewingReminderHours());
        }
        if (r.offerTerms() != null) s.setOfferTerms(text(r.offerTerms(), 3000, "The offer terms"));
        if (r.reservationTerms() != null) s.setReservationTerms(text(r.reservationTerms(), 3000, "The reservation terms"));
        if (r.agreementTerms() != null) s.setAgreementTerms(text(r.agreementTerms(), 3000, "The agreement terms"));
        repository.save(s);
        auditLogService.record("RE_SETTINGS_UPDATED", "Updated the Real Estate settings");
        return dto(s);
    }

    /** Next code in a series: {@code nextCode("PR")} gives PR-0001, PR-0002... Takes a lock so two people never get the same code. */
    @Transactional
    public String nextCode(String prefix) {
        repository.lockAll();
        ReCounter c = counterRepository.findByCounterKey(prefix).orElseGet(() -> {
            ReCounter n = new ReCounter();
            n.setCounterKey(prefix);
            return n;
        });
        c.setLastSeq(c.getLastSeq() + 1);
        counterRepository.save(c);
        return String.format("%s-%04d", prefix, c.getLastSeq());
    }
}
