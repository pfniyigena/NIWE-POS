package com.posapp.common;

import com.posapp.i18n.I18n;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Small toolbox for the A4 papers of the core module (customer statements, delivery notes, goods-received notes, stock sheets):
 * the page with the business name and address from the store settings on top, tables, key/value blocks, signature lines.
 */
@Component
public class PdfKit {


    public static final Color DARK = new Color(0x33, 0x33, 0x33);
    public static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);

    private final StoreSettingsRepository storeSettingsRepository;

    public PdfKit(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    // ---- store details

    private StoreSettings store() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    public String businessName() {
        StoreSettings s = store();
        return s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : I18n.t("Our business");
    }

    public String currency() {
        StoreSettings s = store();
        return s != null && s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank() ? s.getCurrencyCode().trim() : "";
    }

    /** Phone and e-mail on one line; empty when neither is set. */
    public String contactLine() {
        StoreSettings s = store();
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getPhone(), s.getEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim());
        return b.toString();
    }

    // ---- formatting

    public static String money(BigDecimal v) {
        if (v == null) return "";
        return new DecimalFormat("#,##0.00", I18n.symbols()).format(v);
    }

    public static String amount(String cur, BigDecimal v) {
        return v == null ? "" : (cur == null || cur.isBlank() ? "" : cur + " ") + money(v);
    }

    public static String day(LocalDate d) {
        return d == null ? "" : d.format(I18n.longDay());
    }

    public static String shortDay(LocalDate d) {
        return d == null ? "" : I18n.shortDay().format(d);
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---- fonts and cells

    public static Font bold(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size);
    }

    public static Font plain(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size);
    }

    public static Font muted(float size) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size);
        f.setColor(new Color(0x66, 0x66, 0x66));
        return f;
    }

    public static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        if (bg != null) c.setBackgroundColor(bg);
        c.setBorderColor(new Color(0xCC, 0xCC, 0xCC));
        return c;
    }

    public static void section(Document doc, String title) throws Exception {
        Paragraph p = new Paragraph(title, bold(11));
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    /** Label / value pairs without borders; a pair with an empty value is left out. */
    public static PdfPTable facts(String[][] pairs) {
        PdfPTable t = new PdfPTable(new float[]{1.5f, 4f});
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
        t.setSpacingAfter(4);
        return t;
    }

    public static void headerCells(PdfPTable t, String[] heads, int[] aligns) {
        Font f = bold(8.5f);
        f.setColor(Color.WHITE);
        for (int i = 0; i < heads.length; i++) t.addCell(cell(heads[i], f, aligns[i], DARK));
    }

    public static void signatures(Document doc, String... who) throws Exception {
        PdfPTable t = new PdfPTable(who.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(34);
        t.setKeepTogether(true);
        for (String w : who) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(DARK);
            c.setPaddingTop(4);
            c.setPhrase(new Phrase(w + I18n.t("\nName:\nDate:"), muted(9)));
            t.addCell(c);
        }
        doc.add(t);
    }

    // ---- the page

    /** Page numbers at the foot of every page. */
    private static class Footer extends PdfPageEventHelper {
        private final String left;

        Footer(String left) {
            this.left = left;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, muted(8)), document.left(), document.bottom() - 14, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(I18n.t("Page ") + writer.getPageNumber(), muted(8)), document.right(), document.bottom() - 14, 0);
        }
    }

    /** Business name and address, then the paper's title and a reference line. */
    public void head(Document doc, String title, String reference) throws Exception {
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
        t.setSpacingBefore(18);
        t.setSpacingAfter(2);
        doc.add(t);
        if (reference != null && !reference.isBlank()) {
            Paragraph r = new Paragraph(reference, muted(9.5f));
            r.setSpacingAfter(10);
            doc.add(r);
        }
    }

    @FunctionalInterface
    public interface Writer {
        void write(Document doc) throws Exception;
    }

    /** An A4 page (portrait, or landscape for wide tables) with page numbers; {@code w} fills it. */
    public byte[] build(boolean landscape, String footer, Writer w) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 40, 40, 40, 48);
            PdfWriter pw = PdfWriter.getInstance(doc, out);
            pw.setPageEvent(new Footer(footer));
            doc.open();
            w.write(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the document"), e);
        }
    }
}
