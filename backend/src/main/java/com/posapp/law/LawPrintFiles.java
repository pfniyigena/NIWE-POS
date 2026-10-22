package com.posapp.law;

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
import com.posapp.law.dto.LawBillingDtos.BillDto;
import com.posapp.law.dto.LawBillingDtos.BillingSummary;
import com.posapp.law.dto.LawCollectionDtos.Aging;
import com.posapp.law.dto.LawCollectionDtos.FeeLine;
import com.posapp.law.dto.LawCollectionDtos.FeeStatementData;
import com.posapp.law.dto.LawDtos.DeadlineDto;
import com.posapp.law.dto.LawDtos.MatterDto;
import com.posapp.law.dto.LawDtos.PartyDto;
import com.posapp.law.dto.LawDtos.TeamDto;
import com.posapp.law.dto.LawPrintDtos.ClosingData;
import com.posapp.law.dto.LawPrintDtos.CoverData;
import com.posapp.law.dto.LawPrintDtos.EngagementData;
import com.posapp.law.dto.LawPrintDtos.StatementLine;
import com.posapp.law.dto.LawPrintDtos.TrustReceiptData;
import com.posapp.law.dto.LawPrintDtos.TrustStatementData;
import com.posapp.law.dto.LawPrintDtos.WaitingLine;
import com.posapp.law.dto.LawTrustDtos.ReconciliationDto;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The printed papers of the Law Firm module as A4 PDFs, headed with the firm's name and address from the store settings. This phase: the client-money (trust) papers,
 * a receipt for money received, a payment voucher for money paid out or taken as fees, the client's trust ledger statement and the reconciliation sheet.
 */
@Component
public class LawPrintFiles {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final Color DARK = new Color(0x33, 0x33, 0x33);
    private static final Color SOFT = new Color(0xF0, 0xF0, 0xF0);

    private final StoreSettingsRepository storeSettingsRepository;

    public LawPrintFiles(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    // ------------------------------------------------------------------ small helpers

    private StoreSettings store() {
        return storeSettingsRepository.findAll().stream().findFirst().orElse(null);
    }

    public String businessName() {
        StoreSettings s = store();
        return s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : "Law firm";
    }

    public String currency() {
        StoreSettings s = store();
        return s != null && s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank() ? s.getCurrencyCode().trim() : "";
    }

    /** The firm's phone and e-mail on one line; empty when neither is set. */
    public String contactLine() {
        StoreSettings s = store();
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getPhone(), s.getEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim());
        return b.toString();
    }

