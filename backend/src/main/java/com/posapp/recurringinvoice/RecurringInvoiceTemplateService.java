package com.posapp.recurringinvoice;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.GlobalDiscountType;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.recurringinvoice.dto.RecurringInvoiceTemplateDto;
import com.posapp.recurringinvoice.dto.RecurringInvoiceTemplateRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin &gt; Sales &gt; Recurring invoices (Fourier: "Add Recurring Invoice"). CRUD for {@link
 * RecurringInvoiceTemplate} plus {@link #generateDueTemplates} — the daily scheduled sweep that
 * turns a due template into a brand-new, already-{@code ISSUED} {@code
 * com.posapp.invoice.Invoice} via {@code InvoiceService#generateForRecurringInvoiceTemplate}
 * followed immediately by {@code InvoiceService#issue} (Fourier, via AskUserQuestion: "ISSUED
 * immediately") — same two-call composition every other "build it, then issue it" path in this
 * app already uses, just without a human pausing in between. {@link #runNow} exposes the same
 * single-template logic for an admin who doesn't want to wait for the next scheduled sweep.
 *
 * <p>Picked 6:45am — between the Subscription module's own 6:30 sweep and the Recurring Expense
 * module's 6:00 one, and half an hour ahead of Quotation's 7:00 expiry alert — so none of this
 * app's daily sweeps ever contend over the same moment.
 */
@Service
public class RecurringInvoiceTemplateService {

    private final RecurringInvoiceTemplateRepository repository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;
    private final InvoiceService invoiceService;
    private final AuditLogService auditLogService;
    /** Validates the branch chosen on a template. */
    private final com.posapp.branch.BranchService branchService;

    public RecurringInvoiceTemplateService(RecurringInvoiceTemplateRepository repository,
                                            CustomerRepository customerRepository,
                                            CatalogService catalogService,
                                            InvoiceService invoiceService,
                                            AuditLogService auditLogService,
                                            com.posapp.branch.BranchService branchService) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.catalogService = catalogService;
        this.invoiceService = invoiceService;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
    }

    /** Same "on the customer ... view button" convention every other module's own listAll follows — optional `customerId` narrows this to one customer's own templates. */
    @Transactional(readOnly = true)
    public List<RecurringInvoiceTemplateDto> listAll(Long customerId) {
        List<RecurringInvoiceTemplate> rows = customerId != null
                ? repository.findByCustomerIdOrderByNameAsc(customerId)
                : repository.findAllByOrderByNameAsc();
        return rows.stream().map(RecurringInvoiceTemplateDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RecurringInvoiceTemplateDto getById(Long id) {
        return RecurringInvoiceTemplateDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public RecurringInvoiceTemplateDto create(RecurringInvoiceTemplateRequest request) {
        RecurringInvoiceTemplate template = new RecurringInvoiceTemplate();
        applyRequest(template, request);
        RecurringInvoiceTemplate saved = repository.save(template);
        auditLogService.record("RECURRING_INVOICE_TEMPLATE_CREATED",
                "Created recurring invoice template '" + saved.getName() + "' for " + saved.getCustomerName()
                        + " (" + saved.getFrequency() + ", next " + saved.getNextRunDate() + ")",
                "RecurringInvoiceTemplate", saved.getId());
        return RecurringInvoiceTemplateDto.from(saved);
    }

    /** Freely editable at any time — this is just a template/configuration, not a posted document with a lifecycle of its own, same convention as RecurringExpenseTemplate.update. */
    @Transactional
    public RecurringInvoiceTemplateDto update(Long id, RecurringInvoiceTemplateRequest request) {
        RecurringInvoiceTemplate template = getEntityOrThrow(id);
        applyRequest(template, request);
        RecurringInvoiceTemplate saved = repository.save(template);
        auditLogService.record("RECURRING_INVOICE_TEMPLATE_UPDATED", "Updated recurring invoice template '" + saved.getName() + "'", "RecurringInvoiceTemplate", saved.getId());
        return RecurringInvoiceTemplateDto.from(saved);
    }

    /** Pauses or resumes a template without losing its configuration or history — see class javadoc on "toggle, don't delete". */
    @Transactional
    public RecurringInvoiceTemplateDto setActive(Long id, boolean active) {
        RecurringInvoiceTemplate template = getEntityOrThrow(id);
        template.setActive(active);
        RecurringInvoiceTemplate saved = repository.save(template);
        auditLogService.record(active ? "RECURRING_INVOICE_TEMPLATE_RESUMED" : "RECURRING_INVOICE_TEMPLATE_PAUSED",
                (active ? "Resumed" : "Paused") + " recurring invoice template '" + saved.getName() + "'", "RecurringInvoiceTemplate", saved.getId());
        return RecurringInvoiceTemplateDto.from(saved);
    }

    /** Generates this one template's next invoice immediately, regardless of whether {@code nextRunDate} has actually arrived yet — same effect as the scheduled sweep picking it up, just on demand. */
    @Transactional
    public RecurringInvoiceTemplateDto runNow(Long id) {
        RecurringInvoiceTemplate template = getEntityOrThrow(id);
        if (!template.isActive()) {
            throw ApiException.badRequest("This template is paused — resume it before running it");
        }
        generateFrom(template);
        return RecurringInvoiceTemplateDto.from(template);
    }

    /** This template's own history — every invoice it has ever generated, newest first. */
    @Transactional(readOnly = true)
    public List<InvoiceDto> generatedInvoices(Long id) {
        getEntityOrThrow(id);
        return invoiceService.listByRecurringInvoiceTemplateId(id);
    }

    /**
     * Fourier: "Add Recurring Invoice" — once a day, every still-{@link
     * RecurringInvoiceTemplate#isActive()} template whose {@code nextRunDate} has arrived (or
     * passed) generates its next invoice and advances to its following run date. Each template is
     * its own independent unit of work, so one failing never stops the rest — mirrors {@code
     * RecurringExpenseTemplateService}/{@code SubscriptionService}'s own daily sweeps' per-item
     * isolation.
     */
    @Scheduled(cron = "0 45 6 * * *")
    @Transactional
    public void generateDueTemplates() {
        LocalDate today = LocalDate.now();
        for (RecurringInvoiceTemplate template : repository.findByActiveTrueAndNextRunDateLessThanEqual(today)) {
            try {
                generateFrom(template);
            } catch (Exception ex) {
                auditLogService.record("RECURRING_INVOICE_TEMPLATE_FAILED",
                        "Failed to generate the next invoice for recurring invoice template '" + template.getName() + "': " + ex.getMessage(),
                        "RecurringInvoiceTemplate", template.getId());
            }
        }
    }

    private void generateFrom(RecurringInvoiceTemplate template) {
        InvoiceRequest request = buildInvoiceRequest(template);
        InvoiceDto draft = invoiceService.generateForRecurringInvoiceTemplate(request, template.getId(), template.getName());
        // Fourier, via AskUserQuestion: "ISSUED immediately" — flips the brand-new draft straight
        // to ISSUED and posts it to Accounting, same DRAFT -> ISSUED transition InvoiceService.issue
        // makes for any other standalone invoice; see that method's own javadoc. Exactly one call
        // per run, so posting happens exactly once per generated invoice.
        InvoiceDto issued = invoiceService.issue(draft.id());

        template.setLastRunAt(Instant.now());
        template.setNextRunDate(template.getFrequency().addTo(template.getNextRunDate()));
        repository.save(template);

        auditLogService.record("RECURRING_INVOICE_TEMPLATE_RAN",
                "Generated and issued invoice " + issued.invoiceNumber() + " of " + issued.totalAmount() + " from recurring invoice template '"
                        + template.getName() + "' (next due " + template.getNextRunDate() + ")",
                "RecurringInvoiceTemplate", template.getId());
    }

    /** Builds the exact request shape {@code InvoiceService.generateForRecurringInvoiceTemplate} expects, from this template's own current configuration — re-read fresh every run, so an edit to the template (price, lines, discount, due-in-days) takes effect on its very next invoice. */
    private InvoiceRequest buildInvoiceRequest(RecurringInvoiceTemplate template) {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId(template.getCustomerId());
        request.setBranchId(template.getBranchId());
        request.setDueDate(template.getDueInDays() != null ? template.getNextRunDate().plusDays(template.getDueInDays()) : null);
        request.setNotes(template.getNotes() != null && !template.getNotes().isBlank()
                ? template.getNotes()
                : ("Generated from recurring invoice template: " + template.getName()));
        request.setDiscountType(template.getDiscountType() != null ? template.getDiscountType().name() : null);
        request.setDiscountPercent(template.getDiscountPercent());
        request.setDiscountFixedAmount(template.getDiscountFixedAmount());
        request.setLines(template.getLines().stream().map(line -> {
            InvoiceRequest.LineEntry entry = new InvoiceRequest.LineEntry();
            entry.setProductId(line.getProduct().getId());
            entry.setQuantity(line.getQuantity());
            entry.setUnitPrice(line.getUnitPrice());
            entry.setDescription(line.getDescription());
            entry.setDiscountPercent(line.getDiscountPercent());
            return entry;
        }).toList());
        return request;
    }

    private void applyRequest(RecurringInvoiceTemplate template, RecurringInvoiceTemplateRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
        template.setName(request.getName());
        template.setCustomerId(customer.getId());
        template.setCustomerName(customer.getName());
        template.setFrequency(request.getFrequency());
        template.setNextRunDate(request.getNextRunDate());
        template.setDueInDays(request.getDueInDays());
        template.setNotes(request.getNotes());
        // Branch: an explicit choice wins; otherwise keep the existing one on an edit, head office on a new template.
        if (request.getBranchId() != null || template.getBranchId() == null) {
            template.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
        template.setDiscountType(GlobalDiscountType.parse(request.getDiscountType()));
        template.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : java.math.BigDecimal.ZERO);
        template.setDiscountFixedAmount(request.getDiscountFixedAmount());
        template.setActive(request.isActive());

        template.getLines().clear();
        for (RecurringInvoiceTemplateRequest.LineEntry entry : request.getLines()) {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            RecurringInvoiceTemplateLine line = new RecurringInvoiceTemplateLine();
            line.setTemplate(template);
            line.setProduct(product);
            line.setQuantity(entry.getQuantity());
            line.setUnitPrice(entry.getUnitPrice());
            line.setDescription(entry.getDescription());
            line.setDiscountPercent(entry.getDiscountPercent());
            template.getLines().add(line);
        }
    }

    private RecurringInvoiceTemplate getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Recurring invoice template not found: " + id));
    }
}
