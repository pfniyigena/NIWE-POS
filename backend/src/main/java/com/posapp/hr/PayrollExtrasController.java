package com.posapp.hr;

import com.posapp.common.ListPaging;
import com.posapp.hr.PayrollExtrasDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** One-off payroll adjustments and staff advances / loans. Salary data, so it needs the Payroll area. */
@RestController
@RequestMapping("/api/hr/payroll")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
public class PayrollExtrasController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_MANAGE')";

    private final PayrollAdjustmentService adjustmentService;
    private final StaffLoanService loanService;
    private final PayrollRemittanceService remittanceService;
    private final FinalPayService finalPayService;

    public PayrollExtrasController(PayrollAdjustmentService adjustmentService, StaffLoanService loanService, PayrollRemittanceService remittanceService, FinalPayService finalPayService) {
        this.finalPayService = finalPayService;
        this.remittanceService = remittanceService;
        this.adjustmentService = adjustmentService;
        this.loanService = loanService;
    }

    // ---- adjustments ----

    @GetMapping("/adjustments")
    public ResponseEntity<List<AdjustmentDto>> adjustments(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                                           @RequestParam(required = false) Long employeeId, @RequestParam(required = false) AdjustmentKind kind,
                                                           @RequestParam(required = false) String q, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(adjustmentService.list(year, month, employeeId, kind, q), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/adjustments")
    public AdjustmentDto createAdjustment(@RequestBody AdjustmentRequest request) {
        return adjustmentService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/adjustments/{id}")
    public AdjustmentDto updateAdjustment(@PathVariable Long id, @RequestBody AdjustmentRequest request) {
        return adjustmentService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/adjustments/{id}")
    public void deleteAdjustment(@PathVariable Long id) {
        adjustmentService.delete(id);
    }

    // ---- advances and loans ----

    @GetMapping("/loans")
    public ResponseEntity<List<LoanDto>> loans(@RequestParam(required = false) LoanStatus status, @RequestParam(required = false) Long employeeId,
                                               @RequestParam(required = false) LoanType type, @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(loanService.list(status, employeeId, type, q), page, size);
    }

    @GetMapping("/loans/{id}")
    public LoanDetailDto loan(@PathVariable Long id) {
        return loanService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/loans")
    public LoanDto createLoan(@RequestBody LoanRequest request) {
        return loanService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/loans/{id}/repay")
    public LoanDetailDto repay(@PathVariable Long id, @RequestBody RepayRequest request) {
        return loanService.repayInCash(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/loans/{id}/write-off")
    public LoanDetailDto writeOff(@PathVariable Long id, @RequestBody NoteRequest request) {
        return loanService.writeOff(id, request == null ? null : request.note());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/loans/{id}/cancel")
    public LoanDto cancel(@PathVariable Long id) {
        return loanService.cancel(id);
    }

    // ---- final pay of a leaver ----

    @GetMapping("/final-pay/{employeeId}")
    public FinalPayDto finalPay(@PathVariable Long employeeId) {
        return finalPayService.get(employeeId);
    }

    /** Works out the final pay from these figures and puts it on the payroll of the last month. */
    @PreAuthorize(MANAGE)
    @PostMapping("/final-pay/{employeeId}")
    public FinalPayDto saveFinalPay(@PathVariable Long employeeId, @RequestBody FinalPayRequest request) {
        return finalPayService.save(employeeId, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/final-pay/{employeeId}")
    public FinalPayDto removeFinalPay(@PathVariable Long employeeId) {
        return finalPayService.remove(employeeId);
    }

    // ---- tax and social-security payments, and the monthly returns ----

    @GetMapping("/remittances/summary")
    public RemittanceSummaryDto remittanceSummary(@RequestParam(required = false) Integer year) {
        return remittanceService.summary(year);
    }

    @GetMapping("/remittances")
    public ResponseEntity<List<RemittanceDto>> remittances(@RequestParam(required = false) RemittanceKind kind, @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                                           @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(remittanceService.list(kind, year, month, status, q), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/remittances")
    public RemittanceDto payRemittance(@RequestBody RemittanceRequest request) {
        return remittanceService.record(request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/remittances/{id}/void")
    public RemittanceDto voidRemittance(@PathVariable Long id, @RequestBody NoteRequest request) {
        return remittanceService.voidIt(id, request == null ? null : request.note());
    }

    @GetMapping("/runs/{id}/returns/paye")
    public ResponseEntity<byte[]> payeReturn(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format) {
        return remittanceService.payeReturn(id, format);
    }

    @GetMapping("/runs/{id}/returns/social-security")
    public ResponseEntity<byte[]> socialSecurityReturn(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format) {
        return remittanceService.socialSecurityReturn(id, format);
    }
}
