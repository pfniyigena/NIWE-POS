package com.posapp.propertymgmt;

import com.posapp.i18n.I18n;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.propertymgmt.dto.PropertyMgmtFeeInvoiceDtos.FeeInvoiceDto;
import com.posapp.propertymgmt.dto.PropertyMgmtFeeInvoiceDtos.FeeInvoiceLineDto;
import com.posapp.settings.StoreSettings;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** The fee invoice as an A4 page: who sells, who is billed, the fee and VAT for each property, the totals, and how it is settled. The same bytes are downloaded and e-mailed. */
@Component
public class PmFeeInvoicePdf {

    private static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", I18n.symbols());
        return f.format(v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "" : s.trim();
    }

    private static PdfPCell cell(String text, Font font, int align, boolean border) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorder(border ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
        return c;
    }

    public byte[] build(FeeInvoiceDto d, StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14), bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10),
                    small = FontFactory.getFont(FontFactory.HELVETICA, 9), title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20), red = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, java.awt.Color.RED);
            String currency = store != null && nz(store.getCurrencyCode()).length() > 0 ? store.getCurrencyCode() : "";

            if (!nz(d.sellerName()).isEmpty()) doc.add(new Paragraph(d.sellerName(), h1));
            StringBuilder seller = new StringBuilder();
            for (String part : new String[]{nz(d.sellerAddress()), nz(d.sellerPhone()), nz(d.sellerEmail())}) {
                if (!part.isEmpty()) { if (seller.length() > 0) seller.append(" - "); seller.append(part); }
            }
            if (seller.length() > 0) doc.add(new Paragraph(seller.toString(), small));
            if (!nz(d.sellerTaxId()).isEmpty()) doc.add(new Paragraph(I18n.t("Tax ID: ") + d.sellerTaxId(), small));
            Paragraph sp = new Paragraph(" ", small);
            sp.setSpacingAfter(6);
            doc.add(sp);

            Paragraph t = new Paragraph(d.taxInvoice() ? I18n.t("TAX INVOICE") : "INVOICE", title);
            t.setSpacingAfter(2);
            doc.add(t);
            Paragraph num = new Paragraph(d.invoice().number(), normal);
            num.setSpacingAfter(4);
            doc.add(num);
            if (d.invoice().status() == PmFeeInvoiceStatus.VOID) {
                Paragraph v = new Paragraph(I18n.t("VOID - cancelled") + (nz(d.voidReason()).isEmpty() ? "" : ": " + d.voidReason()), red);
                v.setSpacingAfter(6);
                doc.add(v);
            }

            PdfPTable info = new PdfPTable(new float[]{1.2f, 3f, 1.2f, 2.4f});
            info.setWidthPercentage(100);
            info.setSpacingAfter(10);
            info.addCell(cell(I18n.t("Bill to"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(d.invoice().ownerName() + (nz(d.ownerAddress()).isEmpty() ? "" : "\n" + d.ownerAddress()) + (nz(d.invoice().ownerTaxNumber()).isEmpty() ? "" : I18n.t("\nTax ID: ") + d.invoice().ownerTaxNumber()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Invoice date"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(d.invoice().issueDate() + I18n.t("\nFor ") + d.invoice().periodFrom() + " to " + d.invoice().periodTo() + I18n.t("\nStatement ") + nz(d.invoice().statementCode()), normal, Element.ALIGN_LEFT, false));
            doc.add(info);

            PdfPTable table = new PdfPTable(new float[]{3.6f, 1.5f, 0.8f, 1.4f, 1.2f, 1.5f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{I18n.t("Description"), I18n.t("Rent collected"), I18n.t("Rate"), I18n.t("Fee"), "VAT", I18n.t("Total")}) {
                PdfPCell c = cell(h, bold, h.equals("Description") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, true);
                c.setBackgroundColor(new java.awt.Color(235, 235, 235));
                table.addCell(c);
            }
            for (FeeInvoiceLineDto l : d.lines()) {
                table.addCell(cell(l.description(), normal, Element.ALIGN_LEFT, true));
                table.addCell(cell(l.baseAmount() == null ? "" : money(l.baseAmount()), normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(l.feePercent() == null ? "" : l.feePercent().stripTrailingZeros().toPlainString() + "%", normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(money(l.netAmount()), normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(money(l.vatAmount()), normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(money(l.total()), normal, Element.ALIGN_RIGHT, true));
            }
            doc.add(table);

            String suffix = currency.isEmpty() ? "" : " " + currency;
            Paragraph sub = new Paragraph(I18n.t("Fee (before VAT): ") + money(d.invoice().netAmount()) + suffix, bold);
            sub.setAlignment(Element.ALIGN_RIGHT);
            sub.setSpacingBefore(10);
            doc.add(sub);
            if (d.taxInvoice()) {
                Paragraph vat = new Paragraph("VAT" + (d.invoice().vatPercent() == null ? "" : " " + d.invoice().vatPercent().stripTrailingZeros().toPlainString() + "%") + ": " + money(d.invoice().vatAmount()) + suffix, bold);
                vat.setAlignment(Element.ALIGN_RIGHT);
                doc.add(vat);
            }
            Paragraph total = new Paragraph(I18n.t("Total: ") + money(d.invoice().totalAmount()) + suffix, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
            total.setAlignment(Element.ALIGN_RIGHT);
            total.setSpacingAfter(12);
            doc.add(total);

            Paragraph how = new Paragraph(I18n.t("Settled by deduction from the rent collected for you, as shown on owner statement ") + nz(d.invoice().statementCode()) + I18n.t(". Nothing further is payable on this invoice."), small);
            how.setSpacingAfter(6);
            doc.add(how);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the fee invoice PDF"), e);
        }
    }
}
