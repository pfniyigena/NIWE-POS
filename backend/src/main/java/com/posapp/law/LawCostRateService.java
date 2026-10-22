package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.law.dto.LawDtos.Option;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The cost of an hour of each lawyer's time. Admin only (the controller enforces it): these are salary-level figures. */
@Service
public class LawCostRateService {

    private static final BigDecimal MAX = new BigDecimal("1000000000");

    public record RateDto(Long userId, String name, BigDecimal hourlyCost) {
    }

    public record RateEntry(Long userId, BigDecimal hourlyCost) {
    }

    public record RatesRequest(List<RateEntry> rates) {
    }

    private final LawCostRateRepository repository;
    private final LawService lawService;
    private final AuditLogService auditLogService;

    public LawCostRateService(LawCostRateRepository repository, LawService lawService, AuditLogService auditLogService) {
        this.repository = repository;
        this.lawService = lawService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RateDto> list() {
        Map<Long, BigDecimal> rates = repository.findAll().stream().collect(Collectors.toMap(LawCostRate::getUserId, LawCostRate::getHourlyCost, (x, y) -> x));
        return lawService.staff().stream().map(o -> new RateDto(o.id(), o.name(), rates.get(o.id()))).toList();
    }

    /** Sets the rate of every person listed; an empty rate removes it. People not listed are left alone. */
    @Transactional
    public List<RateDto> save(RatesRequest request) {
        if (request == null || request.rates() == null) throw ApiException.badRequest("Send the rates to save.");
        Set<Long> staff = lawService.staff().stream().map(Option::id).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        Map<Long, LawCostRate> existing = repository.findAll().stream().collect(Collectors.toMap(LawCostRate::getUserId, Function.identity(), (x, y) -> x));
        int changed = 0;
        for (RateEntry e : request.rates()) {
            if (e == null || e.userId() == null || !staff.contains(e.userId())) throw ApiException.badRequest("Unknown person in the list.");
            if (!seen.add(e.userId())) throw ApiException.badRequest("A person is listed twice.");
            LawCostRate row = existing.get(e.userId());
            if (e.hourlyCost() == null) {
                if (row != null) {
                    repository.delete(row);
                    changed++;
                }
                continue;
            }
            BigDecimal v = e.hourlyCost().setScale(2, RoundingMode.HALF_UP);
            if (v.signum() < 0 || v.compareTo(MAX) >= 0) throw ApiException.badRequest("A cost per hour must be zero or more, and a sensible amount.");
            if (row == null) {
                row = new LawCostRate();
                row.setUserId(e.userId());
            } else if (row.getHourlyCost().compareTo(v) == 0) {
                continue;
            }
            row.setHourlyCost(v);
            repository.save(row);
            changed++;
        }
        if (changed > 0) auditLogService.record("LAW_COST_RATES_UPDATED", "Changed the cost per hour of " + changed + (changed == 1 ? " person" : " people"));
        return list();
    }
}
