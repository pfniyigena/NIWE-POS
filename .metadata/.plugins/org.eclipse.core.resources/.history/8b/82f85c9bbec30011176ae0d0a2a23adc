package com.posapp.quotation;

import com.posapp.alerts.AlertMailService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.common.GlobalDiscountType;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.quotation.dto.QuotationDto;
import com.posapp.quotation.dto.QuotationRequest;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderRequest;
import com.posapp.settings.StoreSettingsService;
import com.posapp.common.TaxMath;
import com.posapp.tax.TaxRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A quotation — a non-binding price offer to a customer (Fourier: "Build Quotation, Invoice, and
 * the purchase-requisition approval gate ... without touching warehousing"). Deliberately does
 * nothing {@code com.posapp.salesorder.SalesOrderService} does: no stock check, no customer
 * credit-limit check, no amount owed created anywhere — a quotation is just a document until a
 * customer ACCEPTS it and it's {@link #convertToSalesOrder converted} into a real {@code
 * SalesOrder} (see {@link QuotationStatus}'s javadoc for the full lifecycle).
 * <p>
 * {@link #convertToSalesOrder} is the one place this class reaches into the Sales module — always
 * through {@link SalesOrderService}'s own public {@code createDraft}, never by touching a {@code
 * SalesOrder}/{@code SalesOrderLine} entity directly, the same "cross-module calls go through a
 * service's public methods" convention {@code CustomerCreditService}/{@code
 * SupplierPaymentService} already follow across module boundaries. The resulting Sales Order is
 * always a fresh DRAFT — confirming, fulfilling, invoicing, etc. all still happen from there on,
 * exactly as they would for a Sales Order created by hand.
 */
@Service
public class QuotationService {

    private static final Logger log = LoggerFactory.getLogger(QuotationService.class);

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final SalesOrderService salesOrderService;
    private final MailService mailService;
    private final DocumentPdfService documentPdfService;
    private final StoreSettingsService storeSettingsService;
    private final AlertMailService alertMailService;
    /** Validates the branch chosen on a quotation, applies its document prefix, and supplies the branch header for printed copies. */
    private final com.posapp.branch.BranchService branchService;
    /** Fourier: "Wiring a handful of business events to that same mailer" — see {@link #alertExpiringQuotations}. */
    private final int quotationExpiryWarningDays;

    public QuotationService(QuotationRepository quotationRepository,
                             CustomerRepository customerRepository,
                             CatalogService catalogService,
                             AuditLogService auditLogService,
                             SalesOrderService salesOrderService,
                             MailService mailService,
                             DocumentPdfService documentPdfService,
                             StoreSettingsService storeSettingsService,
                             AlertMailService alertMailService,
                             com.posapp.branch.BranchService branchService,
                             @Value("${app.pos.quotation-expiry-warning-days:3}") int quotationExpiryWarningDays) {
        this.quotationRepository = quotationRepository;
        this.customerRepository = customerRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.salesOrderService = salesOrderService;
        this.mailService = mailService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.alertMailService = alertMailService;
        this.branchService = branchService;
        this.quotationExpiryWarningDays = quotationExpiryWarningDays;
    }

