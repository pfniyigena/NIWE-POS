package com.posapp.law;

import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawPrintDtos.*;
import com.posapp.law.dto.LawTrustDtos.*;
import com.posapp.law.dto.LawTrustRequests.*;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The firm's client money (trust) account. Every movement is kept in a ledger per matter (a client's balance is the sum of its
 * matters) and posted to the general ledger: money held for clients is a bank asset and an equal liability, never mixed with the firm's
 * own money. Money in is posted at once; money out (a payout or a fee transfer to an issued invoice) is asked for by one person and
 * approved by another (the matter's lead lawyer or an admin), and a client's balance can never go below zero. Posted rows are never
 * changed: a mistake is taken back with a reversal.
 */
@Service
public class LawTrustService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("9999999999999.99");

    private final LawAccess access;
    private final LawSettingsRepository settingsRepository;
    private final LawTrustTransactionRepository repository;
    private final LawTrustReconciliationRepository reconciliationRepository;
    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawBillRepository billRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerCreditService creditService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;

    public LawTrustService(LawAccess access, LawSettingsRepository settingsRepository, LawTrustTransactionRepository repository,
                           LawTrustReconciliationRepository reconciliationRepository, LawMatterRepository matterRepository,
                           LawMatterTeamRepository teamRepository, LawBillRepository billRepository, InvoiceRepository invoiceRepository,
                           CustomerCreditService creditService, AccountingService accountingService, AuditLogService auditLogService) {
        this.access = access;
        this.settingsRepository = settingsRepository;
        this.repository = repository;
        this.reconciliationRepository = reconciliationRepository;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.billRepository = billRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditService = creditService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ small helpers

    private LawSettings settings() {
        List<LawSettings> all = settingsRepository.findAll();
        return all.isEmpty() ? settingsRepository.save(new LawSettings()) : all.get(0);
    }

    /** Takes the settings row lock, so two people moving money at the same moment cannot both spend the same balance. */
    private void lock() {
        settings();
        settingsRepository.lockAll();
    }

    private void requireOn() {
        access.on();
        if (!settings().isTrustEnabled()) {
            throw ApiException.badRequest("The client money (trust) account is switched off. An admin can switch it on in Law Firm > Settings.");
        }
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static BigDecimal money(BigDecimal v, String what) {
        if (v == null || v.signum() <= 0) throw ApiException.badRequest(what + " must be more than zero.");
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP);
        if (r.signum() <= 0 || r.compareTo(MAX) > 0) throw ApiException.badRequest(what + " is not a valid amount.");
        return r;
    }

    private static BigDecimal signed(LawTrustTransaction t) {
        return t.getType() == TrustType.DEPOSIT ? t.getAmount() : t.getAmount().negate();
    }

    private static BigDecimal balanceOf(List<LawTrustTransaction> txs) {
        return txs.stream().filter(t -> t.getStatus() == TrustStatus.POSTED).map(LawTrustService::signed).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal pendingOf(List<LawTrustTransaction> txs) {
        return txs.stream().filter(t -> t.getStatus() == TrustStatus.PENDING).map(LawTrustTransaction::getAmount).reduce(ZERO, BigDecimal::add);
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean canMove(LawMatter m, Actor a, boolean canBill) {
        return access.canWork(m, a) || (canBill && access.canView(m, a));
    }

    private boolean isLead(LawMatter m, Actor a) {
        return a.admin() || teamRepository.existsByMatterIdAndUserIdAndRole(m.getId(), a.user().getId(), TeamRole.LEAD);
    }

    private LawMatter movable(Actor a, boolean canBill, Long matterId) {
        requireOn();
        LawMatter m = access.viewable(a, matterId);
        if (!canMove(m, a, canBill)) throw ApiException.forbidden("Only the matter's team, a manager or billing staff can record client money.");
        return m;
    }

    private LawTrustTransaction newTx(LawMatter m, TrustType type, TrustStatus status, BigDecimal amount, AppUser by) {
        LawTrustTransaction t = new LawTrustTransaction();
        t.setMatterId(m.getId());
        t.setMatterNumber(m.getNumber());
        t.setClientCustomerId(m.getClientCustomerId());
        t.setClientName(m.getClientName());
        t.setType(type);
        t.setStatus(status);
        t.setAmount(amount);
        t.setBranchId(m.getBranchId());
        t.setRequestedByUserId(by.getId());
        t.setRequestedByName(LawAccess.name(by));
        return t;
    }

    private LawTrustTransaction saveNew(LawTrustTransaction t) {
        LawTrustTransaction saved = repository.save(t);
        saved.setNumber(String.format("TR-%06d", saved.getId()));
        return repository.save(saved);
    }

    private static LocalDate dateOrToday(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d == null) return today;
        if (d.isAfter(today)) throw ApiException.badRequest("The date cannot be in the future.");
        return d;
    }

    /** What the firm holds in client money for a matter (posted movements only). */
    @Transactional(readOnly = true)
    public BigDecimal heldFor(Long matterId) {
        return balanceOf(repository.findByMatterIdOrderByIdAsc(matterId));
    }

    private BigDecimal available(Long matterId) {
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(matterId);
        return balanceOf(txs).subtract(pendingOf(txs));
    }

    // ------------------------------------------------------------------ facts other parts of the module need

    /** True when any client money is held or any payout is waiting. */
    @Transactional(readOnly = true)
    public boolean hasMoneyOrRequests() {
        List<LawTrustTransaction> all = repository.findAllByOrderByIdAsc();
        if (all.stream().anyMatch(t -> t.getStatus() == TrustStatus.PENDING)) return true;
        Map<Long, List<LawTrustTransaction>> byMatter = all.stream().collect(Collectors.groupingBy(LawTrustTransaction::getMatterId));
        return byMatter.values().stream().anyMatch(l -> balanceOf(l).signum() != 0);
    }

    /** A matter cannot be closed while it still holds client money or has a request waiting. */
    @Transactional(readOnly = true)
    public void requireNothingHeld(LawMatter m) {
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(m.getId());
        BigDecimal held = balanceOf(txs);
        if (held.signum() != 0) {
            throw ApiException.conflict("Client money of " + fmt(held) + " is still held for this matter. Pay it out or move it to the firm before closing the matter.");
        }
        if (pendingOf(txs).signum() != 0) {
            throw ApiException.conflict("A payout or fee transfer for this matter is still waiting for approval. Approve, reject or cancel it before closing the matter.");
        }
    }

    /** True when the matter has any client-money movement, so it can no longer be deleted. */
    @Transactional(readOnly = true)
    public boolean hasRecords(LawMatter m) {
        return repository.countByMatterId(m.getId()) > 0;
    }

    // ------------------------------------------------------------------ views

    private TrustTxDto toDto(LawTrustTransaction t, LawMatter m, Actor a, boolean lead, BigDecimal balanceAfter) {
        boolean pending = t.getStatus() == TrustStatus.PENDING;
        boolean mine = t.getRequestedByUserId() != null && t.getRequestedByUserId().equals(a.user().getId());
        boolean canApprove = pending && lead && !mine;
        boolean canCancel = pending && (mine || lead);
        boolean canReverse = t.getType() == TrustType.DEPOSIT && t.getStatus() == TrustStatus.POSTED && !t.isReversed() && m != null && access.canLead(m, a);
        return new TrustTxDto(t.getId(), t.getNumber(), t.getType(), t.getStatus(), t.getMatterId(), t.getMatterNumber(), t.getClientCustomerId(), t.getClientName(),
                t.getAmount(), t.getTxDate(), t.getMethod(), t.getReference(), t.getPartyName(), t.getPurpose(), t.getRequestedByName(), t.getCreatedAt(),
                t.getDecidedByName(), t.getDecidedAt(), t.getDecisionNote(), t.getInvoiceId(), t.getInvoiceNumber(), t.getReversalOfId(), t.isReversed(),
                balanceAfter, canApprove, canCancel, canReverse);
    }

    private List<TrustTxDto> dtos(List<LawTrustTransaction> txs, LawMatter m, Actor a) {
        boolean lead = isLead(m, a);
        BigDecimal running = ZERO;
        List<TrustTxDto> out = new ArrayList<>();
        for (LawTrustTransaction t : txs) {
            BigDecimal after = null;
            if (t.getStatus() == TrustStatus.POSTED) {
                running = running.add(signed(t));
                after = running;
            }
            out.add(toDto(t, m, a, lead, after));
        }
        java.util.Collections.reverse(out);
        return out;
    }

    private List<TrustInvoiceDto> payableInvoices(LawMatter m, List<LawTrustTransaction> txs) {
        List<TrustInvoiceDto> out = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (LawBill b : billRepository.findByMatterIdOrderByIdDesc(m.getId())) {
            if (b.getStatus() != BillStatus.ACTIVE || b.getInvoiceId() == null || !seen.add(b.getInvoiceId())) continue;
            Invoice inv = invoiceRepository.findById(b.getInvoiceId()).orElse(null);
            if (inv == null || inv.getStatus() != InvoiceStatus.ISSUED) continue;
            BigDecimal outstanding = creditService.outstandingForInvoice(inv);
            if (outstanding.signum() <= 0) continue;
            BigDecimal waiting = txs.stream().filter(t -> t.getStatus() == TrustStatus.PENDING && t.getType() == TrustType.FEE_TRANSFER && inv.getId().equals(t.getInvoiceId()))
                    .map(LawTrustTransaction::getAmount).reduce(ZERO, BigDecimal::add);
            out.add(new TrustInvoiceDto(inv.getId(), inv.getInvoiceNumber(), inv.getTotalAmount(), outstanding, waiting));
        }
        return out;
    }

    private MatterTrustDto matterView(Actor a, boolean canBill, LawMatter m) {
        LawSettings s = settings();
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(m.getId());
        BigDecimal balance = balanceOf(txs);
        BigDecimal pending = pendingOf(txs);
        boolean canRecord = s.isTrustEnabled() && canMove(m, a, canBill);
        return new MatterTrustDto(m.getId(), m.getNumber(), m.getTitle(), m.getClientCustomerId(), m.getClientName(), m.getStatus(), s.isTrustEnabled(),
                s.getTrustBankName(), s.getTrustAccountNumber(), balance, pending, balance.subtract(pending), dtos(txs, m, a),
                canRecord ? payableInvoices(m, txs) : List.of(), canRecord, canRecord && m.getStatus() != MatterStatus.CLOSED);
    }

    /** One matter's client-money ledger. */
    @Transactional
    public MatterTrustDto matter(Actor a, boolean canBill, Long matterId) {
        access.on();
        return matterView(a, canBill, access.viewable(a, matterId));
    }

    /** Every matter that holds or has held client money, with what is waiting for approval. Only matters the person may see. */
    @Transactional
    public TrustOverviewDto overview(Actor a, boolean canBill) {
        access.on();
        LawSettings s = settings();
        if (!s.isTrustEnabled()) {
            return new TrustOverviewDto(false, s.getTrustBankName(), s.getTrustAccountNumber(), ZERO, ZERO, 0, List.of(), List.of(), canBill);
        }
        Map<Long, List<LawTrustTransaction>> byMatter = repository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(LawTrustTransaction::getMatterId, LinkedHashMap::new, Collectors.toList()));
        List<TrustBalanceRow> rows = new ArrayList<>();
        List<TrustTxDto> approvals = new ArrayList<>();
        BigDecimal held = ZERO;
        BigDecimal waiting = ZERO;
        Set<Long> clients = new java.util.HashSet<>();
        for (Map.Entry<Long, List<LawTrustTransaction>> e : byMatter.entrySet()) {
            LawMatter m = matterRepository.findById(e.getKey()).orElse(null);
            if (m == null || !access.canView(m, a)) continue;
            List<LawTrustTransaction> txs = e.getValue();
            BigDecimal balance = balanceOf(txs);
            BigDecimal pending = pendingOf(txs);
            LocalDate last = txs.stream().map(LawTrustTransaction::getTxDate).max(LocalDate::compareTo).orElse(null);
            rows.add(new TrustBalanceRow(m.getId(), m.getNumber(), m.getTitle(), m.getStatus(), m.getClientCustomerId(), m.getClientName(), balance, pending,
                    balance.subtract(pending), last));
            held = held.add(balance);
            waiting = waiting.add(pending);
            if (balance.signum() != 0) clients.add(m.getClientCustomerId());
            boolean lead = isLead(m, a);
            for (LawTrustTransaction t : txs) {
                if (t.getStatus() == TrustStatus.PENDING) approvals.add(toDto(t, m, a, lead, null));
            }
        }
        rows.sort((x, y) -> y.balance().compareTo(x.balance()));
        return new TrustOverviewDto(true, s.getTrustBankName(), s.getTrustAccountNumber(), held, waiting, clients.size(), rows, approvals, canBill);
    }

    // ------------------------------------------------------------------ money in

    @Transactional
    public MatterTrustDto deposit(Actor a, boolean canBill, Long matterId, TrustDepositRequest r) {
        LawMatter m = movable(a, canBill, matterId);
        if (m.getStatus() == MatterStatus.CLOSED) throw ApiException.conflict("This matter is closed. Reopen it before taking client money for it.");
        if (!m.isConflictCleared()) throw ApiException.conflict("Clear the conflict check for this matter before taking client money for it.");
        BigDecimal amount = money(r == null ? null : r.amount(), "The amount");
        String method = clean(r.method());
        if (method == null) throw ApiException.badRequest("Say how the money was paid (bank transfer, cheque, cash, mobile money...).");
        lock();
        LawTrustTransaction t = newTx(m, TrustType.DEPOSIT, TrustStatus.POSTED, amount, a.user());
        t.setTxDate(dateOrToday(r.date()));
        t.setMethod(method);
        t.setReference(clean(r.reference()));
        t.setPartyName(clean(r.payerName()));
        t.setPurpose(clean(r.purpose()));
        t.setPostedAt(Instant.now());
        t = saveNew(t);
        accountingService.postLawTrustDeposit(t.getId(), "Client money received — " + m.getClientName() + " — matter " + m.getNumber() + " (" + t.getNumber() + ")", amount, m.getBranchId());
        auditLogService.record("LAW_TRUST_DEPOSIT", "Received " + fmt(amount) + " of client money for matter " + m.getNumber() + " (" + t.getNumber() + ")", "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    // ------------------------------------------------------------------ money out: ask

    @Transactional
    public MatterTrustDto requestPayout(Actor a, boolean canBill, Long matterId, TrustPayoutRequest r) {
        LawMatter m = movable(a, canBill, matterId);
        BigDecimal amount = money(r == null ? null : r.amount(), "The amount");
        String method = clean(r.method());
        String payee = clean(r.payeeName());
        String purpose = clean(r.purpose());
        if (payee == null) throw ApiException.badRequest("Say who is being paid.");
        if (method == null) throw ApiException.badRequest("Say how the money will be paid (bank transfer, cheque, cash...).");
        if (purpose == null) throw ApiException.badRequest("Say what the payment is for.");
        lock();
        BigDecimal avail = available(m.getId());
        if (amount.compareTo(avail) > 0) throw notEnough(m.getId(), avail);
        LawTrustTransaction t = newTx(m, TrustType.PAYOUT, TrustStatus.PENDING, amount, a.user());
        t.setTxDate(dateOrToday(r.date()));
        t.setMethod(method);
        t.setReference(clean(r.reference()));
        t.setPartyName(payee);
        t.setPurpose(purpose);
        t = saveNew(t);
        auditLogService.record("LAW_TRUST_PAYOUT_REQUESTED", "Asked to pay out " + fmt(amount) + " of client money from matter " + m.getNumber() + " (" + t.getNumber() + ")", "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    private ApiException notEnough(Long matterId, BigDecimal avail) {
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(matterId);
        return ApiException.conflict("There is not enough of this client's money for that: " + fmt(avail.max(ZERO)) + " is available (" + fmt(balanceOf(txs)) + " held, "
                + fmt(pendingOf(txs)) + " already waiting to be paid out).");
    }

    @Transactional
    public MatterTrustDto requestFeeTransfer(Actor a, boolean canBill, Long matterId, TrustFeeTransferRequest r) {
        LawMatter m = movable(a, canBill, matterId);
        if (r == null || r.invoiceId() == null) throw ApiException.badRequest("Choose the invoice to pay.");
        lock();
        Invoice inv = payableInvoice(m, r.invoiceId());
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(m.getId());
        BigDecimal outstanding = creditService.outstandingForInvoice(inv);
        BigDecimal waiting = txs.stream().filter(t -> t.getStatus() == TrustStatus.PENDING && t.getType() == TrustType.FEE_TRANSFER && inv.getId().equals(t.getInvoiceId()))
                .map(LawTrustTransaction::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal room = outstanding.subtract(waiting);
        BigDecimal avail = balanceOf(txs).subtract(pendingOf(txs));
        BigDecimal amount = r.amount() != null ? money(r.amount(), "The amount") : room.min(avail).max(ZERO);
        if (amount.signum() <= 0) {
            throw ApiException.conflict(room.signum() <= 0 ? "Invoice " + inv.getInvoiceNumber() + " has nothing left to pay (or all of it is already waiting for approval)."
                    : "This client has no money available to pay it with.");
        }
        if (amount.compareTo(room) > 0) {
            throw ApiException.conflict("That is more than invoice " + inv.getInvoiceNumber() + " still owes: " + fmt(room.max(ZERO)) + " can be paid"
                    + (waiting.signum() > 0 ? " (" + fmt(waiting) + " more is already waiting for approval)." : "."));
        }
        if (amount.compareTo(avail) > 0) throw notEnough(m.getId(), avail);
        LawTrustTransaction t = newTx(m, TrustType.FEE_TRANSFER, TrustStatus.PENDING, amount, a.user());
        t.setTxDate(LocalDate.now());
        t.setInvoiceId(inv.getId());
        t.setInvoiceNumber(inv.getInvoiceNumber());
        t.setPartyName("Firm");
        t.setPurpose(clean(r.purpose()) != null ? clean(r.purpose()) : "Fees on invoice " + inv.getInvoiceNumber());
        t = saveNew(t);
        auditLogService.record("LAW_TRUST_FEE_REQUESTED", "Asked to move " + fmt(amount) + " of client money to pay invoice " + inv.getInvoiceNumber() + " (" + t.getNumber() + ")", "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    /** The invoice must be one of this matter's live bills, issued, for this client, with something still owing. */
    private Invoice payableInvoice(LawMatter m, Long invoiceId) {
        boolean billed = billRepository.findByMatterIdOrderByIdDesc(m.getId()).stream()
                .anyMatch(b -> b.getStatus() == BillStatus.ACTIVE && invoiceId.equals(b.getInvoiceId()));
        if (!billed) throw ApiException.conflict("That invoice was not billed from this matter.");
        Invoice inv = invoiceRepository.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice not found."));
        if (inv.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.conflict("Only an issued invoice can be paid from client money. Invoice " + inv.getInvoiceNumber() + " is " + inv.getStatus() + ".");
        }
        if (inv.getCustomerId() == null || !inv.getCustomerId().equals(m.getClientCustomerId())) {
            throw ApiException.conflict("Invoice " + inv.getInvoiceNumber() + " is not addressed to this matter's client, so it cannot be paid with this client's money.");
        }
        return inv;
    }

    // ------------------------------------------------------------------ money out: decide

    private LawTrustTransaction pendingTx(Long id) {
        LawTrustTransaction t = repository.findById(id).orElseThrow(() -> ApiException.notFound("Request not found."));
        if (t.getStatus() != TrustStatus.PENDING) {
            throw ApiException.conflict("This request is already " + t.getStatus().name().toLowerCase() + ".");
        }
        return t;
    }

    @Transactional
    public MatterTrustDto approve(Actor a, boolean canBill, Long id, TrustNoteRequest r) {
        requireOn();
        lock();
        LawTrustTransaction t = pendingTx(id);
        LawMatter m = access.viewable(a, t.getMatterId());
        if (t.getRequestedByUserId() != null && t.getRequestedByUserId().equals(a.user().getId())) {
            throw ApiException.forbidden("A different person must approve this. You asked for it yourself.");
        }
        if (!isLead(m, a)) throw ApiException.forbidden("Only the matter's lead lawyer or an admin can approve a payout or a fee transfer.");
        List<LawTrustTransaction> txs = repository.findByMatterIdOrderByIdAsc(m.getId());
        BigDecimal held = balanceOf(txs);
        if (t.getAmount().compareTo(held) > 0) {
            throw ApiException.conflict("This client now has only " + fmt(held) + " held, which is less than this request. Reject it.");
        }
        String memo;
        if (t.getType() == TrustType.FEE_TRANSFER) {
            Invoice inv = payableInvoice(m, t.getInvoiceId());
            if (t.getAmount().compareTo(creditService.outstandingForInvoice(inv)) > 0) {
                throw ApiException.conflict("Invoice " + inv.getInvoiceNumber() + " no longer owes that much. Reject this request.");
            }
            CustomerPaymentDto payment = creditService.recordInvoicePaymentFromTrust(inv.getId(), t.getAmount(),
                    "Paid from client money held in trust (" + t.getNumber() + ")", a.user());
            t.setPaymentId(payment.id());
            memo = "Fees taken from client money — " + m.getClientName() + " — invoice " + inv.getInvoiceNumber() + " (" + t.getNumber() + ")";
            accountingService.postLawTrustFeeTransfer(t.getId(), memo, t.getAmount(), m.getBranchId());
        } else {
            memo = "Client money paid out — " + m.getClientName() + " — " + (t.getPartyName() != null ? t.getPartyName() : "") + " — matter " + m.getNumber() + " (" + t.getNumber() + ")";
            accountingService.postLawTrustPayout(t.getId(), "LAW_TRUST_PAYOUT", memo, t.getAmount(), m.getBranchId());
        }
        t.setStatus(TrustStatus.POSTED);
        t.setPostedAt(Instant.now());
        t.setDecidedByUserId(a.user().getId());
        t.setDecidedByName(LawAccess.name(a.user()));
        t.setDecidedAt(Instant.now());
        t.setDecisionNote(r == null ? null : clean(r.note()));
        repository.save(t);
        auditLogService.record("LAW_TRUST_APPROVED", "Approved " + t.getNumber() + ": " + fmt(t.getAmount()) + " " + t.getType().name().toLowerCase().replace('_', ' ') + " for matter " + m.getNumber(), "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    @Transactional
    public MatterTrustDto reject(Actor a, boolean canBill, Long id, TrustNoteRequest r) {
        requireOn();
        lock();
        LawTrustTransaction t = pendingTx(id);
        LawMatter m = access.viewable(a, t.getMatterId());
        if (!isLead(m, a)) throw ApiException.forbidden("Only the matter's lead lawyer or an admin can reject a payout or a fee transfer.");
        String why = r == null ? null : clean(r.note());
        if (why == null) throw ApiException.badRequest("Say why you are rejecting it.");
        t.setStatus(TrustStatus.REJECTED);
        t.setDecidedByUserId(a.user().getId());
        t.setDecidedByName(LawAccess.name(a.user()));
        t.setDecidedAt(Instant.now());
        t.setDecisionNote(why);
        repository.save(t);
        auditLogService.record("LAW_TRUST_REJECTED", "Rejected " + t.getNumber() + " for matter " + m.getNumber() + ": " + why, "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    @Transactional
    public MatterTrustDto cancel(Actor a, boolean canBill, Long id) {
        requireOn();
        lock();
        LawTrustTransaction t = pendingTx(id);
        LawMatter m = access.viewable(a, t.getMatterId());
        boolean mine = t.getRequestedByUserId() != null && t.getRequestedByUserId().equals(a.user().getId());
        if (!mine && !isLead(m, a)) throw ApiException.forbidden("Only the person who asked for it, the lead lawyer or an admin can cancel this request.");
        t.setStatus(TrustStatus.CANCELLED);
        t.setDecidedByUserId(a.user().getId());
        t.setDecidedByName(LawAccess.name(a.user()));
        t.setDecidedAt(Instant.now());
        t.setDecisionNote("Cancelled");
        repository.save(t);
        auditLogService.record("LAW_TRUST_CANCELLED", "Cancelled " + t.getNumber() + " for matter " + m.getNumber(), "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    /** Takes a wrongly entered or bounced deposit back out. Lead lawyer, manager or admin; the money must still be there. */
    @Transactional
    public MatterTrustDto reverseDeposit(Actor a, boolean canBill, Long id, TrustReverseRequest r) {
        requireOn();
        lock();
        LawTrustTransaction dep = repository.findById(id).orElseThrow(() -> ApiException.notFound("Deposit not found."));
        LawMatter m = access.viewable(a, dep.getMatterId());
        if (!access.canLead(m, a)) throw ApiException.forbidden("Only the lead lawyer, a manager or an admin can take a deposit back.");
        if (dep.getType() != TrustType.DEPOSIT || dep.getStatus() != TrustStatus.POSTED) throw ApiException.conflict("Only a posted deposit can be taken back.");
        if (dep.isReversed()) throw ApiException.conflict("This deposit has already been taken back.");
        String why = r == null ? null : clean(r.reason());
        if (why == null) throw ApiException.badRequest("Say why the deposit is being taken back (for example: the cheque bounced).");
        BigDecimal avail = available(m.getId());
        if (dep.getAmount().compareTo(avail) > 0) {
            throw ApiException.conflict("Part of this money has already been paid out or is waiting to be paid out. Only " + fmt(avail.max(ZERO)) + " is free to take back.");
        }
        LawTrustTransaction t = newTx(m, TrustType.DEPOSIT_REVERSAL, TrustStatus.POSTED, dep.getAmount(), a.user());
        t.setTxDate(LocalDate.now());
        t.setPartyName(dep.getPartyName());
        t.setReference(dep.getReference());
        t.setPurpose("Reverses " + dep.getNumber() + ": " + why);
        t.setReversalOfId(dep.getId());
        t.setPostedAt(Instant.now());
        t.setDecidedByUserId(a.user().getId());
        t.setDecidedByName(LawAccess.name(a.user()));
        t.setDecidedAt(Instant.now());
        t.setDecisionNote(why);
        t = saveNew(t);
        dep.setReversed(true);
        repository.save(dep);
        accountingService.postLawTrustPayout(t.getId(), "LAW_TRUST_REVERSAL", "Client money deposit taken back — " + m.getClientName() + " — " + dep.getNumber() + ": " + why, t.getAmount(), m.getBranchId());
        auditLogService.record("LAW_TRUST_REVERSED", "Took back deposit " + dep.getNumber() + " (" + fmt(dep.getAmount()) + ") for matter " + m.getNumber() + ": " + why, "LawTrustTransaction", t.getId());
        return matterView(a, canBill, m);
    }

    // ------------------------------------------------------------------ reconciliation

    private ReconciliationDto toDto(LawTrustReconciliation x) {
        return new ReconciliationDto(x.getId(), x.getAsOfDate(), x.getStatementBalance(), x.getDepositsInTransit(), x.getOutstandingPayments(), x.getAdjustedBank(),
                x.getLedgerTotal(), x.getGlBank(), x.getGlLiability(), x.getDifference(), x.isBalanced(), x.getNote(), x.getPreparedByName(), x.getCreatedAt());
    }

    private void needBilling(boolean canBill) {
        requireOn();
        if (!canBill) throw ApiException.forbidden("Only an admin or billing staff can reconcile the trust account.");
    }

    /** The three figures that must agree: client ledgers, trust bank and client money held in the general ledger. */
    @Transactional
    public ReconciliationView reconciliation(boolean canBill) {
        needBilling(canBill);
        List<LawTrustTransaction> all = repository.findAllByOrderByIdAsc();
        BigDecimal ledger = balanceOf(all);
        return new ReconciliationView(ledger, pendingOf(all), accountingService.balanceOf(AccountService.TRUST_BANK, true),
                accountingService.balanceOf(AccountService.CLIENT_MONEY_HELD, false), reconciliationRepository.findAllByOrderByAsOfDateDescIdDesc().stream().map(this::toDto).toList());
    }

    @Transactional
    public ReconciliationView reconcile(Actor a, boolean canBill, TrustReconcileRequest r) {
        needBilling(canBill);
        if (r == null || r.statementBalance() == null) throw ApiException.badRequest("Enter the closing balance on the bank statement.");
        BigDecimal statement = r.statementBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal inTransit = r.depositsInTransit() == null ? ZERO : r.depositsInTransit().setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = r.outstandingPayments() == null ? ZERO : r.outstandingPayments().setScale(2, RoundingMode.HALF_UP);
        if (inTransit.signum() < 0 || outstanding.signum() < 0) throw ApiException.badRequest("Deposits in transit and outstanding payments cannot be negative.");
        lock();
        List<LawTrustTransaction> all = repository.findAllByOrderByIdAsc();
        BigDecimal ledger = balanceOf(all);
        BigDecimal glBank = accountingService.balanceOf(AccountService.TRUST_BANK, true);
        BigDecimal glLiability = accountingService.balanceOf(AccountService.CLIENT_MONEY_HELD, false);
        BigDecimal adjusted = statement.add(inTransit).subtract(outstanding);
        BigDecimal difference = adjusted.subtract(ledger);
        LawTrustReconciliation x = new LawTrustReconciliation();
        x.setAsOfDate(dateOrToday(r.asOfDate()));
        x.setStatementBalance(statement);
        x.setDepositsInTransit(inTransit);
        x.setOutstandingPayments(outstanding);
        x.setAdjustedBank(adjusted);
        x.setLedgerTotal(ledger);
        x.setGlBank(glBank);
        x.setGlLiability(glLiability);
        x.setDifference(difference);
        x.setBalanced(difference.signum() == 0 && glBank.compareTo(ledger) == 0 && glLiability.compareTo(ledger) == 0);
        x.setNote(clean(r.note()));
        x.setPreparedByUserId(a.user().getId());
        x.setPreparedByName(LawAccess.name(a.user()));
        reconciliationRepository.save(x);
        auditLogService.record("LAW_TRUST_RECONCILED", "Trust account reconciliation as of " + x.getAsOfDate() + (x.isBalanced() ? ": balanced" : ": difference " + fmt(difference)), "LawTrustReconciliation", x.getId());
        return reconciliation(canBill);
    }

    // ------------------------------------------------------------------ papers

    private static String what(TrustType t) {
        return switch (t) {
            case DEPOSIT -> "Money received";
            case PAYOUT -> "Paid out";
            case FEE_TRANSFER -> "Fees taken";
            case DEPOSIT_REVERSAL -> "Deposit reversed";
        };
    }

    /** The facts of one posted movement for its receipt (money in) or payment voucher (money out). Only posted ones; a reversed deposit has no receipt. */
    @Transactional(readOnly = true)
    public TrustReceiptData receiptData(Actor a, Long txId) {
        access.on();
        LawTrustTransaction t = repository.findById(txId).orElseThrow(() -> ApiException.notFound("Client money entry not found."));
        LawMatter m = access.viewable(a, t.getMatterId());
        if (t.getStatus() != TrustStatus.POSTED) throw ApiException.conflict("Only a posted entry can be printed. This one is " + t.getStatus().name().toLowerCase() + ".");
        if (t.getType() == TrustType.DEPOSIT && t.isReversed()) throw ApiException.conflict("This deposit was reversed, so no receipt can be printed for it.");
        BigDecimal running = ZERO;
        BigDecimal after = ZERO;
        for (LawTrustTransaction x : repository.findByMatterIdOrderByIdAsc(m.getId())) {
            if (x.getStatus() != TrustStatus.POSTED) continue;
            running = running.add(signed(x));
            if (x.getId().equals(t.getId())) after = running;
        }
        LawSettings s = settings();
        return new TrustReceiptData(t.getNumber(), t.getType(), t.getTxDate(), m.getNumber(), m.getTitle(), m.getClientName(), t.getPartyName(), t.getAmount(), t.getMethod(),
                t.getReference(), t.getPurpose(), t.getInvoiceNumber(), t.getRequestedByName(), t.getDecidedByName(), after, s.getTrustBankName(), s.getTrustAccountNumber());
    }

    /** One matter's client ledger for a period: the balance brought forward, each posted movement with its running balance, and what is still waiting. */
    @Transactional(readOnly = true)
    public TrustStatementData statementData(Actor a, Long matterId, LocalDate from, LocalDate to) {
        access.on();
        LawMatter m = access.viewable(a, matterId);
        LocalDate end = to == null ? LocalDate.now() : to;
        if (from != null && from.isAfter(end)) throw ApiException.badRequest("The start date is after the end date.");
        List<LawTrustTransaction> all = repository.findByMatterIdOrderByIdAsc(m.getId());
        List<LawTrustTransaction> posted = all.stream().filter(t -> t.getStatus() == TrustStatus.POSTED)
                .sorted(java.util.Comparator.comparing(LawTrustTransaction::getTxDate).thenComparing(LawTrustTransaction::getId)).toList();
        BigDecimal running = ZERO;
        BigDecimal opening = ZERO;
        BigDecimal in = ZERO;
        BigDecimal out = ZERO;
        List<StatementLine> lines = new ArrayList<>();
        for (LawTrustTransaction t : posted) {
            if (t.getTxDate().isAfter(end)) break;
            boolean before = from != null && t.getTxDate().isBefore(from);
            running = running.add(signed(t));
            if (before) {
                opening = running;
                continue;
            }
            boolean money = t.getType() == TrustType.DEPOSIT;
            if (money) in = in.add(t.getAmount()); else out = out.add(t.getAmount());
            String why = what(t.getType()) + (t.getPurpose() != null && !t.getPurpose().isBlank() ? ": " + t.getPurpose().trim() : "");
            lines.add(new StatementLine(t.getTxDate(), t.getNumber(), why, t.getPartyName(), t.getReference(), money ? t.getAmount() : null, money ? null : t.getAmount(), running));
        }
        List<WaitingLine> waiting = all.stream().filter(t -> t.getStatus() == TrustStatus.PENDING)
                .map(t -> new WaitingLine(t.getTxDate(), t.getNumber(), what(t.getType()), t.getPartyName(), t.getAmount())).toList();
        LawSettings s = settings();
        return new TrustStatementData(m.getNumber(), m.getTitle(), m.getClientName(), s.getTrustBankName(), s.getTrustAccountNumber(), from, end, opening, lines, in, out, running, waiting);
    }

    /** One saved reconciliation with the trust bank details, for printing. Needs billing rights, like the reconciliation itself. */
    @Transactional(readOnly = true)
    public ReconciliationDto reconciliationById(boolean canBill, Long id) {
        needBilling(canBill);
        return toDto(reconciliationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Reconciliation not found.")));
    }

    /** Sending a statement to the client is for the people who may record client money on the matter. */
    @Transactional(readOnly = true)
    public void requireSender(Actor a, boolean canBill, Long matterId) {
        access.on();
        LawMatter m = access.viewable(a, matterId);
        if (!canMove(m, a, canBill)) throw ApiException.forbidden("Only the matter's team, a manager or billing staff can send a client statement.");
    }

    public String trustBank() {
        LawSettings s = settings();
        String bank = s.getTrustBankName() == null ? "" : s.getTrustBankName().trim();
        String acc = s.getTrustAccountNumber() == null ? "" : s.getTrustAccountNumber().trim();
        return (bank + (bank.isEmpty() || acc.isEmpty() ? "" : "  |  ") + acc).trim();
    }

    // ------------------------------------------------------------------ reports

    public record BalanceLine(String matterNumber, String matterTitle, String clientName, String matterStatus, BigDecimal balance, BigDecimal pending, LocalDate lastActivity) {
    }

    public record LedgerLine(LocalDate date, String number, String matterNumber, String clientName, String type, String status, String party, String reference,
                             BigDecimal moneyIn, BigDecimal moneyOut, BigDecimal balance) {
    }

    /** A balance per matter for the matters the caller picks (a report hides what a restricted matter must not show). */
    @Transactional
    public List<BalanceLine> balanceLines(Predicate<LawMatter> include, boolean heldOnly) {
        access.on();
        Map<Long, List<LawTrustTransaction>> byMatter = repository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(LawTrustTransaction::getMatterId, LinkedHashMap::new, Collectors.toList()));
        List<BalanceLine> out = new ArrayList<>();
        for (Map.Entry<Long, List<LawTrustTransaction>> e : byMatter.entrySet()) {
            LawMatter m = matterRepository.findById(e.getKey()).orElse(null);
            if (m == null || !include.test(m)) continue;
            BigDecimal balance = balanceOf(e.getValue());
            BigDecimal pending = pendingOf(e.getValue());
            if (heldOnly && balance.signum() == 0 && pending.signum() == 0) continue;
            out.add(new BalanceLine(m.getNumber(), m.getTitle(), m.getClientName(), m.getStatus().name(), balance, pending,
                    e.getValue().stream().map(LawTrustTransaction::getTxDate).max(LocalDate::compareTo).orElse(null)));
        }
        out.sort((x, y) -> y.balance().compareTo(x.balance()));
        return out;
    }

    /** The posted movements in a period with each matter's running balance. */
    @Transactional
    public List<LedgerLine> ledgerLines(Predicate<LawMatter> include, LocalDate from, LocalDate to) {
        access.on();
        Map<Long, List<LawTrustTransaction>> byMatter = repository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(LawTrustTransaction::getMatterId, LinkedHashMap::new, Collectors.toList()));
        List<LedgerLine> out = new ArrayList<>();
        for (Map.Entry<Long, List<LawTrustTransaction>> e : byMatter.entrySet()) {
            LawMatter m = matterRepository.findById(e.getKey()).orElse(null);
            if (m == null || !include.test(m)) continue;
            BigDecimal running = ZERO;
            for (LawTrustTransaction t : e.getValue()) {
                if (t.getStatus() != TrustStatus.POSTED) continue;
                running = running.add(signed(t));
                if ((from != null && t.getTxDate().isBefore(from)) || (to != null && t.getTxDate().isAfter(to))) continue;
                boolean in = t.getType() == TrustType.DEPOSIT;
                out.add(new LedgerLine(t.getTxDate(), t.getNumber(), t.getMatterNumber(), t.getClientName(), t.getType().name(), t.getStatus().name(),
                        t.getPartyName(), t.getReference(), in ? t.getAmount() : null, in ? null : t.getAmount(), running));
            }
        }
        out.sort((x, y) -> x.date().compareTo(y.date()) != 0 ? x.date().compareTo(y.date()) : x.number().compareTo(y.number()));
        return out;
    }
}
