package com.posapp.installment;

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
import com.posapp.installment.dto.InstallmentDtos.InstallmentDto;
import com.posapp.installment.dto.InstallmentDtos.PaymentDto;
import com.posapp.installment.dto.InstallmentDtos.PlanDetailDto;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The two printed papers of an instalment plan: the agreement the customer signs, and the statement of what has been paid and what is still owed. A4, headed with the business's details. */
@Component
public class InstallmentPrintFiles {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static final String STANDARD_TERMS = "The customer acknowledges owing the amount financed shown above on the invoice named, and agrees to pay it in the instalments of the schedule, on or before each due date.\n"
            + "Interest, where shown, is already included in the instalment amounts. An instalment still unpaid after its grace days may carry the late fee shown above.\n"
            + "Each payment is applied to the oldest unpaid instalment first, interest and fees before principal, and a receipt is given for it.\n"
            + "The customer may pay the whole balance early. Whether any interest is waived for early payment is agreed with the business when the payment is made.\n"
            + "If instalments are not paid, the business may treat the agreement as in default, stop the schedule and ask for the whole unpaid balance. The invoice remains the debt in any case.\n"
            + "Anything agreed outside this document is not binding unless it is written down and signed by both sides.";

    private static final Color DARK = new Color(0x33, 0x33, 0x33);
    private static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);
    private static final Color RED = new Color(0xB0, 0x00, 0x20);

    private final StoreSettingsRepository storeSettingsRepository;

    public InstallmentPrintFiles(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    // ------------------------------------------------------------------ helpers

    private StoreSettings store() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    private String businessName() {
        StoreSettings s = store();
        return s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : "Business";
    }

    private static String money(BigDecimal v) {
        if (v == null) return "";
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static String amount(String cur, BigDecimal v) {
        return v == null ? "" : (cur == null || cur.isBlank() ? "" : cur + " ") + money(v);
    }

    private static String day(LocalDate d) {
        return d == null ? "" : d.format(DAY);
    }

    private static String shortDay(LocalDate d) {
        return d == null ? "" : SHORT.format(d);
    }

    private static String nice(Enum<?> e) {
        if (e == null) return "";
        String t = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
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

    private static class Footer extends PdfPageEventHelper {
        private final String left;

        Footer(String left) {
            this.left = left;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, muted(8)), document.left(), document.bottom() - 14, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), muted(8)), document.right(), document.bottom() - 14, 0);
        }
    }

    private Document open(ByteArrayOutputStream out, String footer) throws Exception {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 48);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        w.setPageEvent(new Footer(footer));
        doc.open();
        return doc;
    }

    private void head(Document doc, String title, String reference) throws Exception {
        StoreSettings s = store();
        doc.add(new Paragraph(businessName(), bold(15)));
        StringBuilder b = new StringBuilder();
        if (s != null) {
            for (String part : new String[]{s.getAddress(), s.getPhone(), s.getEmail(), s.getTaxId() == null || s.getTaxId().isBlank() ? null : "Tax ID " + s.getTaxId()}) {
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

    private static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        if (bg != null) c.setBackgroundColor(bg);
        c.setBorderColor(new Color(0xCC, 0xCC, 0xCC));
        return c;
    }

    private static void section(Document doc, String title) throws Exception {
        Paragraph p = new Paragraph(title, bold(11));
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    private static PdfPTable facts(String[][] pairs) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{1.6f, 4f});
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

    private static void headerCells(PdfPTable t, String[] heads, int[] aligns) {
        Font f = bold(8.5f);
        f.setColor(Color.WHITE);
        for (int i = 0; i < heads.length; i++) t.addCell(cell(heads[i], f, aligns[i], DARK));
    }

    private static void terms(Document doc, String custom) throws Exception {
        String text = custom == null || custom.isBlank() ? STANDARD_TERMS : custom;
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        t.setSpacingBefore(12);
        PdfPCell h = new PdfPCell(new Phrase("Terms", bold(11)));
        h.setBorder(Rectangle.NO_BORDER);
        h.setPaddingBottom(4);
        t.addCell(h);
        int n = 0;
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            n++;
            Paragraph p = new Paragraph(n + ".  " + line.trim(), plain(10));
            p.setIndentationLeft(14);
            p.setFirstLineIndent(-14);
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingBottom(4);
            c.addElement(p);
            t.addCell(c);
        }
        doc.add(t);
    }

    private static void signatures(Document doc, String... who) throws Exception {
        PdfPTable t = new PdfPTable(who.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(34);
        t.setKeepTogether(true);
        for (String w : who) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(DARK);
            c.setPaddingTop(4);
            c.setPhrase(new Phrase(w + "\nName:\nDate:", muted(9)));
            t.addCell(c);
        }
        doc.add(t);
    }

    private static String frequencyWord(InstallmentFrequency f) {
        return f == null ? "" : switch (f) {
            case WEEKLY -> "weekly";
            case BIWEEKLY -> "every two weeks";
            case MONTHLY -> "monthly";
            case QUARTERLY -> "every three months";
        };
    }

    private static String interestWords(PlanDetailDto d, String cur) {
        if (d.terms().interestMethod() == null || d.terms().interestMethod() == InterestMethod.NONE || d.terms().interestRate().signum() == 0) {
            return "None";
        }
        return d.terms().interestRate().stripTrailingZeros().toPlainString() + "% a year (" + (d.terms().interestMethod() == InterestMethod.FLAT ? "flat" : "reducing balance")
                + "), " + amount(cur, d.plan().totalInterest()) + " in total";
    }

    private static String lateFeeWords(PlanDetailDto d, String cur) {
        boolean fixed = d.terms().lateFeeFixed().signum() > 0;
        boolean pct = d.terms().lateFeePercent().signum() > 0;
        if (!fixed && !pct) return "None";
        String fee = (fixed ? amount(cur, d.terms().lateFeeFixed()) : "") + (fixed && pct ? " plus " : "") + (pct ? d.terms().lateFeePercent().stripTrailingZeros().toPlainString() + "% of the unpaid part" : "");
        return fee + ", once, if an instalment is still unpaid " + d.terms().graceDays() + " day(s) after its due date";
    }

    // ------------------------------------------------------------------ the papers

    /** The agreement: parties, terms, the schedule, the wording and two signature lines. */
    public byte[] agreement(PlanDetailDto d, String customerPhone, String customTerms, String cur) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = open(out, "Instalment agreement " + d.plan().planNumber());
            head(doc, "Instalment agreement", d.plan().planNumber() + "  |  invoice " + d.plan().invoiceNumber() + "  |  " + day(d.plan().createdAt() == null ? null : d.plan().createdAt().atZone(ZoneId.systemDefault()).toLocalDate()));
            section(doc, "Parties");
            doc.add(facts(new String[][]{{"Seller", businessName()}, {"Customer", d.plan().customerName()}, {"Customer phone", customerPhone}}));
            section(doc, "The agreement");
            doc.add(facts(new String[][]{
                    {"Invoice", d.plan().invoiceNumber() + " (total " + amount(cur, d.terms().invoiceTotal()) + ")"},
                    {"Deposit paid", d.terms().deposit().signum() > 0 ? amount(cur, d.terms().deposit()) : "None"},
                    {"Amount financed", amount(cur, d.plan().financedAmount())},
                    {"Interest", interestWords(d, cur)},
                    {"Total to pay in instalments", amount(cur, d.plan().totalPayable())},
                    {"Instalments", d.plan().numberOfInstallments() + ", " + frequencyWord(d.terms().frequency()) + ", the first on " + day(d.terms().firstDueDate())},
                    {"Late fee", lateFeeWords(d, cur)}}));
            section(doc, "Payment schedule");
            PdfPTable t = new PdfPTable(new float[]{0.7f, 1.6f, 1.5f, 1.4f, 1.4f, 1.6f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{"No.", "Due date", "Principal", "Interest", "Late fee", "Amount due"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            for (InstallmentDto i : d.installments()) {
                t.addCell(cell(String.valueOf(i.seq()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(shortDay(i.dueDate()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(i.principalDue()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(i.interestDue()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(i.lateFee().signum() > 0 ? money(i.lateFee()) : "", plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(i.totalDue()), bold(9), Element.ALIGN_RIGHT, null));
            }
            doc.add(t);
            if (d.terms().notes() != null && !d.terms().notes().isBlank()) {
                section(doc, "Notes");
                doc.add(new Paragraph(d.terms().notes().trim(), plain(10)));
            }
            terms(doc, customTerms);
            signatures(doc, businessName(), d.plan().customerName() == null ? "Customer" : "Customer: " + d.plan().customerName());
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the agreement PDF: " + e.getMessage(), e);
        }
    }

    /** The statement: where the plan stands today, the schedule with what is paid, and the payments received. */
    public byte[] statement(PlanDetailDto d, String cur) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = open(out, "Statement " + d.plan().planNumber());
            head(doc, "Instalment statement", d.plan().planNumber() + "  |  invoice " + d.plan().invoiceNumber() + "  |  as at " + day(LocalDate.now()));
            doc.add(facts(new String[][]{{"Customer", d.plan().customerName()}, {"Status", nice(d.plan().status())},
                    {"Amount financed", amount(cur, d.plan().financedAmount())}, {"Total payable", amount(cur, d.plan().totalPayable())},
                    {"Paid so far", amount(cur, d.plan().paid())}, {"Still to pay", amount(cur, d.plan().outstanding())},
                    {"Overdue now", d.plan().overdueAmount().signum() > 0 ? amount(cur, d.plan().overdueAmount()) : null},
                    {"Next payment", d.plan().nextDueDate() != null ? amount(cur, d.plan().nextDueAmount()) + " on " + day(d.plan().nextDueDate()) : null}}));
            section(doc, "Schedule");
            PdfPTable t = new PdfPTable(new float[]{0.6f, 1.5f, 1.4f, 1.3f, 1.3f, 1.4f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{"No.", "Due date", "Amount due", "Paid", "Balance", "Status"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT});
            for (InstallmentDto i : d.installments()) {
                boolean late = i.state() == InstallmentState.OVERDUE;
                Font f = plain(9);
                if (late) f.setColor(RED);
                t.addCell(cell(String.valueOf(i.seq()), f, Element.ALIGN_LEFT, null));
                t.addCell(cell(shortDay(i.dueDate()), f, Element.ALIGN_LEFT, null));
                t.addCell(cell(money(i.totalDue()), f, Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(i.paid()), f, Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(i.balance()), f, Element.ALIGN_RIGHT, null));
                t.addCell(cell(nice(i.state()) + (late ? " (" + i.daysOverdue() + " d)" : ""), f, Element.ALIGN_LEFT, null));
            }
            doc.add(t);
            section(doc, "Payments received");
            if (d.payments().isEmpty()) {
                doc.add(new Paragraph("No payments have been recorded through this plan yet.", muted(9.5f)));
            } else {
                PdfPTable pt = new PdfPTable(new float[]{1.5f, 1.8f, 1.2f, 1.5f});
                pt.setWidthPercentage(100);
                headerCells(pt, new String[]{"Date", "Receipt", "Method", "Amount"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                BigDecimal total = BigDecimal.ZERO;
                for (PaymentDto p : d.payments()) {
                    Font f = plain(9);
                    if (p.reversed()) f.setColor(new Color(0x88, 0x88, 0x88));
                    else total = total.add(p.amount());
                    pt.addCell(cell(shortDay(p.paidAt().atZone(ZoneId.systemDefault()).toLocalDate()), f, Element.ALIGN_LEFT, null));
                    pt.addCell(cell(p.receiptNumber() + (p.reversed() ? " (reversed)" : ""), f, Element.ALIGN_LEFT, null));
                    pt.addCell(cell(p.method(), f, Element.ALIGN_LEFT, null));
                    pt.addCell(cell(money(p.amount()), f, Element.ALIGN_RIGHT, null));
                }
                pt.addCell(cell("Total received", bold(9), Element.ALIGN_LEFT, SOFT));
                pt.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
                pt.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
                pt.addCell(cell(money(total), bold(9), Element.ALIGN_RIGHT, SOFT));
                doc.add(pt);
            }
            Paragraph note = new Paragraph("If you have paid since the date above, thank you — please disregard any overdue amount shown.", muted(8.5f));
            note.setSpacingBefore(14);
            doc.add(note);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the statement PDF: " + e.getMessage(), e);
        }
    }
}