    /**
     * Fourier: "Wiring a handful of business events to that same mailer" — a daily sweep (7am
     * server time) for every still-open (SENT) quotation whose {@code validUntil} is within
     * {@code quotationExpiryWarningDays} days of today, since nothing else in this app watches
     * that date (see {@link #expire}'s own javadoc: marking EXPIRED is still manual-only).
     * {@code expiryAlertSent} is set right after queuing the alert so a quote sitting in the
     * window across several days' runs only ever alerts once — see that field's own javadoc.
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void alertExpiringQuotations() {
        LocalDate cutoff = LocalDate.now().plusDays(quotationExpiryWarningDays);
        List<Quotation> expiringSoon = quotationRepository
                .findByStatusAndValidUntilLessThanEqualAndExpiryAlertSentFalse(QuotationStatus.SENT, cutoff);
        for (Quotation q : expiringSoon) {
            alertMailService.alertQuotationExpiringSoon(q);
            q.setExpiryAlertSent(true);
        }
        if (!expiringSoon.isEmpty()) {
            quotationRepository.saveAll(expiringSoon);
        }
    }

    private QuotationDto toDto(Quotation q) {
        return QuotationDto.from(q, totalAmount(q), subtotalAmount(q), taxAmount(q), discountAmount(q));
    }

    private static BigDecimal lineGrossBeforeDiscount(QuotationLine line) {
        return line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "Same for discount ... it will be per line", then "Except POS other module can
     * have global discount ... Either on line or global" — the line's gross after whichever
     * discount actually applies (this line's own PER_LINE value, or the document's uniform
     * GLOBAL value — see {@code TaxMath.effectiveDiscountPercent}) is knocked off; this is what
     * tax is actually computed on, same "discount before tax" ordering OrderCalculator.lineTotal
     * already uses for POS.
     */
    private static BigDecimal lineGross(BigDecimal headerDiscountPercent, QuotationLine line) {
        BigDecimal discountPercent = TaxMath.effectiveDiscountPercent(headerDiscountPercent, line.getDiscountPercent());
        return TaxMath.applyDiscount(lineGrossBeforeDiscount(line), discountPercent);
    }

