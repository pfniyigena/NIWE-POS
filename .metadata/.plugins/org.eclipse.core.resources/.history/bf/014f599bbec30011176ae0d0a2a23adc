package com.posapp.invoice;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.GlobalDiscountType;
import com.posapp.common.TaxMath;
import com.posapp.tax.TaxRate;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.credit.dto.RecordInvoicePaymentRequest;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.invoice.dto.CancelPaidInvoiceRequest;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A formal invoice — generated from one {@code SalesOrder} (Fourier: "Build Quotation, Invoice,
 * and the purchase-requisition approval gate ... without touching warehousing") or created
 * standalone, with its own lines, by {@link #createDraft} (Fourier: "create invoice related to
 * the Sale Order and record payment on invoice ... someone can create invoice (with invoice
 * lines) without creating sales order"). {@link #generate} is the only place this reaches into
 * the Sales module — always through {@code SalesOrderService}'s own public {@link
 * SalesOrderService#getById}, which returns a plain {@code SalesOrderDto}, never the {@code
 * SalesOrder} entity itself; see {@code Invoice}'s class javadoc for why that keeps this module
 * free of any entity coupling to that one.
 * <p>
 * Where a customer's payment against an invoice is actually recorded/validated is {@code
 * CustomerCreditService} — this service only reads it (via {@link #toDto}, for the amountPaid/
 * amountOutstanding figures on every {@link InvoiceDto}) and, for a standalone invoice only, posts
 * its own revenue/receivable at {@link #issue} time (see {@code AccountingService
 * .postInvoiceIssued}'s javadoc for why a Sales-Order-derived invoice never needs this).
 */
@Service
public class InvoiceService {

    /** Fourier: "For Subscription invoice add in the description start and end date" — same display pattern DocumentPdfService's own DATE_FMT already uses, so a billing period reads "6 Oct 2026" rather than a raw ISO date. */
    private static final DateTimeFormatter PERIOD_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final InvoiceRepository invoiceRepository;
    private final SalesOrderService salesOrderService;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final CustomerRepository customerRepository;
    private final MailService mailService;
    private final DocumentPdfService documentPdfService;
    private final StoreSettingsService storeSettingsService;
    private final CustomerCreditService customerCreditService;
    private final AccountingService accountingService;
    private final PaymentMethodService paymentMethodService;
    private final InvoiceMailService invoiceMailService;
    /** Validates the branch chosen on an invoice, applies its document prefix, and supplies the branch header for printed copies. */
    private final com.posapp.branch.BranchService branchService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           SalesOrderService salesOrderService,
                           CatalogService catalogService,
                           AuditLogService auditLogService,
                           CustomerRepository customerRepository,
                           MailService mailService,
                           DocumentPdfService documentPdfService,
                           StoreSettingsService storeSettingsService,
                           CustomerCreditService customerCreditService,
                           AccountingService accountingService,
                           PaymentMethodService paymentMethodService,
                           InvoiceMailService invoiceMailService,
                           com.posapp.branch.BranchService branchService) {
        this.invoiceRepository = invoiceRepository;
        this.salesOrderService = salesOrderService;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.customerRepository = customerRepository;
        this.mailService = mailService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.customerCreditService = customerCreditService;
        this.accountingService = accountingService;
        this.paymentMethodService = paymentMethodService;
        this.invoiceMailService = invoiceMailService;
        this.branchService = branchService;
    }

    /** Branch header for a printed/emailed copy of this invoice; null for head-office rows with nothing branch-specific to print. */
    private com.posapp.branch.dto.BranchReceiptInfo branchInfo(Invoice inv) {
        return inv.getBranchId() != null ? branchService.receiptInfo(inv.getBranchId()) : null;
    }

    private InvoiceDto toDto(Invoice inv) {
        return InvoiceDto.from(inv, resolveCustomerEmail(inv.getCustomerId()),
                customerCreditService.amountPaidForInvoice(inv), customerCreditService.outstandingForInvoice(inv),
                subtotalAmount(inv), taxAmount(inv), discountAmount(inv));
    }

    private static BigDecimal lineTax(InvoiceLine line) {
        return TaxMath.taxOnAmount(line.getLineTotal(), line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    private static BigDecimal lineNet(InvoiceLine line) {
        return TaxMath.netOfTax(line.getLineTotal(), lineTax(line), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    /** Fourier: "Same for discount ... it will be per line" — derived from (unitPrice * quantity - lineTotal), since the pre-discount gross is never itself persisted. */
    private static BigDecimal lineDiscountAmount(InvoiceLine line) {
        BigDecimal grossBeforeDiscount = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        return grossBeforeDiscount.subtract(line.getLineTotal()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's own discount, purely informational (already folded into subtotalAmount/totalAmount, via lineTotal). Zero whenever the feature is off or no line has one. */
    public static BigDecimal discountAmount(Invoice inv) {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceLine line : inv.getLines()) {
            total = total.add(lineDiscountAmount(line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's pre-tax (net) contribution — computed live from the (frozen, post-issue) lines rather than its own persisted column, same as every other per-document total in this app. */
    public static BigDecimal subtotalAmount(Invoice inv) {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceLine line : inv.getLines()) {
            total = total.add(lineNet(line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's own tax — zero when no line has a tax rate assigned (see InvoiceLine.taxRatePercent's javadoc). */
    public static BigDecimal taxAmount(Invoice inv) {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceLine line : inv.getLines()) {
            total = total.add(lineTax(line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "on the customer ... view button where we can have button to view the list of
     * invoices, payments etc" — {@code customerId} narrows this to one customer's own invoices
     * (Sales-Order-derived and standalone alike), newest first, same as the unfiltered list;
     * null (the store-wide Invoices screen) returns everything, unchanged.
     */
    @Transactional(readOnly = true)
    public List<InvoiceDto> listAll(Long customerId) {
        List<Invoice> invoices = customerId != null
                ? invoiceRepository.findByCustomerIdOrderByInvoiceDateDesc(customerId)
                : invoiceRepository.findAllByOrderByInvoiceDateDesc();
        return invoices.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto getById(Long id) {
        return toDto(getEntityOrThrow(id));
    }

    /** Null (not a 404) when no invoice has ever been generated for this order — lets the Sales Order detail screen tell "none yet" apart from "look it up and something went wrong." */
    @Transactional(readOnly = true)
    public InvoiceDto getBySalesOrderIdOrNull(Long salesOrderId) {
        return invoiceRepository.findBySalesOrderId(salesOrderId).map(this::toDto).orElse(null);
    }

    /**
     * Snapshots the given Sales Order's customer/lines/amount into a brand-new, already-{@code
     * ISSUED} {@link Invoice} — see class javadoc for why this reads that order only through
     * {@code SalesOrderService.getById}'s DTO. Deliberate, explicit action from the Sales Order's
     * own "Generate invoice" button — contrast {@link #generateDraftFromSalesOrder} below, which
     * builds the exact same snapshot but starts it as {@code DRAFT} instead, for the automatic
     * on-fulfillment path.
     */
    @Transactional
    public InvoiceDto generate(Long salesOrderId) {
        Invoice saved = buildFromSalesOrder(salesOrderId, InvoiceStatus.ISSUED);
        auditLogService.record("INVOICE_GENERATED",
                "Generated invoice " + saved.getInvoiceNumber() + " for sales order " + saved.getSalesOrderNumber()
                        + " (" + saved.getTotalAmount() + ")",
                "Invoice", saved.getId());
        InvoiceDto dto = toDto(saved);
        sendIssuedEmail(dto);
        return dto;
    }

    /**
     * Fourier: "Once Sale order is FULFILLED it will create automatically a draft invoice" — the
     * exact same snapshot {@link #generate} builds, just left as {@code DRAFT} rather than
     * immediately {@code ISSUED}, since nobody explicitly asked for this one — it's a side effect
     * of fulfillment completing, not a deliberate "invoice this now" click, so an admin gets a
     * chance to review/edit it (same as any other standalone draft — see {@link #updateDraft}) and
     * issue it themselves when ready. Called from {@code SalesOrderController.fulfill} right after
     * a fulfillment call brings the order to {@code FULFILLED}, guarded there so it only ever runs
     * once per order (see that controller's own javadoc).
     */
    @Transactional
    public InvoiceDto generateDraftFromSalesOrder(Long salesOrderId) {
        Invoice saved = buildFromSalesOrder(salesOrderId, InvoiceStatus.DRAFT);
        auditLogService.record("INVOICE_DRAFT_CREATED",
                "Automatically created draft invoice " + saved.getInvoiceNumber() + " for sales order "
                        + saved.getSalesOrderNumber() + " now that it is fully fulfilled (" + saved.getTotalAmount() + ")",
                "Invoice", saved.getId());
        return toDto(saved);
    }

    /**
     * Refused for a DRAFT or CANCELLED Sales Order (nothing's actually been committed to yet, or
     * the commitment was undone) and refused a second time for any order that already has one,
     * since this app only ever issues one invoice per Sales Order (see {@code Invoice}'s class
     * javadoc — voiding doesn't free the order up for a replacement) — shared by {@link #generate}
     * and {@link #generateDraftFromSalesOrder}, which differ only in the {@code status} they hand in.
     */
    private Invoice buildFromSalesOrder(Long salesOrderId, InvoiceStatus status) {
        SalesOrderDto so = salesOrderService.getById(salesOrderId);
        if ("DRAFT".equals(so.status()) || "CANCELLED".equals(so.status())) {
            throw ApiException.badRequest("Cannot invoice sales order " + so.orderNumber() + " — it is " + so.status()
                    + ". Confirm it first.");
        }
        if (invoiceRepository.findBySalesOrderId(salesOrderId).isPresent()) {
            throw ApiException.badRequest("Sales order " + so.orderNumber() + " already has an invoice.");
        }

        Invoice invoice = new Invoice();
        // Own "INV-" prefix — see Invoice's class javadoc for why this never collides with
        // SalesOrder's "SO-", Quotation's "QT-" or PurchaseOrder's "PO-".
        // Branch comes from the Sales Order this was generated from; its document prefix applies to the number.
        invoice.setBranchId(so.branchId());
        invoice.setInvoiceNumber(branchService.prefixedNumber(invoice.getBranchId(), "INV-" + System.currentTimeMillis()));
        invoice.setStatus(status);
        invoice.setSalesOrderId(so.id());
        invoice.setSalesOrderNumber(so.orderNumber());
        invoice.setCustomerId(so.customerId());
        invoice.setCustomerName(so.customerName());
        invoice.setDueDate(so.dueDate());
        invoice.setNotes(so.notes());
        // Fourier: "Except POS other module can have global discount ... Either on line or
        // global" — carried straight through from the Sales Order this was generated from,
        // same "freeze what was actually ordered" reasoning as every other snapshot field here;
        // already validated once when the order was created/edited.
        invoice.setDiscountPercent(so.discountPercent());
        // Fourier: "Global discount should be a fixed amount or percentage with percentage as
        // the default" — likewise carried straight through, so re-opening this invoice shows
        // back how the Sales Order's own discount was actually entered.
        invoice.setDiscountType(GlobalDiscountType.parse(so.discountType()));
        invoice.setDiscountFixedAmount(so.discountFixedAmount());

        for (SalesOrderLineDto lineDto : so.lines()) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            if (lineDto.productId() != null) {
                Product product = catalogService.getProductOrThrow(lineDto.productId());
                line.setProduct(product);
                line.setDescription(product.getDescription());
            }
            line.setProductName(lineDto.productName());
            line.setQuantity(lineDto.quantityOrdered());
            line.setUnitPrice(lineDto.unitPrice());
            // Fourier: "Same for discount ... it will be per line" — carried straight through
            // from the Sales Order line this was generated from, same "freeze what was actually
            // ordered" reasoning as taxCode/taxRatePercent just below; already validated once
            // when that line was created/edited.
            line.setDiscountPercent(lineDto.discountPercent());
            BigDecimal grossBeforeDiscount = lineDto.unitPrice().multiply(BigDecimal.valueOf(lineDto.quantityOrdered())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = TaxMath.applyDiscount(grossBeforeDiscount, lineDto.discountPercent());
            line.setLineTotal(lineTotal);
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // carried straight through from the Sales Order line this was generated from, not
            // re-derived from the live product — same "freeze what was actually ordered"
            // reasoning as productName/unitPrice just above.
            line.setTaxCode(lineDto.taxCode());
            line.setTaxRatePercent(lineDto.taxRatePercent());
            line.setTaxInclusive(lineDto.taxInclusive());
            invoice.getLines().add(line);
        }
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — now
        // tax-inclusive; previously just the sum of every line's gross lineTotal.
        invoice.setTotalAmount(subtotalAmount(invoice).add(taxAmount(invoice)).setScale(2, RoundingMode.HALF_UP));

        return invoiceRepository.save(invoice);
    }

    /**
     * Fourier: "someone can create invoice (with invoice lines) without creating sales order" —
     * a brand-new invoice with no Sales Order behind it, starting life as {@code DRAFT} (see
     * {@code InvoiceStatus}'s javadoc). Lines are catalog products only, same convention as
     * {@code SalesOrderService.createDraft}'s own {@code applyRequest} — Fourier's own choice,
     * for consistent pricing/reporting rather than free-text line items.
     */
    @Transactional
    public InvoiceDto createDraft(InvoiceRequest request) {
        Customer customer = getCustomerOrThrow(request.getCustomerId());
        Invoice invoice = new Invoice();
        invoice.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        invoice.setInvoiceNumber(branchService.prefixedNumber(invoice.getBranchId(), "INV-" + System.currentTimeMillis()));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setCustomerId(customer.getId());
        invoice.setCustomerName(customer.getName());
        invoice.setDueDate(request.getDueDate());
        invoice.setNotes(request.getNotes());
        applyLines(invoice, request);
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_DRAFT_CREATED",
                "Created draft invoice " + saved.getInvoiceNumber() + " for " + customer.getName()
                        + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "Invoice", saved.getId());
        return toDto(saved);
    }

    /** Only a DRAFT can still be edited — see {@code InvoiceStatus}'s javadoc, same guard as {@code SalesOrderService.updateDraft}. */
    @Transactional
    public InvoiceDto updateDraft(Long id, InvoiceRequest request) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft invoice can be edited — " + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        Customer customer = getCustomerOrThrow(request.getCustomerId());
        invoice.setCustomerId(customer.getId());
        invoice.setCustomerName(customer.getName());
        invoice.setDueDate(request.getDueDate());
        invoice.setNotes(request.getNotes());
        // Branch: an explicit choice wins; otherwise the existing one is kept. The number already issued is left alone.
        if (request.getBranchId() != null || invoice.getBranchId() == null) {
            invoice.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
        invoice.getLines().clear();
        applyLines(invoice, request);
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_DRAFT_UPDATED", "Updated draft invoice " + saved.getInvoiceNumber(), "Invoice", saved.getId());
        return toDto(saved);
    }

    private void applyLines(Invoice invoice, InvoiceRequest request) {
        // Fourier: "Global discount should be a fixed amount or percentage with percentage as
        // the default" — a FIXED_AMOUNT entry needs each line's own raw (pre-discount) gross to
        // convert into the equivalent percentage, so the lines are built first (below) and the
        // header discount is resolved afterward, same reordering as every other document type.
        List<InvoiceRequest.LineEntry> entries = request.getLines();
        GlobalDiscountType discountType = GlobalDiscountType.parse(request.getDiscountType());
        BigDecimal requestedPercent;
        if (discountType == GlobalDiscountType.FIXED_AMOUNT) {
            BigDecimal rawSubtotal = entries.stream().map(entry -> {
                Product p = catalogService.getProductOrThrow(entry.getProductId());
                BigDecimal unitPrice = entry.getUnitPrice() != null ? entry.getUnitPrice() : p.getUnitPrice();
                return unitPrice.multiply(BigDecimal.valueOf(entry.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
            requestedPercent = TaxMath.percentFromFixedAmount(request.getDiscountFixedAmount(), rawSubtotal);
        } else {
            requestedPercent = request.getDiscountPercent();
        }
        // Fourier: "Except POS other module can have global discount ... Either on line or
        // global" — the single whole-invoice percentage, validated/capped (including whether
        // the store's discount mode is actually GLOBAL) before ever reaching the document.
        // Mutually exclusive with any line's own discountPercent in practice — only one of the
        // two is ever nonzero, since StoreSettings.discountMode gates both the same way.
        BigDecimal headerDiscountPercent = storeSettingsService.validateGlobalDiscount(requestedPercent);
        invoice.setDiscountPercent(headerDiscountPercent);
        invoice.setDiscountType(discountType);
        invoice.setDiscountFixedAmount(discountType == GlobalDiscountType.FIXED_AMOUNT ? request.getDiscountFixedAmount() : null);
        for (InvoiceRequest.LineEntry entry : entries) {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setProduct(product);
            line.setProductName(product.getName());
            // Fourier: "add product description text (by default it should pick the product
            // description)" — an explicit, non-blank description on the request wins; otherwise
            // fall back to the product's own, same "override wins, else default" shape as
            // unitPrice just below.
            String description = entry.getDescription() != null && !entry.getDescription().isBlank()
                    ? entry.getDescription()
                    : product.getDescription();
            line.setDescription(description);
            line.setQuantity(entry.getQuantity());
            BigDecimal unitPrice = entry.getUnitPrice() != null ? entry.getUnitPrice() : product.getUnitPrice();
            line.setUnitPrice(unitPrice);
            // Fourier: "Same for discount (but discount should be activate in the setting or
            // configuration) and it will be per line" — validated/capped (including whether the
            // feature is even turned on) before ever reaching the line; resolved against the
            // header discount above via TaxMath.effectiveDiscountPercent so only one of the two
            // ever actually applies, same as every other document type.
            BigDecimal ownDiscountPercent = storeSettingsService.validateLineDiscount(entry.getDiscountPercent());
            BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, ownDiscountPercent);
            line.setDiscountPercent(discountPercent);
            BigDecimal grossBeforeDiscount = unitPrice.multiply(BigDecimal.valueOf(entry.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = TaxMath.applyDiscount(grossBeforeDiscount, discountPercent);
            line.setLineTotal(lineTotal);
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // snapshot the product's currently-assigned tax rate onto the line, same as
            // OrderService.buildLine does for a POS cart line.
            TaxRate taxRate = product.getTaxRate();
            if (taxRate != null) {
                line.setTaxCode(taxRate.getCode());
                line.setTaxRatePercent(taxRate.getRatePercent());
                line.setTaxInclusive(taxRate.isInclusive());
            }
            invoice.getLines().add(line);
        }
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — now
        // tax-inclusive; previously just the sum of every line's gross lineTotal.
        invoice.setTotalAmount(subtotalAmount(invoice).add(taxAmount(invoice)).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * DRAFT -&gt; ISSUED: the moment a standalone invoice becomes a real, numbered, payable
     * document — stamps {@code invoiceDate} now (not at draft-creation time) and, since nothing
     * else ever posts for a standalone invoice, posts its own revenue/receivable to Accounting
     * (see {@code AccountingService.postInvoiceIssued}'s javadoc).
     */
    @Transactional
    public InvoiceDto issue(Long id) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft invoice can be issued — " + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        if (invoice.getLines().isEmpty()) {
            throw ApiException.badRequest("Cannot issue an invoice with no lines");
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setInvoiceDate(Instant.now());
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_ISSUED",
                "Issued invoice " + saved.getInvoiceNumber() + " for " + saved.getCustomerName() + " (" + saved.getTotalAmount() + ")",
                "Invoice", saved.getId());
        accountingService.postInvoiceIssued(saved);
        InvoiceDto dto = toDto(saved);
        sendIssuedEmail(dto);
        return dto;
    }

    /** A DRAFT was never a real document — deleting it (rather than voiding) is fine, same as it never posted anything to reverse. */
    @Transactional
    public void deleteDraft(Long id) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft invoice can be deleted — " + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        invoiceRepository.delete(invoice);
        auditLogService.record("INVOICE_DRAFT_DELETED", "Deleted draft invoice " + invoice.getInvoiceNumber(), "Invoice", invoice.getId());
    }

    /**
     * ISSUED -&gt; VOID: marks this document invalid — e.g. to correct a numbering mistake. For a
     * Sales-Order-derived invoice this never reverses anything on the underlying Sales Order (see
     * class javadoc): what the customer owes stays exactly what it was, tracked exactly the way
     * it always has been. A standalone invoice is different — {@link #issue} posted its own
     * revenue/receivable, so voiding it reverses that posting instead; refused outright once any
     * payment has been recorded against it (reversing the revenue at that point, with cash
     * already collected against it, would leave the books wrong either way — correcting a mistake
     * on a paid invoice means crediting the customer, not voiding the document).
     *
     * <p>Fourier: "Add credit note Menu (Void) Where we can printout them" — every void now also
     * stamps {@link Invoice#creditNoteNumber}, its own identifier as a credit note (see that
     * field's javadoc); this is what makes it show up on the new Credit Notes screen ({@link
     * #listCreditNotes}) and printable there ({@link #creditNotePdf}).
     */
    @Transactional
    public InvoiceDto voidInvoice(Long id, String reason) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " is already " + invoice.getStatus());
        }
        if (invoice.getSalesOrderId() == null && customerCreditService.amountPaidForInvoice(invoice).compareTo(BigDecimal.ZERO) > 0) {
            throw ApiException.badRequest("Cannot void invoice " + invoice.getInvoiceNumber() + " — it already has a payment recorded against it");
        }
        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());
        invoice.setVoidReason(reason);
        // Own "CN-" prefix — see Invoice.creditNoteNumber's javadoc.
        invoice.setCreditNoteNumber(branchService.prefixedNumber(invoice.getBranchId(), "CN-" + System.currentTimeMillis()));
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_VOIDED", "Voided invoice " + saved.getInvoiceNumber()
                + " — credit note " + saved.getCreditNoteNumber(), "Invoice", saved.getId());
        if (saved.getSalesOrderId() == null) {
            accountingService.postInvoiceVoid(saved);
        }
        return toDto(saved);
    }

    /**
     * ISSUED -&gt; VOID, for a standalone invoice that already has a payment recorded against it —
     * Fourier, on invoice INV-1790950561007's "Cannot void invoice ... it already has a payment
     * recorded against it": "VOID INVOICE → Only allowed when there is NO payment. CANCEL/CREDIT
     * PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit."
     * {@link #voidInvoice} stays exactly as it was (still refused once any payment exists); this
     * is the deliberate separate path for that case. It produces the same kind of credit note
     * (own {@code "CN-"} number, same as any void) but additionally requires the admin to say what
     * happens to the amount already collected — refund it back out through a real payment method,
     * or convert it to usable {@code Customer.creditBalance} — since simply voiding would
     * otherwise leave that amount unaccounted for (see {@code CustomerCreditService}'s own "never
     * silently reverses a payment" design).
     *
     * <p>Scoped to a standalone invoice only, same as {@link #voidInvoice}'s payment guard — a
     * Sales-Order-derived invoice's payment is tracked against the order itself, and cancelling
     * that belongs to a Sales Order Return, not here.
     */
    @Transactional
    public InvoiceDto cancelPaidInvoice(Long id, CancelPaidInvoiceRequest request) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " is already " + invoice.getStatus());
        }
        if (invoice.getSalesOrderId() != null) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " was generated from sales order "
                    + invoice.getSalesOrderNumber() + " — cancel the payment there via a Sales Order Return instead.");
        }
        BigDecimal amountPaid = customerCreditService.amountPaidForInvoice(invoice);
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber()
                    + " has no payment recorded against it — use Void invoice instead.");
        }
        if (request.getDisposition() == null) {
            throw ApiException.badRequest("Choose how to handle the payment already recorded on this invoice");
        }

        String refundMethod = null;
        if (request.getDisposition() == PaymentDisposition.REFUND) {
            if (request.getRefundMethod() == null || request.getRefundMethod().isBlank()) {
                throw ApiException.badRequest("Choose which payment method the refund goes out through");
            }
            refundMethod = paymentMethodService.validateActiveCodeOrThrow(request.getRefundMethod());
        }

        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());
        invoice.setVoidReason(request.getReason());
        // Own "CN-" prefix — see Invoice.creditNoteNumber's javadoc.
        invoice.setCreditNoteNumber(branchService.prefixedNumber(invoice.getBranchId(), "CN-" + System.currentTimeMillis()));
        invoice.setPaymentDisposition(request.getDisposition());
        invoice.setRefundMethod(refundMethod);
        Invoice saved = invoiceRepository.save(invoice);

        String dispositionDetail;
        if (request.getDisposition() == PaymentDisposition.REFUND) {
            dispositionDetail = amountPaid + " refunded via " + refundMethod;
        } else {
            Customer customer = getCustomerOrThrow(invoice.getCustomerId());
            customerCreditService.addCustomerCredit(customer, amountPaid);
            dispositionDetail = amountPaid + " converted to customer credit";
        }

        auditLogService.record("INVOICE_CANCELLED_PAID", "Cancelled paid invoice " + saved.getInvoiceNumber()
                + " — credit note " + saved.getCreditNoteNumber() + " — " + dispositionDetail, "Invoice", saved.getId());

        accountingService.postInvoiceCancelPaid(saved, amountPaid, request.getDisposition());
        return toDto(saved);
    }

    /**
     * Fourier: "Subscriptions Module" — {@code SubscriptionService}'s own entry point into this
     * module, same "cross-module calls go through a service's public method" convention {@link
     * #generate}/{@link #buildFromSalesOrder} already follow for the Sales module: this never
     * holds a {@code Subscription} entity, only the handful of primitives it needs (mirrors
     * {@code salesOrderId}/{@code salesOrderNumber} in {@link #buildFromSalesOrder}). Builds a
     * brand-new, standalone invoice — one product-less line, the same shape {@code
     * InvoiceLine.product} already allows for a Sales-Order-derived line with no catalog product
     * behind it.
     *
     * <p>Fourier: "instead of making invoice DRAFT make then ISSUED and hit accounting (when
     * click Bill now or when the Next billing is due)" — reverses the earlier "Bill now should
     * create a DRAFT invoice not ISSUED" decision: this now creates the invoice already {@code
     * ISSUED} and posts its own revenue/receivable to Accounting immediately, same as {@link
     * #issue} does for any other standalone invoice (and the same {@code
     * AccountingService.postInvoiceIssued} call). There's no draft-review step to skip past here
     * the way {@link #issue} skips it for a manually-created draft — both {@code
     * SubscriptionService.billNow} and the daily {@code generateDueInvoices} sweep call this
     * exactly once per billing cycle, so posting happens exactly once per invoice, same
     * "create-time state machine enforces exactly-once" guarantee {@link #issue}'s own {@code
     * DRAFT}-only guard provides for a manually-issued draft.
     */
    @Transactional
    public InvoiceDto generateForSubscription(Long customerId, String customerName, Long subscriptionId, String planName, BigDecimal amount,
                                               String taxCode, BigDecimal taxRatePercent, Boolean taxInclusive, BigDecimal discountPercent,
                                               LocalDate periodStart, LocalDate periodEnd) {
        Invoice invoice = new Invoice();
        // Subscriptions aren't branch-specific yet: the branch stays unset (= head office), so head office's prefix applies.
        invoice.setInvoiceNumber(branchService.prefixedNumber(null, "INV-" + System.currentTimeMillis()));
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setSubscriptionId(subscriptionId);
        invoice.setCustomerId(customerId);
        invoice.setCustomerName(customerName);
        // Fourier: "when I click to Bill now, it will create an invoice from the last end date.
        // Now it is still creating same invoice with same start and end date" — the cycle this
        // bill actually covers, computed by SubscriptionService.billOne from the subscription's
        // own nextBillingDate before/after advancing it, so each successive invoice's periodStart
        // is the previous one's periodEnd rather than every invoice looking like the same single
        // day. See Invoice.periodStart's own javadoc.
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setProductName(planName);
        line.setDescription(periodStart != null && periodEnd != null
                ? "Subscription billing — " + planName + " (" + PERIOD_DATE_FMT.format(periodStart) + " to " + PERIOD_DATE_FMT.format(periodEnd) + ")"
                : "Subscription billing — " + planName);
        line.setQuantity(1);
        line.setUnitPrice(amount);
        // Fourier: "subscription plan should also have tax and discount on it" — discount knocked
        // off this line's gross before tax is even considered, same ordering TaxMath.applyDiscount
        // (and every other document module) already uses; taxCode/taxRatePercent/taxInclusive are
        // snapshotted straight from the subscription (see SubscriptionService.billOne) rather than
        // re-derived here, same "freeze what was actually billed" reasoning as every other line's
        // own tax snapshot.
        BigDecimal discount = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        line.setDiscountPercent(discount);
        line.setLineTotal(TaxMath.applyDiscount(amount, discount).setScale(2, RoundingMode.HALF_UP));
        line.setTaxCode(taxCode);
        line.setTaxRatePercent(taxRatePercent);
        line.setTaxInclusive(taxInclusive);
        invoice.getLines().add(line);
        invoice.setTotalAmount(subtotalAmount(invoice).add(taxAmount(invoice)).setScale(2, RoundingMode.HALF_UP));

        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_GENERATED",
                "Generated and issued invoice " + saved.getInvoiceNumber() + " for subscription billing (" + planName + ", " + saved.getTotalAmount() + ")",
                "Invoice", saved.getId());
        // Fourier: "make then ISSUED and hit accounting" — posted immediately, same call issue()
        // makes for any other standalone invoice; see this method's own javadoc for why this is
        // safe to call unconditionally here (exactly once per billing cycle, never retried).
        accountingService.postInvoiceIssued(saved);
        InvoiceDto dto = toDto(saved);
        sendIssuedEmail(dto);
        return dto;
    }

    /** Fourier: "Subscriptions Module" — every invoice ever generated for one subscription, newest first; backs its own billing-history view. */
    @Transactional(readOnly = true)
    public List<InvoiceDto> listBySubscriptionId(Long subscriptionId) {
        return invoiceRepository.findBySubscriptionIdOrderByInvoiceDateDesc(subscriptionId).stream().map(this::toDto).toList();
    }

    /**
     * Fourier: "Add Recurring Invoice" — {@code RecurringInvoiceTemplateService}'s own entry
     * point into this module, same "cross-module calls go through a service's public method"
     * convention {@link #generate}/{@link #generateForSubscription} already follow. Unlike {@link
     * #generateForSubscription}'s single product-less line, a recurring-invoice-generated invoice
     * has real catalog-product lines — the exact same shape a standalone invoice created via
     * {@link #createDraft} has — so this builds the invoice the same way {@link #createDraft}
     * does (including reusing {@link #applyLines} for every tax/discount computation) and differs
     * from it only in stamping {@link Invoice#setRecurringInvoiceTemplateId}. Always starts as
     * {@code DRAFT}; the caller (see {@code RecurringInvoiceTemplateService#generateFrom}) calls
     * {@link #issue} immediately afterward — Fourier, via AskUserQuestion: "ISSUED immediately" —
     * same two-step composition rather than a third status-skipping path through this method.
     */
    @Transactional
    public InvoiceDto generateForRecurringInvoiceTemplate(InvoiceRequest request, Long recurringInvoiceTemplateId, String templateName) {
        Customer customer = getCustomerOrThrow(request.getCustomerId());
        Invoice invoice = new Invoice();
        invoice.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        invoice.setInvoiceNumber(branchService.prefixedNumber(invoice.getBranchId(), "INV-" + System.currentTimeMillis()));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setRecurringInvoiceTemplateId(recurringInvoiceTemplateId);
        invoice.setCustomerId(customer.getId());
        invoice.setCustomerName(customer.getName());
        invoice.setDueDate(request.getDueDate());
        invoice.setNotes(request.getNotes());
        applyLines(invoice, request);
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.record("INVOICE_GENERATED",
                "Generated invoice " + saved.getInvoiceNumber() + " from recurring invoice template '" + templateName + "' (" + saved.getTotalAmount() + ")",
                "Invoice", saved.getId());
        return toDto(saved);
    }

    /** Fourier: "Add Recurring Invoice" — every invoice ever generated for one recurring invoice template, newest first; backs its own "Generated invoices" history view. */
    @Transactional(readOnly = true)
    public List<InvoiceDto> listByRecurringInvoiceTemplateId(Long recurringInvoiceTemplateId) {
        return invoiceRepository.findByRecurringInvoiceTemplateIdOrderByInvoiceDateDesc(recurringInvoiceTemplateId).stream().map(this::toDto).toList();
    }

    /**
     * Fourier: "Add credit note Menu (Void) Where we can printout them" — every voided invoice,
     * store-wide, newest-voided first; backs the new Credit Notes list screen. An invoice that was
     * never voided has no credit note at all, so it never appears here.
     */
    @Transactional(readOnly = true)
    public List<InvoiceDto> listCreditNotes() {
        return invoiceRepository.findByStatusOrderByVoidedAtDesc(InvoiceStatus.VOID).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * The Credit Note's own printable PDF — same A4 shape {@link #pdf} already builds for the
     * invoice itself, just headed "Credit Note" and carrying {@code creditNoteNumber} as the
     * document's own number instead of {@code invoiceNumber} (see {@code DocumentPdfService
     * .creditNotePdf}). Refused for anything but a VOID invoice — there is no credit note before
     * that.
     */
    @Transactional(readOnly = true)
    public byte[] creditNotePdf(Long id) {
        Invoice invoice = getEntityOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.VOID) {
            throw ApiException.badRequest("Invoice " + invoice.getInvoiceNumber() + " has no credit note — it was never voided");
        }
        return documentPdfService.creditNotePdf(toDto(invoice), storeSettingsService.getSettingsEntity(), branchInfo(invoice));
    }

    /**
     * Fourier: "add print (PDF with A4)" — the same A4 PDF {@link #emailToCustomer} attaches,
     * handed back here instead so the admin UI can open it in a new tab and print it, at any
     * status (same "works for a VOID document too" reasoning as emailing).
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        Invoice invoice = getEntityOrThrow(id);
        return documentPdfService.invoicePdf(toDto(invoice), storeSettingsService.getSettingsEntity(), branchInfo(invoice));
    }

    /**
     * Fourier: "sending other documents like ... invoices ... to the customer's email" — emails
     * a PDF of this invoice, at any status (an already-VOID invoice can still be forwarded, e.g.
     * to explain to the customer why it was voided). {@code overrideEmail} lets the caller send to
     * an address other than the customer's own saved one; null/blank falls back to that.
     */
    @Transactional
    public void emailToCustomer(Long id, String overrideEmail) {
        Invoice invoice = getEntityOrThrow(id);
        String email = resolveEmail(overrideEmail, resolveCustomerEmail(invoice.getCustomerId()));
        byte[] pdf = documentPdfService.invoicePdf(toDto(invoice), storeSettingsService.getSettingsEntity(), branchInfo(invoice));
        String html = "<p>Hello " + escapeHtml(invoice.getCustomerName()) + ",</p>"
                + "<p>Please find attached invoice " + invoice.getInvoiceNumber() + ".</p>";
        mailService.send(email, "Invoice " + invoice.getInvoiceNumber(),
                html, List.of(new EmailAttachment(invoice.getInvoiceNumber() + ".pdf", "application/pdf", pdf)));
        auditLogService.record("INVOICE_EMAILED", "Emailed invoice " + invoice.getInvoiceNumber() + " to " + email, "Invoice", invoice.getId());
    }

    /**
     * Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" —
     * thin pass-through to {@code CustomerCreditService.recordInvoicePayment}, same "this service
     * only reads payment figures, CustomerCreditService is where one is actually recorded/
     * validated" boundary as this class's own javadoc already draws. Returns the
     * {@code CustomerPaymentDto} receipt, not an {@code InvoiceDto} — that's what the Record
     * Payment screen prints.
     */
    @Transactional
    public CustomerPaymentDto recordPayment(Long id, RecordInvoicePaymentRequest request, AppUser caller) {
        return customerCreditService.recordInvoicePayment(id, request, caller);
    }

    /** This invoice's own payment history — see {@code CustomerCreditService.paymentHistoryForInvoice}'s javadoc. */
    @Transactional(readOnly = true)
    public List<CustomerPaymentDto> paymentHistory(Long id) {
        return customerCreditService.paymentHistoryForInvoice(id);
    }

    Invoice getEntityOrThrow(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Invoice not found: " + id));
    }

    private String resolveCustomerEmail(Long customerId) {
        if (customerId == null) return null;
        return customerRepository.findById(customerId).map(Customer::getEmail).orElse(null);
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
    }

    /**
     * Fourier: "Every time invoice is ISSUED send try to send email to customer and to
     * configured emails" — fired from every call site that lands an invoice on {@code ISSUED}
     * ({@link #issue}, {@link #generate}, {@link #generateForSubscription} — never {@link
     * #generateDraftFromSalesOrder} or {@link #generateForRecurringInvoiceTemplate}, both of
     * which always start {@code DRAFT}; a recurring-invoice template's own caller calls {@link
     * #issue} immediately afterward, which fires this exactly once there instead of a second time
     * here). Delegates to {@link InvoiceMailService}, which is {@code @Async} and never throws —
     * see that class's own javadoc for why issuing/posting an invoice must never fail just
     * because its email couldn't go out.
     */
    private void sendIssuedEmail(InvoiceDto dto) {
        invoiceMailService.notifyInvoiceIssued(dto, storeSettingsService.getSettingsEntity());
    }

    /** Same "override wins, otherwise fall back to what's on file, otherwise say so clearly" shape QuotationService/OrderService's own emailing methods use. */
    private String resolveEmail(String overrideEmail, String customerEmail) {
        if (overrideEmail != null && !overrideEmail.isBlank()) return overrideEmail.trim();
        if (customerEmail != null && !customerEmail.isBlank()) return customerEmail;
        throw ApiException.badRequest("This customer has no email on file — enter one to send");
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
