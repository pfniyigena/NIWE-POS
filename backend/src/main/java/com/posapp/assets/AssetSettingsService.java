package com.posapp.assets;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** The one row of Assets rules. */
@Service
public class AssetSettingsService {

    public record SettingsDto(int alertDays, int soonMonths, BigDecimal disposalApprovalLimit) {
    }

    public record SettingsRequest(Integer alertDays, Integer soonMonths, BigDecimal disposalApprovalLimit) {
    }

    private final AssetSettingsRepository repository;
    private final AuditLogService auditLogService;

    public AssetSettingsService(AssetSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AssetSettings entity() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new AssetSettings()));
    }

    @Transactional
    public SettingsDto get() {
        return toDto(entity());
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        AssetSettings s = entity();
        if (r.alertDays() != null) {
            if (r.alertDays() < 0 || r.alertDays() > 365) throw ApiException.badRequest("Alert days must be between 0 and 365.");
            s.setAlertDays(r.alertDays());
        }
        if (r.soonMonths() != null) {
            if (r.soonMonths() < 1 || r.soonMonths() > 60) throw ApiException.badRequest("Months must be between 1 and 60.");
            s.setSoonMonths(r.soonMonths());
        }
        if (r.disposalApprovalLimit() != null) {
            if (r.disposalApprovalLimit().signum() < 0) throw ApiException.badRequest("The disposal limit cannot be negative (use 0 for no limit).");
            s.setDisposalApprovalLimit(r.disposalApprovalLimit());
        }
        repository.save(s);
        auditLogService.record("ASSET_SETTINGS_UPDATED", "Asset rules: alerts " + s.getAlertDays() + " days, soon " + s.getSoonMonths()
                + " months, disposal approval limit " + s.getDisposalApprovalLimit(), "AssetSettings", s.getId());
        return toDto(s);
    }

    static SettingsDto toDto(AssetSettings s) {
        return new SettingsDto(s.getAlertDays(), s.getSoonMonths(), s.getDisposalApprovalLimit());
    }
}
