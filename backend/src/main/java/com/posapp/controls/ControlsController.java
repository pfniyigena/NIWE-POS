package com.posapp.controls;

import com.posapp.controls.ControlsDtos.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code /api/accounting/controls}: the closing date, bank reconciliation and the opening balances of the ledger. Seeing needs the Accounting area (or admin); reconciling
 * needs Accounting management (or admin); closing/reopening the books and recording or reversing opening balances are admin only.
 */
@RestController
@RequestMapping("/api/accounting/controls")
public class ControlsController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final AccountingLockService lockService;
    private final BankReconciliationService reconciliations;
    private final LedgerOpeningService opening;

    public ControlsController(AccountingLockService lockService, BankReconciliationService reconciliations, LedgerOpeningService opening) {
        this.lockService = lockService;
        this.reconciliations = reconciliations;
        this.opening = opening;
    }

    // ---- books closed up to ----

    @PreAuthorize(VIEW)
    @GetMapping("/lock")
    public AccountingLockService.LockState lock() {
        return lockService.state();
    }

    @PreAuthorize(ADMIN)
    @PutMapping("/lock")
    public AccountingLockService.LockState setLock(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody LockRequest request) {
        return lockService.set(request.lockDate(), request.reason(), me.getUsername());
    }

    // ---- bank reconciliation ----

    @PreAuthorize(VIEW)
    @GetMapping("/money-accounts")
    public List<MoneyAccount> moneyAccounts() {
        return reconciliations.moneyAccounts();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/reconciliations")
    public List<ReconRow> reconciliations() {
        return reconciliations.list();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/reconciliations/{id}")
    public ReconDetail reconciliation(@PathVariable Long id, @RequestParam(required = false) LocalDate from) {
        return reconciliations.detail(id, from);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reconciliations")
    public ReconDetail startReconciliation(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ReconStart request) {
        return reconciliations.start(request, me.getUsername());
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/reconciliations/{id}")
    public ReconDetail updateReconciliation(@PathVariable Long id, @RequestBody ReconUpdate request) {
        return reconciliations.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reconciliations/{id}/tick")
    public ReconDetail tick(@PathVariable Long id, @RequestBody TickRequest request) {
        return reconciliations.tick(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reconciliations/{id}/tick-all")
    public ReconDetail tickAll(@PathVariable Long id, @RequestBody TickAllRequest request) {
        return reconciliations.tickAll(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reconciliations/{id}/adjustment")
    public ReconDetail adjustment(@PathVariable Long id, @RequestBody AdjustmentRequest request) {
        return reconciliations.adjustment(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/reconciliations/{id}/finish")
    public ReconDetail finish(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return reconciliations.finish(id, me.getUsername());
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/reconciliations/{id}")
    public void cancel(@PathVariable Long id) {
        reconciliations.cancel(id);
    }

    @PreAuthorize(ADMIN)
    @PostMapping("/reconciliations/{id}/undo")
    public void undo(@PathVariable Long id) {
        reconciliations.undo(id);
    }

    // ---- opening balances of the ledger ----

    @PreAuthorize(VIEW)
    @GetMapping("/opening-balances")
    public OpeningState openingBalances() {
        return opening.state();
    }

    @PreAuthorize(ADMIN)
    @PostMapping("/opening-balances")
    public OpeningState recordOpening(@RequestBody OpeningRequest request) {
        return opening.record(request);
    }

    @PreAuthorize(ADMIN)
    @PostMapping("/opening-balances/reverse")
    public OpeningState reverseOpening() {
        return opening.reverse();
    }
}
