package com.posapp.email;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.bill.dto.BillDto;
import com.posapp.branch.dto.BranchReceiptInfo;
import com.posapp.bill.dto.BillLineDto;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceLineDto;
import com.posapp.order.dto.OrderDto;
import com.posapp.order.dto.OrderLineDto;
import com.posapp.quotation.dto.QuotationDto;
import com.posapp.quotation.dto.QuotationLineDto;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import com.posapp.settings.DiscountMode;
import com.posapp.settings.StoreSettings;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.helpdesk.HelpDeskTicketType;
import com.posapp.helpdesk.dto.HelpDeskCommentDto;
import com.posapp.helpdesk.dto.HelpDeskTicketDto;
import com.posapp.inventory.PayableType;
import com.posapp.inventory.dto.SupplierPaymentDto;
import com.posapp.subscription.SubscriptionStatus;
import com.posapp.subscription.dto.SubscriptionDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

/**
 * Fourier: "sending other documents like quotations, invoices, receipts, etc to the customer's
 * email" — turns a quotation/invoice/receipt into a one-page PDF to attach to that email. Reuses
 * the same OpenPDF library {@code ReportExportService} already depends on for report exports
 * (see its class javadoc) rather than adding a second PDF dependency; deliberately simpler than a
 * full letterhead-styled printout (no logo image embedding here, unlike the printable admin
 * screens' own HTML/print-to-PDF views) — this is the emailed copy, not the print-shop original,
 * and every recipient still gets the same numbers either way.
 *
 * <p>Fourier: "add print (PDF with A4)" — the same {@link #invoicePdf} this class already built
 * for emailing is now also handed straight back to the admin UI (see {@code
 * InvoiceService.pdf}/{@code SalesOrderService.pdf}) to open in a new tab and print, and {@link
 * #salesOrderPdf} was added for the Sale Order detail screen's own "Print" button, which never
 * had an emailed-PDF equivalent to reuse before this.
 */
