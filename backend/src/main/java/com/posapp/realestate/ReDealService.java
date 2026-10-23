package com.posapp.realestate;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.ExpenseService;
import com.posapp.accounting.ExpenseStatus;
import com.posapp.accounting.PayeeType;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDealDtos.*;
import com.posapp.realestate.dto.RealEstateDealRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selling a property: the offer, the reservation, the sale agreement and completion; the buyer's payment schedule (each payment is invoiced on its own through
 * the normal invoices, which is also where payments are recorded); and the commission, split between the people who earned it and posted to the accounts as
 * expenses once the sale is complete. The agency's own commission can be invoiced to the seller.
 */
@Service
public class ReDealService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<DealStage> LIVE = EnumSet.of(DealStage.OFFER, DealStage.RESERVED, DealStage.AGREEMENT);
    private static final Set<DealStage> HOLDING = EnumSet.of(DealStage.RESERVED, DealStage.AGREEMENT);

    private final ReDealRepository repository;
    private final ReDealMilestoneRepository milestoneRepository;
    private final ReDealCommissionRepository commissionRepository;
    private final RePropertyRepository propertyRepository;
    private final ReLeadRepository leadRepository;
    private final ReLeadService leadService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final ExpenseService expenseService;
    private final AccountRepository accountRepository;
    private final CatalogService catalogService;
    private final ModuleService moduleService;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;
    private final ReDocumentService documentService;

    public ReDealService(ReDealRepository repository, ReDealMilestoneRepository milestoneRepository, ReDealCommissionRepository commissionRepository,
                         RePropertyRepository propertyRepository, ReLeadRepository leadRepository, ReLeadService leadService, CustomerRepository customerRepository,
                         UserRepository userRepository, InvoiceService invoiceService, ExpenseService expenseService, AccountRepository accountRepository,
                         CatalogService catalogService, ModuleService moduleService, ReSettingsService settings, RealEstateAccess access, AuditLogService auditLogService, ReDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.milestoneRepository = milestoneRepository;
        this.commissionRepository = commissionRepository;
        this.propertyRepository = propertyRepository;
        this.leadRepository = leadRepository;
        this.leadService = leadService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.expenseService = expenseService;
        this.accountRepository = accountRepository;
        this.catalogService = catalogService;
        this.moduleService = moduleService;
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

    private static String plain(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private ReDeal find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Deal not found: " + id));
    }

    private ReProperty property(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Property not found: " + id));
    }

    private static BigDecimal price(ReDeal d) {
        return d.getAgreedPrice() != null ? d.getAgreedPrice() : d.getOfferAmount();
    }

    /** The agency's commission on this deal: a fixed amount if one is set, otherwise the percentage of the price. */
    static BigDecimal commissionTotal(ReDeal d) {
        if (d.getCommissionFixed() != null && d.getCommissionFixed().signum() > 0) return money(d.getCommissionFixed());
        if (d.getCommissionPercent() != null && d.getCommissionPercent().signum() > 0) return money(price(d).multiply(d.getCommissionPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        return ZERO;
    }

    static BigDecimal shareAmount(ReDealCommission c, BigDecimal total) {
        if (c.getBasis() == CommissionBasis.FIXED) return money(c.getFixedAmount());
        return c.getSharePercent() == null ? ZERO : money(total.multiply(c.getSharePercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal allocated(List<ReDealCommission> lines, BigDecimal total, Long except) {
        BigDecimal sum = ZERO;
        for (ReDealCommission c : lines) {
            if (c.getStatus() == CommissionStatus.CANCELLED || c.getId() != null && c.getId().equals(except)) continue;
            sum = sum.add(shareAmount(c, total));
        }
        return sum;
    }

    /** An invoice by id, or null when it has been deleted. */
    private InvoiceDto invoice(Long id) {
        if (id == null) return null;
        try {
            return invoiceService.getById(id);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    private static boolean liveInvoice(InvoiceDto i) {
        return i != null && !"VOID".equals(i.status());
    }

    private static String milestoneStatus(InvoiceDto i) {
        if (i == null) return "NOT_INVOICED";
        if ("VOID".equals(i.status())) return "VOID";
        if ("DRAFT".equals(i.status())) return "DRAFT";
        return i.amountOutstanding() != null && i.amountOutstanding().signum() <= 0 ? "PAID" : "ISSUED";
    }

    public Product saleProduct() {
        ReSettings s = settings.entity();
        if (s.getSaleProductId() != null) {
            try {
                return catalogService.getProductOrThrow(s.getSaleProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        ProductRequest req = new ProductRequest();
        req.setName("Property sale");
        req.setDescription("Property sales: payments and commission");
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(BigDecimal.ZERO);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        s.setSaleProductId(created.getId());
        return created;
    }

    private void requireStage(ReDeal d, Set<DealStage> allowed, String what) {
        if (!allowed.contains(d.getStage())) throw ApiException.conflict("Deal " + d.getCode() + " is " + d.getStage().name().toLowerCase(Locale.ROOT) + ", so you cannot " + what + ".");
    }

    // ------------------------------------------------------------------ the buyer as a customer

    private Customer ensureCustomer(Actor a, ReDeal d) {
        if (d.getBuyerCustomerId() != null) {
            Customer c = customerRepository.findById(d.getBuyerCustomerId()).orElse(null);
            if (c != null) return c;
            d.setBuyerCustomerId(null);
        }
        if (d.getLeadId() != null && leadRepository.existsById(d.getLeadId())) {
            Long cid = leadService.convert(a, d.getLeadId()).customerId();
            d.setBuyerCustomerId(cid);
            return customerRepository.findById(cid).orElseThrow(() -> ApiException.badRequest("The buyer's customer record no longer exists."));
        }
        Customer c = null;
        String key = ReLeadService.digits(d.getBuyerPhone());
        if (key != null) c = customerRepository.findAll().stream().filter(x -> key.equals(ReLeadService.digits(x.getPhone()))).findFirst().orElse(null);
        if (c == null) {
            c = new Customer();
            c.setName(d.getBuyerName());
            c.setPhone(d.getBuyerPhone());
            c.setEmail(d.getBuyerEmail());
            customerRepository.save(c);
        }
        d.setBuyerCustomerId(c.getId());
        return c;
    }

    // ------------------------------------------------------------------ reading

    private static boolean overdue(ReDeal d, LocalDate today) {
        if (d.getStage() == DealStage.RESERVED && d.getReservationExpiry() != null && d.getReservationExpiry().isBefore(today)) return true;
        if (d.getStage() == DealStage.OFFER && d.getOfferValidUntil() != null && d.getOfferValidUntil().isBefore(today)) return true;
        return LIVE.contains(d.getStage()) && d.getExpectedCompletion() != null && d.getExpectedCompletion().isBefore(today);
    }

    private MilestoneDto milestoneDto(ReDealMilestone m, boolean live, LocalDate today) {
        InvoiceDto inv = invoice(m.getInvoiceId());
        String status = milestoneStatus(inv);
        BigDecimal paid = liveInvoice(inv) && inv.amountPaid() != null ? inv.amountPaid() : ZERO;
        BigDecimal out = liveInvoice(inv) && inv.amountOutstanding() != null ? inv.amountOutstanding() : "DRAFT".equals(status) ? m.getAmount() : m.getAmount().subtract(paid);
        boolean late = live && m.getDueDate() != null && m.getDueDate().isBefore(today) && !"PAID".equals(status);
        return new MilestoneDto(m.getId(), m.getKind(), m.getLabel(), m.getAmount(), m.getDueDate(), liveInvoice(inv) ? m.getInvoiceId() : null, inv == null ? null : inv.invoiceNumber(),
                status, paid, out.max(ZERO), late);
    }

    private CommissionDto commissionDto(ReDealCommission c, BigDecimal total, boolean completed) {
        String expStatus = null;
        boolean expPaid = false;
        if (c.getExpenseId() != null) {
            try {
                ExpenseDto e = expenseService.getById(c.getExpenseId());
                expStatus = e.status().name();
                expPaid = e.status() == ExpenseStatus.APPROVED && (e.paid() || e.settledAt() != null);
            } catch (ApiException ex) {
                if (ex.getStatus() != HttpStatus.NOT_FOUND) throw ex;
                expStatus = "MISSING";
            }
        }
        return new CommissionDto(c.getId(), c.getRole(), c.getPayeeKind(), c.getPayeeUserId(), c.getPayeeName(), c.getBasis(), c.getSharePercent(), c.getFixedAmount(),
                shareAmount(c, total), c.getStatus(), completed && c.getStatus() == CommissionStatus.PLANNED, c.getExpenseId(), expStatus, expPaid, c.getNote());
    }

    private BigDecimal paidOf(List<ReDealMilestone> ms) {
        BigDecimal paid = ZERO;
        for (ReDealMilestone m : ms) {
            InvoiceDto inv = invoice(m.getInvoiceId());
            if (liveInvoice(inv) && inv.amountPaid() != null) paid = paid.add(inv.amountPaid());
        }
        return paid;
    }

    private DealRow row(ReDeal d, ReProperty p, List<ReDealMilestone> ms, LocalDate today) {
        BigDecimal paid = d.getStage() == DealStage.OFFER || d.getStage() == DealStage.CANCELLED && ms.isEmpty() ? ZERO : paidOf(ms);
        BigDecimal outstanding = d.getAgreedPrice() == null ? null : d.getAgreedPrice().subtract(paid).max(ZERO);
        return new DealRow(d.getId(), d.getCode(), d.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), d.getBuyerName(), d.getBuyerCustomerId(),
                d.getAgentName(), d.getAgentUserId(), d.getStage(), d.getListPrice(), d.getOfferAmount(), d.getAgreedPrice(), paid, outstanding, commissionTotal(d), d.getOfferDate(),
                d.getOfferValidUntil(), d.getReservationExpiry(), d.getExpectedCompletion(), d.getCompletedDate(), overdue(d, today), d.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<DealRow> list(String q, DealStage stage, Long propertyId, Long agentId, Boolean openOnly) {
        access.on();
        LocalDate today = LocalDate.now();
        List<ReDeal> deals = propertyId != null ? repository.findByPropertyIdOrderByIdDesc(propertyId) : repository.findAll();
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<ReDeal> keep = new ArrayList<>();
        for (ReDeal d : deals) {
            if (stage != null && d.getStage() != stage) continue;
            if (agentId != null && !agentId.equals(d.getAgentUserId())) continue;
            if (Boolean.TRUE.equals(openOnly) && !LIVE.contains(d.getStage())) continue;
            if (needle != null) {
                ReProperty p = props.get(d.getPropertyId());
                String hay = (d.getCode() + " " + d.getBuyerName() + " " + (d.getBuyerPhone() == null ? "" : d.getBuyerPhone()) + " " + (p == null ? "" : p.getCode() + " " + p.getTitle())).toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            keep.add(d);
        }
        Map<Long, List<ReDealMilestone>> ms = keep.isEmpty() ? Map.of()
                : milestoneRepository.findByDealIdIn(keep.stream().map(ReDeal::getId).toList()).stream().collect(Collectors.groupingBy(ReDealMilestone::getDealId));
        List<DealRow> out = new ArrayList<>();
        for (ReDeal d : keep) out.add(row(d, props.get(d.getPropertyId()), ms.getOrDefault(d.getId(), List.of()), today));
        out.sort(Comparator.comparing((DealRow r) -> LIVE.contains(r.stage()) ? 0 : 1).thenComparing(DealRow::id, Comparator.reverseOrder()));
        return out;
    }

    @Transactional(readOnly = true)
    public DealDto get(Actor a, Long id) {
        access.on();
        return dto(a, find(id));
    }

    private DealDto dto(Actor a, ReDeal d) {
        LocalDate today = LocalDate.now();
        ReProperty p = propertyRepository.findById(d.getPropertyId()).orElse(null);
        List<ReDealMilestone> ms = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(d.getId());
        List<ReDealCommission> cs = commissionRepository.findByDealIdOrderByIdAsc(d.getId());
        BigDecimal total = commissionTotal(d);
        boolean completed = d.getStage() == DealStage.COMPLETED;
        List<MilestoneDto> mdtos = ms.stream().map(m -> milestoneDto(m, LIVE.contains(d.getStage()), today)).toList();
        BigDecimal scheduled = ms.stream().map(ReDealMilestone::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal invoiced = mdtos.stream().filter(m -> m.invoiceId() != null && !"DRAFT".equals(m.status())).map(MilestoneDto::amount).reduce(ZERO, BigDecimal::add);
        BigDecimal paid = mdtos.stream().map(MilestoneDto::paid).reduce(ZERO, BigDecimal::add);
        BigDecimal outstanding = d.getAgreedPrice() == null ? null : d.getAgreedPrice().subtract(paid).max(ZERO);
        List<CommissionDto> cdtos = cs.stream().map(c -> commissionDto(c, total, completed)).toList();
        BigDecimal alloc = allocated(cs, total, null);
        FeeInvoiceDto fee = null;
        InvoiceDto fi = invoice(d.getFeeInvoiceId());
        if (liveInvoice(fi)) fee = new FeeInvoiceDto(fi.id(), fi.invoiceNumber(), milestoneStatus(fi), fi.totalAmount(), fi.amountPaid());
        String lead = d.getLeadId() == null ? null : leadRepository.findById(d.getLeadId()).map(ReLead::getName).orElse(null);
        String seller = d.getSellerCustomerId() == null ? null : customerRepository.findById(d.getSellerCustomerId()).map(Customer::getName).orElse(null);
        return new DealDto(d.getId(), d.getCode(), d.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), p == null ? null : p.getArea(), d.getLeadId(), lead,
                d.getBuyerCustomerId(), d.getBuyerName(), d.getBuyerPhone(), d.getBuyerEmail(), d.getAgentUserId(), d.getAgentName(), d.getStage(), d.getListPrice(), d.getOfferAmount(),
                d.getAgreedPrice(), d.getOfferDate(), d.getOfferValidUntil(), d.getReservationDeposit(), d.getReservationExpiry(), d.getAgreementDate(), d.getExpectedCompletion(),
                d.getCompletedDate(), d.getCommissionPercent(), d.getCommissionFixed(), total, alloc, total.subtract(alloc).max(ZERO), d.getSellerCustomerId(), seller, fee, d.getNotes(),
                d.getCancelReason(), d.getCreatedByName(), d.getCreatedAt(), scheduled, invoiced, paid, outstanding, mdtos, cdtos, a.manager() && LIVE.contains(d.getStage())
                || a.manager() && completed);
    }

    // ------------------------------------------------------------------ the offer

    private void setAgent(ReDeal d, Long agentUserId) {
        if (agentUserId == null) return;
        AppUser u = userRepository.findById(agentUserId).orElseThrow(() -> ApiException.badRequest("That agent does not exist."));
        d.setAgentUserId(u.getId());
        d.setAgentName(RealEstateAccess.name(u));
    }

    private static void checkPercent(BigDecimal v, String what) {
        if (v != null && (v.signum() < 0 || v.compareTo(HUNDRED) > 0)) throw ApiException.badRequest(what + " is between 0 and 100 percent.");
    }

    @Transactional
    public DealDto create(Actor a, DealRequest r) {
        access.on();
        if (r.propertyId() == null) throw ApiException.badRequest("Choose the property.");
        ReProperty p = property(r.propertyId());
        if (p.getPurpose() == ListingPurpose.RENT) throw ApiException.badRequest("This property is for rent only, so it cannot be sold.");
        if (p.getStatus() != PropertyStatus.AVAILABLE && p.getStatus() != PropertyStatus.RESERVED) {
            throw ApiException.conflict("Property " + p.getCode() + " is " + p.getStatus().name().toLowerCase(Locale.ROOT) + ". Only a property that is on the market can receive an offer.");
        }
        if (r.offerAmount() == null || r.offerAmount().signum() <= 0) throw ApiException.badRequest("Enter the amount offered.");
        checkPercent(r.commissionPercent(), "The commission");
        if (r.commissionFixed() != null && r.commissionFixed().signum() < 0) throw ApiException.badRequest("The commission cannot be negative.");
        ReDeal d = new ReDeal();
        d.setPropertyId(p.getId());
        ReLead lead = null;
        if (r.leadId() != null) {
            lead = leadRepository.findById(r.leadId()).orElseThrow(() -> ApiException.badRequest("That lead does not exist."));
            d.setLeadId(lead.getId());
            d.setBuyerCustomerId(lead.getCustomerId());
        }
        if (r.buyerCustomerId() != null) {
            Customer c = customerRepository.findById(r.buyerCustomerId()).orElseThrow(() -> ApiException.badRequest("That customer does not exist."));
            d.setBuyerCustomerId(c.getId());
            if (clean(r.buyerName(), 200, "The buyer's name") == null) {
                d.setBuyerName(c.getName());
                d.setBuyerPhone(c.getPhone());
                d.setBuyerEmail(c.getEmail());
            }
        }
        String name = clean(r.buyerName(), 200, "The buyer's name");
        if (name != null) {
            d.setBuyerName(name);
            d.setBuyerPhone(clean(r.buyerPhone(), 40, "The phone number"));
            d.setBuyerEmail(clean(r.buyerEmail(), 255, "The e-mail"));
        } else if (lead != null && d.getBuyerName() == null) {
            d.setBuyerName(lead.getName());
            d.setBuyerPhone(lead.getPhone());
            d.setBuyerEmail(lead.getEmail());
        }
        if (d.getBuyerName() == null) throw ApiException.badRequest("Who is buying? Give the buyer's name, or pick a lead.");
        d.setCode(settings.nextCode("DL"));
        d.setStage(DealStage.OFFER);
        d.setListPrice(p.getSalePrice());
        d.setOfferAmount(money(r.offerAmount()));
        d.setOfferDate(r.offerDate() != null ? r.offerDate() : LocalDate.now());
        d.setOfferValidUntil(r.offerValidUntil());
        d.setExpectedCompletion(r.expectedCompletion());
        d.setNotes(clean(r.notes(), 2000, "The notes"));
        d.setCommissionPercent(r.commissionPercent() != null ? r.commissionPercent() : settings.entity().getDefaultCommissionPercent());
        if (r.commissionFixed() != null && r.commissionFixed().signum() > 0) d.setCommissionFixed(money(r.commissionFixed()));
        if (r.agentUserId() != null) {
            setAgent(d, r.agentUserId());
        } else if (p.getAgentUserId() != null) {
            d.setAgentUserId(p.getAgentUserId());
            d.setAgentName(p.getAgentName());
        } else {
            d.setAgentUserId(a.user().getId());
            d.setAgentName(RealEstateAccess.name(a.user()));
        }
        d.setCreatedByName(RealEstateAccess.name(a.user()));
        repository.save(d);
        if (lead != null && (lead.getStatus() == LeadStatus.NEW || lead.getStatus() == LeadStatus.CONTACTED || lead.getStatus() == LeadStatus.VIEWING)) {
            lead.setStatus(LeadStatus.NEGOTIATING);
            lead.setLastContactAt(Instant.now());
            leadRepository.save(lead);
        }
        auditLogService.record("RE_DEAL_CREATED", "Offer " + d.getCode() + " of " + d.getOfferAmount() + " on " + p.getCode() + " from " + d.getBuyerName(), "ReDeal", d.getId());
        return dto(a, d);
    }

    @Transactional
    public DealDto update(Actor a, Long id, DealRequest r) {
        access.on();
        ReDeal d = find(id);
        requireStage(d, LIVE, "change it");
        if (r.buyerName() != null) {
            String name = clean(r.buyerName(), 200, "The buyer's name");
            if (name == null) throw ApiException.badRequest("The buyer needs a name.");
            d.setBuyerName(name);
        }
        if (r.buyerPhone() != null) d.setBuyerPhone(clean(r.buyerPhone(), 40, "The phone number"));
        if (r.buyerEmail() != null) d.setBuyerEmail(clean(r.buyerEmail(), 255, "The e-mail"));
        if (r.buyerCustomerId() != null && !r.buyerCustomerId().equals(d.getBuyerCustomerId())) {
            if (milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id).stream().anyMatch(m -> liveInvoice(invoice(m.getInvoiceId())))) {
                throw ApiException.conflict("Payments of this deal are already invoiced to the current buyer. Void those invoices first.");
            }
            Customer c = customerRepository.findById(r.buyerCustomerId()).orElseThrow(() -> ApiException.badRequest("That customer does not exist."));
            d.setBuyerCustomerId(c.getId());
        }
        if (r.offerAmount() != null) {
            if (d.getStage() != DealStage.OFFER) throw ApiException.conflict("The offer can only be changed before the reservation. Change the agreed price instead.");
            if (r.offerAmount().signum() <= 0) throw ApiException.badRequest("Enter the amount offered.");
            d.setOfferAmount(money(r.offerAmount()));
        }
        if (r.agreedPrice() != null) {
            if (d.getStage() == DealStage.OFFER) throw ApiException.conflict("The price is agreed when the property is reserved.");
            if (r.agreedPrice().signum() <= 0) throw ApiException.badRequest("Enter the agreed price.");
            BigDecimal np = money(r.agreedPrice());
            BigDecimal scheduled = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id).stream().map(ReDealMilestone::getAmount).reduce(ZERO, BigDecimal::add);
            if (scheduled.compareTo(np) > 0) throw ApiException.badRequest("The payment schedule already adds up to " + plain(scheduled) + ", more than " + plain(np) + ".");
            d.setAgreedPrice(np);
        }
        if (r.offerDate() != null) d.setOfferDate(r.offerDate());
        if (r.offerValidUntil() != null) d.setOfferValidUntil(r.offerValidUntil());
        if (r.expectedCompletion() != null) d.setExpectedCompletion(r.expectedCompletion());
        if (r.notes() != null) d.setNotes(clean(r.notes(), 2000, "The notes"));
        checkPercent(r.commissionPercent(), "The commission");
        if (r.commissionPercent() != null) d.setCommissionPercent(r.commissionPercent().signum() == 0 ? null : r.commissionPercent());
        if (r.commissionFixed() != null) {
            if (r.commissionFixed().signum() < 0) throw ApiException.badRequest("The commission cannot be negative.");
            d.setCommissionFixed(r.commissionFixed().signum() == 0 ? null : money(r.commissionFixed()));
        }
        if (r.agentUserId() != null) setAgent(d, r.agentUserId());
        BigDecimal total = commissionTotal(d);
        List<ReDealCommission> lines = commissionRepository.findByDealIdOrderByIdAsc(id);
        BigDecimal alloc = allocated(lines, total, null);
        if (alloc.compareTo(total) > 0) {
            throw ApiException.badRequest("The commission shares add up to " + plain(alloc) + ", more than the commission of " + plain(total) + ". Change the shares first.");
        }
        repository.save(d);
        auditLogService.record("RE_DEAL_UPDATED", "Updated deal " + d.getCode(), "ReDeal", id);
        return dto(a, d);
    }

    // ------------------------------------------------------------------ stages

    @Transactional
    public DealDto reserve(Actor a, Long id, ReserveRequest r) {
        access.on();
        ReDeal d = find(id);
        requireStage(d, EnumSet.of(DealStage.OFFER), "reserve it");
        ReProperty p = property(d.getPropertyId());
        for (ReDeal other : repository.findByPropertyIdAndStageIn(p.getId(), HOLDING)) {
            if (!other.getId().equals(d.getId())) throw ApiException.conflict("Property " + p.getCode() + " is already reserved by deal " + other.getCode() + ".");
        }
        if (p.getStatus() != PropertyStatus.AVAILABLE && p.getStatus() != PropertyStatus.RESERVED) {
            throw ApiException.conflict("Property " + p.getCode() + " is " + p.getStatus().name().toLowerCase(Locale.ROOT) + " and cannot be reserved.");
        }
        BigDecimal price = r != null && r.agreedPrice() != null ? money(r.agreedPrice()) : d.getOfferAmount();
        if (price.signum() <= 0) throw ApiException.badRequest("Enter the agreed price.");
        BigDecimal deposit = r != null && r.deposit() != null ? money(r.deposit()) : ZERO;
        if (deposit.signum() < 0 || deposit.compareTo(price) > 0) throw ApiException.badRequest("The deposit must be between 0 and the price.");
        LocalDate expiry = r == null ? null : r.reservationExpiry();
        if (expiry != null && expiry.isBefore(LocalDate.now())) throw ApiException.badRequest("The reservation cannot end in the past.");
        if (r != null && r.expectedCompletion() != null) d.setExpectedCompletion(r.expectedCompletion());
        ensureCustomer(a, d);
        d.setAgreedPrice(price);
        d.setReservationDeposit(deposit.signum() > 0 ? deposit : null);
        d.setReservationExpiry(expiry);
        d.setStage(DealStage.RESERVED);
        if (milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id).isEmpty()) {
            int order = 0;
            if (deposit.signum() > 0) addMilestoneRow(d, MilestoneKind.DEPOSIT, "Reservation deposit", deposit, LocalDate.now(), order++);
            if (price.subtract(deposit).signum() > 0) addMilestoneRow(d, MilestoneKind.BALANCE, "Balance", price.subtract(deposit), d.getExpectedCompletion(), order);
        }
        repository.save(d);
        p.setStatus(PropertyStatus.RESERVED);
        propertyRepository.save(p);
        auditLogService.record("RE_DEAL_RESERVED", "Reserved " + p.getCode() + " for " + d.getBuyerName() + " at " + price + " (deal " + d.getCode() + ")", "ReDeal", id);
        return dto(a, d);
    }

    private ReDealMilestone addMilestoneRow(ReDeal d, MilestoneKind kind, String label, BigDecimal amount, LocalDate due, int order) {
        ReDealMilestone m = new ReDealMilestone();
        m.setDealId(d.getId());
        m.setKind(kind);
        m.setLabel(label);
        m.setAmount(amount);
        m.setDueDate(due);
        m.setSortOrder(order);
        return milestoneRepository.save(m);
    }

    @Transactional
    public DealDto agreement(Actor a, Long id, AgreementRequest r) {
        access.on();
        ReDeal d = find(id);
        requireStage(d, EnumSet.of(DealStage.RESERVED), "record the agreement");
        BigDecimal scheduled = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id).stream().map(ReDealMilestone::getAmount).reduce(ZERO, BigDecimal::add);
        if (scheduled.compareTo(d.getAgreedPrice()) != 0) {
            throw ApiException.badRequest("The payment schedule adds up to " + plain(scheduled) + " but the price is " + plain(d.getAgreedPrice()) + ". Fix the schedule before the agreement.");
        }
        d.setStage(DealStage.AGREEMENT);
        d.setAgreementDate(r != null && r.agreementDate() != null ? r.agreementDate() : LocalDate.now());
        if (r != null && r.expectedCompletion() != null) d.setExpectedCompletion(r.expectedCompletion());
        repository.save(d);
        auditLogService.record("RE_DEAL_AGREEMENT", "Sale agreement signed for deal " + d.getCode(), "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto complete(Actor a, Long id, CompleteRequest r) {
        access.on();
        ReDeal d = find(id);
        requireStage(d, HOLDING, "complete it");
        boolean force = r != null && Boolean.TRUE.equals(r.force());
        if (force && !a.admin()) throw ApiException.forbidden("Only an administrator can complete a deal that is not fully paid.");
        if (!force) {
            List<ReDealMilestone> ms = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id);
            BigDecimal scheduled = ms.stream().map(ReDealMilestone::getAmount).reduce(ZERO, BigDecimal::add);
            if (scheduled.compareTo(d.getAgreedPrice()) != 0) {
                throw ApiException.conflict("The payment schedule adds up to " + plain(scheduled) + " but the price is " + plain(d.getAgreedPrice()) + ".");
            }
            List<String> open = new ArrayList<>();
            for (ReDealMilestone m : ms) {
                MilestoneDto md = milestoneDto(m, true, LocalDate.now());
                if (!"PAID".equals(md.status())) open.add(m.getLabel() + " (" + md.status().toLowerCase(Locale.ROOT).replace('_', ' ') + ")");
            }
            if (!open.isEmpty()) throw ApiException.conflict("Not everything is paid yet: " + String.join(", ", open) + ".");
        }
        LocalDate date = r != null && r.completedDate() != null ? r.completedDate() : LocalDate.now();
        d.setStage(DealStage.COMPLETED);
        d.setCompletedDate(date);
        repository.save(d);
        ReProperty p = property(d.getPropertyId());
        p.setStatus(PropertyStatus.SOLD);
        propertyRepository.save(p);
        for (ReDeal other : repository.findByPropertyIdAndStageIn(p.getId(), EnumSet.of(DealStage.OFFER))) {
            other.setStage(DealStage.CANCELLED);
            other.setCancelReason("The property was sold to another buyer (" + d.getCode() + ").");
            repository.save(other);
        }
        if (d.getLeadId() != null) {
            leadRepository.findById(d.getLeadId()).ifPresent(l -> {
                l.setStatus(LeadStatus.WON);
                l.setLostReason(null);
                l.setLastContactAt(Instant.now());
                leadRepository.save(l);
            });
        }
        auditLogService.record("RE_DEAL_COMPLETED", "Sale completed: " + p.getCode() + " to " + d.getBuyerName() + " for " + d.getAgreedPrice() + (force ? " (not fully paid)" : ""), "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto cancel(Actor a, Long id, CancelRequest r) {
        access.on();
        ReDeal d = find(id);
        requireStage(d, LIVE, "cancel it");
        String reason = clean(r == null ? null : r.reason(), 300, "The reason");
        if (reason == null) throw ApiException.badRequest("Say why the deal is cancelled.");
        List<ReDealMilestone> ms = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id);
        for (ReDealMilestone m : ms) {
            InvoiceDto inv = invoice(m.getInvoiceId());
            if (liveInvoice(inv) && inv.amountPaid() != null && inv.amountPaid().signum() > 0) {
                throw ApiException.conflict("The buyer has already paid on invoice " + inv.invoiceNumber() + ". Cancel that invoice and refund or credit the buyer in Invoices first.");
            }
        }
        for (ReDealMilestone m : ms) {
            InvoiceDto inv = invoice(m.getInvoiceId());
            if (!liveInvoice(inv)) continue;
            if ("DRAFT".equals(inv.status())) invoiceService.deleteDraft(inv.id());
            else invoiceService.voidInvoice(inv.id(), "Deal " + d.getCode() + " cancelled: " + reason);
            m.setInvoiceId(null);
        }
        milestoneRepository.saveAll(ms);
        boolean held = HOLDING.contains(d.getStage());
        d.setStage(DealStage.CANCELLED);
        d.setCancelReason(reason);
        repository.save(d);
        for (ReDealCommission c : commissionRepository.findByDealIdOrderByIdAsc(id)) {
            if (c.getStatus() == CommissionStatus.PLANNED) {
                c.setStatus(CommissionStatus.CANCELLED);
                commissionRepository.save(c);
            }
        }
        if (held) {
            ReProperty p = property(d.getPropertyId());
            if (p.getStatus() == PropertyStatus.RESERVED && repository.countByPropertyIdAndStageIn(p.getId(), HOLDING) == 0) {
                p.setStatus(PropertyStatus.AVAILABLE);
                propertyRepository.save(p);
            }
        }
        auditLogService.record("RE_DEAL_CANCELLED", "Cancelled deal " + d.getCode() + ": " + reason, "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReDeal d = find(id);
        if (d.getStage() != DealStage.OFFER && d.getStage() != DealStage.CANCELLED) throw ApiException.conflict("Only an offer or a cancelled deal can be deleted.");
        for (ReDealMilestone m : milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id)) {
            if (liveInvoice(invoice(m.getInvoiceId()))) throw ApiException.conflict("This deal has invoices. Cancel the deal instead of deleting it.");
        }
        milestoneRepository.deleteAll(milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id));
        commissionRepository.deleteAll(commissionRepository.findByDealIdOrderByIdAsc(id));
        documentService.deleteFor(DocSubject.DEAL, id);
        repository.delete(d);
        auditLogService.record("RE_DEAL_DELETED", "Deleted deal " + d.getCode());
    }

    // ------------------------------------------------------------------ the buyer's payments

    private ReDealMilestone milestone(ReDeal d, Long milestoneId) {
        return milestoneRepository.findById(milestoneId).filter(m -> m.getDealId().equals(d.getId())).orElseThrow(() -> ApiException.notFound("Payment not found."));
    }

    private void requireScheduleOpen(ReDeal d) {
        requireStage(d, HOLDING, "change the payment schedule");
    }

    private BigDecimal scheduledExcept(Long dealId, Long except) {
        return milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(dealId).stream().filter(m -> !m.getId().equals(except)).map(ReDealMilestone::getAmount).reduce(ZERO, BigDecimal::add);
    }

    private boolean invoiced(ReDealMilestone m) {
        return liveInvoice(invoice(m.getInvoiceId()));
    }

    @Transactional
    public DealDto addMilestone(Actor a, Long id, MilestoneRequest r) {
        access.on();
        ReDeal d = find(id);
        requireScheduleOpen(d);
        String label = clean(r.label(), 120, "The label");
        if (label == null) throw ApiException.badRequest("Name the payment (deposit, first instalment...).");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount.");
        BigDecimal amount = money(r.amount());
        if (scheduledExcept(id, null).add(amount).compareTo(d.getAgreedPrice()) > 0) throw ApiException.badRequest("The payments would add up to more than the price of " + plain(d.getAgreedPrice()) + ".");
        int order = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id).size();
        addMilestoneRow(d, r.kind() == null ? MilestoneKind.INSTALMENT : r.kind(), label, amount, r.dueDate(), order);
        return dto(a, d);
    }

    @Transactional
    public DealDto updateMilestone(Actor a, Long id, Long milestoneId, MilestoneRequest r) {
        access.on();
        ReDeal d = find(id);
        requireScheduleOpen(d);
        ReDealMilestone m = milestone(d, milestoneId);
        if (invoiced(m)) {
            if (r.amount() != null && money(r.amount()).compareTo(m.getAmount()) != 0 || r.label() != null && !r.label().trim().equals(m.getLabel())) {
                throw ApiException.conflict("This payment is invoiced. Only its due date can change; void the invoice to change the amount.");
            }
        } else {
            if (r.label() != null) {
                String label = clean(r.label(), 120, "The label");
                if (label == null) throw ApiException.badRequest("Name the payment.");
                m.setLabel(label);
            }
            if (r.amount() != null) {
                if (r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount.");
                BigDecimal amount = money(r.amount());
                if (scheduledExcept(id, milestoneId).add(amount).compareTo(d.getAgreedPrice()) > 0) {
                    throw ApiException.badRequest("The payments would add up to more than the price of " + plain(d.getAgreedPrice()) + ".");
                }
                m.setAmount(amount);
            }
            if (r.kind() != null) m.setKind(r.kind());
        }
        if (r.dueDate() != null) m.setDueDate(r.dueDate());
        milestoneRepository.save(m);
        return dto(a, d);
    }

    @Transactional
    public DealDto deleteMilestone(Actor a, Long id, Long milestoneId) {
        access.on();
        ReDeal d = find(id);
        requireScheduleOpen(d);
        ReDealMilestone m = milestone(d, milestoneId);
        InvoiceDto inv = invoice(m.getInvoiceId());
        if (liveInvoice(inv)) {
            if (!"DRAFT".equals(inv.status())) throw ApiException.conflict("This payment is invoiced (" + inv.invoiceNumber() + "). Void the invoice first.");
            invoiceService.deleteDraft(inv.id());
        }
        milestoneRepository.delete(m);
        return dto(a, d);
    }

    /** Replaces the payments that are not invoiced yet with a deposit and equal instalments for what is still to be scheduled. */
    @Transactional
    public DealDto plan(Actor a, Long id, PlanRequest r) {
        access.on();
        ReDeal d = find(id);
        requireScheduleOpen(d);
        int n = r.instalments() == null ? 1 : r.instalments();
        if (n < 1 || n > 120) throw ApiException.badRequest("Use 1 to 120 instalments.");
        BigDecimal pct = r.depositPercent() == null ? ZERO : r.depositPercent();
        checkPercent(pct, "The deposit");
        int every = r.everyMonths() == null ? 1 : r.everyMonths();
        if (every < 1 || every > 24) throw ApiException.badRequest("Instalments can be 1 to 24 months apart.");
        List<ReDealMilestone> all = milestoneRepository.findByDealIdOrderBySortOrderAscIdAsc(id);
        List<ReDealMilestone> keep = new ArrayList<>();
        BigDecimal kept = ZERO;
        for (ReDealMilestone m : all) {
            if (invoiced(m)) {
                keep.add(m);
                kept = kept.add(m.getAmount());
            } else {
                milestoneRepository.delete(m);
            }
        }
        BigDecimal remaining = d.getAgreedPrice().subtract(kept);
        if (remaining.signum() <= 0) throw ApiException.badRequest("The price is already fully scheduled.");
        BigDecimal deposit = keep.isEmpty() ? money(remaining.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP)) : ZERO;
        BigDecimal rest = remaining.subtract(deposit);
        int order = keep.size();
        LocalDate today = LocalDate.now();
        if (deposit.signum() > 0) addMilestoneRow(d, MilestoneKind.DEPOSIT, "Deposit", deposit, today, order++);
        if (rest.signum() > 0) {
            BigDecimal each = rest.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
            BigDecimal used = ZERO;
            LocalDate first = r.firstDue() != null ? r.firstDue() : today.plusMonths(1);
            for (int i = 1; i <= n; i++) {
                BigDecimal amt = i == n ? rest.subtract(used) : each;
                used = used.add(amt);
                String label = n == 1 ? "Balance" : "Instalment " + i + " of " + n;
                addMilestoneRow(d, i == n ? MilestoneKind.BALANCE : MilestoneKind.INSTALMENT, label, amt, first.plusMonths((long) (i - 1) * every), order++);
            }
        }
        auditLogService.record("RE_DEAL_PLAN", "Payment plan for deal " + d.getCode() + ": " + n + " instalment(s)", "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto invoiceMilestone(Actor a, Long id, Long milestoneId, InvoiceMilestoneRequest r) {
        access.on();
        ReDeal d = find(id);
        requireScheduleOpen(d);
        ReDealMilestone m = milestone(d, milestoneId);
        if (invoiced(m)) throw ApiException.conflict("This payment already has an invoice.");
        Customer buyer = ensureCustomer(a, d);
        ReProperty p = property(d.getPropertyId());
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(buyer.getId());
        req.setDueDate(m.getDueDate());
        req.setNotes("Deal " + d.getCode() + ": " + p.getCode() + " " + p.getTitle());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(saleProduct().getId());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(m.getAmount());
        line.setDescription(m.getLabel() + ": " + p.getCode() + " " + p.getTitle());
        req.setLines(List.of(line));
        InvoiceDto inv = invoiceService.createDraft(req);
        if (r != null && Boolean.TRUE.equals(r.issue())) inv = invoiceService.issue(inv.id());
        m.setInvoiceId(inv.id());
        milestoneRepository.save(m);
        auditLogService.record("RE_DEAL_INVOICE", "Invoiced \"" + m.getLabel() + "\" of deal " + d.getCode() + " (" + inv.invoiceNumber() + ")", "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto issueMilestoneInvoice(Actor a, Long id, Long milestoneId) {
        access.on();
        ReDeal d = find(id);
        ReDealMilestone m = milestone(d, milestoneId);
        InvoiceDto inv = invoice(m.getInvoiceId());
        if (!liveInvoice(inv)) throw ApiException.badRequest("This payment has no invoice yet.");
        if (!"DRAFT".equals(inv.status())) throw ApiException.conflict("Invoice " + inv.invoiceNumber() + " is already issued.");
        invoiceService.issue(inv.id());
        return dto(a, d);
    }

    // ------------------------------------------------------------------ commission

    private ReDealCommission commissionLine(ReDeal d, Long commissionId) {
        return commissionRepository.findById(commissionId).filter(c -> c.getDealId().equals(d.getId())).orElseThrow(() -> ApiException.notFound("Commission line not found."));
    }

    private void applyCommission(ReDeal d, ReDealCommission c, CommissionRequest r, boolean full) {
        if (full || r.role() != null) {
            String role = clean(r.role(), 60, "The role");
            if (role == null) throw ApiException.badRequest("What did this person do (selling agent, referral...)?");
            c.setRole(role);
        }
        if (r.payeeKind() != null) c.setPayeeKind(r.payeeKind());
        if (c.getPayeeKind() == PayeeKind.STAFF) {
            Long uid = r.payeeUserId() != null ? r.payeeUserId() : c.getPayeeUserId();
            if (uid == null) throw ApiException.badRequest("Choose the staff member.");
            AppUser u = userRepository.findById(uid).orElseThrow(() -> ApiException.badRequest("That staff member does not exist."));
            c.setPayeeUserId(u.getId());
            c.setPayeeName(RealEstateAccess.name(u));
        } else {
            c.setPayeeUserId(null);
            String name = clean(r.payeeName(), 200, "The name");
            if (name == null && c.getPayeeName() == null) throw ApiException.badRequest("Who is the commission for? Enter a name.");
            if (name != null) c.setPayeeName(name);
        }
        if (r.basis() != null) c.setBasis(r.basis());
        if (c.getBasis() == CommissionBasis.FIXED) {
            BigDecimal amt = r.fixedAmount() != null ? r.fixedAmount() : c.getFixedAmount();
            if (amt == null || amt.signum() <= 0) throw ApiException.badRequest("Enter the amount.");
            c.setFixedAmount(money(amt));
            c.setSharePercent(null);
        } else {
            BigDecimal pct = r.sharePercent() != null ? r.sharePercent() : c.getSharePercent();
            if (pct == null || pct.signum() <= 0 || pct.compareTo(HUNDRED) > 0) throw ApiException.badRequest("The share is between 0 and 100 percent of the commission.");
            c.setSharePercent(pct.setScale(2, RoundingMode.HALF_UP));
            c.setFixedAmount(null);
        }
        if (r.note() != null) c.setNote(clean(r.note(), 300, "The note"));
        BigDecimal total = commissionTotal(d);
        if (total.signum() <= 0) throw ApiException.badRequest("Set the deal's commission (a percentage or an amount) before sharing it.");
        List<ReDealCommission> lines = commissionRepository.findByDealIdOrderByIdAsc(d.getId());
        BigDecimal others = allocated(lines, total, c.getId());
        BigDecimal mine = shareAmount(c, total);
        if (others.add(mine).compareTo(total) > 0) {
            throw ApiException.badRequest("The shares would add up to " + plain(others.add(mine)) + ", more than the commission of " + plain(total) + ".");
        }
    }

    @Transactional
    public DealDto addCommission(Actor a, Long id, CommissionRequest r) {
        access.on();
        ReDeal d = find(id);
        if (d.getStage() == DealStage.CANCELLED) throw ApiException.conflict("The deal is cancelled.");
        ReDealCommission c = new ReDealCommission();
        c.setDealId(id);
        applyCommission(d, c, r, true);
        commissionRepository.save(c);
        auditLogService.record("RE_COMMISSION_ADDED", "Commission share for " + c.getPayeeName() + " on deal " + d.getCode(), "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto updateCommission(Actor a, Long id, Long commissionId, CommissionRequest r) {
        access.on();
        ReDeal d = find(id);
        ReDealCommission c = commissionLine(d, commissionId);
        if (c.getStatus() != CommissionStatus.PLANNED) throw ApiException.conflict("This share was already posted to the accounts.");
        applyCommission(d, c, r, false);
        commissionRepository.save(c);
        return dto(a, d);
    }

    @Transactional
    public DealDto deleteCommission(Actor a, Long id, Long commissionId) {
        access.on();
        ReDeal d = find(id);
        ReDealCommission c = commissionLine(d, commissionId);
        if (c.getStatus() == CommissionStatus.POSTED) throw ApiException.conflict("This share was posted to the accounts. Reject or void the expense first and take the share back.");
        commissionRepository.delete(c);
        return dto(a, d);
    }

    /** Logs the share as an expense (awaiting approval in Expenses, and owed to the payee until it is paid). */
    @Transactional
    public DealDto postCommission(Actor a, Long id, Long commissionId, PostCommissionRequest r) {
        access.on();
        ReDeal d = find(id);
        ReDealCommission c = commissionLine(d, commissionId);
        if (d.getStage() != DealStage.COMPLETED) throw ApiException.conflict("Commission is paid when the sale is complete.");
        if (c.getStatus() != CommissionStatus.PLANNED) throw ApiException.conflict("This share is " + c.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        if (!moduleService.isEnabled(ModuleKey.EXPENSES)) throw ApiException.badRequest("Switch on the Expenses module to post commission to the accounts.");
        BigDecimal amount = shareAmount(c, commissionTotal(d));
        if (amount.signum() <= 0) throw ApiException.badRequest("This share is zero.");
        Long accountId = r != null && r.accountId() != null ? r.accountId() : settings.entity().getCommissionAccountId();
        if (accountId == null) throw ApiException.badRequest("Choose the expense account for commission (or set one in the Real Estate settings).");
        Account acc = accountRepository.findById(accountId).orElseThrow(() -> ApiException.badRequest("That account does not exist."));
        if (acc.getType() != AccountType.EXPENSE) throw ApiException.badRequest("Commission is posted to an expense account.");
        ReProperty p = property(d.getPropertyId());
        ExpenseRequest er = new ExpenseRequest();
        er.setExpenseDate(LocalDate.now());
        er.setAccountId(accountId);
        er.setPayeeType(c.getPayeeKind() == PayeeKind.STAFF ? PayeeType.STAFF : PayeeType.VENDOR);
        if (c.getPayeeKind() == PayeeKind.STAFF) er.setPayeeId(c.getPayeeUserId());
        er.setPayeeName(c.getPayeeName());
        er.setAmount(amount);
        er.setPaid(false);
        er.setMemo("Commission (" + c.getRole() + ") on " + p.getCode() + " " + p.getTitle() + ", deal " + d.getCode());
        ExpenseDto e = expenseService.create(er);
        c.setExpenseId(e.id());
        c.setStatus(CommissionStatus.POSTED);
        commissionRepository.save(c);
        auditLogService.record("RE_COMMISSION_POSTED", "Posted commission of " + amount + " for " + c.getPayeeName() + " on deal " + d.getCode(), "ReDeal", id);
        return dto(a, d);
    }

    /** Takes a share back when its expense was rejected or voided (or deleted), so it can be posted again. */
    @Transactional
    public DealDto unpostCommission(Actor a, Long id, Long commissionId) {
        access.on();
        ReDeal d = find(id);
        ReDealCommission c = commissionLine(d, commissionId);
        if (c.getStatus() != CommissionStatus.POSTED) throw ApiException.conflict("This share was not posted.");
        boolean gone = true;
        try {
            ExpenseDto e = expenseService.getById(c.getExpenseId());
            gone = e.status() == ExpenseStatus.REJECTED || e.status() == ExpenseStatus.VOIDED;
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) throw ex;
        }
        if (!gone) throw ApiException.conflict("The expense is still live. Reject or void it in Expenses first.");
        c.setExpenseId(null);
        c.setStatus(CommissionStatus.PLANNED);
        commissionRepository.save(c);
        return dto(a, d);
    }

    // ------------------------------------------------------------------ the agency's own commission (invoiced to the seller)

    /** Makes the property's owner a customer (or finds the customer with the same phone) so the commission can be invoiced to them. */
    @Transactional
    public DealDto sellerCustomer(Actor a, Long id) {
        access.on();
        ReDeal d = find(id);
        ReProperty p = property(d.getPropertyId());
        if (d.getSellerCustomerId() != null && customerRepository.existsById(d.getSellerCustomerId())) return dto(a, d);
        String name = clean(p.getOwnerName(), 200, "The owner's name");
        if (name == null) throw ApiException.badRequest("The property has no owner name. Add the owner on the property first.");
        Customer c = null;
        String key = ReLeadService.digits(p.getOwnerPhone());
        if (key != null) c = customerRepository.findAll().stream().filter(x -> key.equals(ReLeadService.digits(x.getPhone()))).findFirst().orElse(null);
        if (c == null) {
            c = new Customer();
            c.setName(name);
            c.setPhone(p.getOwnerPhone());
            c.setEmail(p.getOwnerEmail());
            customerRepository.save(c);
        }
        d.setSellerCustomerId(c.getId());
        repository.save(d);
        auditLogService.record("RE_DEAL_SELLER", "Seller of deal " + d.getCode() + " is customer #" + c.getId(), "ReDeal", id);
        return dto(a, d);
    }

    @Transactional
    public DealDto invoiceFee(Actor a, Long id, InvoiceMilestoneRequest r) {
        access.on();
        ReDeal d = find(id);
        if (d.getStage() != DealStage.COMPLETED) throw ApiException.conflict("The commission is invoiced when the sale is complete.");
        BigDecimal total = commissionTotal(d);
        if (total.signum() <= 0) throw ApiException.badRequest("This deal has no commission.");
        if (d.getSellerCustomerId() == null || !customerRepository.existsById(d.getSellerCustomerId())) throw ApiException.badRequest("Set the seller as a customer first.");
        if (liveInvoice(invoice(d.getFeeInvoiceId()))) throw ApiException.conflict("The commission already has an invoice.");
        ReProperty p = property(d.getPropertyId());
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(d.getSellerCustomerId());
        req.setDueDate(LocalDate.now().plusDays(14));
        req.setNotes("Deal " + d.getCode() + ": " + p.getCode() + " " + p.getTitle());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(saleProduct().getId());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(total);
        boolean pct = d.getCommissionFixed() == null || d.getCommissionFixed().signum() <= 0;
        line.setDescription("Sales commission on " + p.getCode() + " " + p.getTitle() + " (sold for " + plain(price(d)) + (pct ? ", " + plain(d.getCommissionPercent()) + "%" : "") + ")");
        req.setLines(List.of(line));
        InvoiceDto inv = invoiceService.createDraft(req);
        if (r != null && Boolean.TRUE.equals(r.issue())) inv = invoiceService.issue(inv.id());
        d.setFeeInvoiceId(inv.id());
        repository.save(d);
        auditLogService.record("RE_DEAL_FEE_INVOICE", "Invoiced the commission of deal " + d.getCode() + " (" + inv.invoiceNumber() + ")", "ReDeal", id);
        return dto(a, d);
    }

    // ------------------------------------------------------------------ for other parts of the module

    public record Snapshot(long openDeals, BigDecimal openValue, long soldThisMonth, BigDecimal soldValue, BigDecimal commissionToPost) {
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        List<ReDeal> all = repository.findAll();
        long open = 0;
        BigDecimal openValue = ZERO;
        long sold = 0;
        BigDecimal soldValue = ZERO;
        Map<Long, BigDecimal> toPost = new HashMap<>();
        for (ReDeal d : all) {
            if (LIVE.contains(d.getStage())) {
                open++;
                openValue = openValue.add(price(d));
            } else if (d.getStage() == DealStage.COMPLETED) {
                if (d.getCompletedDate() != null && !d.getCompletedDate().isBefore(monthStart)) {
                    sold++;
                    soldValue = soldValue.add(price(d));
                }
                toPost.put(d.getId(), commissionTotal(d));
            }
        }
        BigDecimal due = ZERO;
        if (!toPost.isEmpty()) {
            for (ReDealCommission c : commissionRepository.findByDealIdIn(toPost.keySet())) {
                if (c.getStatus() == CommissionStatus.PLANNED) due = due.add(shareAmount(c, toPost.get(c.getDealId())));
            }
        }
        return new Snapshot(open, openValue, sold, soldValue, due);
    }

    /** Commission shares of the sales completed in the period (or, with no period, of every deal that is not cancelled), for the commission report. */
    @Transactional(readOnly = true)
    public List<CommissionRow> commissionRows(LocalDate from, LocalDate to, Long payeeUserId) {
        access.on();
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        List<ReDeal> deals = repository.findAll().stream().filter(d -> d.getStage() != DealStage.CANCELLED).filter(d -> {
            if (from == null && to == null) return true;
            if (d.getStage() != DealStage.COMPLETED || d.getCompletedDate() == null) return false;
            return (from == null || !d.getCompletedDate().isBefore(from)) && (to == null || !d.getCompletedDate().isAfter(to));
        }).toList();
        Map<Long, ReDeal> byId = deals.stream().collect(Collectors.toMap(ReDeal::getId, Function.identity()));
        List<CommissionRow> out = new ArrayList<>();
        if (deals.isEmpty()) return out;
        for (ReDealCommission c : commissionRepository.findByDealIdIn(byId.keySet())) {
            if (c.getStatus() == CommissionStatus.CANCELLED) continue;
            if (payeeUserId != null && !payeeUserId.equals(c.getPayeeUserId())) continue;
            ReDeal d = byId.get(c.getDealId());
            ReProperty p = props.get(d.getPropertyId());
            CommissionDto cd = commissionDto(c, commissionTotal(d), d.getStage() == DealStage.COMPLETED);
            out.add(new CommissionRow(d.getId(), d.getCode(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), d.getCompletedDate(), d.getStage(), price(d), c.getId(),
                    c.getRole(), c.getPayeeName(), c.getPayeeKind(), cd.amount(), c.getStatus(), cd.due(), cd.expenseStatus(), cd.expensePaid()));
        }
        out.sort(Comparator.comparing(CommissionRow::completedDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(CommissionRow::dealCode));
        return out;
    }
}
