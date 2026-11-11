package com.posapp.papers;
import com.posapp.i18n.I18n;
import com.posapp.common.Qty;


import java.math.BigDecimal;import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.posapp.common.PdfKit;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.PurchaseOrderLineDto;
import com.posapp.inventory.dto.StockCountDto;
import com.posapp.inventory.dto.StockCountLineDto;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.salesorder.dto.SalesOrderLineDto;
import com.posapp.warehouse.dto.StockTransferDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.posapp.common.PdfKit.SOFT;
import static com.posapp.common.PdfKit.bold;
import static com.posapp.common.PdfKit.cell;
import static com.posapp.common.PdfKit.headerCells;
import static com.posapp.common.PdfKit.muted;
import static com.posapp.common.PdfKit.nz;
import static com.posapp.common.PdfKit.plain;
import static com.posapp.common.PdfKit.section;
import static com.posapp.common.PdfKit.shortDay;

/**
 * The paper that travels with the goods or the clipboard: delivery note, goods-received note, stock count sheet and stock transfer note. None of them shows prices
 * or costs (the people holding them do not need to see those), and each ends with signature lines.
 */
@Component
public class PaperFiles {

    /** One delivery that was booked against a sales order. */
    public record Delivery(Instant when, String product, BigDecimal quantity, String from) {
    }

    /** One receipt that was booked against a purchase order. */
    public record Receipt(Instant when, String product, BigDecimal quantity) {
    }

    private final PdfKit kit;

    public PaperFiles(PdfKit kit) {
        this.kit = kit;
    }