    static String money(BigDecimal v) {
        if (v == null) return "";
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    static String amount(String cur, BigDecimal v) {
        return v == null ? "" : (cur == null || cur.isBlank() ? "" : cur + " ") + money(v);
    }

    static String day(LocalDate d) {
        return d == null ? "" : d.format(DAY);
    }

    static String shortDay(LocalDate d) {
        return d == null ? "" : SHORT.format(d);
    }

    static String nz(String s) {
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

    // ------------------------------------------------------------------ client money (trust account)

    /** A receipt for money received (a deposit), or a payment voucher for money paid out, taken as fees or taken back. */
    public byte[] trustReceipt(TrustReceiptData r) {
        String cur = currency();
        boolean in = r.type() == com.posapp.law.TrustType.DEPOSIT;
        String title = switch (r.type()) {
            case DEPOSIT -> "Receipt for client money";
            case PAYOUT -> "Trust account payment voucher";
            case FEE_TRANSFER -> "Trust account fee transfer";
            case DEPOSIT_REVERSAL -> "Trust account deposit reversal";
        };
        return build(doc -> {
            head(doc, title, r.number() + "  |  " + day(r.txDate()));
            doc.add(facts(new String[][]{{"Matter", r.matterNumber() + "  " + r.matterTitle()}, {"Client", r.clientName()}}));
            section(doc, in ? "The money received" : r.type() == com.posapp.law.TrustType.FEE_TRANSFER ? "The transfer" : "The payment");
            doc.add(facts(new String[][]{{"Amount", amount(cur, r.amount())},
                    {in ? "Received from" : r.type() == com.posapp.law.TrustType.FEE_TRANSFER ? "Taken for" : "Paid to", r.partyName()},
                    {"Invoice", r.invoiceNumber()}, {"Method", r.method()}, {"Reference", r.reference()}, {"Purpose", r.purpose()}}));
            section(doc, "Client account");
            doc.add(facts(new String[][]{{"Held for this client after this entry", amount(cur, r.balanceAfter())}, {"Trust account", join(r.bankName(), r.accountNumber())},
                    {"Asked by", r.requestedBy()}, {"Approved by", r.approvedBy()}}));
            Paragraph note = new Paragraph(in
                    ? "This money is held in the firm's client money (trust) account for the client named above. It is not the firm's money and is paid out only as the client instructs or to pay fees the client has been billed."
                    : "This entry was posted to the client's trust ledger on the date shown.", muted(9));
            note.setSpacingBefore(10);
            doc.add(note);
            signatures(doc, in ? "Received for " + businessName() : "Approved for " + businessName(), in ? "Paid in by" : "Received by");
        }, title + " " + r.number());
    }

    /** The client's trust ledger for a matter: the balance brought forward, every movement and the closing balance, with anything waiting listed apart. */
    public byte[] trustStatement(TrustStatementData d) {
        String cur = currency();
        return build(doc -> {
            String period = d.from() == null ? "Up to " + day(d.to()) : day(d.from()) + " to " + day(d.to());
            head(doc, "Client trust account statement", d.matterNumber() + "  |  " + period);
            doc.add(facts(new String[][]{{"Client", d.clientName()}, {"Matter", d.matterNumber() + "  " + d.matterTitle()}, {"Trust account", join(d.bankName(), d.accountNumber())}}));
            section(doc, "Movements");
            PdfPTable t = new PdfPTable(new float[]{1.3f, 1.1f, 3f, 1.4f, 1.3f, 1.3f, 1.4f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            headerCells(t, new String[]{"Date", "No.", "What", "Party", "Money in", "Money out", "Balance"},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            if (d.from() != null) {
                t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("Balance brought forward", bold(8.5f), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8.5f), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell("", plain(8.5f), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(money(d.opening()), bold(8.5f), Element.ALIGN_RIGHT, SOFT));
            }
            for (StatementLine l : d.lines()) {
                t.addCell(cell(shortDay(l.date()), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.number(), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(l.what() + (nz(l.reference()).isBlank() ? "" : " (ref " + l.reference() + ")"), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(nz(l.party()), plain(8.5f), Element.ALIGN_LEFT, null));
                t.addCell(cell(money(l.moneyIn()), plain(8.5f), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.moneyOut()), plain(8.5f), Element.ALIGN_RIGHT, null));
                t.addCell(cell(money(l.balance()), plain(8.5f), Element.ALIGN_RIGHT, null));
            }
            if (d.lines().isEmpty()) {
                PdfPCell c = cell("No movements in this period.", muted(9), Element.ALIGN_LEFT, null);
                c.setColspan(7);
                t.addCell(c);
            }
            t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("Totals and closing balance", bold(8.5f), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell("", plain(8.5f), Element.ALIGN_LEFT, SOFT));
            t.addCell(cell(money(d.totalIn()), bold(8.5f), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(money(d.totalOut()), bold(8.5f), Element.ALIGN_RIGHT, SOFT));
            t.addCell(cell(money(d.closing()), bold(8.5f), Element.ALIGN_RIGHT, SOFT));
            doc.add(t);
            Paragraph held = new Paragraph("Held for you on " + day(d.to()) + ": " + amount(cur, d.closing()), bold(11));
            held.setSpacingBefore(10);
            doc.add(held);
            if (!d.waiting().isEmpty()) {
                section(doc, "Waiting for approval (not yet taken from the balance above)");
                PdfPTable w = new PdfPTable(new float[]{1.3f, 1.1f, 2f, 3f, 1.4f});
                w.setWidthPercentage(100);
                headerCells(w, new String[]{"Asked on", "No.", "What", "Party", "Amount"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (WaitingLine l : d.waiting()) {
                    w.addCell(cell(shortDay(l.date()), plain(8.5f), Element.ALIGN_LEFT, null));
                    w.addCell(cell(l.number(), plain(8.5f), Element.ALIGN_LEFT, null));
                    w.addCell(cell(l.what(), plain(8.5f), Element.ALIGN_LEFT, null));
                    w.addCell(cell(nz(l.party()), plain(8.5f), Element.ALIGN_LEFT, null));
                    w.addCell(cell(money(l.amount()), plain(8.5f), Element.ALIGN_RIGHT, null));
                }
                doc.add(w);
            }
            Paragraph note = new Paragraph("Client money is held in the firm's trust account and is not the firm's money. If anything here looks wrong, please tell us"
                    + (contactLine().isBlank() ? "." : " (" + contactLine() + ")."), muted(9));
            note.setSpacingBefore(10);
            doc.add(note);
        }, "Trust statement " + d.matterNumber());
    }

    /** One saved reconciliation of the trust bank account against the client ledgers and the books, with lines to sign. */
    public byte[] reconciliation(ReconciliationDto r, String bankLine) {
        String cur = currency();
        return build(doc -> {
            head(doc, "Trust account reconciliation", "As of " + day(r.asOfDate()));
            doc.add(facts(new String[][]{{"Trust account", bankLine}, {"Prepared by", r.preparedByName()}, {"Note", r.note()}}));
            section(doc, "Bank side");
            PdfPTable b = new PdfPTable(new float[]{4f, 1.6f});
            b.setWidthPercentage(100);
            row(b, "Closing balance on the bank statement", amount(cur, r.statementBalance()), false);
            row(b, "Add: deposits in transit", amount(cur, r.depositsInTransit()), false);
            row(b, "Less: outstanding payments", amount(cur, r.outstandingPayments()), false);
            row(b, "Adjusted bank balance", amount(cur, r.adjustedBank()), true);
            doc.add(b);
            section(doc, "The three figures that must agree");
            PdfPTable g = new PdfPTable(new float[]{4f, 1.6f});
            g.setWidthPercentage(100);
            row(g, "Client ledgers (total held for clients)", amount(cur, r.ledgerTotal()), false);
            row(g, "Trust bank account in the books", amount(cur, r.glBank()), false);
            row(g, "Client money held, in the books", amount(cur, r.glLiability()), false);
            row(g, "Difference (adjusted bank less client ledgers)", amount(cur, r.difference()), true);
            doc.add(g);
            Paragraph res = new Paragraph(r.balanced() ? "Result: BALANCED" : "Result: NOT BALANCED. The difference above must be explained and cleared.", bold(12));
            res.setSpacingBefore(12);
            doc.add(res);
            signatures(doc, "Prepared by", "Reviewed by (partner)");
        }, "Trust reconciliation " + shortDay(r.asOfDate()));
    }

    private static void row(PdfPTable t, String label, String value, boolean strong) {
        Color bg = strong ? SOFT : null;
        t.addCell(cell(label, strong ? bold(9.5f) : plain(9.5f), Element.ALIGN_LEFT, bg));
        t.addCell(cell(value, strong ? bold(9.5f) : plain(9.5f), Element.ALIGN_RIGHT, bg));
    }

    static String join(String a, String b) {
        String x = nz(a).trim();
        String y = nz(b).trim();
        return x.isEmpty() ? y : y.isEmpty() ? x : x + "  |  " + y;
    }

    // ------------------------------------------------------------------ matter papers: cover sheet, engagement letter, closing letter

    private static void para(Document doc, String text) throws Exception {
        if (text == null || text.isBlank()) return;
        Paragraph p = new Paragraph(text.trim(), plain(10.5f));
        p.setSpacingAfter(6);
        p.setLeading(14);
        doc.add(p);
    }

    private static String niceWord(String name) {
        if (name == null || name.isBlank()) return "";
        String t = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String teamRole(TeamRole r) {
        return r == TeamRole.LEAD ? "Lead lawyer" : niceWord(r == null ? null : r.name());
    }

    static String billingLabel(BillingType t) {
        if (t == null) return "";
        return switch (t) {
            case HOURLY -> "Hourly";
            case FIXED -> "Fixed fee";
            case RETAINER -> "Retainer";
            case CONTINGENCY -> "Contingency (a percentage of what is recovered)";
            case PRO_BONO -> "No fee (pro bono)";
        };
    }

    private static PdfPTable grid(float[] widths, String[] heads) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        int[] aligns = new int[heads.length];
        for (int i = 0; i < aligns.length; i++) aligns[i] = Element.ALIGN_LEFT;
        headerCells(t, heads, aligns);
        t.setSpacingAfter(4);
        return t;
    }

    private static void gridRow(PdfPTable t, String... values) {
        for (String v : values) t.addCell(cell(v, plain(9), Element.ALIGN_LEFT, null));
    }

    /** The sheet that goes on the front of the paper file. */
    public byte[] coverSheet(CoverData d) {
        MatterDto m = d.matter();
        String cur = currency();
        return build(doc -> {
            head(doc, "Matter file cover sheet", m.number() + "  |  printed " + day(d.printedOn()) + (d.printedBy() != null && !d.printedBy().isBlank() ? " by " + d.printedBy() : ""));
            if (m.restricted()) {
                Font red = bold(10);
                red.setColor(new Color(0xB9, 0x1C, 0x1C));
                Paragraph r = new Paragraph("RESTRICTED: only the matter team may see this file", red);
                r.setSpacingAfter(6);
                doc.add(r);
            }
            String status = niceWord(m.status().name()) + (m.closedDate() != null ? " on " + day(m.closedDate()) : "");
            String conflict = m.conflictCleared() ? "Cleared" + (m.conflictClearedBy() != null ? " by " + m.conflictClearedBy() : "") + (m.conflictClearedAt() != null
                    ? " on " + day(m.conflictClearedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) : "") : "NOT CLEARED";
            doc.add(facts(new String[][]{{"Matter", m.number() + "  " + m.title()}, {"Client", m.clientName()}, {"Client phone", m.clientPhone()}, {"Client e-mail", m.clientEmail()},
                    {"Practice area", m.practiceArea()}, {"Jurisdiction", m.jurisdiction()},
                    {"Court or forum", join(m.courtOrForum(), m.courtCaseNumber() == null || m.courtCaseNumber().isBlank() ? null : "Case no. " + m.courtCaseNumber())},
                    {"Opened", day(m.openedDate())}, {"Status", status}, {"Outcome", m.outcome()}, {"Lead lawyer", m.leadName()}, {"Fees", billingLabel(m.billingType())},
                    {"Conflict check", conflict}, {"Client money held", d.trustEnabled() && d.trustHeld() != null && d.trustHeld().signum() > 0 ? amount(cur, d.trustHeld()) : null}}));
            if (m.description() != null && !m.description().isBlank()) {
                section(doc, "About this matter");
                para(doc, m.description());
            }
            if (m.team() != null && !m.team().isEmpty()) {
                section(doc, "Team");
                PdfPTable t = grid(new float[]{3f, 2f}, new String[]{"Name", "Part"});
                for (TeamDto x : m.team()) gridRow(t, x.userName(), teamRole(x.role()));
                doc.add(t);
            }
            if (m.parties() != null && !m.parties().isEmpty()) {
                section(doc, "Other people on the matter");
                PdfPTable t = grid(new float[]{2f, 3f, 2f, 3f}, new String[]{"Part", "Name", "Phone", "E-mail"});
                for (PartyDto x : m.parties()) gridRow(t, niceWord(x.role() == null ? null : x.role().name()), x.name(), nz(x.phone()), nz(x.email()));
                doc.add(t);
            }
            List<DeadlineDto> open = m.deadlines() == null ? List.of() : m.deadlines().stream().filter(x -> x.status() == DeadlineStatus.OPEN)
                    .sorted(Comparator.comparing(DeadlineDto::dueDate)).limit(12).toList();
            if (!open.isEmpty()) {
                section(doc, "Open deadlines");
                PdfPTable t = grid(new float[]{2f, 4f, 2f, 2.5f}, new String[]{"Date", "What", "Type", "Assigned to"});
                for (DeadlineDto x : open) gridRow(t, shortDay(x.dueDate()) + (x.dueTime() != null ? " " + x.dueTime().toString().substring(0, 5) : ""), x.title(), niceWord(x.type() == null ? null : x.type().name()), nz(x.assignedToName()));
                doc.add(t);
            }
            section(doc, "Paper file");
            for (String line : new String[]{"File location / box number:  ____________________________________", "Archive until:  ____________________________________", "Notes:  ________________________________________________________________"}) {
                Paragraph p = new Paragraph(line, plain(10));
                p.setSpacingAfter(8);
                doc.add(p);
            }
        }, "Cover sheet " + m.number());
    }

    private void letterHead(Document doc, String title, String ref, LocalDate date, MatterDto m) throws Exception {
        head(doc, title, "Our ref: " + ref + "  |  " + day(date));
        Paragraph to = new Paragraph(nz(m.clientName()), bold(11));
        doc.add(to);
        String contact = join(m.clientPhone(), m.clientEmail());
        if (!contact.isBlank()) doc.add(new Paragraph(contact, muted(9)));
        Paragraph dear = new Paragraph("Dear " + nz(m.clientName()) + ",", plain(10.5f));
        dear.setSpacingBefore(12);
        dear.setSpacingAfter(6);
        doc.add(dear);
        Paragraph re = new Paragraph("Re: " + m.number() + "  " + m.title(), bold(10.5f));
        re.setSpacingAfter(8);
        doc.add(re);
    }

    /** The letter that sets out what the firm will do and how it charges. Uses the fee terms already on the matter. */
    public byte[] engagementLetter(EngagementData d) {
        MatterDto m = d.matter();
        String cur = currency();
        var terms = d.billing().terms();
        return build(doc -> {
            letterHead(doc, "Engagement and fee letter", m.number(), d.letterDate(), m);
            para(doc, "Thank you for choosing " + businessName() + " to act for you. This letter sets out what we will do, who will do it and how we will charge. Please read it and, if you agree, sign and return a copy.");
            section(doc, "1. What we will do for you");
            String scope = d.scope() != null && !d.scope().isBlank() ? d.scope() : m.description();
            para(doc, scope != null && !scope.isBlank() ? scope : "We will act for you in the matter named above.");
            section(doc, "2. Who will work on your matter");
            para(doc, (m.leadName() != null ? m.leadName() + " is the lawyer responsible for your matter and your first point of contact. " : "")
                    + (m.team() != null && m.team().size() > 1 ? "The team is:" : ""));
            if (m.team() != null && m.team().size() > 1) {
                PdfPTable t = grid(new float[]{3f, 2f}, new String[]{"Name", "Part"});
                for (TeamDto x : m.team()) gridRow(t, x.userName(), teamRole(x.role()));
                doc.add(t);
            }
            section(doc, "3. Our fees");
            BillingType bt = terms.billingType() != null ? terms.billingType() : m.billingType();
            switch (bt) {
                case HOURLY -> {
                    para(doc, "We charge for the time spent on your matter. " + (terms.hourlyRate() != null && terms.hourlyRate().signum() > 0
                            ? "Our standard rate is " + amount(cur, terms.hourlyRate()) + " per hour." : "The hourly rates are set out below.")
                            + " Time is recorded as the work is done and billed at the rate of the person who did it.");
                    if (d.billing().rates() != null && !d.billing().rates().isEmpty()) {
                        PdfPTable t = grid(new float[]{3f, 2f}, new String[]{"Person", "Rate per hour"});
                        for (var r : d.billing().rates()) gridRow(t, r.userName(), amount(cur, r.hourlyRate()));
                        doc.add(t);
                    }
                }
                case FIXED -> para(doc, "We will charge a fixed fee of " + (terms.fixedFee() != null ? amount(cur, terms.fixedFee()) : "the amount agreed") + " for the work described above.");
                case RETAINER -> para(doc, "We ask you to pay a retainer of " + (terms.retainerAmount() != null ? amount(cur, terms.retainerAmount()) : "the amount agreed")
                        + ". We will invoice it before we start. Our fees are set against it as the work is done.");
                case CONTINGENCY -> para(doc, "Our fee is " + (terms.contingencyPercent() != null ? terms.contingencyPercent().stripTrailingZeros().toPlainString() + "%" : "a percentage")
                        + " of the amount recovered for you.");
                case PRO_BONO -> para(doc, "We are acting for you without charging a fee for our time.");
                default -> {
                }
            }
            if (d.billing().stages() != null && !d.billing().stages().isEmpty()) {
                para(doc, "Our fee is payable in these stages:");
                PdfPTable t = grid(new float[]{4f, 2f, 2f}, new String[]{"Stage", "Due", "Amount"});
                for (var st : d.billing().stages()) gridRow(t, st.description(), st.dueDate() != null ? shortDay(st.dueDate()) : "", amount(cur, st.amount()));
                doc.add(t);
            }
            if (terms.billingNote() != null && !terms.billingNote().isBlank()) para(doc, terms.billingNote());
            section(doc, "4. Expenses");
            para(doc, "Costs we pay out for you, such as court fees, search fees and travel, are charged to you in addition to our fees, unless this letter says otherwise. We will send you an invoice, and you can ask us for a statement of the costs at any time.");
            int n = 5;
            if (d.trustEnabled()) {
                section(doc, n++ + ". Money we hold for you");
                para(doc, "Money you give us for your matter, other than to pay our invoices, is kept in our client account, apart from the firm's own money. We pay it out only as you instruct or to pay fees you have been billed, and we can send you a statement of it whenever you ask.");
            }
            if (d.terms() != null && !d.terms().isBlank()) {
                section(doc, n++ + ". Other terms");
                para(doc, d.terms());
            }
            section(doc, n + ". Your agreement");
            para(doc, "Please sign below and return a copy to us. If anything in this letter is not what you expected, or if your instructions change, tell us straight away." + (contactLine().isEmpty() ? "" : " You can reach us on " + contactLine() + "."));
            para(doc, "Yours sincerely,");
            signatures(doc, "For " + businessName() + (d.signedBy() != null ? " (" + d.signedBy() + ")" : ""), "Client: " + nz(m.clientName()) + " (agreed)");
        }, "Engagement letter " + m.number());
    }

    /** The letter that closes the matter: what was done, the final bills and any client money still held. */
    public byte[] closingLetter(ClosingData d) {
        MatterDto m = d.matter();
        String cur = currency();
        BillingSummary s = d.billing().summary();
        List<BillDto> bills = d.billing().bills() == null ? List.of() : d.billing().bills().stream()
                .filter(b -> b.status() == BillStatus.ACTIVE && b.kind() == BillKind.INVOICE && b.invoiceNumber() != null).toList();
        return build(doc -> {
            letterHead(doc, "Closing letter", m.number(), d.letterDate(), m);
            para(doc, "We have now finished our work on your matter" + (m.closedDate() != null ? ", and we closed our file on " + day(m.closedDate()) : "") + "." + (m.outcome() != null && !m.outcome().isBlank() ? " The outcome: " + m.outcome().trim() : ""));
            if (d.notes() != null && !d.notes().isBlank()) para(doc, d.notes());
            section(doc, "Fees and costs");
            if (!bills.isEmpty()) {
                PdfPTable t = new PdfPTable(new float[]{2.5f, 2f, 2f, 2f});
                t.setWidthPercentage(100);
                headerCells(t, new String[]{"Invoice", "Amount", "Paid", "Still to pay"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
                for (BillDto b : bills) {
                    t.addCell(cell(b.invoiceNumber(), plain(9), Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(b.amount()), plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(b.amountPaid()), plain(9), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(b.amountOutstanding()), plain(9), Element.ALIGN_RIGHT, null));
                }
                t.addCell(cell("Total", bold(9), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell(money(s.billed()), bold(9), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(money(s.paid()), bold(9), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(money(s.outstanding()), bold(9), Element.ALIGN_RIGHT, SOFT));
                t.setSpacingAfter(6);
                doc.add(t);
            } else {
                para(doc, "No fees or costs have been invoiced on this matter.");
            }
            para(doc, s.outstanding() != null && s.outstanding().signum() > 0
                    ? "An amount of " + amount(cur, s.outstanding()) + " is still unpaid. Please pay it as soon as you can" + (contactLine().isEmpty() ? "." : ", or contact us on " + contactLine() + " if you have a question about it.")
                    : "Everything we have invoiced has been paid. Thank you.");
            if (d.trustEnabled()) {
                section(doc, "Money we hold for you");
                para(doc, d.trustHeld() != null && d.trustHeld().signum() > 0
                        ? "We still hold " + amount(cur, d.trustHeld()) + " for you in our client account. Please tell us in writing where to pay it, and we will do so."
                        : "We hold no money for you in our client account.");
            }
            section(doc, "Your documents and our file");
            para(doc, "If we hold original documents of yours, please tell us how you would like to collect them or have them sent. We keep our file for the period the law and our rules require and may destroy it after that. If you want a copy of anything on it, ask us before then.");
            para(doc, "Thank you for instructing us. If you need us again, or if you have any question about this matter, please get in touch." + (contactLine().isEmpty() ? "" : " " + contactLine() + "."));
            para(doc, "Yours sincerely,");
            signatures(doc, "For " + businessName() + (d.signedBy() != null ? " (" + d.signedBy() + ")" : ""));
        }, "Closing letter " + m.number());
    }

    // ------------------------------------------------------------------ fee collections

    /** A client's statement of fees: every invoice with what is paid and owed, and how late the unpaid ones are. */
    public byte[] feeStatement(FeeStatementData d) {
        String cur = currency();
        return build(doc -> {
            head(doc, "Statement of fees", "Up to " + day(d.asOf()) + (d.from() != null || d.to() != null
                    ? "  |  invoices " + (d.from() != null ? "from " + day(d.from()) : "") + (d.from() != null && d.to() != null ? " " : "") + (d.to() != null ? "to " + day(d.to()) : "") : ""));
            doc.add(facts(new String[][]{{"Client", d.clientName()}, {"Phone", d.clientPhone()}, {"E-mail", d.clientEmail()}}));
            Paragraph held = new Paragraph("Still to pay on " + day(d.asOf()) + ": " + amount(cur, d.totalOutstanding()), bold(11));
            held.setSpacingBefore(6);
            held.setSpacingAfter(6);
            doc.add(held);
            section(doc, "Invoices");
            if (d.lines().isEmpty()) {
                doc.add(new Paragraph("No invoices have been issued in this period.", muted(9.5f)));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1.7f, 2.2f, 3.4f, 1.9f, 1.9f, 1.9f, 1.7f, 1.6f});
                t.setWidthPercentage(100);
                headerCells(t, new String[]{"Date", "Invoice", "Matter", "Amount", "Paid", "To pay", "Due", "Late (days)"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (FeeLine l : d.lines()) {
                    t.addCell(cell(shortDay(l.date()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(l.invoiceNumber()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(l.matter()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(money(l.amount()), plain(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(l.paid()), plain(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(money(l.outstanding()), l.outstanding().signum() > 0 ? bold(8) : plain(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(l.outstanding().signum() > 0 ? shortDay(l.dueDate()) : "", plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(l.daysOverdue() > 0 ? String.valueOf(l.daysOverdue()) : "", plain(8), Element.ALIGN_RIGHT, null));
                }
                t.addCell(cell("Total", bold(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell(money(d.totalBilled()), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(money(d.totalPaid()), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(money(d.totalOutstanding()), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                doc.add(t);
            }
            if (d.totalOutstanding().signum() > 0) {
                Aging g = d.aging();
                section(doc, "How late");
                PdfPTable a = new PdfPTable(5);
                a.setWidthPercentage(100);
                headerCells(a, new String[]{"Not yet due", "1 to 30 days", "31 to 60 days", "61 to 90 days", "Over 90 days"}, new int[]{Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
                for (BigDecimal v : new BigDecimal[]{g.current(), g.days1to30(), g.days31to60(), g.days61to90(), g.over90()}) a.addCell(cell(money(v), plain(9), Element.ALIGN_RIGHT, null));
                doc.add(a);
                Paragraph note = new Paragraph("Please pay the amount that is overdue as soon as you can" + (contactLine().isEmpty() ? "." : ", or contact us on " + contactLine() + " if there is a problem with any invoice."), muted(9));
                note.setSpacingBefore(8);
                doc.add(note);
            }
        }, "Statement of fees " + shortDay(d.asOf()));
    }
}
