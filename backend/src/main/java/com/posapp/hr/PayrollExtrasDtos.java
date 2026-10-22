package com.posapp.hr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Requests and answers for one-off payroll adjustments and staff advances / loans. */
public final class PayrollExtrasDtos {

    private PayrollExtrasDtos() {
    }

    // ---- adjustments ----

    public record AdjustmentRequest(Long employeeId, AdjustmentKind kind, String name, BigDecimal amount, Boolean taxable, Integer year, Integer month, String note) {
    }

    /** {@code status}: PENDING until the month's payroll is approved, then APPLIED. {@code draftRunId} is set when a draft payroll exists for the month (regenerate it to include a new adjustment). */
    public record AdjustmentDto(Long id, Long employeeId, String employeeNumber, String employeeName, AdjustmentKind kind, String name, BigDecimal amount, boolean taxable,
                                int year, int month, String note, String createdByName, String status, Long draftRunId, Instant createdAt) {
    }

    // ---- payroll corrections ----

    /** {@code kind}: PAYMENT_CANCELLED or REOPENED. */
    public record PayrollEventDto(String kind, String note, String byName, Instant at) {
    }

    // ---- final pay ----

    public record FinalPayRequest(BigDecimal leaveDays, BigDecimal noticePayDays, BigDecimal noticeRecoverDays, Boolean settleLoans, String note) {
    }

    /** One line of the final pay: {@code kind} EARNING or DEDUCTION. */
    public record FinalPayLineDto(String name, String kind, BigDecimal days, BigDecimal amount, String note) {
    }

    public record FinalPayLeaveDto(String leaveTypeName, BigDecimal remaining) {
    }

    public record FinalPayLoanDto(Long loanId, String loanNumber, BigDecimal balance) {
    }

    /**
     * What an employee who left is owed or owes. {@code applied}: the lines are already on the payroll of {@code year}/{@code month} as adjustments.
     * {@code blocked} says why it can't be (re)applied now (employee has not left, that month's payroll is already approved...).
     */
    public record FinalPayDto(Long employeeId, String employeeNumber, String employeeName, LocalDate lastDay, int year, int month, BigDecimal dailyRate, BigDecimal baseSalary,
                              int workingDays, BigDecimal leaveDays, BigDecimal suggestedLeaveDays, List<FinalPayLeaveDto> leave, BigDecimal noticePayDays, BigDecimal noticeRecoverDays,
                              boolean settleLoans, List<FinalPayLoanDto> loans, BigDecimal loansBalance, List<FinalPayLineDto> lines, BigDecimal earnings, BigDecimal deductions,
                              boolean applied, PayrollStatus runStatus, String blocked, String note) {
    }

    // ---- loans ----

    public record LoanRequest(Long employeeId, LoanType loanType, BigDecimal principal, BigDecimal installment, LocalDate issueDate, Integer firstYear, Integer firstMonth, String note) {
    }

    public record RepayRequest(BigDecimal amount, LocalDate date, String note) {
    }

    public record NoteRequest(String note) {
    }

    public record LoanDto(Long id, String loanNumber, Long employeeId, String employeeNumber, String employeeName, LoanType loanType, BigDecimal principal, BigDecimal installment,
                          LocalDate issueDate, int firstYear, int firstMonth, LoanStatus status, BigDecimal repaid, BigDecimal balance, Integer installmentsLeft, String note,
                          String createdByName, Instant createdAt) {
    }

    public record RepaymentDto(Long id, BigDecimal amount, LocalDate repaidOn, RepaymentSource source, Long runId, String note, String createdByName) {
    }

    public record LoanDetailDto(LoanDto loan, List<RepaymentDto> repayments) {
    }

    /** What a loan will take from one employee's pay in a month. */
    public record Due(StaffLoan loan, BigDecimal amount, BigDecimal balance) {
    }

    // ---- remittances (PAYE, social security, other payables) ----

    public record RemittanceRequest(RemittanceKind kind, Integer year, Integer month, java.math.BigDecimal amount, LocalDate paidOn, String reference, String paidTo, String note) {
    }

    public record RemittanceDto(Long id, RemittanceKind kind, String kindLabel, int year, int month, java.math.BigDecimal amount, LocalDate paidOn, String reference, String paidTo, String note,
                                String status, String voidNote, String createdByName) {
    }

    /** One tax / contribution of one month: what the approved payroll withheld or owes, what has been paid, and what is left. */
    public record RemittanceRowDto(int year, int month, Long runId, PayrollStatus runStatus, RemittanceKind kind, String kindLabel, java.math.BigDecimal owed, java.math.BigDecimal paid,
                                   java.math.BigDecimal outstanding, LocalDate dueDate, boolean overdue) {
    }

    public record KindTotalDto(RemittanceKind kind, String kindLabel, java.math.BigDecimal owed, java.math.BigDecimal paid, java.math.BigDecimal outstanding, java.math.BigDecimal overdue) {
    }

    /** {@code totals} cover every month; {@code rows} only the chosen year (all years when none is chosen). */
    public record RemittanceSummaryDto(List<KindTotalDto> totals, List<RemittanceRowDto> rows) {
    }
}
