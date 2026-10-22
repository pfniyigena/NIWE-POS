package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.installment.InstallmentPlanStatus;
import com.posapp.installment.InstallmentService;
import com.posapp.installment.dto.InstallmentDtos.PlanDetailDto;
import com.posapp.installment.dto.InstallmentDtos.PlanSummaryDto;
import com.posapp.installment.dto.InstallmentDtos.PreviewDto;
import com.posapp.installment.dto.InstallmentRequests.PlanRequest;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateProjectDtos.*;
import com.posapp.realestate.dto.RealEstateProjectRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selling a unit of a project: the reservation, the signed agreement (one invoice for the agreed price, issued at once), the payment plan (an Installments plan
 * on that invoice, or simply paying the invoice), and completion once everything is paid. Payments are recorded on the invoice or the plan, as everywhere.
 */
@Service
public class ReUnitSaleService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<UnitSaleStage> LIVE = EnumSet.of(UnitSaleStage.RESERVED, UnitSaleStage.SOLD);

    private final ReUnitSaleRepository repository;
    private final ReUnitRepository unitRepository;
    private final ReProjectRepository projectRepository;
    private final ReLeadRepository leadRepository;
    private final ReLeadService leadService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final InstallmentService installmentService;
    private final ReDealService dealService;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;
    private final ReDocumentService documentService;

    public ReUnitSaleService(ReUnitSaleRepository repository, ReUnitRepository unitRepository, ReProjectRepository projectRepository, ReLeadRepository leadRepository,
                             ReLeadService leadService, CustomerRepository customerRepository, UserRepository userRepository, InvoiceService invoiceService,
                             InstallmentService installmentService, ReDealService dealService, ReSettingsService settings, RealEstateAccess access, AuditLogService auditLogService, ReDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.unitRepository = unitRepository;
        this.projectRepository = projectRepository;
        this.leadRepository = leadRepository;
        this.leadService = leadService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.installmentService = installmentService;
        this.dealService = dealService;
        this.settings = settings;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private ReUnitSale find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Unit sale not found: " + id));
    }

    private ReUnit unit(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> ApiException.notFound("Unit not found: " + id));
    }

    private ReProject project(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project not found: " + id));
    }

    private InvoiceDto invoice(Long id) {
        if (id == null) return null;
        try {
            return invoiceService.getById(id);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    private static boolean live(InvoiceDto i) {
        return i != null && !"VOID".equals(i.status());
    }

    private static BigDecimal paidOn(InvoiceDto i) {
        return live(i) && i.amountPaid() != null ? i.amountPaid() : ZERO;
    }

    private void requireStage(ReUnitSale s, Set<UnitSaleStage> allowed, String what) {
        if (!allowed.contains(s.getStage())) throw ApiException.conflict("Sale " + s.getCode() + " is " + s.getStage().name().toLowerCase(Locale.ROOT) + ", so you cannot " + what + ".");
    }

    private PlanSummaryDto activePlan(ReUnitSale s) {
        if (s.getInvoiceId() == null) return null;
        return installmentService.forInvoice(s.getInvoiceId()).stream().filter(p -> p.status() == InstallmentPlanStatus.ACTIVE).findFirst().orElse(null);
    }

    private Customer ensureCustomer(Actor a, ReUnitSale s) {
        if (s.getBuyerCustomerId() != null) {
            Customer c = customerRepository.findById(s.getBuyerCustomerId()).orElse(null);
            if (c != null) return c;
            s.setBuyerCustomerId(null);
        }
        if (s.getLeadId() != null && leadRepository.existsById(s.getLeadId())) {
            Long cid = leadService.convert(a, s.getLeadId()).customerId();
            s.setBuyerCustomerId(cid);
            return customerRepository.findById(cid).orElseThrow(() -> ApiException.badRequest("The buyer's customer record no longer exists."));
        }
        Customer c = null;
        String key = ReLeadService.digits(s.getBuyerPhone());
        if (key != null) c = customerRepository.findAll().stream().filter(x -> key.equals(ReLeadService.digits(x.getPhone()))).findFirst().orElse(null);
        if (c == null) {
            c = new Customer();
            c.setName(s.getBuyerName());
            c.setPhone(s.getBuyerPhone());
            c.setEmail(s.getBuyerEmail());
            customerRepository.save(c);
        }
        s.setBuyerCustomerId(c.getId());
        return c;
    }

    // ------------------------------------------------------------------ reading

    private static boolean reservationLapsed(ReUnitSale s, LocalDate today) {
        return s.getStage() == UnitSaleStage.RESERVED && s.getReservationExpiry() != null && s.getReservationExpiry().isBefore(today);
    }

    private UnitSaleRow row(ReUnitSale s, ReUnit u, ReProject p, LocalDate today, Map<Long, PlanSummaryDto> plans) {
        InvoiceDto inv = invoice(s.getInvoiceId());
        BigDecimal paid = paidOn(inv);
        BigDecimal out = s.getStage() == UnitSaleStage.CANCELLED ? ZERO : s.getAgreedPrice().subtract(paid).max(ZERO);
        boolean late = reservationLapsed(s, today);
        if (!late && s.getStage() == UnitSaleStage.SOLD) {
            PlanSummaryDto plan = s.getInvoiceId() == null ? null : plans.get(s.getInvoiceId());
            late = plan != null && plan.overdueInstallments() > 0;
        }
        return new UnitSaleRow(s.getId(), s.getCode(), s.getUnitId(), u == null ? null : u.getUnitNo(), s.getProjectId(), p == null ? null : p.getCode(), p == null ? null : p.getName(),
                s.getBuyerName(), s.getBuyerCustomerId(), s.getAgentName(), s.getStage(), s.getListPrice(), s.getAgreedPrice(), paid, out, s.getReservedOn(), s.getReservationExpiry(),
                s.getAgreementDate(), s.getCompletedDate(), s.getInvoiceNumber(), late, s.getCreatedAt());
    }

    @Transactional
    public List<UnitSaleRow> list(String q, UnitSaleStage stage, Long projectId, Long unitId, Boolean openOnly) {
        access.on();
        LocalDate today = LocalDate.now();
        List<ReUnitSale> all = unitId != null ? repository.findByUnitIdOrderByIdDesc(unitId) : projectId != null ? repository.findByProjectId(projectId) : repository.findAll();
        Map<Long, ReUnit> units = unitRepository.findAll().stream().collect(Collectors.toMap(ReUnit::getId, Function.identity()));
        Map<Long, ReProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(ReProject::getId, Function.identity()));
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Map<Long, PlanSummaryDto> plans = all.stream().anyMatch(x -> x.getStage() == UnitSaleStage.SOLD)
                ? installmentService.list(InstallmentPlanStatus.ACTIVE, null).stream().collect(Collectors.toMap(PlanSummaryDto::invoiceId, Function.identity(), (x, y) -> x))
                : Map.of();
        List<UnitSaleRow> out = new ArrayList<>();
        for (ReUnitSale s : all) {
            if (stage != null && s.getStage() != stage) continue;
            if (Boolean.TRUE.equals(openOnly) && !LIVE.contains(s.getStage())) continue;
            ReUnit u = units.get(s.getUnitId());
            ReProject p = projects.get(s.getProjectId());
            if (needle != null) {
                String hay = (s.getCode() + " " + s.getBuyerName() + " " + (s.getBuyerPhone() == null ? "" : s.getBuyerPhone()) + " " + (u == null ? "" : u.getUnitNo()) + " "
                        + (p == null ? "" : p.getCode() + " " + p.getName())).toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            out.add(row(s, u, p, today, plans));
        }
        out.sort(Comparator.comparing((UnitSaleRow r) -> LIVE.contains(r.stage()) ? 0 : 1).thenComparing(UnitSaleRow::id, Comparator.reverseOrder()));
        return out;
    }

    @Transactional
    public UnitSaleDto get(Actor a, Long id) {
        access.on();
        return dto(a, find(id));
    }

    private UnitSaleDto dto(Actor a, ReUnitSale s) {
        LocalDate today = LocalDate.now();
        ReUnit u = unitRepository.findById(s.getUnitId()).orElse(null);
        ReProject p = projectRepository.findById(s.getProjectId()).orElse(null);
        InvoiceDto inv = invoice(s.getInvoiceId());
        SaleInvoiceDto invDto = live(inv) ? new SaleInvoiceDto(inv.id(), inv.invoiceNumber(), inv.status(), inv.totalAmount(), paidOn(inv), inv.amountOutstanding()) : null;
        PlanDetailDto plan = null;
        if (s.getInvoiceId() != null) {
            List<PlanSummaryDto> plans = installmentService.forInvoice(s.getInvoiceId());
            PlanSummaryDto pick = plans.stream().filter(x -> x.status() == InstallmentPlanStatus.ACTIVE).findFirst().orElse(plans.isEmpty() ? null : plans.get(0));
            if (pick != null) plan = installmentService.get(pick.id());
        }
        String lead = s.getLeadId() == null ? null : leadRepository.findById(s.getLeadId()).map(ReLead::getName).orElse(null);
        boolean late = reservationLapsed(s, today) || plan != null && plan.plan().status() == InstallmentPlanStatus.ACTIVE && plan.plan().overdueInstallments() > 0;
        return new UnitSaleDto(s.getId(), s.getCode(), s.getUnitId(), u == null ? null : u.getUnitNo(), u == null ? null : u.getKind().name(), u == null ? null : u.getBlock(),
                s.getProjectId(), p == null ? null : p.getCode(), p == null ? null : p.getName(), s.getLeadId(), lead, s.getBuyerCustomerId(), s.getBuyerName(), s.getBuyerPhone(),
                s.getBuyerEmail(), s.getAgentUserId(), s.getAgentName(), s.getStage(), s.getListPrice(), s.getListPrice().subtract(s.getAgreedPrice()), s.getAgreedPrice(), s.getReservedOn(),
                s.getReservationExpiry(), s.getAgreementDate(), s.getCompletedDate(), s.getNotes(), s.getCancelReason(), s.getCreatedByName(), s.getCreatedAt(), invDto, plan, late,
                a.manager() && LIVE.contains(s.getStage()));
    }

    // ------------------------------------------------------------------ reserving

    private void setAgent(ReUnitSale s, Long agentUserId) {
        if (agentUserId == null) return;
        AppUser u = userRepository.findById(agentUserId).orElseThrow(() -> ApiException.badRequest("That agent does not exist."));
        s.setAgentUserId(u.getId());
        s.setAgentName(RealEstateAccess.name(u));
    }

    @Transactional
    public UnitSaleDto create(Actor a, UnitSaleRequest r) {
        access.on();
        if (r.unitId() == null) throw ApiException.badRequest("Choose the unit.");
        ReUnit u = unit(r.unitId());
        ReProject p = project(u.getProjectId());
        if (p.getStatus() == ProjectStatus.PLANNING) throw ApiException.conflict("Project " + p.getCode() + " is still in planning. Set it to Selling before reserving a unit.");
        if (p.getStatus() == ProjectStatus.ARCHIVED) throw ApiException.conflict("Project " + p.getCode() + " is archived.");
        if (u.getStatus() != UnitStatus.AVAILABLE) {
            throw ApiException.conflict("Unit " + u.getUnitNo() + " is " + u.getStatus().name().toLowerCase(Locale.ROOT) + " and cannot be reserved.");
        }
        BigDecimal price = r.agreedPrice() != null ? money(r.agreedPrice()) : u.getListPrice();
        if (price.signum() <= 0) throw ApiException.badRequest("Enter the agreed price.");
        if (r.reservationExpiry() != null && r.reservationExpiry().isBefore(LocalDate.now())) throw ApiException.badRequest("The reservation cannot end in the past.");
        ReUnitSale s = new ReUnitSale();
        s.setUnitId(u.getId());
        s.setProjectId(p.getId());
        ReLead lead = null;
        if (r.leadId() != null) {
            lead = leadRepository.findById(r.leadId()).orElseThrow(() -> ApiException.badRequest("That lead does not exist."));
            s.setLeadId(lead.getId());
            s.setBuyerCustomerId(lead.getCustomerId());
        }
        if (r.buyerCustomerId() != null) {
            Customer c = customerRepository.findById(r.buyerCustomerId()).orElseThrow(() -> ApiException.badRequest("That customer does not exist."));
            s.setBuyerCustomerId(c.getId());
            if (clean(r.buyerName(), 200, "The buyer's name") == null) {
                s.setBuyerName(c.getName());
                s.setBuyerPhone(c.getPhone());
                s.setBuyerEmail(c.getEmail());
            }
        }
        String name = clean(r.buyerName(), 200, "The buyer's name");
        if (name != null) {
            s.setBuyerName(name);
            s.setBuyerPhone(clean(r.buyerPhone(), 40, "The phone number"));
            s.setBuyerEmail(clean(r.buyerEmail(), 255, "The e-mail"));
        } else if (lead != null && s.getBuyerName() == null) {
            s.setBuyerName(lead.getName());
            s.setBuyerPhone(lead.getPhone());
            s.setBuyerEmail(lead.getEmail());
        }
        if (s.getBuyerName() == null) throw ApiException.badRequest("Who is buying? Give the buyer's name, or pick a lead.");
        s.setCode(settings.nextCode("US"));
        s.setStage(UnitSaleStage.RESERVED);
        s.setListPrice(u.getListPrice());
        s.setAgreedPrice(price);
        s.setDiscount(u.getListPrice().subtract(price));
        s.setReservedOn(LocalDate.now());
        s.setReservationExpiry(r.reservationExpiry());
        s.setNotes(clean(r.notes(), 2000, "The notes"));
        if (r.agentUserId() != null) {
            setAgent(s, r.agentUserId());
        } else {
            s.setAgentUserId(a.user().getId());
            s.setAgentName(RealEstateAccess.name(a.user()));
        }
        s.setCreatedByName(RealEstateAccess.name(a.user()));
        repository.save(s);
        u.setStatus(UnitStatus.RESERVED);
        unitRepository.save(u);
        if (lead != null && (lead.getStatus() == LeadStatus.NEW || lead.getStatus() == LeadStatus.CONTACTED || lead.getStatus() == LeadStatus.VIEWING)) {
            lead.setStatus(LeadStatus.NEGOTIATING);
            lead.setLastContactAt(java.time.Instant.now());
            leadRepository.save(lead);
        }
        auditLogService.record("RE_UNIT_RESERVED", "Reserved unit " + u.getUnitNo() + " of " + p.getCode() + " for " + s.getBuyerName() + " at " + price + " (sale " + s.getCode() + ")", "ReUnitSale", s.getId());
        return dto(a, s);
    }

    @Transactional
    public UnitSaleDto update(Actor a, Long id, UnitSaleRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requireStage(s, EnumSet.of(UnitSaleStage.RESERVED), "edit it");
        if (r.buyerCustomerId() != null && !r.buyerCustomerId().equals(s.getBuyerCustomerId())) {
            Customer c = customerRepository.findById(r.buyerCustomerId()).orElseThrow(() -> ApiException.badRequest("That customer does not exist."));
            s.setBuyerCustomerId(c.getId());
            if (clean(r.buyerName(), 200, "The buyer's name") == null) {
                s.setBuyerName(c.getName());
                s.setBuyerPhone(c.getPhone());
                s.setBuyerEmail(c.getEmail());
            }
        }
        String name = clean(r.buyerName(), 200, "The buyer's name");
        if (name != null) {
            if (s.getBuyerCustomerId() != null && !name.equalsIgnoreCase(s.getBuyerName()) && r.buyerCustomerId() == null) s.setBuyerCustomerId(null);
            s.setBuyerName(name);
        }
        if (r.buyerPhone() != null) s.setBuyerPhone(clean(r.buyerPhone(), 40, "The phone number"));
        if (r.buyerEmail() != null) s.setBuyerEmail(clean(r.buyerEmail(), 255, "The e-mail"));
        if (r.agentUserId() != null) setAgent(s, r.agentUserId());
        if (r.agreedPrice() != null) {
            if (r.agreedPrice().signum() <= 0) throw ApiException.badRequest("Enter the agreed price.");
            s.setAgreedPrice(money(r.agreedPrice()));
            s.setDiscount(s.getListPrice().subtract(s.getAgreedPrice()));
        }
        if (r.reservationExpiry() != null) {
            if (r.reservationExpiry().isBefore(LocalDate.now())) throw ApiException.badRequest("The reservation cannot end in the past.");
            s.setReservationExpiry(r.reservationExpiry());
        }
        if (r.notes() != null) s.setNotes(clean(r.notes(), 2000, "The notes"));
        repository.save(s);
        auditLogService.record("RE_UNIT_SALE_UPDATED", "Updated unit sale " + s.getCode(), "ReUnitSale", id);
        return dto(a, s);
    }

    // ------------------------------------------------------------------ agreement, plan, completion

    /** Signs the sale agreement: one invoice for the whole agreed price is made and issued to the buyer, and the unit is sold. */
    @Transactional
    public UnitSaleDto agreement(Actor a, Long id, SaleAgreementRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requireStage(s, EnumSet.of(UnitSaleStage.RESERVED), "sign the agreement");
        ReUnit u = unit(s.getUnitId());
        ReProject p = project(s.getProjectId());
        Customer buyer = ensureCustomer(a, s);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(buyer.getId());
        req.setNotes("Sale " + s.getCode() + ": unit " + u.getUnitNo() + ", " + p.getName());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(dealService.saleProduct().getId());
        line.setQuantity(1);
        line.setUnitPrice(s.getAgreedPrice());
        line.setDescription("Unit " + u.getUnitNo() + (u.getBlock() == null ? "" : " (" + u.getBlock() + ")") + ", " + p.getName());
        req.setLines(List.of(line));
        InvoiceDto inv = invoiceService.issue(invoiceService.createDraft(req).id());
        s.setInvoiceId(inv.id());
        s.setInvoiceNumber(inv.invoiceNumber());
        s.setStage(UnitSaleStage.SOLD);
        s.setAgreementDate(r != null && r.agreementDate() != null ? r.agreementDate() : LocalDate.now());
        repository.save(s);
        u.setStatus(UnitStatus.SOLD);
        unitRepository.save(u);
        if (s.getLeadId() != null) {
            leadRepository.findById(s.getLeadId()).ifPresent(l -> {
                l.setStatus(LeadStatus.WON);
                l.setLostReason(null);
                l.setLastContactAt(java.time.Instant.now());
                leadRepository.save(l);
            });
        }
        auditLogService.record("RE_UNIT_SOLD", "Agreement signed: unit " + u.getUnitNo() + " of " + p.getCode() + " to " + s.getBuyerName() + " for " + s.getAgreedPrice()
                + " (invoice " + inv.invoiceNumber() + ")", "ReUnitSale", id);
        return dto(a, s);
    }

    private PlanRequest planRequest(ReUnitSale s, UnitPlanRequest r) {
        return new PlanRequest(s.getInvoiceId(), r.deposit(), r.depositMethod(), r.numberOfInstallments(), r.frequency(), r.firstDueDate(), r.interestMethod(), r.interestRate(),
                r.graceDays(), r.lateFeeFixed(), r.lateFeePercent(), r.remindersEnabled(), r.wholeUnits(), r.notes());
    }

    private void requirePayable(ReUnitSale s) {
        requireStage(s, EnumSet.of(UnitSaleStage.SOLD), "set a payment plan");
        InvoiceDto inv = invoice(s.getInvoiceId());
        if (!live(inv)) throw ApiException.conflict("The sale's invoice is gone.");
    }

    @Transactional
    public PreviewDto planPreview(Long id, UnitPlanRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requirePayable(s);
        return installmentService.preview(planRequest(s, r));
    }

    @Transactional
    public UnitSaleDto createPlan(Actor a, Long id, UnitPlanRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requirePayable(s);
        installmentService.create(planRequest(s, r), a.user());
        auditLogService.record("RE_UNIT_PLAN", "Payment plan made for unit sale " + s.getCode(), "ReUnitSale", id);
        return dto(a, s);
    }

    @Transactional
    public UnitSaleDto complete(Actor a, Long id, SaleCompleteRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requireStage(s, EnumSet.of(UnitSaleStage.SOLD), "complete it");
        boolean force = r != null && Boolean.TRUE.equals(r.force());
        if (force && !a.admin()) throw ApiException.forbidden("Only an administrator can complete a sale that is not fully paid.");
        InvoiceDto inv = invoice(s.getInvoiceId());
        if (!force) {
            if (!live(inv)) throw ApiException.conflict("The sale's invoice is gone.");
            if (inv.amountOutstanding() != null && inv.amountOutstanding().signum() > 0) {
                throw ApiException.conflict("Not fully paid yet: " + inv.amountOutstanding().setScale(2, RoundingMode.HALF_UP).toPlainString() + " is still owed on invoice " + inv.invoiceNumber() + ".");
            }
        }
        s.setStage(UnitSaleStage.COMPLETED);
        s.setCompletedDate(r != null && r.completedDate() != null ? r.completedDate() : LocalDate.now());
        repository.save(s);
        auditLogService.record("RE_UNIT_SALE_COMPLETED", "Unit sale " + s.getCode() + " completed" + (force ? " (not fully paid)" : ""), "ReUnitSale", id);
        return dto(a, s);
    }

    @Transactional
    public UnitSaleDto cancel(Actor a, Long id, SaleCancelRequest r) {
        access.on();
        ReUnitSale s = find(id);
        requireStage(s, LIVE, "cancel it");
        String reason = clean(r == null ? null : r.reason(), 300, "The reason");
        if (reason == null) throw ApiException.badRequest("Say why the sale is cancelled.");
        InvoiceDto inv = invoice(s.getInvoiceId());
        if (live(inv) && paidOn(inv).signum() > 0) {
            throw ApiException.conflict("The buyer has already paid on invoice " + inv.invoiceNumber() + ". Cancel that invoice and refund or credit the buyer in Invoices first.");
        }
        if (live(inv)) {
            PlanSummaryDto plan = activePlan(s);
            if (plan != null) installmentService.cancel(plan.id(), "Sale " + s.getCode() + " cancelled: " + reason);
            if ("DRAFT".equals(inv.status())) invoiceService.deleteDraft(inv.id());
            else invoiceService.voidInvoice(inv.id(), "Sale " + s.getCode() + " cancelled: " + reason);
        }
        s.setStage(UnitSaleStage.CANCELLED);
        s.setCancelReason(reason);
        repository.save(s);
        ReUnit u = unit(s.getUnitId());
        if (u.getStatus() == UnitStatus.RESERVED || u.getStatus() == UnitStatus.SOLD) {
            u.setStatus(UnitStatus.AVAILABLE);
            unitRepository.save(u);
        }
        auditLogService.record("RE_UNIT_SALE_CANCELLED", "Cancelled unit sale " + s.getCode() + ": " + reason, "ReUnitSale", id);
        return dto(a, s);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReUnitSale s = find(id);
        if (s.getStage() != UnitSaleStage.RESERVED && s.getStage() != UnitSaleStage.CANCELLED) throw ApiException.conflict("Only a reservation or a cancelled sale can be deleted.");
        if (s.getStage() == UnitSaleStage.RESERVED) {
            ReUnit u = unit(s.getUnitId());
            if (u.getStatus() == UnitStatus.RESERVED) {
                u.setStatus(UnitStatus.AVAILABLE);
                unitRepository.save(u);
            }
        }
        documentService.deleteFor(DocSubject.UNIT_SALE, id);
        repository.delete(s);
        auditLogService.record("RE_UNIT_SALE_DELETED", "Deleted unit sale " + s.getCode());
    }

    // ------------------------------------------------------------------ figures for the overview and the projects

    /** Collected and still owed on the live and completed sales of the given projects (all when null). */
    public record Money(BigDecimal sold, BigDecimal collected, BigDecimal outstanding) {
    }

    @Transactional
    public Money totals(Long projectId) {
        BigDecimal sold = ZERO;
        BigDecimal collected = ZERO;
        BigDecimal out = ZERO;
        List<ReUnitSale> sales = projectId == null ? repository.findAll() : repository.findByProjectId(projectId);
        for (ReUnitSale s : sales) {
            if (s.getStage() != UnitSaleStage.SOLD && s.getStage() != UnitSaleStage.COMPLETED) continue;
            sold = sold.add(s.getAgreedPrice());
            BigDecimal paid = paidOn(invoice(s.getInvoiceId()));
            collected = collected.add(paid);
            out = out.add(s.getAgreedPrice().subtract(paid).max(ZERO));
        }
        return new Money(sold, collected, out);
    }

    public record Snapshot(long available, long reserved, long soldThisMonth, BigDecimal soldValue, BigDecimal receivable) {
    }

    @Transactional
    public Snapshot snapshot() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        List<ReUnit> units = unitRepository.findAll();
        long available = units.stream().filter(u -> u.getStatus() == UnitStatus.AVAILABLE).count();
        long reserved = units.stream().filter(u -> u.getStatus() == UnitStatus.RESERVED).count();
        long soldCount = 0;
        BigDecimal soldValue = ZERO;
        BigDecimal receivable = ZERO;
        for (ReUnitSale s : repository.findByStageIn(EnumSet.of(UnitSaleStage.SOLD, UnitSaleStage.COMPLETED))) {
            if (s.getAgreementDate() != null && !s.getAgreementDate().isBefore(first)) {
                soldCount++;
                soldValue = soldValue.add(s.getAgreedPrice());
            }
            if (s.getStage() == UnitSaleStage.SOLD) receivable = receivable.add(s.getAgreedPrice().subtract(paidOn(invoice(s.getInvoiceId()))).max(ZERO));
        }
        return new Snapshot(available, reserved, soldCount, soldValue, receivable);
    }

    /** The live sale (reserved or sold) holding a unit, or null. */
    public ReUnitSale holding(Long unitId) {
        return repository.findByUnitIdAndStageIn(unitId, EnumSet.of(UnitSaleStage.RESERVED, UnitSaleStage.SOLD)).stream().findFirst().orElse(null);
    }
}