@Service
public class DocumentPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    /**
     * Fourier: "Add print A4 on Quotation and Label it as proforma" — headed "Proforma Invoice"
     * rather than "Quotation" on the printed/emailed page itself (the standard term for a
     * non-binding priced document that looks like an invoice but isn't a demand for payment —
     * matches this document's own actual status, see {@code QuotationStatus}'s javadoc). Nothing
     * else about the quote changes: {@code quoteNumber} (still "QT-...") is still what prints as
     * the document's own number, and every other screen/label in the app still calls this a
     * Quotation.
     */
    public byte[] quotationPdf(QuotationDto q, StoreSettings settings) {
        return quotationPdf(q, settings, null);
    }

    /** Same, with the document's branch header printed under the business details (null = none). */
    public byte[] quotationPdf(QuotationDto q, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Proforma Invoice", q.quoteNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Customer", q.customerName() != null ? q.customerName() : "—"},
                    new String[]{"Quote date", q.quoteDate() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(q.quoteDate()) : "—"},
                    new String[]{"Valid until", q.validUntil() != null ? DATE_FMT.format(q.validUntil()) : "—"},
                    new String[]{"Status", q.status()}
            ));
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount (but discount should be activate in the setting or
            // configuration) and it will be per line" — the Discount column only appears once an
            // admin has actually turned a discount mode on, either PER_LINE or GLOBAL (see
            // StoreSettings.discountMode's javadoc); otherwise every line would show a column of
            // nothing but 0.00.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = q.lines().stream()
                    .map((QuotationLineDto l) -> discountsOn
                            ? new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.discountAmount()), money(l.taxTotal()), money(l.lineTotal())}
                            : new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.taxTotal()), money(l.lineTotal())}
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // Quotation lines now carry a real per-line tax snapshot (see QuotationLine.taxCode's
            // javadoc), so this prints the real figures instead of the Subtotal=Total placeholder
            // this used before that existed.
            addSubtotalBreakdown(doc, q.subtotalAmount(), q.taxAmount(), q.discountAmount(), q.totalAmount());
            if (q.notes() != null && !q.notes().isBlank()) {
                addNotes(doc, q.notes());
            }
        });
    }

    public byte[] invoicePdf(InvoiceDto inv, StoreSettings settings) {
        return invoicePdf(inv, settings, null);
    }

    /** Same, with the document's branch header printed under the business details (null = none). */
    public byte[] invoicePdf(InvoiceDto inv, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Invoice", inv.invoiceNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Bill to", inv.customerName() != null ? inv.customerName() : "—"},
                    new String[]{"Sale order", inv.salesOrderNumber() != null ? inv.salesOrderNumber() : "—"},
                    new String[]{"Invoice date", inv.invoiceDate() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(inv.invoiceDate()) : "—"},
                    new String[]{"Due date", inv.dueDate() != null ? DATE_FMT.format(inv.dueDate()) : "—"},
                    new String[]{"Status", inv.status()},
                    // Fourier: "Add payment status on printout of bill and Invoice".
                    new String[]{"Payment status", paymentStatusLabel(inv.status(), inv.amountPaid(), inv.amountOutstanding())}
            ));
            // Fourier: "On Invoice Line add product description text" — shown as a second line
            // under the product name within the same "Item" cell, same \n-within-a-Paragraph
            // convention OpenPDF already renders correctly for a multi-line cell.
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount ... it will be per line" — see quotationPdf's own
            // comment just above for why the Discount column is conditional.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = inv.lines().stream()
                    .map((InvoiceLineDto l) -> discountsOn
                            ? new String[]{itemLabel(l.productName(), l.description()), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.discountAmount()), money(l.taxTotal()), money(l.lineTotal())}
                            : new String[]{itemLabel(l.productName(), l.description()), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.taxTotal()), money(l.lineTotal())}
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // Invoice lines now carry a real per-line tax snapshot (see InvoiceLine.taxCode's
            // javadoc), so this prints the real figures instead of the Subtotal=Total placeholder
            // this used before that existed.
            addSubtotalBreakdown(doc, inv.subtotalAmount(), inv.taxAmount(), inv.discountAmount(), inv.totalAmount());
            if (inv.notes() != null && !inv.notes().isBlank()) {
                addNotes(doc, inv.notes());
            }
        });
    }

    /**
     * Fourier: "add print (PDF with A4)" on the Sale Order detail screen — same shape and same
     * library call as {@link #invoicePdf} above, just fed from a {@code SalesOrderDto} instead.
     */
    public byte[] salesOrderPdf(SalesOrderDto so, StoreSettings settings) {
        return salesOrderPdf(so, settings, null);
    }

    /** Same, with the sale order's branch header printed under the business details (null = none). */
    public byte[] salesOrderPdf(SalesOrderDto so, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Sale Order", so.orderNumber(), settings, branch, doc -> {
            List<String[]> infoRows = new java.util.ArrayList<>(List.of(
                    new String[]{"Customer", so.customerName() != null ? so.customerName() : "—"},
                    new String[]{"Order date", so.orderDate() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(so.orderDate()) : "—"},
                    new String[]{"Due date", so.dueDate() != null ? DATE_FMT.format(so.dueDate()) : "—"},
                    new String[]{"Status", so.status()}
            ));
            // Multi-warehouse: shown only once something was actually fulfilled from a named location.
            if (so.fulfilledFrom() != null && !so.fulfilledFrom().isEmpty()) {
                infoRows.add(new String[]{"Fulfilled from", String.join(", ", so.fulfilledFrom())});
            }
            addInfoTable(doc, infoRows);
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount ... it will be per line" — see quotationPdf's own
            // comment for why the Discount column is conditional.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = so.lines().stream()
                    .map((SalesOrderLineDto l) -> discountsOn
                            ? new String[]{
                                    l.productName(),
                                    String.valueOf(l.quantityOrdered()),
                                    money(l.unitPrice()),
                                    money(l.discountAmount()),
                                    money(l.taxTotal()),
                                    money(l.lineTotal())
                            }
                            : new String[]{
                                    l.productName(),
                                    String.valueOf(l.quantityOrdered()),
                                    money(l.unitPrice()),
                                    money(l.taxTotal()),
                                    money(l.lineTotal())
                            }
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // Sale Order lines now carry a real per-line tax snapshot (see SalesOrderLine
            // .taxCode's javadoc), so this prints the real figures instead of the Subtotal=Total
            // placeholder this used before that existed.
            addSubtotalBreakdown(doc, so.subtotalAmount(), so.taxAmount(), so.discountAmount(), so.totalAmount());
            if (so.notes() != null && !so.notes().isBlank()) {
                addNotes(doc, so.notes());
            }
        });
    }

    /**
     * Fourier: "Add credit note Menu (Void) Where we can printout them" — the same shape as
     * {@link #invoicePdf}, just headed "Credit Note" with {@code creditNoteNumber} as the
     * document's own number (never {@code invoiceNumber} — the two are deliberately kept apart on
     * the printed page, see {@code Invoice.creditNoteNumber}'s javadoc) and the void reason shown
     * instead of due date, since a voided invoice no longer has one that matters.
     */
    public byte[] creditNotePdf(InvoiceDto inv, StoreSettings settings) {
        return creditNotePdf(inv, settings, null);
    }

    /** Same, with the document's branch header printed under the business details (null = none). */
    public byte[] creditNotePdf(InvoiceDto inv, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Credit Note", inv.creditNoteNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Customer", inv.customerName() != null ? inv.customerName() : "—"},
                    new String[]{"Original invoice", inv.invoiceNumber()},
                    new String[]{"Sale order", inv.salesOrderNumber() != null ? inv.salesOrderNumber() : "—"},
                    new String[]{"Voided on", inv.voidedAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(inv.voidedAt()) : "—"},
                    new String[]{"Reason", inv.voidReason() != null && !inv.voidReason().isBlank() ? inv.voidReason() : "—"}
            ));
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount ... it will be per line" — a Credit Note is a voided
            // Invoice (see this method's own class javadoc), so it inherits whatever discount was
            // already captured on that Invoice's lines — same conditional-column reasoning as
            // quotationPdf's own comment.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = inv.lines().stream()
                    .map((InvoiceLineDto l) -> discountsOn
                            ? new String[]{itemLabel(l.productName(), l.description()), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.discountAmount()), money(l.taxTotal()), money(l.lineTotal())}
                            : new String[]{itemLabel(l.productName(), l.description()), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.taxTotal()), money(l.lineTotal())}
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // same real-figures reasoning as invoicePdf above, since this is still an InvoiceDto
            // underneath (a Credit Note is a voided Invoice, not a separate entity).
            addSubtotalBreakdown(doc, inv.subtotalAmount(), inv.taxAmount(), inv.discountAmount(), inv.totalAmount());
            if (inv.notes() != null && !inv.notes().isBlank()) {
                addNotes(doc, inv.notes());
            }
        });
    }

    private String itemLabel(String productName, String description) {
        return description != null && !description.isBlank() ? productName + "\n" + description : productName;
    }

    /**
     * Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ...
     * equivalent with Invoices)" — the accounts-payable mirror of {@link #invoicePdf}, fed from a
     * {@code BillDto} instead; opened by the Bill detail screen's own "Print" button, same
     * convention as every other document here.
     */
    public byte[] billPdf(BillDto bill, StoreSettings settings) {
        return billPdf(bill, settings, null);
    }

    /** Same, with the document's branch header printed under the business details (null = none). */
    public byte[] billPdf(BillDto bill, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Bill", bill.billNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Supplier", bill.supplierName() != null ? bill.supplierName() : "—"},
                    new String[]{"Purchase order", bill.purchaseOrderNumber() != null ? bill.purchaseOrderNumber() : "—"},
                    new String[]{"Bill date", bill.billDate() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(bill.billDate()) : "—"},
                    new String[]{"Status", bill.status()},
                    // Fourier: "Add payment status on printout of bill and Invoice".
                    new String[]{"Payment status", paymentStatusLabel(bill.status(), bill.amountPaid(), bill.amountOutstanding())}
            ));
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount ... it will be per line" — see quotationPdf's own
            // comment for why the Discount column is conditional.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = bill.lines().stream()
                    .map((BillLineDto l) -> discountsOn
                            ? new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitCost()), money(l.discountAmount()), money(l.taxTotal()), money(l.lineTotal())}
                            : new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitCost()), money(l.taxTotal()), money(l.lineTotal())}
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // Bill lines now carry a real per-line tax snapshot (see BillLine.taxCode's javadoc),
            // so this prints the real figures instead of the Subtotal=Total placeholder this used
            // before that existed.
            addSubtotalBreakdown(doc, bill.subtotalAmount(), bill.taxAmount(), bill.discountAmount(), bill.totalAmount());
            if (bill.notes() != null && !bill.notes().isBlank()) {
                addNotes(doc, bill.notes());
            }
        });
    }

    /**
     * Fourier: "Add payment status on printout of bill and Invoice" — only an ISSUED document is
     * ever payable (a DRAFT/VOID one always reads back zero for both amounts — see {@code
     * InvoiceDto.amountPaid}'s own javadoc, mirrored on {@code BillDto}), so this only resolves to
     * an actual Paid/Partially paid/Unpaid reading for one; anything else prints "—" rather than
     * the misleading "Paid" a naive "nothing outstanding" check would otherwise show for a DRAFT.
     */
    private String paymentStatusLabel(String status, BigDecimal amountPaid, BigDecimal amountOutstanding) {
        if (!"ISSUED".equals(status)) {
            return "—";
        }
        BigDecimal outstanding = amountOutstanding == null ? BigDecimal.ZERO : amountOutstanding;
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return "Paid";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return "Partially paid (outstanding " + money(outstanding) + ")";
        }
        return "Unpaid";
    }

    /**
     * Fourier: "Refunds equivalent with Credit note" — the same shape as {@link #billPdf}, just
     * headed "Refund" with {@code refundNumber} as the document's own number (never {@code
     * billNumber} — the two are deliberately kept apart on the printed page, same as {@code
     * Invoice.creditNoteNumber}'s own reasoning) and the void reason shown instead of the bill
     * date, since a voided bill no longer has one that matters.
     */
    public byte[] refundPdf(BillDto bill, StoreSettings settings) {
        return refundPdf(bill, settings, null);
    }

    /** Same, with the document's branch header printed under the business details (null = none). */
    public byte[] refundPdf(BillDto bill, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Refund", bill.refundNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Supplier", bill.supplierName() != null ? bill.supplierName() : "—"},
                    new String[]{"Original bill", bill.billNumber()},
                    new String[]{"Purchase order", bill.purchaseOrderNumber() != null ? bill.purchaseOrderNumber() : "—"},
                    new String[]{"Voided on", bill.voidedAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(bill.voidedAt()) : "—"},
                    new String[]{"Reason", bill.voidReason() != null && !bill.voidReason().isBlank() ? bill.voidReason() : "—"}
            ));
            // Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill,
            // Refund)" — each line now shows its own taxTotal alongside the existing columns.
            // Fourier: "Same for discount ... it will be per line" — a Refund is a voided Bill
            // (see this method's own class javadoc), so it inherits whatever discount was already
            // captured on that Bill's lines — same conditional-column reasoning as quotationPdf's
            // own comment.
            boolean discountsOn = settings != null && settings.getDiscountMode() != DiscountMode.NONE;
            List<String[]> rows = bill.lines().stream()
                    .map((BillLineDto l) -> discountsOn
                            ? new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitCost()), money(l.discountAmount()), money(l.taxTotal()), money(l.lineTotal())}
                            : new String[]{l.productName(), String.valueOf(l.quantity()), money(l.unitCost()), money(l.taxTotal()), money(l.lineTotal())}
                    ).toList();
            if (discountsOn) {
                addLinesTableWithTaxAndDiscount(doc, rows);
            } else {
                addLinesTableWithTax(doc, rows);
            }
            // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
            // same real-figures reasoning as billPdf above, since this is still a BillDto
            // underneath (a Refund is a voided Bill, not a separate entity).
            addSubtotalBreakdown(doc, bill.subtotalAmount(), bill.taxAmount(), bill.discountAmount(), bill.totalAmount());
            if (bill.notes() != null && !bill.notes().isBlank()) {
                addNotes(doc, bill.notes());
            }
        });
    }

    public byte[] receiptPdf(OrderDto o, StoreSettings settings) {
        return receiptPdf(o, settings, null);
    }

    /** Same, with the sale's branch header printed under the business details (null = none). */
    public byte[] receiptPdf(OrderDto o, StoreSettings settings, BranchReceiptInfo branch) {
        return build("Receipt", o.invoiceNumber(), settings, branch, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Customer", o.customerName() != null ? o.customerName() : "—"},
                    new String[]{"Date", o.completedAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(o.completedAt()) : "—"},
                    new String[]{"Served by", o.cashierName() != null ? o.cashierName() : "—"}
            ));
            List<String[]> rows = o.lines().stream()
                    .map((OrderLineDto l) -> new String[]{l.description(), String.valueOf(l.quantity()), money(l.unitPrice()), money(l.lineTotal())})
                    .toList();
            addLinesTable(doc, rows);
            // Fourier: "Add sub total amount, tax amount if exist, discount amount if exists,
            // total amount for all printout" — unlike the other document types here, a POS sale
            // actually has real tax (OrderDto.taxTotal, from each line's own snapshotted
            // TaxRate — see OrderCalculator's javadoc) and real per-line discounts
            // (OrderLine.discountPercent); discountTotal is derived here from each line's own
            // pre-discount gross vs. its already-discounted lineTotal, same math
            // OrderCalculator.lineTotal itself applies, just summed and reported back out as its
            // own figure — it's purely informational (how much was knocked off), not a further
            // deduction from o.total(), which already has it baked in.
            BigDecimal discountTotal = o.lines().stream()
                    .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())).subtract(l.lineTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            addSubtotalBreakdown(doc, o.subtotal(), o.taxTotal(), discountTotal, o.total());
        });
    }

    /**
     * Fourier: "Change payment receipt printout. Make a PDF with A4" — replaces the thermal-
     * receipt-styled {@code window.print()} views {@code InvoiceRecordPaymentComponent}/{@code
     * RecordPaymentComponent} used to render in-browser, same shape as every other document this
     * service builds. {@code customerBalanceAfter} is always the customer's *current* outstanding
     * balance, not frozen at the time this particular payment was made — see {@code
     * CustomerPaymentDto}'s own class javadoc for why.
     */
    public byte[] paymentReceiptPdf(CustomerPaymentDto payment, StoreSettings settings) {
        return build("Payment Receipt", payment.receiptNumber(), settings, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Customer", payment.customerName() != null ? payment.customerName() : "—"},
                    new String[]{"Date", payment.paidAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(payment.paidAt()) : "—"},
                    new String[]{"Method", payment.method() != null && !payment.method().isBlank() ? payment.method() : "—"},
                    new String[]{"Recorded by", payment.recordedByName() != null ? payment.recordedByName() : "—"}
            ));
            if (payment.allocations() != null && !payment.allocations().isEmpty()) {
                List<String[]> rows = payment.allocations().stream()
                        .map(a -> new String[]{a.invoiceNumber() != null ? a.invoiceNumber() : "—", money(a.amountApplied())})
                        .toList();
                addTable(doc, List.of("Applied to", "Amount"), rows, new float[]{2, 1}, 1);
            }
            addAmountLine(doc, "Amount paid", payment.amount(), true);
            addAmountLine(doc, "Customer balance", payment.customerBalanceAfter(), false);
            if (payment.note() != null && !payment.note().isBlank()) {
                addNotes(doc, payment.note());
            }
        });
    }

    /**
     * Fourier: "Add view details on supplier payments list" — the accounts-payable mirror of
     * {@link #paymentReceiptPdf}, fed from a {@code SupplierPaymentDto} instead. {@code method} is
     * shown only once a supplier payment can actually carry one (see {@code SupplierPayment
     * .method}'s own javadoc — currently only {@code recordBillPayment} sets it), same "—" fallback
     * as the customer-side receipt's own Method row for a payment recorded before that field
     * existed.
     */
    public byte[] supplierPaymentReceiptPdf(SupplierPaymentDto payment, StoreSettings settings) {
        return build("Payment Receipt", payment.receiptNumber(), settings, doc -> {
            addInfoTable(doc, List.of(
                    new String[]{"Supplier", payment.supplierName() != null ? payment.supplierName() : "—"},
                    new String[]{"Date", payment.paidAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(payment.paidAt()) : "—"},
                    new String[]{"Method", payment.method() != null && !payment.method().isBlank() ? payment.method() : "—"},
                    new String[]{"Recorded by", payment.recordedByName() != null ? payment.recordedByName() : "—"}
            ));
            if (payment.allocations() != null && !payment.allocations().isEmpty()) {
                List<String[]> rows = payment.allocations().stream()
                        .map(a -> new String[]{
                                (a.orderNumber() != null ? a.orderNumber() : "—") + (a.payableType() == PayableType.BILL ? " (Bill)" : ""),
                                money(a.amountApplied())
                        })
                        .toList();
                addTable(doc, List.of("Applied to", "Amount"), rows, new float[]{2, 1}, 1);
            }
            addAmountLine(doc, "Amount paid", payment.amount(), true);
            addAmountLine(doc, "Supplier balance", payment.supplierBalanceAfter(), false);
            if (payment.note() != null && !payment.note().isBlank()) {
                addNotes(doc, payment.note());
            }
        });
    }

    /**
     * Fourier: "Print also subscription" — an A4 PDF of one subscription's own current terms,
     * plus its billing history (same invoices shown on the Subscription detail screen), to open in
     * a new tab and print. Unlike an Invoice/Quotation/Sale Order, a Subscription has no business
     * "document number" of its own, so its id stands in for one here — display-only, never stored.
     */
    public byte[] subscriptionPdf(SubscriptionDto subscription, List<InvoiceDto> billingHistory, StoreSettings settings) {
        return build("Subscription", "Subscription #" + subscription.id(), settings, doc -> {
            List<String[]> info = new java.util.ArrayList<>(List.of(
                    new String[]{"Customer", subscription.customerName() != null ? subscription.customerName() : "—"},
                    new String[]{"Plan", subscription.planName() != null ? subscription.planName() : "—"},
                    new String[]{"Price", money(subscription.price())},
                    new String[]{"Billing interval", subscription.billingInterval() != null ? subscription.billingInterval().name() : "—"},
                    // Fourier: "may be some subscription will be paid after service other will be prepaid"
                    new String[]{"Billing timing", subscription.billInAdvance() ? "Prepaid" : "Paid after service"},
                    new String[]{"Status", subscription.status() != null ? subscription.status().name() : "—"},
                    new String[]{"Start date", subscription.startDate() != null ? DATE_FMT.format(subscription.startDate()) : "—"}
            ));
            if (subscription.status() != SubscriptionStatus.CANCELLED) {
                info.add(new String[]{"Next billing", subscription.nextBillingDate() != null ? DATE_FMT.format(subscription.nextBillingDate()) : "—"});
            }
            if (subscription.lastBilledAt() != null) {
                info.add(new String[]{"Last billed", DATE_FMT.withZone(ZoneId.systemDefault()).format(subscription.lastBilledAt())});
            }
            if (subscription.cancelledAt() != null) {
                info.add(new String[]{"Cancelled", DATE_FMT.withZone(ZoneId.systemDefault()).format(subscription.cancelledAt())});
            }
            addInfoTable(doc, info);

            if (billingHistory != null && !billingHistory.isEmpty()) {
                Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                Paragraph heading = new Paragraph("Billing history", headingFont);
                heading.setSpacingAfter(6);
                doc.add(heading);
                // Fourier: "create an invoice from the last end date" — the Period column makes it
                // visible on the printed document too that each invoice covers a distinct,
                // consecutive cycle rather than repeating the same date.
                List<String[]> rows = billingHistory.stream()
                        .map(inv -> new String[]{
                                inv.invoiceNumber(),
                                inv.invoiceDate() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(inv.invoiceDate()) : "—",
                                inv.periodStart() != null ? DATE_FMT.format(inv.periodStart()) + " - " + DATE_FMT.format(inv.periodEnd()) : "—",
                                inv.status(),
                                money(inv.totalAmount())
                        }).toList();
                addTable(doc, List.of("Invoice #", "Billed on", "Period", "Status", "Total"), rows, new float[]{1.2f, 1.2f, 1.6f, 0.9f, 1f}, 4);
            }

            if (subscription.notes() != null && !subscription.notes().isBlank()) {
                addNotes(doc, subscription.notes());
            }
            if (subscription.cancelReason() != null && !subscription.cancelReason().isBlank()) {
                addNotes(doc, "Cancel reason: " + subscription.cancelReason());
            }
        });
    }

    /**
     * Fourier: "print tick[et] on PDF" — the Help Desk admin screen's own "Print" button, same
     * open-in-new-tab-and-print convention as {@link #invoicePdf}/{@link #salesOrderPdf}. {@code
     * updates} is every comment on the ticket, internal notes included — unlike the public "check
     * my ticket" lookup page (see {@code PublicHelpDeskCommentDto}'s own javadoc), this endpoint
     * is reachable only by staff (same ADMIN-only tier as the rest of {@code
     * HelpDeskTicketController}), so there's no requester-facing audience to keep an internal note
     * from; each one is just labeled "(internal)" so a reader can still tell which notes were never
     * meant for the requester. Like {@link #subscriptionPdf}, a ticket has no separate "document
     * number" field distinct from its own {@code ticketNumber}, so that's used as both the title's
     * document number and, implicitly, the only number this PDF ever shows.
     */
    public byte[] helpdeskTicketPdf(HelpDeskTicketDto t, List<HelpDeskCommentDto> updates, StoreSettings settings) {
        return build("Help Desk Ticket", t.ticketNumber(), settings, doc -> {
            List<String[]> info = new java.util.ArrayList<>(List.of(
                    new String[]{"Type", t.type() == HelpDeskTicketType.CUSTOMER ? "Customer" : "Internal"},
                    new String[]{"Status", t.status().name()},
                    new String[]{"Priority", t.priority().name()},
                    new String[]{"Category", t.categoryName() != null ? t.categoryName() : "—"},
                    new String[]{"Requester", t.requesterName() != null ? t.requesterName() : "—"}
            ));
            if (t.requesterEmail() != null && !t.requesterEmail().isBlank()) {
                info.add(new String[]{"Email", t.requesterEmail()});
            }
            if (t.requesterPhone() != null && !t.requesterPhone().isBlank()) {
                info.add(new String[]{"Phone", t.requesterPhone()});
            }
            if (t.customerName() != null) {
                info.add(new String[]{"Customer record", t.customerName()});
            }
            info.add(new String[]{"Created", t.createdAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(t.createdAt()) : "—"});
            if (t.createdByUserFullName() != null) {
                info.add(new String[]{"Created by", t.createdByUserFullName()});
            }
            if (t.assignedToUserFullName() != null) {
                info.add(new String[]{"Assigned to", t.assignedToUserFullName()});
            }
            if (t.resolvedAt() != null) {
                info.add(new String[]{"Resolved", DATE_FMT.withZone(ZoneId.systemDefault()).format(t.resolvedAt())});
            }
            if (t.closedAt() != null) {
                info.add(new String[]{"Closed", DATE_FMT.withZone(ZoneId.systemDefault()).format(t.closedAt())});
            }
            addInfoTable(doc, info);

            Font subjectFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Paragraph subjectPara = new Paragraph(t.subject(), subjectFont);
            subjectPara.setSpacingAfter(4);
            doc.add(subjectPara);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Paragraph descPara = new Paragraph(t.description(), bodyFont);
            descPara.setSpacingAfter(12);
            doc.add(descPara);

            if (t.resolutionNotes() != null && !t.resolutionNotes().isBlank()) {
                addNotes(doc, t.resolutionNotes());
            }

            if (updates != null && !updates.isEmpty()) {
                Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                Paragraph heading = new Paragraph("Updates & notes", sectionFont);
                heading.setSpacingAfter(6);
                doc.add(heading);
                List<String[]> rows = updates.stream()
                        .map(c -> new String[]{
                                c.createdAt() != null ? DATE_FMT.withZone(ZoneId.systemDefault()).format(c.createdAt()) : "—",
                                c.authorUserFullName() != null ? c.authorUserFullName() : "—",
                                c.body() + (c.internal() ? " (internal)" : "")
                        }).toList();
                // firstRightAlignedColumn = 3 (= headers.size()) — none of Date/By/Update is numeric, so nothing right-aligns.
                addTable(doc, List.of("Date", "By", "Update"), rows, new float[]{1.1f, 1.1f, 3.3f}, 3);
            }
        });
    }

    private interface BodyWriter {
        void write(Document doc) throws Exception;
    }

    private byte[] build(String title, String docNumber, StoreSettings settings, BodyWriter body) {
        return build(title, docNumber, settings, null, body);
    }

    private byte[] build(String title, String docNumber, StoreSettings settings, BranchReceiptInfo branch, BodyWriter body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            if (settings != null && settings.getBusinessName() != null && !settings.getBusinessName().isBlank()) {
                Font businessFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
                document.add(new Paragraph(settings.getBusinessName(), businessFont));
                Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
                StringBuilder details = new StringBuilder();
                if (settings.getAddress() != null && !settings.getAddress().isBlank()) details.append(settings.getAddress());
                if (settings.getPhone() != null && !settings.getPhone().isBlank()) {
                    if (details.length() > 0) details.append(" · ");
                    details.append(settings.getPhone());
                }
                if (settings.getEmail() != null && !settings.getEmail().isBlank()) {
                    if (details.length() > 0) details.append(" · ");
                    details.append(settings.getEmail());
                }
                if (details.length() > 0) {
                    Paragraph detailsPara = new Paragraph(details.toString(), smallFont);
                    detailsPara.setSpacingAfter(8);
                    document.add(detailsPara);
                }
            }

            // Branches: the branch's own name/contact/receipt header under the business details.
            if (branch != null) {
                Font branchFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                Font smallBranchFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
                document.add(new Paragraph(branch.name(), branchFont));
                StringBuilder branchDetails = new StringBuilder();
                for (String part : new String[]{branch.address(), branch.phone(), branch.email()}) {
                    if (part != null && !part.isBlank()) {
                        if (branchDetails.length() > 0) branchDetails.append(" · ");
                        branchDetails.append(part);
                    }
                }
                if (branch.taxId() != null && !branch.taxId().isBlank()) {
                    if (branchDetails.length() > 0) branchDetails.append(" · ");
                    branchDetails.append("Tax ID ").append(branch.taxId());
                }
                if (branchDetails.length() > 0) {
                    document.add(new Paragraph(branchDetails.toString(), smallBranchFont));
                }
                if (branch.receiptHeader() != null && !branch.receiptHeader().isBlank()) {
                    document.add(new Paragraph(branch.receiptHeader(), smallBranchFont));
                }
                Paragraph spacer = new Paragraph(" ", smallBranchFont);
                spacer.setSpacingAfter(4);
                document.add(spacer);
            }

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setSpacingAfter(2);
            document.add(titlePara);

            if (docNumber != null && !docNumber.isBlank()) {
                Font numberFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
                Paragraph numberPara = new Paragraph(docNumber, numberFont);
                numberPara.setSpacingAfter(14);
                document.add(numberPara);
            }

            body.write(document);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build " + title + " PDF", e);
        }
    }

    private void addInfoTable(Document doc, List<String[]> rows) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (String[] row : rows) {
            PdfPCell labelCell = new PdfPCell(new Paragraph(row[0], labelFont));
            labelCell.setBorder(Rectangle.NO_BORDER);
            labelCell.setPadding(3);
            table.addCell(labelCell);
            PdfPCell valueCell = new PdfPCell(new Paragraph(row[1], valueFont));
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setPadding(3);
            table.addCell(valueCell);
        }
        doc.add(table);
    }

    private void addLinesTable(Document doc, List<String[]> rows) throws Exception {
        addTable(doc, List.of("Item", "Qty", "Unit price", "Line total"), rows, new float[]{4, 1, 1.3f, 1.3f}, 1);
    }

    /**
     * Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill, Refund)"
     * — the same table as {@link #addLinesTable}, with an extra "Tax" column between "Line total"
     * and nothing (it's the last column) showing each line's own {@code taxTotal} — the amount
     * already snapshotted per-line (see e.g. {@code QuotationLineDto.taxTotal}'s javadoc), not a
     * value recomputed here. Used by every document type that now carries a real per-line tax
     * snapshot (Quotation/Proforma, Invoice, Credit Note, Sale Order, Bill, Refund); the POS
     * receipt keeps using the plain {@link #addLinesTable} since its own printout
     * (receipt.component.ts et al.) has never shown a per-line tax column and this change didn't
     * ask for one there.
     */
    private void addLinesTableWithTax(Document doc, List<String[]> rows) throws Exception {
        addTable(doc, List.of("Item", "Qty", "Unit price", "Tax", "Line total"), rows, new float[]{3.4f, 1, 1.2f, 1.1f, 1.3f}, 1);
    }

    /**
     * Fourier: "Same for discount (but discount should be activate in the setting or
     * configuration) and it will be per line" — the same table as {@link #addLinesTableWithTax},
     * with an extra "Discount" column between "Unit price" and "Tax" showing each line's own
     * {@code discountAmount}. Used instead of {@link #addLinesTableWithTax} only when the calling
     * document type's {@code discountsOn} check (see e.g. {@link #quotationPdf}) finds {@code
     * StoreSettings.discountMode} is something other than {@code NONE} — otherwise every row
     * would show a column of nothing but 0.00, which is worse than not showing the column at all.
     */
    private void addLinesTableWithTaxAndDiscount(Document doc, List<String[]> rows) throws Exception {
        addTable(doc, List.of("Item", "Qty", "Unit price", "Discount", "Tax", "Line total"), rows, new float[]{2.8f, 1, 1.1f, 1f, 1f, 1.2f}, 1);
    }

    /**
     * Fourier: "Print also subscription" / "Change payment receipt printout" — the generic table
     * builder {@link #addLinesTable} now delegates to, so the payment receipt's "Applied to"
     * table and the subscription's own billing-history table can reuse the identical look (dark
     * header row, right-aligned numeric columns) instead of each hand-rolling their own OpenPDF
     * table. {@code firstRightAlignedColumn} is the first column index to right-align — every
     * column before it stays left-aligned (e.g. "Item"/"Invoice #"/"Applied to" labels).
     */
    private void addTable(Document doc, List<String> headers, List<String[]> rows, float[] widths, int firstRightAlignedColumn) throws Exception {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingAfter(10);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        java.awt.Color headerBg = new java.awt.Color(0x33, 0x33, 0x33);
        headerFont.setColor(java.awt.Color.WHITE);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(5);
            table.addCell(cell);
        }
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                PdfPCell cell = new PdfPCell(new Paragraph(row[i] == null ? "" : row[i], bodyFont));
                cell.setPadding(5);
                if (i >= firstRightAlignedColumn) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cell);
            }
        }
        doc.add(table);
    }

    private void addTotal(Document doc, BigDecimal total) throws Exception {
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Paragraph totalPara = new Paragraph("Total: " + money(total), totalFont);
        totalPara.setAlignment(Element.ALIGN_RIGHT);
        totalPara.setSpacingAfter(10);
        doc.add(totalPara);
    }

    /**
     * Fourier: "Add sub total amount, tax amount if exist, discount amount if exists, total
     * amount for all printout" — a Subtotal row on every line-item document this file prints,
     * followed by a Tax row (whenever this document type even has the concept — {@code taxAmount}
     * non-null — regardless of whether the actual figure happens to be zero) and a Discount row
     * (only when this particular call actually passes a nonzero one), then the existing bold
     * {@link #addTotal} row. Fourier: "for purchases, sales, invoices, bills the tax are not
     * being captured" — every line-item document here (Quotation/Proforma, Invoice, Credit Note,
     * Sale Order, Bill, Refund, and the POS receipt) now carries a real per-line tax snapshot
     * (see e.g. {@code QuotationLine.taxCode}'s javadoc) and passes its own real {@code
     * subtotalAmount}/{@code taxAmount} here, zero or not. Fourier: "Same for discount ... it
     * will be per line" — Quotation/Sale Order/Invoice/Purchase Order (and Bill/Credit
     * Note/Refund, which inherit theirs) now have a discount concept too, gated by {@code
     * StoreSettings.discountMode} (Fourier: "Except POS other module can have global discount
     * ... Either on line or global" — PER_LINE or GLOBAL, never both); a caller whose document
     * type's discount mode is NONE still passes its real (always-zero in that case) {@code
     * discountAmount} here rather
     * than {@code null} — this method already treats a zero discount as "nothing to show" below,
     * same as it always has, so there's no behavior difference, just one less special case for
     * callers to reason about. Only the POS receipt ({@link #receiptPdf}) has its own
     * separate, always-on discount concept via {@code OrderCalculator} (gated purely by role/
     * permission, not this setting).
     *
     * <p>Tax is deliberately NOT suppressed at zero the way Discount is — {@code
     * receipt.component.ts}/{@code checkout.component.ts}/{@code terminal.component.ts} all print
     * an unconditional "Tax: 0.00" row on-screen even for an untaxed sale, and the PDF receipt
     * matches that existing convention rather than hiding the row only here.
     *
     * <p>{@code discountAmount} is purely informational — it reports how much per-line discount
     * was already folded into {@code subtotal}/{@code total} before this page was built (see
     * {@code OrderCalculator.lineTotal}'s own javadoc for why discount is applied before tax is
     * even considered); it is never itself subtracted from {@code total} again here, since
     * {@code total} already reflects it. Shown as a negative figure (a deduction), same
     * convention a printed invoice/receipt normally uses.
     */
    private void addSubtotalBreakdown(Document doc, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal total) throws Exception {
        addAmountLine(doc, "Subtotal", subtotal, false);
        if (taxAmount != null) {
            addAmountLine(doc, "Tax", taxAmount, false);
        }
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) != 0) {
            addAmountLine(doc, "Discount", discountAmount.negate(), false);
        }
        addTotal(doc, total);
    }

    /**
     * Fourier: "Change payment receipt printout" — a labelled amount line for the payment
     * receipt ("Amount paid"/"Customer balance"), right-aligned like {@link #addTotal} but not
     * tied to the word "Total", and with a smaller, unemphasized variant for the secondary line.
     */
    private void addAmountLine(Document doc, String label, BigDecimal value, boolean emphasize) throws Exception {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, emphasize ? 12 : 10);
        Paragraph para = new Paragraph(label + ": " + money(value), font);
        para.setAlignment(Element.ALIGN_RIGHT);
        para.setSpacingAfter(emphasize ? 4 : 10);
        doc.add(para);
    }

    private void addNotes(Document doc, String notes) throws Exception {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        doc.add(new Paragraph("Notes", labelFont));
        doc.add(new Paragraph(notes, bodyFont));
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
