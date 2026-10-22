package com.posapp.law;

import com.posapp.accounting.ExpenseService;
import com.posapp.accounting.PayeeType;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawBillingDtos.*;
import com.posapp.law.dto.LawBillingRequests.*;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.timetracking.TimeEntryStatus;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.timetracking.dto.TimeDtos.BillingPreviewDto;
import com.posapp.timetracking.dto.TimeDtos.EntryDto;
import com.posapp.timetracking.dto.TimeRequests.BillingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turning a matter's work into money: fee terms and the per-person rates, fixed-fee stages, costs paid for the client, retainers, a contingency
 * fee, and one draft invoice that carries the approved time, the chosen stages and costs and the fee. Hours are logged and approved in Time
 * Tracking on the matter's own project; this service reads them and marks them billed. A retainer is invoiced in advance and then taken off the
 * fees of later invoices (invoice lines cannot be negative, so the fee lines are reduced).
 */
@Service
public class LawBillingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LawAccess access;
    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawFeeStageRepository stageRepository;
    private final LawMatterExpenseRepository costRepository;
    private final LawBillRepository billRepository;
    private final LawTimeLink timeLink;
    private final TimeTrackingService timeService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ExpenseService expenseService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public LawBillingService(LawAccess access, LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository, LawFeeStageRepository stageRepository,
                             LawMatterExpenseRepository costRepository, LawBillRepository billRepository, LawTimeLink timeLink, TimeTrackingService timeService,
                             InvoiceService invoiceService, InvoiceRepository invoiceRepository, CustomerRepository customerRepository, ExpenseService expenseService,
                             ModuleService moduleService, AuditLogService auditLogService) {
        this.access = access;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.stageRepository = stageRepository;
        this.costRepository = costRepository;
        this.billRepository = billRepository;
        this.timeLink = timeLink;
        this.timeService = timeService;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.expenseService = expenseService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static String plain(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ what is still alive

    /** Not taken back, and its invoice (if it has one) still exists and is not void. */
    private boolean live(LawBill b) {
        if (b.getStatus() != BillStatus.ACTIVE) return false;
        if (b.getInvoiceId() == null) return true;
        return invoiceRepository.findById(b.getInvoiceId()).map(i -> i.getStatus() != InvoiceStatus.VOID).orElse(false);
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) {
        return s.reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal retainerBalance(List<LawBill> bills) {
        BigDecimal invoiced = sum(bills.stream().filter(b -> b.getKind() == BillKind.RETAINER && live(b)).map(LawBill::getAmount));
        BigDecimal applied = sum(bills.stream().filter(b -> b.getKind() == BillKind.INVOICE && live(b)).map(LawBill::getRetainerApplied));
        return invoiced.subtract(applied).max(ZERO);
    }

    // ------------------------------------------------------------------ the billing tab

    private BillingSummary summary(LawMatter m, List<EntryDto> time, List<LawMatterExpense> costs, List<LawFeeStage> stages, List<LawBill> bills) {
        BigDecimal hours = sum(time.stream().map(EntryDto::hours));
        BigDecimal billableHours = sum(time.stream().filter(EntryDto::billable).map(EntryDto::hours));
        int waiting = (int) time.stream().filter(e -> e.status() == TimeEntryStatus.SUBMITTED).count();
        BigDecimal unbilledTime = sum(time.stream().filter(e -> e.billable() && e.status() == TimeEntryStatus.APPROVED && e.invoiceId() == null).map(EntryDto::amount));
        BigDecimal unbilledCosts = sum(costs.stream().filter(c -> c.getBillId() == null).map(LawMatterExpense::getChargeAmount));
        BigDecimal planned = sum(stages.stream().filter(s -> s.getStatus() == StageStatus.PLANNED).map(LawFeeStage::getAmount));
        BigDecimal billed = ZERO;
        BigDecimal paid = ZERO;
        BigDecimal outstanding = ZERO;
        BigDecimal retainerInvoiced = ZERO;
        BigDecimal retainerApplied = ZERO;
        for (LawBill b : bills) {
            if (!live(b)) continue;
            billed = billed.add(b.getAmount());
            if (b.getKind() == BillKind.RETAINER) retainerInvoiced = retainerInvoiced.add(b.getAmount());
            else retainerApplied = retainerApplied.add(b.getRetainerApplied());
            if (b.getInvoiceId() != null) {
                InvoiceDto inv = invoiceService.getById(b.getInvoiceId());
                if (inv.amountPaid() != null) paid = paid.add(inv.amountPaid());
                if (inv.amountOutstanding() != null) outstanding = outstanding.add(inv.amountOutstanding());
            }
        }
        return new BillingSummary(hours, billableHours, waiting, unbilledTime, unbilledCosts, planned, billed, paid, outstanding, retainerInvoiced, retainerApplied,
                retainerInvoiced.subtract(retainerApplied).max(ZERO));
    }

    private BillDto billDto(LawBill b) {
        String status = null;
        BigDecimal paid = null;
        BigDecimal outstanding = null;
        boolean stale = false;
        if (b.getInvoiceId() != null) {
            Optional<Invoice> inv = invoiceRepository.findById(b.getInvoiceId());
            if (inv.isPresent()) {
                status = inv.get().getStatus().name();
                if (inv.get().getStatus() != InvoiceStatus.VOID) {
                    InvoiceDto d = invoiceService.getById(b.getInvoiceId());
                    paid = d.amountPaid();
                    outstanding = d.amountOutstanding();
                }
                stale = b.getStatus() == BillStatus.ACTIVE && inv.get().getStatus() == InvoiceStatus.VOID;
            } else {
                status = "DELETED";
                stale = b.getStatus() == BillStatus.ACTIVE;
            }
        }
        return new BillDto(b.getId(), b.getKind(), b.getStatus(), b.getInvoiceId(), b.getInvoiceNumber(), status, b.getAmount(), b.getFeesAmount(), b.getExpensesAmount(),
                b.getRetainerApplied(), b.getRecoveredAmount(), b.getSummary(), b.getCreatedByName(), b.getCreatedAt(), paid, outstanding, stale);
    }

    private FeeTermsDto terms(LawMatter m) {
        return new FeeTermsDto(m.getBillingType(), m.getBillingNote(), m.getHourlyRate(), m.getFixedFee(), m.getRetainerAmount(), m.getContingencyPercent());
    }

    private BillingDto assemble(Actor a, LawMatter m, boolean canBill) {
        timeLink.sync(m);
        List<EntryDto> time = timeService.entriesOfProject(m.getTimeProjectId());
        List<LawMatterExpense> costs = costRepository.findByMatterIdOrderByExpenseDateDescIdDesc(m.getId());
        List<LawFeeStage> stages = stageRepository.findByMatterIdOrderByIdAsc(m.getId());
        List<LawBill> bills = billRepository.findByMatterIdOrderByIdDesc(m.getId());
        Map<Long, LawBill> billsById = bills.stream().collect(Collectors.toMap(LawBill::getId, b -> b));
        boolean lead = access.canLead(m, a);
        List<StageDto> stageDtos = stages.stream().map(s -> new StageDto(s.getId(), s.getDescription(), s.getAmount(), s.getDueDate(), s.getStatus(), s.getBillId(),
                s.getBillId() == null || !billsById.containsKey(s.getBillId()) ? null : billsById.get(s.getBillId()).getInvoiceNumber())).toList();
        List<CostDto> costDtos = costs.stream().map(c -> new CostDto(c.getId(), c.getExpenseDate(), c.getDescription(), c.getSupplierName(), c.getAmount(), c.getChargeAmount(),
                c.getBillId() != null, c.getBillId(), c.getBillId() == null || !billsById.containsKey(c.getBillId()) ? null : billsById.get(c.getBillId()).getInvoiceNumber(),
                c.getExpenseId(), c.getCreatedByName(), c.getBillId() == null && (lead || (c.getCreatedByUserId() != null && c.getCreatedByUserId().equals(a.user().getId()))
                        && access.canWork(m, a)))).toList();
        return new BillingDto(m.getId(), m.getNumber(), m.getTitle(), m.getClientName(), m.getTimeProjectId(), terms(m), summary(m, time, costs, stages, bills),
                timeService.projectDetail(m.getTimeProjectId()).rates(), stageDtos, costDtos, bills.stream().map(this::billDto).toList(), time, lead, canBill,
                access.canWork(m, a), lead);
    }

    @Transactional
    public BillingDto billing(Actor a, Long id, boolean canBill) {
        LawMatter m = access.viewable(a, id);
        return assemble(a, m, canBill);
    }

    // ------------------------------------------------------------------ fee terms, rates, approval

    @Transactional
    public BillingDto updateTerms(Actor a, Long id, FeeTermsRequest r, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        if (r.hourlyRate() != null && r.hourlyRate().signum() < 0) throw ApiException.badRequest("The hourly rate cannot be negative.");
        if (r.fixedFee() != null && r.fixedFee().signum() < 0) throw ApiException.badRequest("The fixed fee cannot be negative.");
        if (r.retainerAmount() != null && r.retainerAmount().signum() < 0) throw ApiException.badRequest("The retainer cannot be negative.");
        if (r.contingencyPercent() != null && (r.contingencyPercent().signum() < 0 || r.contingencyPercent().compareTo(HUNDRED) > 0)) {
            throw ApiException.badRequest("The contingency percentage must be between 0 and 100.");
        }
        BigDecimal stageTotal = sum(stageRepository.findByMatterIdOrderByIdAsc(id).stream().map(LawFeeStage::getAmount));
        if (r.fixedFee() != null && stageTotal.compareTo(r.fixedFee()) > 0) {
            throw ApiException.badRequest("The stages already add up to " + plain(stageTotal) + ", more than that fixed fee. Change the stages first.");
        }
        if (r.billingType() != null) m.setBillingType(r.billingType());
        m.setBillingNote(clean(r.billingNote()));
        m.setHourlyRate(r.hourlyRate() != null && r.hourlyRate().signum() > 0 ? money(r.hourlyRate()) : null);
        m.setFixedFee(r.fixedFee() != null && r.fixedFee().signum() > 0 ? money(r.fixedFee()) : null);
        m.setRetainerAmount(r.retainerAmount() != null && r.retainerAmount().signum() > 0 ? money(r.retainerAmount()) : null);
        m.setContingencyPercent(r.contingencyPercent() != null && r.contingencyPercent().signum() > 0 ? money(r.contingencyPercent()) : null);
        matterRepository.save(m);
        timeLink.sync(m);
        auditLogService.record("LAW_FEE_TERMS_UPDATED", "Updated the fee terms of matter " + m.getNumber(), "LawMatter", id);
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto setRate(Actor a, Long id, Long userId, BigDecimal rate, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        timeLink.sync(m);
        timeService.setRate(m.getTimeProjectId(), new com.posapp.timetracking.dto.TimeRequests.RateRequest(userId, rate));
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto removeRate(Actor a, Long id, Long userId, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        timeLink.sync(m);
        timeService.removeRate(m.getTimeProjectId(), userId);
        return assemble(a, m, canBill);
    }

    private List<Long> ownEntryIds(LawMatter m, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("Choose the time entries.");
        Set<Long> mine = timeService.entriesOfProject(m.getTimeProjectId()).stream().map(EntryDto::id).collect(Collectors.toSet());
        return ids.stream().filter(mine::contains).toList();
    }

    @Transactional
    public BillingDto approveTime(Actor a, Long id, DecisionRequest r, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        timeService.approve(a.user(), ownEntryIds(m, r.ids()));
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto rejectTime(Actor a, Long id, DecisionRequest r, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        timeService.reject(a.user(), ownEntryIds(m, r.ids()), r.reason());
        return assemble(a, m, canBill);
    }

    // ------------------------------------------------------------------ fixed-fee stages

    private LawFeeStage stage(LawMatter m, Long stageId) {
        return stageRepository.findById(stageId).filter(s -> s.getMatterId().equals(m.getId())).orElseThrow(() -> ApiException.notFound("Stage not found."));
    }

    private void checkStage(LawMatter m, StageRequest r, Long exceptId) {
        if (clean(r.description()) == null) throw ApiException.badRequest("Describe the stage.");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount of the stage.");
        if (m.getFixedFee() == null) throw ApiException.badRequest("Set the fixed fee in the fee terms first.");
        BigDecimal others = sum(stageRepository.findByMatterIdOrderByIdAsc(m.getId()).stream().filter(s -> !s.getId().equals(exceptId)).map(LawFeeStage::getAmount));
        if (others.add(r.amount()).compareTo(m.getFixedFee()) > 0) {
            throw ApiException.badRequest("The stages would add up to " + plain(others.add(r.amount())) + ", more than the fixed fee of " + plain(m.getFixedFee()) + ".");
        }
    }

    @Transactional
    public BillingDto addStage(Actor a, Long id, StageRequest r, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        checkStage(m, r, null);
        LawFeeStage s = new LawFeeStage();
        s.setMatterId(id);
        s.setDescription(r.description().trim());
        s.setAmount(money(r.amount()));
        s.setDueDate(r.dueDate());
        stageRepository.save(s);
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto updateStage(Actor a, Long id, Long stageId, StageRequest r, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        LawFeeStage s = stage(m, stageId);
        if (s.getStatus() != StageStatus.PLANNED) throw ApiException.conflict("This stage is already billed.");
        checkStage(m, r, stageId);
        s.setDescription(r.description().trim());
        s.setAmount(money(r.amount()));
        s.setDueDate(r.dueDate());
        stageRepository.save(s);
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto deleteStage(Actor a, Long id, Long stageId, boolean canBill) {
        LawMatter m = access.leadable(a, id);
        LawFeeStage s = stage(m, stageId);
        if (s.getStatus() != StageStatus.PLANNED) throw ApiException.conflict("This stage is already billed.");
        stageRepository.delete(s);
        return assemble(a, m, canBill);
    }

    // ------------------------------------------------------------------ costs paid for the client

    private LawMatterExpense cost(LawMatter m, Long costId) {
        return costRepository.findById(costId).filter(c -> c.getMatterId().equals(m.getId())).orElseThrow(() -> ApiException.notFound("Cost not found."));
    }

    private void requireCostEditor(LawMatter m, Actor a, LawMatterExpense c) {
        boolean own = c.getCreatedByUserId() != null && c.getCreatedByUserId().equals(a.user().getId());
        if (!access.canLead(m, a) && !own) throw ApiException.forbidden("Only the person who recorded it or the lead lawyer can change a cost.");
        if (c.getBillId() != null) throw ApiException.conflict("This cost is already on an invoice.");
    }

    @Transactional
    public BillingDto addCost(Actor a, Long id, CostRequest r, boolean canBill) {
        LawMatter m = access.workable(a, id);
        if (clean(r.description()) == null) throw ApiException.badRequest("Describe the cost.");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount spent.");
        if (r.chargeAmount() != null && r.chargeAmount().signum() < 0) throw ApiException.badRequest("The amount charged cannot be negative.");
        LawMatterExpense c = new LawMatterExpense();
        c.setMatterId(id);
        c.setExpenseDate(r.expenseDate() != null ? r.expenseDate() : LocalDate.now());
        c.setDescription(r.description().trim());
        c.setSupplierName(clean(r.supplierName()));
        c.setAmount(money(r.amount()));
        c.setChargeAmount(r.chargeAmount() == null ? money(r.amount()) : money(r.chargeAmount()));
        c.setCreatedByUserId(a.user().getId());
        c.setCreatedByName(LawAccess.name(a.user()));
        if (r.expenseAccountId() != null) {
            if (!moduleService.isEnabled(ModuleKey.EXPENSES)) throw ApiException.badRequest("Switch on the Expenses module to record this as an expense.");
            ExpenseRequest er = new ExpenseRequest();
            er.setExpenseDate(c.getExpenseDate());
            er.setAccountId(r.expenseAccountId());
            er.setPayeeType(PayeeType.VENDOR);
            er.setPayeeName(c.getSupplierName() != null ? c.getSupplierName() : "Matter " + m.getNumber());
            er.setAmount(c.getAmount());
            er.setMemo(m.getNumber() + ": " + c.getDescription());
            er.setBranchId(m.getBranchId());
            ExpenseDto e = expenseService.create(er);
            c.setExpenseId(e.id());
        }
        costRepository.save(c);
        auditLogService.record("LAW_COST_ADDED", "Added a cost of " + c.getAmount() + " to matter " + m.getNumber(), "LawMatter", id);
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto updateCost(Actor a, Long id, Long costId, CostRequest r, boolean canBill) {
        LawMatter m = access.workable(a, id);
        LawMatterExpense c = cost(m, costId);
        requireCostEditor(m, a, c);
        if (clean(r.description()) == null) throw ApiException.badRequest("Describe the cost.");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount spent.");
        if (r.chargeAmount() != null && r.chargeAmount().signum() < 0) throw ApiException.badRequest("The amount charged cannot be negative.");
        if (c.getExpenseId() != null && c.getAmount().compareTo(money(r.amount())) != 0) {
            throw ApiException.conflict("This cost was recorded in Expenses. Change the amount there, or delete this cost and enter it again.");
        }
        if (r.expenseDate() != null) c.setExpenseDate(r.expenseDate());
        c.setDescription(r.description().trim());
        c.setSupplierName(clean(r.supplierName()));
        c.setAmount(money(r.amount()));
        c.setChargeAmount(r.chargeAmount() == null ? c.getChargeAmount() : money(r.chargeAmount()));
        costRepository.save(c);
        return assemble(a, m, canBill);
    }

    @Transactional
    public BillingDto deleteCost(Actor a, Long id, Long costId, boolean canBill) {
        LawMatter m = access.workable(a, id);
        LawMatterExpense c = cost(m, costId);
        requireCostEditor(m, a, c);
        costRepository.delete(c);
        return assemble(a, m, canBill);
    }

    // ------------------------------------------------------------------ preview and invoice

    private record Built(List<PreviewLine> fees, List<PreviewLine> costs, List<Long> entryIds, List<LawFeeStage> stages, List<LawMatterExpense> costRows,
                         BigDecimal recovered, BigDecimal feeTotal, BigDecimal costTotal, BigDecimal balance, BigDecimal applied, String warning) {
        BigDecimal total() {
            return feeTotal.subtract(applied).add(costTotal);
        }
    }

    private Built build(LawMatter m, BillRequest r) {
        if (m.getTimeProjectId() == null) timeLink.sync(m);
        List<PreviewLine> fees = new ArrayList<>();
        List<PreviewLine> costLines = new ArrayList<>();
        List<Long> entryIds = new ArrayList<>();
        String warning = null;
        boolean time = r == null || r.includeTime() == null || r.includeTime();
        if (time) {
            BillingPreviewDto tp = timeService.billingPreview(new BillingRequest(m.getTimeProjectId(), null, null, r == null ? null : r.timeTo(), null, null, null));
            if (tp.lines().stream().anyMatch(l -> l.hourlyRate().signum() <= 0)) {
                warning = "Some hours have no hourly rate. Set a rate for the matter or the person (or a default in the Time Tracking settings) and have the time resubmitted.";
            }
            tp.lines().forEach(l -> {
                String rest = l.description().startsWith(tp.projectName()) ? l.description().substring(tp.projectName().length()) : " — " + l.description();
                fees.add(new PreviewLine("TIME", "Matter " + m.getNumber() + " — " + m.getTitle() + rest, l.amount()));
            });
            entryIds.addAll(tp.entryIds());
        }
        List<LawFeeStage> stages = new ArrayList<>();
        if (r != null && r.stageIds() != null) {
            for (LawFeeStage s : stageRepository.findAllById(r.stageIds())) {
                if (!s.getMatterId().equals(m.getId()) || s.getStatus() != StageStatus.PLANNED) throw ApiException.badRequest("Stage \"" + s.getDescription() + "\" cannot be billed now.");
                stages.add(s);
                fees.add(new PreviewLine("STAGE", "Fixed fee — " + s.getDescription(), s.getAmount()));
            }
        }
        BigDecimal recovered = null;
        if (r != null && r.recoveredAmount() != null && r.recoveredAmount().signum() > 0) {
            if (m.getContingencyPercent() == null || m.getContingencyPercent().signum() <= 0) throw ApiException.badRequest("Set the contingency percentage in the fee terms first.");
            recovered = money(r.recoveredAmount());
            BigDecimal fee = recovered.multiply(m.getContingencyPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            fees.add(new PreviewLine("CONTINGENCY", "Contingency fee — " + plain(m.getContingencyPercent()) + "% of " + plain(recovered) + " recovered", fee));
        }
        List<LawMatterExpense> costRows = new ArrayList<>();
        if (r != null && r.costIds() != null) {
            for (LawMatterExpense c : costRepository.findAllById(r.costIds())) {
                if (!c.getMatterId().equals(m.getId()) || c.getBillId() != null) throw ApiException.badRequest("Cost \"" + c.getDescription() + "\" is already billed.");
                if (c.getChargeAmount().signum() <= 0) throw ApiException.badRequest("Cost \"" + c.getDescription() + "\" is not charged to the client.");
                costRows.add(c);
                costLines.add(new PreviewLine("COST", c.getDescription() + (c.getSupplierName() != null ? " (" + c.getSupplierName() + ")" : ""), c.getChargeAmount()));
            }
        }
        BigDecimal feeTotal = sum(fees.stream().map(PreviewLine::amount));
        BigDecimal costTotal = sum(costLines.stream().map(PreviewLine::amount));
        BigDecimal balance = retainerBalance(billRepository.findByMatterIdOrderByIdDesc(m.getId()));
        boolean apply = r == null || r.applyRetainer() == null || r.applyRetainer();
        BigDecimal applied = apply ? balance.min(feeTotal) : ZERO;
        return new Built(fees, costLines, entryIds, stages, costRows, recovered, feeTotal, costTotal, balance, applied, warning);
    }

    @Transactional
    public PreviewDto preview(Actor a, Long id, BillRequest r) {
        LawMatter m = access.viewable(a, id);
        Built b = build(m, r);
        List<PreviewLine> lines = new ArrayList<>(b.fees());
        lines.addAll(b.costs());
        return new PreviewDto(m.getId(), m.getNumber(), m.getClientCustomerId(), m.getClientName(), lines, b.feeTotal(), b.costTotal(), b.balance(), b.applied(), b.total(),
                b.entryIds().size(), b.warning());
    }

    private Product serviceProduct() {
        return timeService.serviceProductForInvoices();
    }

    private InvoiceRequest.LineEntry line(Product p, String description, BigDecimal amount) {
        InvoiceRequest.LineEntry e = new InvoiceRequest.LineEntry();
        e.setProductId(p.getId());
        e.setQuantity(1);
        e.setUnitPrice(amount);
        e.setDescription(description);
        return e;
    }

    private InvoiceDto draft(LawMatter m, List<InvoiceRequest.LineEntry> lines, LocalDate dueDate, String notes) {
        if (customerRepository.findById(m.getClientCustomerId()).isEmpty()) throw ApiException.badRequest("The client no longer exists.");
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(m.getClientCustomerId());
        req.setDueDate(dueDate);
        req.setNotes(clean(notes) != null ? notes.trim() : "Matter " + m.getNumber() + " — " + m.getTitle());
        if (m.getBranchId() != null) req.setBranchId(m.getBranchId());
        req.setLines(lines);
        return invoiceService.createDraft(req);
    }

    @Transactional
    public BillResult createBill(Actor a, Long id, BillRequest r) {
        LawMatter m = access.viewable(a, id);
        Built b = build(m, r);
        if (b.feeTotal().add(b.costTotal()).signum() <= 0) throw ApiException.badRequest("There is nothing to bill: no approved time, stage, contingency fee or cost was chosen.");
        if (b.warning() != null) throw ApiException.badRequest(b.warning());
        Product product = serviceProduct();
        List<InvoiceRequest.LineEntry> lines = new ArrayList<>();
        BigDecimal remaining = b.applied();
        for (PreviewLine f : b.fees()) {
            BigDecimal use = remaining.min(f.amount());
            remaining = remaining.subtract(use);
            BigDecimal net = f.amount().subtract(use);
            if (net.signum() > 0) lines.add(line(product, f.description() + (use.signum() > 0 ? " (less retainer applied " + plain(use) + ")" : ""), net));
        }
        for (PreviewLine c : b.costs()) lines.add(line(product, c.description(), c.amount()));
        String notes = r != null ? r.notes() : null;
        if (b.applied().signum() > 0 && clean(notes) == null) {
            notes = "Matter " + m.getNumber() + " — " + m.getTitle() + ". Retainer applied: " + plain(b.applied()) + " (balance left " + plain(b.balance().subtract(b.applied())) + ").";
        }
        InvoiceDto invoice = lines.isEmpty() ? null : draft(m, lines, r == null ? null : r.dueDate(), notes);
        LawBill bill = new LawBill();
        bill.setMatterId(id);
        bill.setKind(BillKind.INVOICE);
        bill.setInvoiceId(invoice == null ? null : invoice.id());
        bill.setInvoiceNumber(invoice == null ? null : invoice.invoiceNumber());
        bill.setFeesAmount(b.feeTotal());
        bill.setExpensesAmount(b.costTotal());
        bill.setRetainerApplied(b.applied());
        bill.setAmount(b.total());
        bill.setRecoveredAmount(b.recovered());
        bill.setSummary(summaryText(b));
        bill.setTimeEntryIds(b.entryIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        bill.setCreatedByName(LawAccess.name(a.user()));
        billRepository.save(bill);
        b.stages().forEach(s -> {
            s.setStatus(StageStatus.BILLED);
            s.setBillId(bill.getId());
        });
        stageRepository.saveAll(b.stages());
        b.costRows().forEach(c -> c.setBillId(bill.getId()));
        costRepository.saveAll(b.costRows());
        if (!b.entryIds().isEmpty()) timeService.markInvoiced(b.entryIds(), invoice == null ? null : invoice.id());
        auditLogService.record("LAW_MATTER_BILLED", "Billed matter " + m.getNumber() + (invoice == null ? " against the retainer" : " on " + invoice.invoiceNumber()), "LawMatter", id);
        return new BillResult(bill.getId(), bill.getInvoiceId(), bill.getInvoiceNumber(), b.total(), b.applied());
    }

    private static String summaryText(Built b) {
        List<String> parts = new ArrayList<>();
        if (!b.entryIds().isEmpty()) parts.add("time (" + b.entryIds().size() + " entries)");
        if (!b.stages().isEmpty()) parts.add(b.stages().size() + " fixed-fee stage" + (b.stages().size() == 1 ? "" : "s"));
        if (b.recovered() != null) parts.add("contingency fee");
        if (!b.costRows().isEmpty()) parts.add(b.costRows().size() + " cost" + (b.costRows().size() == 1 ? "" : "s"));
        return String.join(", ", parts);
    }

    @Transactional
    public BillResult requestRetainer(Actor a, Long id, RetainerRequest r) {
        LawMatter m = access.viewable(a, id);
        if (m.getBillingType() == BillingType.PRO_BONO) throw ApiException.badRequest("A pro bono matter is not billed.");
        BigDecimal amount = r != null && r.amount() != null ? money(r.amount()) : m.getRetainerAmount();
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Enter the retainer amount.");
        String notes = r != null && clean(r.notes()) != null ? r.notes() : "Retainer for matter " + m.getNumber() + " — " + m.getTitle();
        InvoiceDto invoice = draft(m, List.of(line(serviceProduct(), "Retainer — matter " + m.getNumber(), amount)), r == null ? null : r.dueDate(), notes);
        LawBill bill = new LawBill();
        bill.setMatterId(id);
        bill.setKind(BillKind.RETAINER);
        bill.setInvoiceId(invoice.id());
        bill.setInvoiceNumber(invoice.invoiceNumber());
        bill.setAmount(amount);
        bill.setFeesAmount(ZERO);
        bill.setSummary("Retainer");
        bill.setCreatedByName(LawAccess.name(a.user()));
        billRepository.save(bill);
        auditLogService.record("LAW_RETAINER_REQUESTED", "Asked for a retainer of " + plain(amount) + " on matter " + m.getNumber(), "LawMatter", id);
        return new BillResult(bill.getId(), invoice.id(), invoice.invoiceNumber(), amount, ZERO);
    }

    /** Takes a bill back so its time, stages and costs can be billed again. A draft invoice is deleted with it; an issued one must be voided first. */
    @Transactional
    public BillingDto release(Actor a, Long id, Long billId, boolean canBill) {
        LawMatter m = access.viewable(a, id);
        LawBill bill = billRepository.findById(billId).filter(x -> x.getMatterId().equals(id)).orElseThrow(() -> ApiException.notFound("Bill not found."));
        if (bill.getStatus() != BillStatus.ACTIVE) throw ApiException.conflict("This bill was already taken back.");
        if (bill.getInvoiceId() != null) {
            Optional<Invoice> inv = invoiceRepository.findById(bill.getInvoiceId());
            if (inv.isPresent() && inv.get().getStatus() == InvoiceStatus.ISSUED) {
                throw ApiException.conflict("Invoice " + inv.get().getInvoiceNumber() + " is issued. Void it first, then take the bill back.");
            }
            if (inv.isPresent() && inv.get().getStatus() == InvoiceStatus.DRAFT) invoiceService.deleteDraft(inv.get().getId());
        }
        if (bill.getTimeEntryIds() != null && !bill.getTimeEntryIds().isBlank()) {
            timeService.releaseEntries(Arrays.stream(bill.getTimeEntryIds().split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList());
        }
        List<LawFeeStage> stages = stageRepository.findByBillId(billId);
        stages.forEach(s -> {
            s.setStatus(StageStatus.PLANNED);
            s.setBillId(null);
        });
        stageRepository.saveAll(stages);
        List<LawMatterExpense> costs = costRepository.findByBillId(billId);
        costs.forEach(c -> c.setBillId(null));
        costRepository.saveAll(costs);
        bill.setStatus(BillStatus.RELEASED);
        billRepository.save(bill);
        auditLogService.record("LAW_BILL_RELEASED", "Took back a bill of matter " + m.getNumber(), "LawMatter", id);
        return assemble(a, m, canBill);
    }

    // ------------------------------------------------------------------ across matters

    /** True when a matter has anything billed, recorded or logged, so it can no longer be deleted. */
    @Transactional(readOnly = true)
    public boolean hasRecords(LawMatter m) {
        return billRepository.countByMatterId(m.getId()) > 0 || costRepository.countByMatterId(m.getId()) > 0 || stageRepository.countByMatterId(m.getId()) > 0
                || (m.getTimeProjectId() != null && timeService.hoursOf(m.getTimeProjectId()).signum() > 0);
    }

    /** What every matter the person can see has earned, billed and still to bill. */
    @Transactional
    public List<OverviewRow> overview(Actor a, boolean openOnly) {
        access.on();
        return rows(m -> access.canView(m, a), openOnly);
    }

    /** The same rows for a report: the caller decides which matters to include (and hides what a restricted matter must not show). */
    @Transactional
    public List<OverviewRow> rows(java.util.function.Predicate<LawMatter> include, boolean openOnly) {
        List<OverviewRow> out = new ArrayList<>();
        for (LawMatter m : matterRepository.findAllByOrderByOpenedDateDescIdDesc()) {
            if (!include.test(m)) continue;
            if (openOnly && m.getStatus() == MatterStatus.CLOSED) continue;
            List<EntryDto> time = m.getTimeProjectId() == null ? List.of() : timeService.entriesOfProject(m.getTimeProjectId());
            BillingSummary s = summary(m, time, costRepository.findByMatterIdOrderByExpenseDateDescIdDesc(m.getId()), stageRepository.findByMatterIdOrderByIdAsc(m.getId()),
                    billRepository.findByMatterIdOrderByIdDesc(m.getId()));
            out.add(new OverviewRow(m.getId(), m.getNumber(), m.getTitle(), m.getClientName(), m.getBillingType(), m.getStatus().name(), s.hoursLogged(), s.unbilledTime(),
                    s.unbilledCosts(), s.stagesPlanned(), s.billed(), s.paid(), s.outstanding(), s.retainerBalance(), m.isRestricted()));
        }
        return out;
    }

    /** Every live bill made in a period, with what was paid on its invoice (for the fees report). */
    @Transactional
    public List<FeeRow> feeRows(LocalDate from, LocalDate to) {
        Map<Long, LawMatter> matters = matterRepository.findAll().stream().collect(Collectors.toMap(LawMatter::getId, m -> m));
        List<FeeRow> out = new ArrayList<>();
        for (LawBill b : billRepository.findByStatus(BillStatus.ACTIVE)) {
            LocalDate day = b.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (day.isBefore(from) || day.isAfter(to) || !live(b)) continue;
            LawMatter m = matters.get(b.getMatterId());
            if (m == null) continue;
            BigDecimal paid = ZERO;
            BigDecimal outstanding = ZERO;
            String invoiceStatus = null;
            if (b.getInvoiceId() != null) {
                InvoiceDto inv = invoiceService.getById(b.getInvoiceId());
                invoiceStatus = inv.status();
                paid = inv.amountPaid() == null ? ZERO : inv.amountPaid();
                outstanding = inv.amountOutstanding() == null ? ZERO : inv.amountOutstanding();
            }
            out.add(new FeeRow(day, m.getNumber(), m.getTitle(), m.getClientName(), m.isRestricted(), b.getKind().name(), b.getInvoiceNumber(), invoiceStatus, b.getFeesAmount(),
                    b.getExpensesAmount(), b.getRetainerApplied(), b.getAmount(), paid, outstanding));
        }
        out.sort(java.util.Comparator.comparing(FeeRow::date).thenComparing(FeeRow::matterNumber));
        return out;
    }
}
