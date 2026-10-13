package com.posapp.hr;

import com.posapp.accounting.AccountService;
import com.posapp.accounting.ChartCountry;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.BandDto;
import com.posapp.hr.dto.HrDtos.RuleDto;
import com.posapp.hr.dto.HrRequests.BandRequest;
import com.posapp.hr.dto.HrRequests.RuleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Statutory payroll rules (contributions) and the income-tax bands. The ready-made Rwanda and Chad figures are only a
 * starting point: they are fully editable and must be confirmed by an accountant against the current law.
 */
@Service
public class PayrollRuleService {

    private final PayrollRuleRepository ruleRepository;
    private final TaxBandRepository bandRepository;
    private final HrSettingsService settingsService;
    private final HrSettingsRepository settingsRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;

    public PayrollRuleService(PayrollRuleRepository ruleRepository, TaxBandRepository bandRepository, HrSettingsService settingsService,
                              HrSettingsRepository settingsRepository, AccountService accountService, AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.bandRepository = bandRepository;
        this.settingsService = settingsService;
        this.settingsRepository = settingsRepository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    /** Loads the ready-made rules for the store's country the first time they are needed. */
    @Transactional
    public void ensureDefaults() {
        HrSettings settings = settingsService.entity();
        if (settings.isPayrollDefaultsLoaded()) {
            return;
        }
        if (ruleRepository.count() == 0 && bandRepository.count() == 0) {
            loadCountryDefaults(accountService.getDefaultChartCountrySuggestion());
        }
        settings.setPayrollDefaultsLoaded(true);
        settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<RuleDto> listRules() {
        return ruleRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(RuleDto::from).toList();
    }

    @Transactional
    public List<RuleDto> rulesWithDefaults() {
        ensureDefaults();
        return listRules();
    }

    @Transactional
    public List<BandDto> bandsWithDefaults() {
        ensureDefaults();
        return listBands();
    }

    @Transactional(readOnly = true)
    public List<BandDto> listBands() {
        return bandRepository.findAllByOrderBySortOrderAscFromAmountAsc().stream().map(BandDto::from).toList();
    }

    @Transactional
    public RuleDto createRule(RuleRequest r) {
        PayrollRule rule = new PayrollRule();
        String code = r.code() != null && !r.code().isBlank() ? r.code().trim().toUpperCase(Locale.ROOT) : codeFrom(r.name());
        if (ruleRepository.existsByCode(code)) {
            throw ApiException.conflict("There is already a rule with the code " + code + ".");
        }
        rule.setCode(code);
        apply(rule, r);
        PayrollRule saved = ruleRepository.save(rule);
        auditLogService.record("PAYROLL_RULE_CREATED", "Created payroll rule " + saved.getName(), "PayrollRule", saved.getId());
        return RuleDto.from(saved);
    }

    @Transactional
    public RuleDto updateRule(Long id, RuleRequest r) {
        PayrollRule rule = rule(id);
        apply(rule, r);
        PayrollRule saved = ruleRepository.save(rule);
        auditLogService.record("PAYROLL_RULE_UPDATED", "Updated payroll rule " + saved.getName(), "PayrollRule", saved.getId());
        return RuleDto.from(saved);
    }

    @Transactional
    public void deleteRule(Long id) {
        PayrollRule rule = rule(id);
        ruleRepository.delete(rule);
        auditLogService.record("PAYROLL_RULE_DELETED", "Deleted payroll rule " + rule.getName(), "PayrollRule", id);
    }

    private void apply(PayrollRule rule, RuleRequest r) {
        if (r.ratePercent().signum() < 0 || r.ratePercent().compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.badRequest("The rate must be between 0 and 100%.");
        }
        if (r.capAmount() != null && r.capAmount().signum() <= 0) {
            throw ApiException.badRequest("The monthly cap must be above zero (or empty for no cap).");
        }
        rule.setName(r.name().trim());
        rule.setKind(r.kind());
        rule.setBase(r.base() != null ? r.base() : RuleBase.GROSS);
        rule.setRatePercent(r.ratePercent());
        rule.setCapAmount(r.capAmount());
        // Only an employee deduction can reduce taxable pay.
        rule.setDeductibleFromTax(r.kind() == RuleKind.EMPLOYEE_DEDUCTION && r.deductibleFromTax() != null && r.deductibleFromTax()
                && rule.getBase() != RuleBase.NET);
        rule.setTarget(r.target() != null ? r.target() : PayableTarget.SOCIAL_SECURITY);
        if (r.active() != null) {
            rule.setActive(r.active());
        }
        if (r.sortOrder() != null) {
            rule.setSortOrder(r.sortOrder());
        }
    }

    private PayrollRule rule(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Payroll rule not found"));
    }

    private String codeFrom(String name) {
        String base = name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        String code = base.isEmpty() ? "RULE" : base;
        String candidate = code;
        int i = 2;
        while (ruleRepository.existsByCode(candidate)) {
            candidate = code + "_" + i++;
        }
        return candidate;
    }

    // ---- tax bands ----

    /** Replaces all the income-tax bands. */
    @Transactional
    public List<BandDto> saveBands(List<BandRequest> bands) {
        List<BandRequest> sorted = new ArrayList<>(bands == null ? List.of() : bands);
        sorted.sort(Comparator.comparing(BandRequest::fromAmount));
        for (int i = 0; i < sorted.size(); i++) {
            BandRequest b = sorted.get(i);
            if (b.fromAmount().signum() < 0) {
                throw ApiException.badRequest("A band can't start below zero.");
            }
            if (b.ratePercent().signum() < 0 || b.ratePercent().compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.badRequest("Band rates must be between 0 and 100%.");
            }
            if (b.toAmount() != null && b.toAmount().compareTo(b.fromAmount()) <= 0) {
                throw ApiException.badRequest("A band's upper limit must be above its start.");
            }
            if (b.toAmount() == null && i != sorted.size() - 1) {
                throw ApiException.badRequest("Only the last band can have no upper limit.");
            }
            if (i > 0) {
                BigDecimal prevTo = sorted.get(i - 1).toAmount();
                if (prevTo != null && b.fromAmount().compareTo(prevTo) < 0) {
                    throw ApiException.badRequest("Tax bands can't overlap.");
                }
            }
        }
        bandRepository.deleteAll(bandRepository.findAll());
        bandRepository.flush();
        int order = 1;
        for (BandRequest b : sorted) {
            TaxBand band = new TaxBand();
            band.setFromAmount(b.fromAmount());
            band.setToAmount(b.toAmount());
            band.setRatePercent(b.ratePercent());
            band.setSortOrder(order++);
            bandRepository.save(band);
        }
        auditLogService.record("TAX_BANDS_UPDATED", "Updated the income-tax bands (" + sorted.size() + " bands)");
        return listBands();
    }

    // ---- ready-made country rules ----

    /** Adds the country's ready-made rules that don't exist yet (by code) and its tax bands when there are none. */
    @Transactional
    public void loadCountryDefaults(ChartCountry country) {
        int order = ruleRepository.findAll().stream().mapToInt(PayrollRule::getSortOrder).max().orElse(0);
        if (country == ChartCountry.CHAD) {
            order = seed("CNPS_EE", "CNPS contribution (employee)", RuleKind.EMPLOYEE_DEDUCTION, RuleBase.GROSS, "3.5", "500000", true, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("CNPS_ER", "CNPS contribution (employer)", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "16.5", "500000", false, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("PAYROLL_TAX_ER", "Employer payroll tax", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "7.5", null, false, PayableTarget.OTHER, true, order);
            seed("FONAP_ER", "FONAP apprenticeship levy", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "1.2", null, false, PayableTarget.OTHER, true, order);
            // The IRPP bands are deliberately not pre-filled: they must be entered from the current finance law.
        } else {
            order = seed("PENSION_EE", "RSSB pension (employee)", RuleKind.EMPLOYEE_DEDUCTION, RuleBase.GROSS, "3", null, true, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("MATERNITY_EE", "RSSB maternity (employee)", RuleKind.EMPLOYEE_DEDUCTION, RuleBase.GROSS, "0.3", null, true, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("CBHI_EE", "Community health insurance (CBHI)", RuleKind.EMPLOYEE_DEDUCTION, RuleBase.NET, "0.5", null, false, PayableTarget.OTHER, true, order);
            order = seed("RAMA_EE", "RAMA medical insurance (employee)", RuleKind.EMPLOYEE_DEDUCTION, RuleBase.BASIC, "7.5", null, false, PayableTarget.OTHER, false, order);
            order = seed("PENSION_ER", "RSSB pension (employer)", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "5.3", null, false, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("MATERNITY_ER", "RSSB maternity (employer)", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "0.3", null, false, PayableTarget.SOCIAL_SECURITY, true, order);
            order = seed("OCC_HAZARD_ER", "Occupational hazards (employer)", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.GROSS, "2", null, false, PayableTarget.SOCIAL_SECURITY, true, order);
            seed("RAMA_ER", "RAMA medical insurance (employer)", RuleKind.EMPLOYER_CONTRIBUTION, RuleBase.BASIC, "7.5", null, false, PayableTarget.OTHER, false, order);
            if (bandRepository.count() == 0) {
                band("0", "60000", "0", 1);
                band("60000", "100000", "10", 2);
                band("100000", "200000", "20", 3);
                band("200000", null, "30", 4);
            }
        }
        auditLogService.record("PAYROLL_DEFAULTS_LOADED", "Loaded the ready-made payroll rules for " + country.name());
    }

    @Transactional
    public void loadDefaultsForStore() {
        loadCountryDefaults(accountService.getDefaultChartCountrySuggestion());
        HrSettings s = settingsService.entity();
        s.setPayrollDefaultsLoaded(true);
        settingsRepository.save(s);
    }

    private int seed(String code, String name, RuleKind kind, RuleBase base, String rate, String cap, boolean deductible, PayableTarget target, boolean active, int order) {
        if (ruleRepository.existsByCode(code)) {
            return order;
        }
        PayrollRule r = new PayrollRule();
        r.setCode(code);
        r.setName(name);
        r.setKind(kind);
        r.setBase(base);
        r.setRatePercent(new BigDecimal(rate));
        r.setCapAmount(cap != null ? new BigDecimal(cap) : null);
        r.setDeductibleFromTax(deductible);
        r.setTarget(target);
        r.setActive(active);
        r.setSortOrder(++order);
        ruleRepository.save(r);
        return order;
    }

    private void band(String from, String to, String rate, int order) {
        TaxBand b = new TaxBand();
        b.setFromAmount(new BigDecimal(from));
        b.setToAmount(to != null ? new BigDecimal(to) : null);
        b.setRatePercent(new BigDecimal(rate));
        b.setSortOrder(order);
        bandRepository.save(b);
    }
}
