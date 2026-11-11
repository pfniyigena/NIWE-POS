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
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.AccountLine;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.Receipt;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.ReceiptLine;
import com.posapp.propertymgmt.dto.PropertyMgmtAccountDtos.TenantAccount;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** The tenant's papers as PDFs: the account statement and the rent receipt. Headed with the business's name and address from the store settings. */
@Component
public class PmTenantDocuments {

    private static final Color DARK = new Color(0x33, 0x33, 0x33);
    private static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);

    private final StoreSettingsRepository storeSettingsRepository;

    public PmTenantDocuments(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    public String businessName() {
        StoreSettings s = store();
        return s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : I18n.t("Your property manager");
    }

    private StoreSettings store() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    private static String money(BigDecimal v) {
        if (v == null) return "";
        return new DecimalFormat("#,##0.00", I18n.symbols()).format(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Font bold(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size);
    }

    private static Font plain(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size);
    }

    private static Font muted(float size) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size);
        f.setColor(new Color(0x66, 0x66, 0x66));
        return f;
    }

    private void head(Document doc, String title) throws Exception {
        StoreSettings s = store();
        doc.add(new Paragraph(businessName(), bold(15)));
        StringBuilder b = new StringBuilder();
        if (s != null) {
            for (String part : new String[]{s.getAddress(), s.getPhone(), s.getEmail(), s.getTaxId() == null || s.getTaxId().isBlank() ? null : I18n.t("Tax ID ") + s.getTaxId()}) {
                if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim().replace("\n", ", "));
            }
        }
        if (b.length() > 0) doc.add(new Paragraph(b.toString(), muted(8.5f)));
        Paragraph t = new Paragraph(title, bold(18));
        t.setSpacingBefore(16);
        t.setSpacingAfter(8);
        doc.add(t);
    }

    private static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        if (bg != null) c.setBackgroundColor(bg);
        c.setBorderColor(new Color(0xCC, 0xCC, 0xCC));
        return c;
    }

    private static PdfPTable facts(String[][] pairs) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{1.4f, 4f});
        t.setWidthPercentage(100);
        for (String[] p : pairs) {
            if (p[1] == null || p[1].isBlank()) continue;
            PdfPCell k = cell(p[0], muted(9), Element.ALIGN_LEFT, null);
            PdfPCell v = cell(p[1], plain(10), Element.ALIGN_LEFT, null);
            k.setBorder(Rectangle.NO_BORDER);
            v.setBorder(Rectangle.NO_BORDER);
            t.addCell(k);
            t.addCell(v);
        }
        t.setSpacingAfter(10);
        return t;
    }

    private static void headerCells(PdfPTable t, String[] heads, int[] aligns) {
        Font f = bold(8.5f);
        f.setColor(Color.WHITE);
        for (int i = 0; i < heads.length; i++) t.addCell(cell(heads[i], f, aligns[i], DARK));
    }

    /** The account statement of one tenant. */
    public byte[] statement(TenantAccount a, String cur) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            head(doc, I18n.t("Tenant statement"));
            doc.add(facts(new String[][]{{I18n.t("Tenant"), a.tenantName() + " (" + a.tenantCode() + ")"}, {I18n.t("Period"), a.from().format(I18n.shortDay()) + " to " + a.to().format(I18n.shortDay())},
                    {I18n.t("Currency"), cur}}));
            PdfPTable t = new PdfPTable(new float[]{1.6f, 3.1f, 2.6f, 2.0f, 2.0f, 2.1f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{I18n.t("Date"), I18n.t("Description"), I18n.t("Reference"), I18n.t("Charged"), I18n.t("Paid"), I18n.t("Balance")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(I18n.t("Balance brought forward"), bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(money(a.opening()), bold(9), Element.ALIGN_RIGHT, SOFT));
            for (AccountLine l : a.lines()) {
                t.addCell(cell(l.date().format(I18n.shortDay()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.description(), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(nz(l.reference()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(l.charge()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.payment()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.balance()), plain(9), Element.ALIGN_RIGHT, null));
            }
            t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(I18n.t("Totals for the period"), bold(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(money(a.charged()), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(money(a.paid()), bold(9), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, SOFT));
            doc.add(t);
            Paragraph close = new Paragraph((a.closing().signum() > 0 ? I18n.t("Balance owed at ") : I18n.t("Balance at ")) + a.to().format(I18n.shortDay()) + ":  " + money(a.closing()), bold(12));
            close.setSpacingBefore(12);
            close.setAlignment(Element.ALIGN_RIGHT);
            doc.add(close);
            if (a.overdue().signum() > 0) {
                Paragraph od = new Paragraph(I18n.t("Of which overdue today: ") + money(a.overdue()), plain(10));
                od.setAlignment(Element.ALIGN_RIGHT);
                doc.add(od);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the tenant statement"), e);
        }
    }

    /** The receipt for one rent payment. */
    public byte[] receipt(Receipt r, String cur) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            head(doc, I18n.t("Rent receipt"));
            var p = r.payment();
            doc.add(facts(new String[][]{{I18n.t("Receipt no."), p.receiptNumber()}, {I18n.t("Date"), p.paidDate() == null ? "" : p.paidDate().format(I18n.shortDay())}, {I18n.t("Received from"), p.tenantName()},
                    {I18n.t("For"), (p.leaseCode() == null ? "" : I18n.t("Lease ") + p.leaseCode() + " · ") + nz(p.unit())}, {I18n.t("Paid by"), p.method()}, {I18n.t("Reference"), p.reference()}, {I18n.t("Received by"), r.recordedBy()}}));
            PdfPTable t = new PdfPTable(new float[]{2.4f, 3.6f, 1.8f, 1.8f, 1.8f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{I18n.t("Invoice"), I18n.t("Rent for"), I18n.t("Invoice total"), I18n.t("Paid now"), I18n.t("Still owed")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            for (ReceiptLine l : r.lines()) {
                t.addCell(cell(nz(l.invoiceNumber()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.period(), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(l.invoiceTotal()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.paidNow()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.balanceAfter()), plain(9), Element.ALIGN_RIGHT, null));
            }
            doc.add(t);
            Paragraph total = new Paragraph(I18n.t("Total received:  ") + cur + " " + money(p.amount()), bold(13));
            total.setSpacingBefore(14);
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);
            Paragraph thanks = new Paragraph(I18n.t("Thank you."), plain(10));
            thanks.setSpacingBefore(18);
            doc.add(thanks);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the rent receipt"), e);
        }
    }

    /** The business's phone and e-mail on one line, for the foot of a message; empty when neither is set. */
    public String contactLine() {
        StoreSettings s = store();
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getPhone(), s.getEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : " | ").append(part.trim());
        return b.toString();
    }

    public String currency() {
        StoreSettings s = store();
        return s != null && s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank() ? s.getCurrencyCode().trim() : "";
    }
}
