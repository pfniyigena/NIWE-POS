package com.posapp.realestate;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
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
import com.posapp.installment.dto.InstallmentDtos.PlanDetailDto;
import com.posapp.realestate.dto.RealEstateDealDtos.DealDto;
import com.posapp.realestate.dto.RealEstateDealDtos.MilestoneDto;
import com.posapp.realestate.dto.RealEstateDtos.PropertyDto;
import com.posapp.realestate.dto.RealEstateCommissionDtos;
import com.posapp.realestate.dto.RealEstatePrintDtos.PaymentRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.ProjectDto;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitSaleDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The printed papers of Real Estate as A4 PDFs, headed with the business's name and address from the store settings: the offer letter, the reservation letter,
 * the sale agreement with its payment schedule, the project price list and the property flyer. The wording of the three letters is the agency's own when it
 * has set it in the Real Estate settings, and a plain standard text otherwise.
 */
@Component
public class RePrintFiles {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public static final String STANDARD_OFFER = "This offer is made subject to contract and to the seller's acceptance.\n"
            + "The offer stays open until the date shown above. After that date it lapses unless it is extended in writing.\n"
            + "The property is offered as seen. Nothing in this letter transfers any right in the property; that happens only through a written sale agreement signed by both sides.";
    public static final String STANDARD_RESERVATION = "The property is held for the buyer until the reservation ends on the date shown above. During that time it will not be offered to anyone else.\n"
            + "The deposit is part of the price and is credited against it.\n"
            + "If the sale agreement is not signed by the end date, the reservation lapses unless it is extended in writing.";
    public static final String STANDARD_AGREEMENT = "The seller sells and the buyer buys the property described above for the price shown, payable as set out in the payment schedule.\n"
            + "Ownership passes to the buyer when the full price has been paid.\n"
            + "Payments are made to the seller, who gives a receipt for each one. A payment received late may carry the late charge shown in the schedule.\n"
            + "Anything agreed outside this document is not binding unless it is written down and signed by both sides.";

