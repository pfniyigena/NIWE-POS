package com.posapp.hr;

import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Payroll rules, tax bands and payroll runs. Salary data, so it needs the Payroll area. */
@RestController
@RequestMapping("/api/hr/payroll")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
public class PayrollController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_MANAGE')";

    private final PayrollService payrollService;
    private final PayrollRuleService ruleService;
    private final HrSettingsService settingsService;
    private final PayrollDeliveryService deliveryService;

    public PayrollController(PayrollService payrollService, PayrollRuleService ruleService, HrSettingsService settingsService,
                             PayrollDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
        this.payrollService = payrollService;
        this.ruleService = ruleService;
        this.settingsService = settingsService;
    }

    // ---- rules and bands ----

    @GetMapping("/rules")
    public List<RuleDto> rules() {
        return ruleService.rulesWithDefaults();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/rules")
    public RuleDto createRule(@Valid @RequestBody RuleRequest request) {
        return ruleService.createRule(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/rules/{id}")
    public RuleDto updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        return ruleService.updateRule(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
    }

    /** Adds the country's ready-made rules that are missing (and the tax bands if there are none). */
    @PreAuthorize(MANAGE)
    @PostMapping("/rules/load-defaults")
    public List<RuleDto> loadDefaults() {
        ruleService.loadDefaultsForStore();
        return ruleService.listRules();
    }

    @GetMapping("/bands")
    public List<BandDto> bands() {
        return ruleService.bandsWithDefaults();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/bands")
    public List<BandDto> saveBands(@RequestBody BandsRequest request) {
        return ruleService.saveBands(request.bands());
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return settingsService.get();
    }

    /** Whether the tax bands are yearly amounts (true) or monthly amounts (false). */
    @PreAuthorize(MANAGE)
    @PutMapping("/tax-mode")
    public SettingsDto taxMode(@RequestParam boolean annual) {
        return settingsService.update(new SettingsRequest(null, null, null, null, null, annual));
    }

    // ---- runs ----

    @GetMapping("/runs")
    public List<PayrollRunDto> runs() {
        return payrollService.list();
    }

    @GetMapping("/runs/{id}")
    public PayrollRunDetailDto run(@PathVariable Long id) {
        return payrollService.get(id);
    }

    @GetMapping("/runs/{id}/lines/{lineId}/payslip")
    public PayslipDto payslip(@PathVariable Long id, @PathVariable Long lineId) {
        return payrollService.payslip(id, lineId);
    }

    /** Works out (or re-works out, while still a draft) the payroll of a month. */
    @PreAuthorize(MANAGE)
    @PostMapping("/runs")
    public PayrollRunDetailDto generate(@Valid @RequestBody GenerateRequest request) {
        return payrollService.generate(request.year(), request.month());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/runs/{id}/approve")
    public PayrollRunDto approve(@PathVariable Long id) {
        return payrollService.approve(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/runs/{id}/pay")
    public PayrollRunDto pay(@PathVariable Long id, @RequestBody(required = false) PayRunRequest request) {
        return payrollService.pay(id, request != null ? request.paidDate() : null);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/runs/{id}")
    public void delete(@PathVariable Long id) {
        payrollService.delete(id);
    }

    /** The bank transfer list of an approved payroll, as CSV (default) or Excel. */
    @PreAuthorize(MANAGE)
    @GetMapping("/runs/{id}/bank-file")
    public ResponseEntity<byte[]> bankFile(@PathVariable Long id, @RequestParam(defaultValue = "csv") String format) {
        PayrollDeliveryService.FileOut f = deliveryService.bankFile(id, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.filename() + "\"")
                .body(f.bytes());
    }

    /** Emails the payslips of an approved payroll (one employee when {@code lineId} is given). */
    @PreAuthorize(MANAGE)
    @PostMapping("/runs/{id}/email-payslips")
    public PayslipEmailResultDto emailPayslips(@PathVariable Long id, @RequestParam(required = false) Long lineId) {
        return deliveryService.emailPayslips(id, lineId);
    }
}
