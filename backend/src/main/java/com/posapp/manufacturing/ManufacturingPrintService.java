package com.posapp.manufacturing;
import com.posapp.common.Qty;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** MF-6: the job card a production team works from: what to make, the components to issue, the steps with their minutes, and empty boxes for what actually happened. */
@Component
public class ManufacturingPrintService {

    private static final Color GREY = new Color(0xCC, 0xCC, 0xCC);
    private static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);

    private final ManufacturingOrderRepository orderRepository;
    private final StoreSettingsRepository storeSettingsRepository;

    public ManufacturingPrintService(ManufacturingOrderRepository orderRepository, StoreSettingsRepository storeSettingsRepository) {
        this.orderRepository = orderRepository;
        this.storeSettingsRepository = storeSettingsRepository;
    }

    public record Printed(String fileName, byte[] content) {}

    private static Font bold(float size) { return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size); }
    private static Font plain(float size) { return FontFactory.getFont(FontFactory.HELVETICA, size); }
    private static Font muted(float size) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size);
        f.setColor(new Color(0x66, 0x66, 0x66));
        return f;
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static PdfPCell cell(String text, Font font, int align, Color bg, float minHeight) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        c.setBorderColor(GREY);
        if (bg != null) c.setBackgroundColor(bg);
        if (minHeight > 0) c.setMinimumHeight(minHeight);
        return c;
    }

    private static class Footer extends PdfPageEventHelper {
        private final String left;
        Footer(String left) { this.left = left; }
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, muted(8)), document.left(), document.bottom() - 14, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), muted(8)), document.right(), document.bottom() - 14, 0);
        }
    }

    private static void heading(Document doc, String text) throws Exception {
        Paragraph p = new Paragraph(text, bold(11));
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    @Transactional(readOnly = true)
    public Printed jobCard(Long id) {
        ManufacturingOrder mo = orderRepository.findById(id).orElseThrow(() -> com.posapp.common.ApiException.notFound("Manufacturing order not found: " + id));
        StoreSettings s = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String business = s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : "Production";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 48);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new Footer("Job card " + mo.getOrderNumber()));
            doc.open();

            doc.add(new Paragraph(business, bold(15)));
            Paragraph title = new Paragraph("Job card " + mo.getOrderNumber(), bold(20));
            title.setSpacingBefore(10);
            doc.add(title);
            doc.add(new Paragraph(mo.getStatus().name().replace('_', ' ').toLowerCase(Locale.ROOT), muted(10)));

            PdfPTable info = new PdfPTable(new float[]{1.2f, 3f, 1.2f, 2f});
            info.setWidthPercentage(100);
            info.setSpacingBefore(10);
            String[][] rows = {
                    {"Product", mo.getProduct().getName(), "Quantity to make", Qty.str(mo.getQuantityPlanned().subtract(mo.getQuantityProduced()))},
                    {"Recipe", mo.getBom() != null ? mo.getBom().getCode() + (mo.getBom().getName() != null ? " - " + mo.getBom().getName() : "") + " (v" + mo.getBom().getVersion() + ")" : "", "Already made", Qty.str(mo.getQuantityProduced()) + " of " + mo.getQuantityPlanned()},
                    {"Scheduled", mo.getScheduledDate() != null ? mo.getScheduledDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) : "Not scheduled", "Location", mo.getLocationName() != null ? mo.getLocationName() : "Main stock"},
            };
            for (String[] r : rows) {
                info.addCell(cell(r[0], bold(9), Element.ALIGN_LEFT, SOFT, 0));
                info.addCell(cell(r[1], plain(10), Element.ALIGN_LEFT, null, 0));
                info.addCell(cell(r[2], bold(9), Element.ALIGN_LEFT, SOFT, 0));
                info.addCell(cell(r[3], plain(10), Element.ALIGN_LEFT, null, 0));
            }
            doc.add(info);
            if (mo.getNote() != null && !mo.getNote().isBlank()) {
                Paragraph n = new Paragraph("Note: " + mo.getNote(), plain(10));
                n.setSpacingBefore(6);
                doc.add(n);
            }

            BigDecimal remaining = Qty.max(BigDecimal.ZERO, mo.getQuantityPlanned().subtract(mo.getQuantityProduced()));
            heading(doc, "Components to issue (for " + Qty.str(remaining) + ")");
            PdfPTable comp = new PdfPTable(new float[]{4f, 1.2f, 1.4f, 1.8f});
            comp.setWidthPercentage(100);
            for (String h : new String[]{"Component", "Per unit", "Needed", "Issued"}) comp.addCell(cell(h, bold(9), Element.ALIGN_LEFT, SOFT, 0));
            if (mo.getBom() != null) {
                for (BomLine l : mo.getBom().getLines()) {
                    comp.addCell(cell(l.getComponent().getName(), plain(10), Element.ALIGN_LEFT, null, 20));
                    comp.addCell(cell(Qty.str(l.getQuantityPerUnit()), plain(10), Element.ALIGN_RIGHT, null, 20));
                    comp.addCell(cell(Qty.str(l.getQuantityPerUnit().multiply(remaining)), plain(10), Element.ALIGN_RIGHT, null, 20));
                    comp.addCell(cell("", plain(10), Element.ALIGN_LEFT, null, 20));
                }
            }
            doc.add(comp);

            // Steps: the copy on the order once confirmed, otherwise what the recipe's routing says today.
            heading(doc, "Steps");
            PdfPTable ops = new PdfPTable(new float[]{0.7f, 2.6f, 2.2f, 1.3f, 1.4f, 1.4f});
            ops.setWidthPercentage(100);
            for (String h : new String[]{"Step", "Operation", "Workstation", "Min per unit", "Planned min", "Actual min"}) ops.addCell(cell(h, bold(9), Element.ALIGN_LEFT, SOFT, 0));
            boolean any = false;
            if (!mo.getOperations().isEmpty()) {
                for (ManufacturingOrderOperation o : mo.getOperations()) {
                    any = true;
                    BigDecimal planned = o.getMinutesPerUnit().multiply(remaining);
                    addStep(ops, o.getSequence(), o.getName(), o.getWorkstationName(), o.getMinutesPerUnit(), planned);
                }
            } else if (mo.getBom() != null && mo.getBom().getRouting() != null) {
                List<RoutingOperation> list = mo.getBom().getRouting().getOperations().stream().sorted(Comparator.comparingInt(RoutingOperation::getSequence)).toList();
                for (RoutingOperation o : list) {
                    any = true;
                    BigDecimal per = o.getEstimatedMinutesPerUnit() != null ? o.getEstimatedMinutesPerUnit() : BigDecimal.ZERO;
                    addStep(ops, o.getSequence(), o.getName(), o.getWorkstation() != null ? o.getWorkstation().getName() : null, per, per.multiply(remaining));
                }
            }
            if (any) {
                doc.add(ops);
            } else {
                doc.add(new Paragraph("This recipe has no routing steps.", muted(10)));
            }

            if (mo.getBom() != null && !mo.getBom().getByProducts().isEmpty()) {
                heading(doc, "By-products expected");
                PdfPTable bp = new PdfPTable(new float[]{4f, 1.5f, 1.5f});
                bp.setWidthPercentage(100);
                for (String h : new String[]{"By-product", "Expected", "Received"}) bp.addCell(cell(h, bold(9), Element.ALIGN_LEFT, SOFT, 0));
                for (BomByProduct b : mo.getBom().getByProducts()) {
                    bp.addCell(cell(b.getProduct().getName(), plain(10), Element.ALIGN_LEFT, null, 20));
                    bp.addCell(cell(Qty.str(b.getQuantityPerUnit().multiply(remaining)), plain(10), Element.ALIGN_RIGHT, null, 20));
                    bp.addCell(cell("", plain(10), Element.ALIGN_LEFT, null, 20));
                }
                doc.add(bp);
            }

            heading(doc, "Result");
            PdfPTable res = new PdfPTable(new float[]{1.6f, 1f, 1.6f, 3f});
            res.setWidthPercentage(100);
            res.addCell(cell("Good units made", bold(9), Element.ALIGN_LEFT, SOFT, 26));
            res.addCell(cell("", plain(10), Element.ALIGN_LEFT, null, 26));
            res.addCell(cell("Scrapped units", bold(9), Element.ALIGN_LEFT, SOFT, 26));
            res.addCell(cell("", plain(10), Element.ALIGN_LEFT, null, 26));
            res.addCell(cell("Scrap reason", bold(9), Element.ALIGN_LEFT, SOFT, 26));
            PdfPCell reason = cell("", plain(10), Element.ALIGN_LEFT, null, 26);
            reason.setColspan(3);
            res.addCell(reason);
            doc.add(res);

            PdfPTable sign = new PdfPTable(3);
            sign.setWidthPercentage(100);
            sign.setSpacingBefore(26);
            for (String l : new String[]{"Made by", "Checked by", "Date"}) {
                PdfPCell c = new PdfPCell(new Phrase(l, muted(9)));
                c.setBorder(com.lowagie.text.Rectangle.TOP);
                c.setBorderColor(GREY);
                c.setPaddingTop(4);
                sign.addCell(c);
            }
            doc.add(sign);
            doc.close();
            return new Printed("job-card-" + mo.getOrderNumber() + ".pdf", out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the job card", e);
        }
    }

    private static void addStep(PdfPTable t, int seq, String name, String workstation, BigDecimal perUnit, BigDecimal planned) {
        t.addCell(cell(String.valueOf(seq), plain(10), Element.ALIGN_LEFT, null, 22));
        t.addCell(cell(name, plain(10), Element.ALIGN_LEFT, null, 22));
        t.addCell(cell(workstation != null ? workstation : "Hand work", plain(10), Element.ALIGN_LEFT, null, 22));
        t.addCell(cell(num(perUnit), plain(10), Element.ALIGN_RIGHT, null, 22));
        t.addCell(cell(num(planned), plain(10), Element.ALIGN_RIGHT, null, 22));
        t.addCell(cell("", plain(10), Element.ALIGN_LEFT, null, 22));
    }
}