    private static final Color DARK = new Color(0x33, 0x33, 0x33);
    private static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);
    private static final Color ACCENT = new Color(0x15, 0x80, 0x3D);

    private final StoreSettingsRepository storeSettingsRepository;

    public RePrintFiles(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    // ------------------------------------------------------------------ small helpers

    private StoreSettings store() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    public String businessName() {
        StoreSettings s = store();
        return s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : "Real estate agency";
    }

    public String currency() {
        StoreSettings s = store();
        return s != null && s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank() ? s.getCurrencyCode().trim() : "";
    }

    /** The business's phone and e-mail on one line (from the store settings); empty when neither is set. */
    public String contactLine() {
        StoreSettings s = store();
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getPhone(), s.getEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim());
        return b.toString();
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
        return d == null ? "" : DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(d);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nice(Enum<?> e) {
        if (e == null) return "";
        String t = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String nice(String name) {
        if (name == null || name.isBlank()) return "";
        String t = name.toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private static void headerCells(PdfPTable t, String[] heads, int[] aligns) {
        Font f = bold(8.5f);
        f.setColor(Color.WHITE);
        for (int i = 0; i < heads.length; i++) t.addCell(cell(heads[i], f, aligns[i], DARK));
    }

    /** The "Terms" heading and the agency's wording (or the standard one), one numbered paragraph per line, kept together on one page. */
    private static void terms(Document doc, String custom, String standard) throws Exception {
        String text = custom == null || custom.isBlank() ? standard : custom;
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

    /** Signature lines side by side. */
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

    private static String propertyLine(PropertyDto p) {
        StringBuilder b = new StringBuilder(p.title());
        if (p.address() != null && !p.address().isBlank()) b.append(", ").append(p.address().trim());
        else if (p.area() != null && !p.area().isBlank()) b.append(", ").append(p.area().trim());
        return b.toString();
    }

    // ------------------------------------------------------------------ deals (the agency sells another owner's property)

    private byte[] build(Writer w, String footer) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = open(out, footer);
            w.write(doc);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the document", e);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(Document doc) throws Exception;
    }

    /** The buyer's offer to purchase a listed property. */
    public byte[] offer(DealDto d, PropertyDto p, String customTerms, String cur) {
        return build(doc -> {
            head(doc, "Offer to purchase", "Reference " + d.code() + "  |  " + day(d.offerDate() != null ? d.offerDate() : LocalDate.now()));
            section(doc, "The buyer");
            doc.add(facts(new String[][]{{"Name", d.buyerName()}, {"Phone", d.buyerPhone()}, {"E-mail", d.buyerEmail()}}));
            section(doc, "The property");
            doc.add(facts(new String[][]{{"Property", p.code() + "  " + p.title()}, {"Location", join(p.address(), p.area())}, {"Title reference", p.titleReference()},
                    {"Asking price", d.listPrice() == null ? null : amount(cur, d.listPrice())}}));
            section(doc, "The offer");
            doc.add(facts(new String[][]{{"Amount offered", amount(cur, d.offerAmount())}, {"Offer valid until", day(d.offerValidUntil())}, {"Agent", d.agentName()}}));
            terms(doc, customTerms, STANDARD_OFFER);
            signatures(doc, "The buyer", businessName() + " (for the seller)");
        }, "Offer " + d.code());
    }

    /** The reservation of a property for a buyer who has paid a deposit. */
    public byte[] dealReservation(DealDto d, PropertyDto p, String customTerms, String cur) {
        return build(doc -> {
            head(doc, "Reservation letter", "Reference " + d.code() + "  |  " + day(LocalDate.now()));
            section(doc, "The buyer");
            doc.add(facts(new String[][]{{"Name", d.buyerName()}, {"Phone", d.buyerPhone()}, {"E-mail", d.buyerEmail()}}));
            section(doc, "The property");
            doc.add(facts(new String[][]{{"Property", p.code() + "  " + p.title()}, {"Location", join(p.address(), p.area())}, {"Title reference", p.titleReference()}}));
            section(doc, "The reservation");
            BigDecimal price = d.agreedPrice() != null ? d.agreedPrice() : d.offerAmount();
            doc.add(facts(new String[][]{{"Price", amount(cur, price)}, {"Deposit", d.reservationDeposit() == null ? null : amount(cur, d.reservationDeposit())},
                    {"Reserved until", day(d.reservationExpiry())}, {"Agent", d.agentName()}}));
            terms(doc, customTerms, STANDARD_RESERVATION);
            signatures(doc, "The buyer", businessName());
        }, "Reservation " + d.code());
    }

    /** The sale agreement of a deal with its payment milestones. */
    public byte[] dealAgreement(DealDto d, PropertyDto p, String seller, String customTerms, String cur) {
        return build(doc -> {
            head(doc, "Sale agreement", "Reference " + d.code() + "  |  " + day(d.agreementDate() != null ? d.agreementDate() : LocalDate.now()));
            section(doc, "The parties");
            doc.add(facts(new String[][]{{"Seller", seller}, {"Buyer", d.buyerName()}, {"Buyer's phone", d.buyerPhone()}, {"Buyer's e-mail", d.buyerEmail()}, {"Agent", businessName() + (d.agentName() == null ? "" : " (" + d.agentName() + ")")}}));
            section(doc, "The property");
            doc.add(facts(new String[][]{{"Property", p.code() + "  " + p.title()}, {"Location", join(p.address(), p.area())}, {"Title reference", p.titleReference()},
                    {"Size", p.sizeSqm() == null ? null : money(p.sizeSqm()) + " m²"}, {"Plot", p.plotSqm() == null ? null : money(p.plotSqm()) + " m²"}}));
            section(doc, "The price");
            BigDecimal price = d.agreedPrice() != null ? d.agreedPrice() : d.offerAmount();
            doc.add(facts(new String[][]{{"Agreed price", amount(cur, price)}, {"Expected completion", day(d.expectedCompletion())}}));
            List<MilestoneDto> ms = d.milestones() == null ? List.of() : d.milestones();
            if (!ms.isEmpty()) {
                section(doc, "Payment schedule");
                PdfPTable t = new PdfPTable(new float[]{0.7f, 4f, 2f, 2.4f});
                t.setWidthPercentage(100);
                headerCells(t, new String[]{"No.", "Payment", "Due", "Amount"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                BigDecimal total = BigDecimal.ZERO;
                int n = 0;
                for (MilestoneDto m : ms) {
                    n++;
                    total = total.add(m.amount() == null ? BigDecimal.ZERO : m.amount());
                    t.addCell(cell(String.valueOf(n), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(m.label(), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(m.dueDate() == null ? "On signing" : day(m.dueDate()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(amount(cur, m.amount()), plain(9), Element.ALIGN_RIGHT, null));
                }
                t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("Total of the schedule", bold(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell(amount(cur, total), bold(9), Element.ALIGN_RIGHT, SOFT));
                doc.add(t);
                if (price != null && total.compareTo(price) != 0) {
                    Paragraph note = new Paragraph("The schedule covers " + amount(cur, total) + " of the agreed price of " + amount(cur, price) + "; the rest is settled as agreed between the parties.", muted(8.5f));
                    note.setSpacingBefore(4);
                    doc.add(note);
                }
            }
            terms(doc, customTerms, STANDARD_AGREEMENT);
            signatures(doc, "The seller", "The buyer", businessName() + " (witness)");
        }, "Sale agreement " + d.code());
    }

    // ------------------------------------------------------------------ unit sales (a developer selling its own units)

    private static String unitLine(UnitSaleDto s, UnitRow u) {
        StringBuilder b = new StringBuilder("Unit ").append(s.unitNo());
        if (s.block() != null && !s.block().isBlank()) b.append(", block ").append(s.block());
        b.append(", ").append(s.projectName());
        return b.toString();
    }

    private String[][] unitFacts(UnitSaleDto s, ProjectDto pr, UnitRow u) {
        return new String[][]{{"Unit", unitLine(s, u)}, {"Type", nice(s.unitKind())}, {"Floor", u == null || u.floor() == null ? null : String.valueOf(u.floor())},
                {"Bedrooms", u == null || u.bedrooms() == null ? null : String.valueOf(u.bedrooms())}, {"Size", u == null || u.sizeSqm() == null ? null : money(u.sizeSqm()) + " m²"},
                {"Location", pr == null ? null : join(pr.address(), pr.area())}};
    }

    /** The reservation of a unit. */
    public byte[] saleReservation(UnitSaleDto s, ProjectDto pr, UnitRow u, String customTerms, String cur) {
        return build(doc -> {
            head(doc, "Reservation letter", "Reference " + s.code() + "  |  " + day(s.reservedOn() != null ? s.reservedOn() : LocalDate.now()));
            section(doc, "The buyer");
            doc.add(facts(new String[][]{{"Name", s.buyerName()}, {"Phone", s.buyerPhone()}, {"E-mail", s.buyerEmail()}}));
            section(doc, "The unit");
            doc.add(facts(unitFacts(s, pr, u)));
            section(doc, "The reservation");
            doc.add(facts(new String[][]{{"List price", amount(cur, s.listPrice())}, {"Discount", s.discount() != null && s.discount().signum() > 0 ? amount(cur, s.discount()) : null},
                    {"Agreed price", amount(cur, s.agreedPrice())}, {"Reserved until", day(s.reservationExpiry())}, {"Agent", s.agentName()}}));
            terms(doc, customTerms, STANDARD_RESERVATION);
            signatures(doc, "The buyer", businessName());
        }, "Reservation " + s.code());
    }

    /** The sale agreement of a unit with the payment schedule taken from its payment plan. */
    public byte[] saleAgreement(UnitSaleDto s, ProjectDto pr, UnitRow u, String customTerms, String cur) {
        return build(doc -> {
            head(doc, "Sale agreement", "Reference " + s.code() + "  |  " + day(s.agreementDate() != null ? s.agreementDate() : LocalDate.now()));
            section(doc, "The parties");
            String seller = pr != null && pr.developerName() != null && !pr.developerName().isBlank() ? pr.developerName().trim() : businessName();
            doc.add(facts(new String[][]{{"Seller", seller}, {"Buyer", s.buyerName()}, {"Buyer's phone", s.buyerPhone()}, {"Buyer's e-mail", s.buyerEmail()}}));
            section(doc, "The unit");
            doc.add(facts(unitFacts(s, pr, u)));
            section(doc, "The price");
            doc.add(facts(new String[][]{{"List price", amount(cur, s.listPrice())}, {"Discount", s.discount() != null && s.discount().signum() > 0 ? amount(cur, s.discount()) : null},
                    {"Agreed price", amount(cur, s.agreedPrice())}, {"Invoice", s.invoice() == null ? null : s.invoice().invoiceNumber()}}));
            section(doc, "Payment schedule");
            PlanDetailDto plan = s.plan();
            if (plan == null || plan.installments() == null || plan.installments().isEmpty()) {
                doc.add(new Paragraph("The agreed price of " + amount(cur, s.agreedPrice()) + " is payable in full"
                        + (s.invoice() == null ? "." : " under invoice " + s.invoice().invoiceNumber() + "."), plain(10)));
            } else {
                PdfPTable t = new PdfPTable(new float[]{0.7f, 2.4f, 2.2f, 2f, 2.4f});
                t.setWidthPercentage(100);
                headerCells(t, new String[]{"No.", "Due", "Principal", "Interest", "Amount due"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
                BigDecimal deposit = plan.terms() == null ? null : plan.terms().deposit();
                BigDecimal total = BigDecimal.ZERO;
                if (deposit != null && deposit.signum() > 0) {
                    t.addCell(cell("", plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell("Deposit, on signing", plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(deposit), plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(deposit), plain(9), Element.ALIGN_RIGHT, null));
                    total = total.add(deposit);
                }
                for (InstallmentDto i : plan.installments()) {
                    t.addCell(cell(String.valueOf(i.seq()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(day(i.dueDate()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(i.principalDue()), plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(i.interestDue()), plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(i.principalDue().add(i.interestDue())), plain(9), Element.ALIGN_RIGHT, null));
                    total = total.add(i.principalDue()).add(i.interestDue());
                }
                t.addCell(cell("", plain(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("Total to pay", bold(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell("", plain(9), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(amount(cur, total), bold(9), Element.ALIGN_RIGHT, SOFT));
                doc.add(t);
                List<String> notes = new ArrayList<>();
                if (plan.plan() != null && plan.plan().totalInterest() != null && plan.plan().totalInterest().signum() > 0) {
                    notes.add("The instalments include interest of " + amount(cur, plan.plan().totalInterest()) + ".");
                }
                if (plan.terms() != null) {
                    var tm = plan.terms();
                    boolean fixed = tm.lateFeeFixed() != null && tm.lateFeeFixed().signum() > 0;
                    boolean pct = tm.lateFeePercent() != null && tm.lateFeePercent().signum() > 0;
                    if (fixed || pct) {
                        notes.add("An instalment paid more than " + tm.graceDays() + " day(s) late carries a late charge of "
                                + (fixed ? amount(cur, tm.lateFeeFixed()) : "") + (fixed && pct ? " plus " : "") + (pct ? money(tm.lateFeePercent()) + "% of the instalment" : "") + ".");
                    }
                }
                for (String n : notes) {
                    Paragraph p = new Paragraph(n, muted(8.5f));
                    p.setSpacingBefore(3);
                    doc.add(p);
                }
            }
            terms(doc, customTerms, STANDARD_AGREEMENT);
            signatures(doc, "The seller", "The buyer", businessName() + " (witness)");
        }, "Sale agreement " + s.code());
    }

    /** The statement of a sold unit: what was agreed, what has been paid, the schedule with its state, and the balance. */
    public byte[] statement(UnitSaleDto s, ProjectDto pr, UnitRow u, List<PaymentRow> payments, String cur) {
        return build(doc -> {
            LocalDate today = LocalDate.now();
            head(doc, "Buyer statement", "Reference " + s.code() + "  |  as at " + day(today));
            section(doc, "The buyer");
            doc.add(facts(new String[][]{{"Name", s.buyerName()}, {"Phone", s.buyerPhone()}, {"E-mail", s.buyerEmail()}}));
            section(doc, "The unit");
            doc.add(facts(unitFacts(s, pr, u)));
            section(doc, "Where you stand");
            BigDecimal paid = s.invoice() == null || s.invoice().paid() == null ? BigDecimal.ZERO : s.invoice().paid();
            BigDecimal out = s.invoice() == null || s.invoice().outstanding() == null ? BigDecimal.ZERO : s.invoice().outstanding();
            BigDecimal overdue = s.plan() == null || s.plan().plan() == null || s.plan().plan().overdueAmount() == null ? BigDecimal.ZERO : s.plan().plan().overdueAmount();
            doc.add(facts(new String[][]{{"Agreed price", amount(cur, s.agreedPrice())}, {"Paid to date", amount(cur, paid)}, {"Balance owed", amount(cur, out)},
                    {"Overdue now", overdue.signum() > 0 ? amount(cur, overdue) : null}, {"Invoice", s.invoice() == null ? null : s.invoice().invoiceNumber()}}));
            PlanDetailDto plan = s.plan();
            if (plan != null && plan.installments() != null && !plan.installments().isEmpty()) {
                section(doc, "Payment schedule");
                PdfPTable t = new PdfPTable(new float[]{0.7f, 2.4f, 2.2f, 2.2f, 2.2f, 1.8f});
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                headerCells(t, new String[]{"No.", "Due", "Amount due", "Paid", "Balance", "Status"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT});
                for (InstallmentDto i : plan.installments()) {
                    boolean late = i.daysOverdue() > 0 && i.balance().signum() > 0;
                    Font f = late ? bold(9) : plain(9);
                    t.addCell(cell(String.valueOf(i.seq()), f, Element.ALIGN_LEFT, null));
                    t.addCell(cell(day(i.dueDate()), f, Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(i.totalDue()), f, Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(i.paid()), f, Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(i.balance()), f, Element.ALIGN_RIGHT, null));
                    t.addCell(cell(late ? "Overdue " + i.daysOverdue() + " d" : nice(i.state()), f, Element.ALIGN_LEFT, null));
                }
                doc.add(t);
            }
            section(doc, "Payments received");
            if (payments.isEmpty()) {
                doc.add(new Paragraph("No payment has been received yet.", muted(10)));
            } else {
                PdfPTable t = new PdfPTable(new float[]{2f, 3f, 2f, 2.4f});
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                headerCells(t, new String[]{"Date", "Receipt no.", "Method", "Amount"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (PaymentRow r : payments) {
                    t.addCell(cell(day(r.paidAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(r.receiptNumber()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(r.method()), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(r.amount()), plain(9), Element.ALIGN_RIGHT, null));
                }
                doc.add(t);
            }
            Paragraph close = new Paragraph((out.signum() > 0 ? "Balance owed at " : "Balance at ") + day(today) + ":  " + amount(cur, out), bold(12));
            close.setSpacingBefore(12);
            close.setAlignment(Element.ALIGN_RIGHT);
            doc.add(close);
        }, "Statement " + s.code());
    }

    /** What one person has earned in commission, what has gone to the accounts and what has been paid out, line by line. */
    public byte[] commissionStatement(RealEstateCommissionDtos.PayeeStatement st, String period, String cur) {
        return build(doc -> {
            LocalDate today = LocalDate.now();
            head(doc, "Commission statement", st.payeeName() + "  |  as at " + day(today));
            section(doc, "For");
            doc.add(facts(new String[][]{{"Name", st.payeeName()}, {"Who", st.payeeKind() == PayeeKind.STAFF ? "Staff" : "External (referrer or other agency)"}, {"Period", period}}));
            section(doc, "Where you stand");
            doc.add(facts(new String[][]{{"Earned on completed sales", amount(cur, st.earned())}, {"Paid out", amount(cur, st.paid())}, {"Still to be paid", amount(cur, st.owed())},
                    {"Sale not complete yet", st.planned().signum() > 0 ? amount(cur, st.planned()) : null},
                    {"Rejected (to post again)", st.rejected().signum() > 0 ? amount(cur, st.rejected()) : null}}));
            section(doc, "Commission by sale");
            PdfPTable t = new PdfPTable(new float[]{1.6f, 3f, 1.9f, 2.3f, 2f, 2.2f, 2.8f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            headerCells(t, new String[]{"Deal", "Property", "Completed", "Sale price", "Role", "Commission", "Status"},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT});
            for (RealEstateCommissionDtos.ShareLine l : st.lines()) {
                String status = switch (l.state()) {
                    case PLANNED -> "Not earned yet";
                    case TO_POST -> "Earned";
                    case AWAITING_APPROVAL -> "Awaiting approval";
                    case TO_PAY -> "Approved, to pay";
                    case PAID -> "Paid" + (l.paidOn() == null ? "" : " " + shortDay(l.paidOn()));
                    case REJECTED -> "Rejected";
                };
                Font f = l.state() == PayoutState.PAID ? plain(9) : bold(9);
                t.addCell(cell(l.dealCode(), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(join(l.propertyCode(), l.propertyTitle()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.completedDate() == null ? "" : shortDay(l.completedDate()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(l.salePrice()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(nz(l.role()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(l.amount()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(status, f, Element.ALIGN_LEFT, null));
            }
            doc.add(t);
            Paragraph close = new Paragraph((st.owed().signum() > 0 ? "Still to be paid at " : "Balance at ") + day(today) + ":  " + amount(cur, st.owed()), bold(12));
            close.setSpacingBefore(12);
            close.setAlignment(Element.ALIGN_RIGHT);
            doc.add(close);
        }, "Commission statement " + st.payeeName());
    }

    // ------------------------------------------------------------------ price list

    /** The price list of a project: its units by block, with the price of each. */
    public byte[] priceList(ProjectDto pr, List<UnitRow> units, boolean all, String contact, String cur) {
        return build(doc -> {
            head(doc, "Price list: " + pr.name(), "Prices as at " + day(LocalDate.now()) + (all ? "" : "  |  units still available"));
            doc.add(facts(new String[][]{{"Project", pr.code() + "  " + pr.name()}, {"Location", join(pr.address(), pr.area())}, {"Developer", pr.developerName()},
                    {"Handover", day(pr.handoverDate())}}));
            if (units.isEmpty()) {
                doc.add(new Paragraph("No units to list.", muted(10)));
                return;
            }
            String block = null;
            PdfPTable t = null;
            boolean first = true;
            for (UnitRow u : units) {
                String b = u.block() == null || u.block().isBlank() ? "" : u.block();
                if (first || !b.equals(block)) {
                    if (t != null) doc.add(t);
                    first = false;
                    block = b;
                    section(doc, b.isEmpty() ? "Units" : "Block " + b);
                    float[] widths = all ? new float[]{1.5f, 1.8f, 0.9f, 1.1f, 1.4f, 2.4f, 2f, 1.5f} : new float[]{1.5f, 1.8f, 0.9f, 1.1f, 1.4f, 2.4f, 2f};
                    t = new PdfPTable(widths);
                    t.setWidthPercentage(100);
                    t.setHeaderRows(1);
                    String[] heads = all ? new String[]{"Unit", "Type", "Floor", "Beds", "Size m²", "Price", "Per m²", "Status"} : new String[]{"Unit", "Type", "Floor", "Beds", "Size m²", "Price", "Per m²"};
                    int[] al = new int[heads.length];
                    for (int i = 0; i < al.length; i++) al[i] = i < 2 || i == 7 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
                    headerCells(t, heads, al);
                }
                t.addCell(cell(u.unitNo(), bold(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(nice(u.kind()), plain(9), Element.ALIGN_LEFT, null));
                t.addCell(cell(u.floor() == null ? "" : String.valueOf(u.floor()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(u.bedrooms() == null ? "" : String.valueOf(u.bedrooms()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(u.sizeSqm() == null ? "" : money(u.sizeSqm()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(amount(cur, u.listPrice()), plain(9), Element.ALIGN_RIGHT, null));
                t.addCell(cell(u.pricePerSqm() == null ? "" : money(u.pricePerSqm()), plain(9), Element.ALIGN_RIGHT, null));
                if (all) t.addCell(cell(nice(u.status()), plain(9), Element.ALIGN_LEFT, null));
            }
            if (t != null) doc.add(t);
            if (contact != null && !contact.isBlank()) {
                Paragraph c = new Paragraph("To reserve a unit or to ask about a payment plan, contact us: " + contact, bold(10));
                c.setSpacingBefore(14);
                doc.add(c);
            }
            Paragraph fine = new Paragraph("Prices can change without notice and a unit is held only once it is reserved.", muted(8.5f));
            fine.setSpacingBefore(4);
            doc.add(fine);
        }, "Price list " + pr.code());
    }

    // ------------------------------------------------------------------ flyer

    /** The flyer of a listed property: photos, price, the main facts and who to call. Never shows the owner. */
    public byte[] flyer(PropertyDto p, List<byte[]> photos, String contact, String cur) {
        return build(doc -> {
            boolean forSale = p.purpose() != ListingPurpose.RENT;
            boolean forRent = p.purpose() != ListingPurpose.SALE;
            doc.add(new Paragraph(businessName(), muted(10)));
            Paragraph kind = new Paragraph((forSale && forRent ? "FOR SALE OR TO LET" : forRent ? "TO LET" : "FOR SALE") + "  |  " + nice(p.type())
                    + (p.area() == null || p.area().isBlank() ? "" : "  |  " + p.area().trim()), bold(10));
            kind.setSpacingBefore(6);
            doc.add(kind);
            Paragraph title = new Paragraph(p.title(), bold(24));
            title.setSpacingBefore(2);
            doc.add(title);
            List<String> prices = new ArrayList<>();
            if (forSale && p.salePrice() != null) prices.add(amount(cur, p.salePrice()));
            if (forRent && p.rentPrice() != null) prices.add(amount(cur, p.rentPrice()) + " per month");
            if (!prices.isEmpty()) {
                Font pf = bold(17);
                pf.setColor(ACCENT);
                Paragraph pp = new Paragraph(String.join("   |   ", prices), pf);
                if (p.negotiable()) pp.add(new com.lowagie.text.Chunk("   negotiable", muted(10)));
                pp.setSpacingBefore(4);
                pp.setSpacingAfter(8);
                doc.add(pp);
            }
            List<Image> imgs = new ArrayList<>();
            for (byte[] b : photos) {
                try {
                    imgs.add(Image.getInstance(b));
                } catch (Exception ignore) {
                    // a photo type the PDF library cannot read (such as WebP) is left out
                }
            }
            if (!imgs.isEmpty()) {
                Image cover = imgs.get(0);
                cover.scaleToFit(515, 200);
                cover.setAlignment(Element.ALIGN_CENTER);
                doc.add(cover);
                if (imgs.size() > 1) {
                    int n = Math.min(3, imgs.size() - 1);
                    PdfPTable row = new PdfPTable(n);
                    row.setWidthPercentage(100);
                    row.setSpacingBefore(6);
                    for (int i = 1; i <= n; i++) {
                        Image im = imgs.get(i);
                        im.scaleToFit(160, 80);
                        PdfPCell c = new PdfPCell(im, false);
                        c.setBorder(Rectangle.NO_BORDER);
                        c.setHorizontalAlignment(Element.ALIGN_CENTER);
                        c.setPadding(2);
                        row.addCell(c);
                    }
                    doc.add(row);
                }
            }
            List<String[]> f = new ArrayList<>();
            if (p.bedrooms() != null) f.add(new String[]{"Bedrooms", String.valueOf(p.bedrooms())});
            if (p.bathrooms() != null) f.add(new String[]{"Bathrooms", String.valueOf(p.bathrooms())});
            if (p.parkingSpaces() != null && p.parkingSpaces() > 0) f.add(new String[]{"Parking", String.valueOf(p.parkingSpaces())});
            if (p.sizeSqm() != null) f.add(new String[]{"Built area", money(p.sizeSqm()) + " m²"});
            if (p.plotSqm() != null) f.add(new String[]{"Plot", money(p.plotSqm()) + " m²"});
            if (p.yearBuilt() != null) f.add(new String[]{"Built", String.valueOf(p.yearBuilt())});
            if (p.furnished()) f.add(new String[]{"Furnished", "Yes"});
            if (!f.isEmpty()) {
                int cols = Math.min(4, f.size());
                PdfPTable t = new PdfPTable(cols);
                t.setWidthPercentage(100);
                t.setSpacingBefore(10);
                for (String[] kv : f) {
                    PdfPCell c = new PdfPCell();
                    c.setBackgroundColor(SOFT);
                    c.setBorderColor(Color.WHITE);
                    c.setPadding(6);
                    c.addElement(new Paragraph(kv[0], muted(8.5f)));
                    c.addElement(new Paragraph(kv[1], bold(12)));
                    t.addCell(c);
                }
                for (int i = f.size() % cols; i != 0 && i < cols; i++) {
                    PdfPCell e = new PdfPCell(new Phrase(""));
                    e.setBorder(Rectangle.NO_BORDER);
                    t.addCell(e);
                }
                doc.add(t);
            }
            if (p.description() != null && !p.description().isBlank()) {
                String d = p.description().trim();
                if (d.length() > 700) d = d.substring(0, 697).trim() + "...";
                Paragraph dp = new Paragraph(d, plain(10f));
                dp.setSpacingBefore(12);
                doc.add(dp);
            }
            if (p.features() != null && !p.features().isBlank()) {
                Paragraph fp = new Paragraph("Features: " + p.features().trim().replaceAll("\\s*[\\r\\n]+\\s*", ", "), plain(10));
                fp.setSpacingBefore(8);
                doc.add(fp);
            }
            String where = join(p.address(), p.area());
            if (!where.isBlank()) {
                Paragraph lp = new Paragraph("Location: " + where, plain(10));
                lp.setSpacingBefore(6);
                doc.add(lp);
            }
            PdfPTable band = new PdfPTable(1);
            band.setWidthPercentage(100);
            band.setSpacingBefore(10);
            band.setKeepTogether(true);
            PdfPCell c = new PdfPCell();
            c.setBackgroundColor(DARK);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(10);
            Font w1 = bold(12);
            w1.setColor(Color.WHITE);
            Font w2 = plain(10);
            w2.setColor(Color.WHITE);
            c.addElement(new Paragraph(p.agentName() != null && !p.agentName().isBlank() ? "Call " + p.agentName() + " to arrange a viewing" : "Call us to arrange a viewing", w1));
            if (contact != null && !contact.isBlank()) c.addElement(new Paragraph(contact, w2));
            band.addCell(c);
            doc.add(band);
        }, p.code() + "  |  " + day(LocalDate.now()));
    }

    private static String join(String a, String b) {
        StringBuilder s = new StringBuilder();
        for (String x : new String[]{a, b}) if (x != null && !x.isBlank()) s.append(s.length() == 0 ? "" : ", ").append(x.trim());
        return s.toString();
    }
}