    private static String day(Instant t) {
        return t == null ? "" : shortDay(t.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static String dayOf(Instant t) {
        return t == null ? "" : PdfKit.day(t.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static String num(int n) {
        return String.valueOf(n);
    }

    private static String num(BigDecimal n) {
        return Qty.str(n);
    }

    /** An empty box to write in. */
    private static com.lowagie.text.pdf.PdfPCell blank() {
        com.lowagie.text.pdf.PdfPCell c = cell("", plain(9), Element.ALIGN_LEFT, null);
        c.setMinimumHeight(22);
        return c;
    }

    private static void note(Document doc, String text) throws Exception {
        if (text == null || text.isBlank()) return;
        section(doc, I18n.t("Notes"));
        doc.add(new Paragraph(text.trim(), plain(9.5f)));
    }

    // ------------------------------------------------------------------ delivery note

    public byte[] deliveryNote(SalesOrderDto so, String phone, String email, List<Delivery> deliveries) {
        return kit.build(false, I18n.t("Delivery note ") + so.orderNumber(), doc -> {
            kit.head(doc, I18n.t("Delivery note"), so.orderNumber() + I18n.t("  |  Printed ") + PdfKit.day(LocalDate.now()));
            doc.add(PdfKit.facts(new String[][]{{I18n.t("Deliver to"), so.customerName()}, {I18n.t("Phone"), phone}, {I18n.t("E-mail"), email}, {I18n.t("Order date"), dayOf(so.orderDate())},
                    {I18n.t("Expected delivery"), dayOf(so.expectedDate())}, {I18n.t("Order status"), so.status().replace('_', ' ').toLowerCase()}}));
            section(doc, I18n.t("Goods"));
            PdfPTable t = new PdfPTable(new float[]{0.6f, 5f, 1.6f, 1.8f, 1.8f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{"#", I18n.t("Item"), I18n.t("Ordered"), I18n.t("Delivered to date"), I18n.t("Still to deliver")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            int i = 1;
            BigDecimal ordered = BigDecimal.ZERO, done = BigDecimal.ZERO;
            for (SalesOrderLineDto l : so.lines()) {
                BigDecimal left = Qty.max(BigDecimal.ZERO, l.quantityOrdered().subtract(l.quantityFulfilled()));
                t.addCell(cell(String.valueOf(i++), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.productName(), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(num(l.quantityOrdered()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(num(l.quantityFulfilled()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(num(left), bold(9), Element.ALIGN_RIGHT, null));
                ordered = ordered.add(l.quantityOrdered());
                done = done.add(l.quantityFulfilled());
            }
            t.addCell(cell("", bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(I18n.t("Total units"), bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(num(ordered), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(num(done), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(num(Qty.max(BigDecimal.ZERO, ordered.subtract(done))), bold(9), Element.ALIGN_RIGHT, SOFT));
            doc.add(t);
            if (!deliveries.isEmpty()) {
                section(doc, I18n.t("Deliveries so far"));
                PdfPTable d = new PdfPTable(new float[]{1.6f, 5f, 1.2f, 3f});
                d.setWidthPercentage(100);
                headerCells(d, new String[]{I18n.t("Date"), I18n.t("Item"), I18n.t("Quantity"), I18n.t("Taken from")}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT});
                for (Delivery x : deliveries) {
                    d.addCell(cell(day(x.when()), plain(8.5f), Element.ALIGN_LEFT, null));
                    d.addCell(cell(x.product(), plain(8.5f), Element.ALIGN_LEFT, null));
                    d.addCell(cell(num(x.quantity()), plain(8.5f), Element.ALIGN_RIGHT, null));
                    d.addCell(cell(nz(x.from()), plain(8.5f), Element.ALIGN_LEFT, null));
                }
                doc.add(d);
            }
            note(doc, so.notes());
            Paragraph p = new Paragraph(I18n.t("Please check the goods on delivery. Tell us about any shortage or damage when you sign."), muted(9));
            p.setSpacingBefore(14);
            doc.add(p);
            PdfKit.signatures(doc, I18n.t("Prepared by"), I18n.t("Driver / carrier"), I18n.t("Received by (customer)"));
        });
    }

    // ------------------------------------------------------------------ goods received note

    public byte[] goodsReceived(PurchaseOrderDto po, String contact, String phone, List<Receipt> receipts) {
        return kit.build(false, I18n.t("Goods received note ") + po.orderNumber(), doc -> {
            kit.head(doc, I18n.t("Goods received note"), po.orderNumber() + I18n.t("  |  Printed ") + PdfKit.day(LocalDate.now()));
            doc.add(PdfKit.facts(new String[][]{{I18n.t("Supplier"), po.supplierName()}, {I18n.t("Contact"), contact}, {I18n.t("Phone"), phone}, {I18n.t("Ordered on"), dayOf(po.orderDate())},
                    {I18n.t("Expected"), dayOf(po.expectedDate())}, {I18n.t("Receiving into"), po.receivingWarehouseName()}, {I18n.t("Order status"), po.status().replace('_', ' ').toLowerCase()}}));
            section(doc, I18n.t("Goods expected"));
            PdfPTable t = new PdfPTable(new float[]{0.5f, 4f, 1.3f, 1.5f, 1.4f, 1.7f, 2.4f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{"#", I18n.t("Item"), I18n.t("Ordered"), I18n.t("Received to date"), I18n.t("Still expected"), I18n.t("Received now"), I18n.t("Remarks")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_LEFT});
            int i = 1;
            BigDecimal ordered = BigDecimal.ZERO, got = BigDecimal.ZERO;
            for (PurchaseOrderLineDto l : po.lines()) {
                BigDecimal left = Qty.max(BigDecimal.ZERO, l.quantityOrdered().subtract(l.quantityReceived()));
                t.addCell(cell(String.valueOf(i++), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.productName(), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(num(l.quantityOrdered()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(num(l.quantityReceived()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(num(left), bold(9), Element.ALIGN_RIGHT, null));
                t.addCell(blank());
                t.addCell(blank());
                ordered = ordered.add(l.quantityOrdered());
                got = got.add(l.quantityReceived());
            }
            t.addCell(cell("", bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(I18n.t("Total units"), bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(num(ordered), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(num(got), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(num(Qty.max(BigDecimal.ZERO, ordered.subtract(got))), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell("", bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", bold(9), Element.ALIGN_LEFT, SOFT));
            doc.add(t);
            if (!receipts.isEmpty()) {
                section(doc, I18n.t("Received so far"));
                PdfPTable d = new PdfPTable(new float[]{1.6f, 6f, 1.2f});
                d.setWidthPercentage(100);
                headerCells(d, new String[]{I18n.t("Date"), I18n.t("Item"), I18n.t("Quantity")}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (Receipt x : receipts) {
                    d.addCell(cell(day(x.when()), plain(8.5f), Element.ALIGN_LEFT, null));
                    d.addCell(cell(x.product(), plain(8.5f), Element.ALIGN_LEFT, null));
                    d.addCell(cell(num(x.quantity()), plain(8.5f), Element.ALIGN_RIGHT, null));
                }
                doc.add(d);
            }
            note(doc, po.notes());
            Paragraph p = new Paragraph(I18n.t("Count and check the goods against this list before signing. Write what you actually received in the empty boxes, then record it in the system."), muted(9));
            p.setSpacingBefore(14);
            doc.add(p);
            PdfKit.signatures(doc, I18n.t("Received by"), I18n.t("Checked by"), I18n.t("Supplier's driver"));
        });
    }

    // ------------------------------------------------------------------ stock count sheet

    /**
     * While the count is in progress this is the clipboard sheet: counted column empty and, unless {@code showSystem}, the expected quantity left off so nobody counts
     * towards a number. Once the count is submitted or approved it becomes the result sheet with counted quantity and difference.
     */
    public byte[] countSheet(StockCountDto c, boolean showSystem) {
        boolean result = !"IN_PROGRESS".equals(c.status());
        boolean system = showSystem || result;
        String title = result ? I18n.t("Stock count result") : I18n.t("Stock count sheet");
        return kit.build(true, title + " " + c.countNumber(), doc -> {
            kit.head(doc, title, c.countNumber() + I18n.t("  |  Printed ") + PdfKit.day(LocalDate.now()));
            doc.add(PdfKit.facts(new String[][]{{I18n.t("Warehouse"), c.warehouseName()}, {I18n.t("Category"), c.categoryName() == null ? I18n.t("All products") : c.categoryName()}, {I18n.t("Started"), dayOf(c.createdAt())},
                    {I18n.t("Status"), c.status().replace('_', ' ').toLowerCase()}, {I18n.t("Approved"), c.approvedAt() == null ? null : dayOf(c.approvedAt()) + (c.approvedBy() == null ? "" : " by " + c.approvedBy())}}));
            section(doc, c.lines().size() + " line" + (c.lines().size() == 1 ? "" : "s"));
            float[] widths = result ? new float[]{0.5f, 2f, 5f, 3f, 1.4f, 1.4f, 1.4f} : system ? new float[]{0.5f, 2f, 5f, 3f, 1.4f, 1.6f, 3.2f} : new float[]{0.5f, 2f, 5f, 3f, 1.8f, 3.6f};
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            if (result) {
                headerCells(t, new String[]{"#", "SKU", I18n.t("Item"), I18n.t("Location"), I18n.t("System"), I18n.t("Counted"), I18n.t("Difference")},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            } else if (system) {
                headerCells(t, new String[]{"#", "SKU", I18n.t("Item"), I18n.t("Location"), I18n.t("System"), I18n.t("Counted"), I18n.t("Notes")},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_LEFT});
            } else {
                headerCells(t, new String[]{"#", "SKU", I18n.t("Item"), I18n.t("Location"), I18n.t("Counted"), I18n.t("Notes")},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT});
            }
            t.setHeaderRows(1);
            int i = 1;
            for (StockCountLineDto l : c.lines()) {
                t.addCell(cell(String.valueOf(i++), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(nz(l.sku()), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.productName(), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(nz(l.locationName()), plain(8.5f), Element.ALIGN_LEFT, null));
                if (result) {
                    t.addCell(cell(num(l.systemQuantity()), plain(8.5f), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(l.countedQuantity() == null ? "-" : num(l.countedQuantity()), plain(8.5f), Element.ALIGN_RIGHT, null));
                    BigDecimal v = l.variance() == null ? BigDecimal.ZERO : l.variance();
                    t.addCell(cell(l.variance() == null ? "" : (v.signum() > 0 ? "+" + Qty.str(v) : Qty.str(v)), v.signum() == 0 ? plain(8.5f) : bold(8.5f), Element.ALIGN_RIGHT, null));
                } else if (system) {
                    t.addCell(cell(num(l.systemQuantity()), plain(8.5f), Element.ALIGN_RIGHT, null));
                    t.addCell(blank());
                    t.addCell(blank());
                } else {
                    t.addCell(blank());
                    t.addCell(blank());
                }
            }
            doc.add(t);
            note(doc, c.notes());
            PdfKit.signatures(doc, I18n.t("Counted by"), I18n.t("Checked by"), result ? I18n.t("Approved by") : I18n.t("Entered in the system by"));
        });
    }

    // ------------------------------------------------------------------ transfer note

    public byte[] transferNote(StockTransferDto t) {
        String ref = I18n.t("TR-") + String.format("%05d", t.id());
        return kit.build(false, I18n.t("Stock transfer note ") + ref, doc -> {
            kit.head(doc, I18n.t("Stock transfer note"), ref + I18n.t("  |  Printed ") + PdfKit.day(LocalDate.now()));
            String from = nz(t.fromWarehouseName()) + (t.fromLocationName() == null ? "" : " / " + t.fromLocationName()) + (t.fromBranchName() == null ? "" : " (" + t.fromBranchName() + ")");
            String to = nz(t.toWarehouseName()) + (t.toLocationName() == null ? "" : " / " + t.toLocationName()) + (t.toBranchName() == null ? "" : " (" + t.toBranchName() + ")");
            doc.add(PdfKit.facts(new String[][]{{I18n.t("From"), from}, {I18n.t("To"), to}, {I18n.t("Status"), t.status() == null ? "" : t.status().replace('_', ' ').toLowerCase()}, {I18n.t("Logged"), dayOf(t.createdAt())},
                    {I18n.t("Confirmed"), dayOf(t.confirmedAt())}, {I18n.t("Sent"), dayOf(t.dispatchedAt())}, {I18n.t("Received"), dayOf(t.receivedAt())},
                    {I18n.t("Between branches"), t.interBranch() ? I18n.t("Yes") : null}}));
            section(doc, I18n.t("Goods"));
            PdfPTable g = new PdfPTable(new float[]{5f, 1.6f, 2f, 2f});
            g.setWidthPercentage(100);
            headerCells(g, new String[]{I18n.t("Item"), I18n.t("Quantity sent"), I18n.t("Quantity received"), I18n.t("Counted on arrival")}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT});
            g.addCell(cell(t.productName(), plain(10), Element.ALIGN_LEFT, null));
            g.addCell(cell(num(t.quantity()), bold(10), Element.ALIGN_RIGHT, null));
            g.addCell(cell(t.receivedQuantity() == null ? "" : num(t.receivedQuantity()), plain(10), Element.ALIGN_RIGHT, null));
            g.addCell(blank());
            doc.add(g);
            note(doc, t.note());
            if (t.receiveNote() != null && !t.receiveNote().isBlank()) {
                section(doc, I18n.t("Remarks on arrival"));
                doc.add(new Paragraph(t.receiveNote().trim(), plain(9.5f)));
            }
            PdfKit.signatures(doc, I18n.t("Sent by"), I18n.t("Carrier"), I18n.t("Received by"));
        });
    }
}