    private static BigDecimal lineDiscountAmount(BigDecimal headerDiscountPercent, QuotationLine line) {
        return lineGrossBeforeDiscount(line).subtract(lineGross(headerDiscountPercent, line)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lineTax(BigDecimal headerDiscountPercent, QuotationLine line) {
        return TaxMath.taxOnAmount(lineGross(headerDiscountPercent, line), line.getTaxRatePercent(), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    private static BigDecimal lineNet(BigDecimal headerDiscountPercent, QuotationLine line) {
        return TaxMath.netOfTax(lineGross(headerDiscountPercent, line), lineTax(headerDiscountPercent, line), Boolean.TRUE.equals(line.getTaxInclusive()));
    }

    /** Sum of every line's pre-tax (net) contribution — same "lines, not a persisted column" shape as the rest of this class's totals. */
    public static BigDecimal subtotalAmount(Quotation q) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationLine line : q.getLines()) {
            total = total.add(lineNet(q.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sum of every line's own tax — zero when no line has a tax rate assigned (see QuotationLine.taxRatePercent's javadoc). */
    public static BigDecimal taxAmount(Quotation q) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationLine line : q.getLines()) {
            total = total.add(lineTax(q.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discount (PER_LINE or GLOBAL, whichever applies), purely informational (already folded into subtotalAmount/totalAmount above, via lineGross). Zero whenever discount is off or no line/document has one. */
    public static BigDecimal discountAmount(Quotation q) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationLine line : q.getLines()) {
            total = total.add(lineDiscountAmount(q.getDiscountPercent(), line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
     * subtotalAmount + taxAmount (previously just the sum of every line's {@code quantity *
     * unitPrice}, with no tax at all). Same shape as SalesOrderService.totalAmount.
     */
    public static BigDecimal totalAmount(Quotation q) {
        return subtotalAmount(q).add(taxAmount(q)).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<QuotationDto> listAll() {
        return quotationRepository.findAllByOrderByQuoteDateDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuotationDto getById(Long id) {
        return toDto(getEntityOrThrow(id));
    }

    @Transactional
    public QuotationDto createDraft(QuotationRequest request) {
        Quotation q = new Quotation();
        q.setCustomer(getCustomerOrThrow(request.getCustomerId()));
        // Own "QT-" prefix — see Quotation's class javadoc for why this never collides with
        // SalesOrder's "SO-" or PurchaseOrder's "PO-".
        q.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        q.setQuoteNumber(branchService.prefixedNumber(q.getBranchId(), "QT-" + System.currentTimeMillis()));
        applyRequest(q, request);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_CREATED",
                "Created quotation " + saved.getQuoteNumber() + " for " + saved.getCustomer().getName()
                        + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "Quotation", saved.getId());
        return toDto(saved);
    }

    /** Only a DRAFT can still be edited — see QuotationStatus's javadoc. */
    @Transactional
    public QuotationDto updateDraft(Long id, QuotationRequest request) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft quotation can be edited — " + q.getQuoteNumber() + " is " + q.getStatus());
        }
        q.setCustomer(getCustomerOrThrow(request.getCustomerId()));
        // Branch: an explicit choice wins; otherwise the existing one is kept. The quote number already issued is left alone.
        if (request.getBranchId() != null || q.getBranchId() == null) {
            q.setBranchId(branchService.resolveForDocument(request.getBranchId()));
        }
        q.getLines().clear();
        applyRequest(q, request);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_UPDATED", "Updated quotation " + saved.getQuoteNumber(), "Quotation", saved.getId());
        return toDto(saved);
    }

    /**
     * DRAFT -> SENT: locks the quote to further line edits and marks it as actually shown to the
     * customer. Best-effort auto-emails the quote PDF to the customer's own email if one's on
     * file and SMTP is configured (Fourier: "sending ... quotations ... to the customer's
     * email") — but never lets a failed/unconfigured send block the status change itself: a quote
     * can legitimately be "sent" by handing it over in person or over WhatsApp, so a cashier
     * without email set up yet should still be able to mark it sent. Use
     * {@link #emailToCustomer} directly (its own button on the detail screen) to resend, pick a
     * different address, or actually see whether it failed.
     */
    @Transactional
    public QuotationDto send(Long id) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft quotation can be sent — " + q.getQuoteNumber() + " is " + q.getStatus());
        }
        q.setStatus(QuotationStatus.SENT);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_SENT", "Sent quotation " + saved.getQuoteNumber() + " to " + saved.getCustomer().getName(), "Quotation", saved.getId());
        try {
            String email = saved.getCustomer() != null ? saved.getCustomer().getEmail() : null;
            if (email != null && !email.isBlank() && mailService.isConfigured()) {
                emailToCustomer(saved.getId(), null);
            }
        } catch (Exception ex) {
            log.warn("Best-effort auto-email on quotation send failed for {}: {}", saved.getQuoteNumber(), ex.getMessage());
        }
        return toDto(saved);
    }

    /**
     * Fourier: "sending other documents like quotations ... to the customer's email" — emails a
     * PDF of this quotation, at any status (unlike {@link #send}, which only auto-sends once on
     * the DRAFT -> SENT transition). {@code overrideEmail} lets the caller send to an address
     * other than the customer's own saved one (or supply one at all, if the customer has none on
     * file); null/blank falls back to the customer's stored email.
     */
    @Transactional
    public void emailToCustomer(Long id, String overrideEmail) {
        Quotation q = getEntityOrThrow(id);
        String email = resolveEmail(overrideEmail, q.getCustomer() != null ? q.getCustomer().getEmail() : null);
        byte[] pdf = documentPdfService.quotationPdf(toDto(q), storeSettingsService.getSettingsEntity(), q.getBranchId() != null ? branchService.receiptInfo(q.getBranchId()) : null);
        String html = "<p>Hello " + (q.getCustomer() != null ? escapeHtml(q.getCustomer().getName()) : "") + ",</p>"
                + "<p>Please find attached quotation " + q.getQuoteNumber() + ".</p>";
        mailService.send(email, "Quotation " + q.getQuoteNumber(),
                html, List.of(new EmailAttachment(q.getQuoteNumber() + ".pdf", "application/pdf", pdf)));
        auditLogService.record("QUOTATION_EMAILED", "Emailed quotation " + q.getQuoteNumber() + " to " + email, "Quotation", q.getId());
    }

    /**
     * Fourier: "Add print A4 on Quotation and Label it as proforma" — an A4 PDF of this quotation
     * to open in a new tab and print, same shape as {@code InvoiceService.pdf}/{@code
     * SalesOrderService.pdf}; available at any status, same as {@link #emailToCustomer}. See
     * {@code DocumentPdfService.quotationPdf}'s own javadoc for why the printed page itself is
     * headed "Proforma Invoice" rather than "Quotation".
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        Quotation q = getEntityOrThrow(id);
        return documentPdfService.quotationPdf(toDto(q), storeSettingsService.getSettingsEntity(), q.getBranchId() != null ? branchService.receiptInfo(q.getBranchId()) : null);
    }

    /** SENT -> ACCEPTED: the customer said yes — see {@link #convertToSalesOrder} for turning this into a real order. */
    @Transactional
    public QuotationDto accept(Long id) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("Only a sent quotation can be accepted — " + q.getQuoteNumber() + " is " + q.getStatus());
        }
        q.setStatus(QuotationStatus.ACCEPTED);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_ACCEPTED", "Quotation " + saved.getQuoteNumber() + " accepted by " + saved.getCustomer().getName(), "Quotation", saved.getId());
        return toDto(saved);
    }

    /** Allowed from DRAFT or SENT — covers both "the customer said no" and "we're abandoning this before sending it," same leniency PurchaseOrderService/SalesOrderService's own cancel() allow. */
    @Transactional
    public QuotationDto reject(Long id) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.DRAFT && q.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("Cannot reject quotation " + q.getQuoteNumber() + " — it is " + q.getStatus());
        }
        q.setStatus(QuotationStatus.REJECTED);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_REJECTED", "Rejected quotation " + saved.getQuoteNumber(), "Quotation", saved.getId());
        return toDto(saved);
    }

    /** SENT -> EXPIRED: manually marked (this app has no scheduled-job infrastructure to do this automatically off {@code validUntil}) once the offer has lapsed unanswered. */
    @Transactional
    public QuotationDto expire(Long id) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("Only a sent quotation can be marked expired — " + q.getQuoteNumber() + " is " + q.getStatus());
        }
        q.setStatus(QuotationStatus.EXPIRED);
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_EXPIRED", "Marked quotation " + saved.getQuoteNumber() + " as expired", "Quotation", saved.getId());
        return toDto(saved);
    }

    /**
     * ACCEPTED -> CONVERTED: copies this quote's customer/notes/lines into a brand-new DRAFT
     * {@code SalesOrder} via {@link SalesOrderService#createDraft} — see class javadoc for why
     * this never touches that module's entities directly. Returns the new Sales Order (not this
     * quotation) so the caller can navigate straight to it, the same way {@code
     * SalesOrderService.confirm} returns the order it just changed.
     */
    @Transactional
    public SalesOrderDto convertToSalesOrder(Long id) {
        Quotation q = getEntityOrThrow(id);
        if (q.getStatus() != QuotationStatus.ACCEPTED) {
            throw ApiException.badRequest("Only an accepted quotation can be converted — " + q.getQuoteNumber() + " is " + q.getStatus());
        }
        SalesOrderRequest request = new SalesOrderRequest();
        request.setCustomerId(q.getCustomer().getId());
        // The converted Sales Order stays at the quotation's branch.
        request.setBranchId(q.getBranchId());
        request.setNotes("Converted from quotation " + q.getQuoteNumber() + (q.getNotes() != null && !q.getNotes().isBlank() ? " — " + q.getNotes() : ""));
        // Fourier: "Except POS other module can have global discount ... Either on line or
        // global" — carry the quote's own GLOBAL-mode discount through to the converted Sales
        // Order too, same "already validated once, re-validates cleanly" reasoning as each
        // line's own discountPercent just below.
        request.setDiscountPercent(q.getDiscountPercent());
        request.setLines(q.getLines().stream().map(line -> {
            SalesOrderRequest.LineEntry entry = new SalesOrderRequest.LineEntry();
            entry.setProductId(line.getProduct() != null ? line.getProduct().getId() : null);
            entry.setQuantityOrdered(line.getQuantity());
            entry.setUnitPrice(line.getUnitPrice());
            // Fourier: "Same for discount ... it will be per line" — carry the quoted discount
            // through to the converted Sales Order rather than losing it; already validated once
            // when this quotation's own line was created/edited, so this re-validates cleanly
            // against the exact same store-wide settings (nothing has changed in between).
            entry.setDiscountPercent(line.getDiscountPercent());
            return entry;
        }).collect(Collectors.toList()));

        SalesOrderDto created = salesOrderService.createDraft(request);

        q.setStatus(QuotationStatus.CONVERTED);
        q.setConvertedSalesOrderId(created.id());
        q.setConvertedSalesOrderNumber(created.orderNumber());
        Quotation saved = quotationRepository.save(q);
        auditLogService.record("QUOTATION_CONVERTED",
                "Converted quotation " + saved.getQuoteNumber() + " into sales order " + created.orderNumber(),
                "Quotation", saved.getId());
        return created;
    }

    Quotation getEntityOrThrow(Long id) {
        return quotationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Quotation not found: " + id));
    }

    /** Same "override wins, otherwise fall back to what's on file, otherwise say so clearly" shape InvoiceService/OrderService's own emailing methods use. */
    private String resolveEmail(String overrideEmail, String customerEmail) {
        if (overrideEmail != null && !overrideEmail.isBlank()) return overrideEmail.trim();
        if (customerEmail != null && !customerEmail.isBlank()) return customerEmail;
        throw ApiException.badRequest("This customer has no email on file — enter one to send");
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Customer getCustomerOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
    }

    private void applyRequest(Quotation q, QuotationRequest request) {
        q.setValidUntil(request.getValidUntil());
        q.setNotes(request.getNotes());
        List<QuotationLine> lines = request.getLines().stream().map(entry -> {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            QuotationLine line = new QuotationLine();
            line.setQuotation(q);
            line.setProduct(product);
            line.setProductName(product.getName());
            line.setQuantity(entry.getQuantity());
            line.setUnitPrice(entry.getUnitPrice() != null ? entry.getUnitPrice() : product.getUnitPrice());
            // Fourier: "Same for discount (but discount should be activate in the setting or
            // configuration) and it will be per line" — validated/capped (including whether the
            // feature is even turned on) before ever reaching the line.
            line.setDiscountPercent(storeSettingsService.validateLineDiscount(entry.getDiscountPercent()));
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // snapshot the product's currently-assigned tax rate onto the line, same as
            // OrderService.buildLine does for a POS cart line.
            TaxRate taxRate = product.getTaxRate();
            if (taxRate != null) {
                line.setTaxCode(taxRate.getCode());
                line.setTaxRatePercent(taxRate.getRatePercent());
                line.setTaxInclusive(taxRate.isInclusive());
            }
            return line;
        }).collect(Collectors.toList());
        // Fourier: "Except POS other module can have global discount ... Either on line or
        // global", then "Global discount should be a fixed amount or percentage with
        // percentage as the default" — resolved after the lines above are built, since a
        // FIXED_AMOUNT entry needs each line's own raw (pre-discount) gross to convert into
        // the equivalent percentage; validated/capped (including whether the store's discount
        // mode is actually GLOBAL) the same as a directly-entered percentage always was.
        GlobalDiscountType discountType = GlobalDiscountType.parse(request.getDiscountType());
        BigDecimal requestedPercent;
        if (discountType == GlobalDiscountType.FIXED_AMOUNT) {
            BigDecimal rawSubtotal = lines.stream().map(QuotationService::lineGrossBeforeDiscount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            requestedPercent = TaxMath.percentFromFixedAmount(request.getDiscountFixedAmount(), rawSubtotal);
        } else {
            requestedPercent = request.getDiscountPercent();
        }
        q.setDiscountPercent(storeSettingsService.validateGlobalDiscount(requestedPercent));
        q.setDiscountType(discountType);
        q.setDiscountFixedAmount(discountType == GlobalDiscountType.FIXED_AMOUNT ? request.getDiscountFixedAmount() : null);
        q.getLines().addAll(lines);
    }
}
