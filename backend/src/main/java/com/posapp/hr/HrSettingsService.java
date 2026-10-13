package com.posapp.hr;

import com.posapp.accounting.AccountService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.SettingsDto;
import com.posapp.hr.dto.HrRequests.SettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** The single HR settings row (working hours, working days, overtime, tax-band mode). */
@Service
public class HrSettingsService {

    private final HrSettingsRepository repository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;

    public HrSettingsService(HrSettingsRepository repository, AccountService accountService, AuditLogService auditLogService) {
        this.repository = repository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public HrSettings entity() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new HrSettings()));
    }

    @Transactional
    public SettingsDto get() {
        return toDto(entity());
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        HrSettings s = entity();
        if (r.workStart() != null) {
            s.setWorkStart(r.workStart());
        }
        if (r.graceMinutes() != null) {
            if (r.graceMinutes() < 0 || r.graceMinutes() > 240) {
                throw ApiException.badRequest("The grace period must be between 0 and 240 minutes.");
            }
            s.setGraceMinutes(r.graceMinutes());
        }
        if (r.standardDailyHours() != null) {
            if (r.standardDailyHours().signum() <= 0 || r.standardDailyHours().compareTo(new BigDecimal("24")) > 0) {
                throw ApiException.badRequest("Standard daily hours must be between 0 and 24.");
            }
            s.setStandardDailyHours(r.standardDailyHours());
        }
        if (r.workingDays() != null) {
            Set<DayOfWeek> days = parseDays(r.workingDays());
            if (days.isEmpty()) {
                throw ApiException.badRequest("Choose at least one working day.");
            }
            s.setWorkingDays(String.join(",", days.stream().map(d -> d.name().substring(0, 3)).toList()));
        }
        if (r.overtimeMultiplier() != null) {
            if (r.overtimeMultiplier().compareTo(BigDecimal.ONE) < 0) {
                throw ApiException.badRequest("The overtime multiplier can't be below 1.");
            }
            s.setOvertimeMultiplier(r.overtimeMultiplier());
        }
        if (r.taxBandsAnnual() != null) {
            s.setTaxBandsAnnual(r.taxBandsAnnual());
        }
        repository.save(s);
        auditLogService.record("HR_SETTINGS_UPDATED", "Updated HR settings");
        return toDto(s);
    }

    @Transactional
    public Set<DayOfWeek> workingDays() {
        return parseDays(entity().getWorkingDays());
    }

    public static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (csv == null) {
            return days;
        }
        for (String part : Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList()) {
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(part.toUpperCase())) {
                    days.add(d);
                    break;
                }
            }
        }
        return days;
    }

    /** True for a normal working day: one of the working weekdays (public holidays are handled by the callers). */
    public static boolean isWorkingWeekday(Set<DayOfWeek> days, LocalDate date) {
        return days.contains(date.getDayOfWeek());
    }

    private SettingsDto toDto(HrSettings s) {
        return new SettingsDto(s.getWorkStart(), s.getGraceMinutes(), s.getStandardDailyHours(), s.getWorkingDays(),
                s.getOvertimeMultiplier(), s.isTaxBandsAnnual(), accountService.getDefaultChartCountrySuggestion().name());
    }
}
