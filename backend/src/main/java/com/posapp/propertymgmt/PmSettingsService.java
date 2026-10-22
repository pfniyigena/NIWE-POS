package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.SettingsDto;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.SettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Module settings, and the code series (OW-0001...) used by the Property Management records. */
@Service
public class PmSettingsService {

    private final PmSettingsRepository repository;
    private final PmCounterRepository counterRepository;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmSettingsService(PmSettingsRepository repository, PmCounterRepository counterRepository, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.counterRepository = counterRepository;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PmSettings entity() {
        List<PmSettings> all = repository.findAll();
        return all.isEmpty() ? repository.save(new PmSettings()) : all.get(0);
    }

    @Transactional
    public void save(PmSettings s) {
        repository.save(s);
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    static SettingsDto dto(PmSettings s) {
        return new SettingsDto(split(s.getAreas()), s.getExpiryWarningDays(), s.getDefaultDueDay(), s.getDefaultFeePercent(), s.getInvoiceAheadDays(), s.isAutoInvoice(), s.getFeeVatPercent(), s.isAutoStatements(), s.getAutoStatementsDay(), s.isAutoSendStatements(), s.getAutoStatementsThrough());
    }

    @Transactional
    public SettingsDto get() {
        access.on();
        return dto(entity());
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        access.on();
        PmSettings s = entity();
        if (r.areas() != null) {
            List<String> list = split(r.areas());
            if (list.stream().anyMatch(a -> a.length() > 120)) throw ApiException.badRequest("An area name can be at most 120 characters.");
            s.setAreas(String.join(", ", list));
        }
        if (r.expiryWarningDays() != null) {
            if (r.expiryWarningDays() < 1 || r.expiryWarningDays() > 365) throw ApiException.badRequest("Leases are flagged 1 to 365 days before they end.");
            s.setExpiryWarningDays(r.expiryWarningDays());
        }
        if (r.defaultDueDay() != null) {
            if (r.defaultDueDay() < 1 || r.defaultDueDay() > 28) throw ApiException.badRequest("Rent can fall due on the 1st to the 28th of the month.");
            s.setDefaultDueDay(r.defaultDueDay());
        }
        if (r.invoiceAheadDays() != null) {
            if (r.invoiceAheadDays() < 0 || r.invoiceAheadDays() > 60) throw ApiException.badRequest("Rent is invoiced 0 to 60 days before it falls due.");
            s.setInvoiceAheadDays(r.invoiceAheadDays());
        }
        if (r.autoInvoice() != null) s.setAutoInvoice(r.autoInvoice());
        if (Boolean.TRUE.equals(r.clearFee())) {
            s.setDefaultFeePercent(null);
        } else if (r.defaultFeePercent() != null) {
            if (r.defaultFeePercent().signum() < 0 || r.defaultFeePercent().compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("A management fee is between 0 and 100 percent.");
            s.setDefaultFeePercent(r.defaultFeePercent());
        }
        if (r.autoStatementsDay() != null) {
            if (r.autoStatementsDay() < 1 || r.autoStatementsDay() > 28) throw ApiException.badRequest("Statements can be made from the 1st to the 28th of the month.");
            s.setAutoStatementsDay(r.autoStatementsDay());
        }
        if (r.autoStatements() != null) s.setAutoStatements(r.autoStatements());
        if (r.autoSendStatements() != null) s.setAutoSendStatements(r.autoSendStatements());
        if (s.isAutoSendStatements() && !s.isAutoStatements()) throw ApiException.badRequest("Turn on making statements automatically first. Agreeing and e-mailing them builds on it.");
        if (Boolean.TRUE.equals(r.clearFeeVat())) {
            s.setFeeVatPercent(null);
        } else if (r.feeVatPercent() != null) {
            if (r.feeVatPercent().signum() < 0 || r.feeVatPercent().compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("VAT is between 0 and 100 percent.");
            s.setFeeVatPercent(r.feeVatPercent().signum() == 0 ? null : r.feeVatPercent());
        }
        repository.save(s);
        auditLogService.record("PM_SETTINGS", "Changed the Property Management settings");
        return dto(s);
    }

    /** Next code in a series: {@code nextCode("OW")} gives OW-0001, OW-0002... Takes a lock so two people never get the same code. */
    @Transactional
    public String nextCode(String prefix) {
        entity();
        repository.lockAll();
        PmCounter c = counterRepository.findByCounterKey(prefix).orElseGet(() -> {
            PmCounter n = new PmCounter();
            n.setCounterKey(prefix);
            return n;
        });
        c.setLastSeq(c.getLastSeq() + 1);
        counterRepository.save(c);
        return String.format("%s-%04d", prefix, c.getLastSeq());
    }
}
